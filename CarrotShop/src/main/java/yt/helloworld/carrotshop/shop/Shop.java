package yt.helloworld.carrotshop.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.spongepowered.api.block.tileentity.TileEntity;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.manipulator.mutable.tileentity.SignData;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.entity.spawn.EntitySpawnCause;
import org.spongepowered.api.event.cause.entity.spawn.SpawnTypes;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColor;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.extent.Extent;

import ninja.leaping.configurate.objectmapping.Setting;
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable;
import yt.helloworld.carrotshop.ShopsData;

@ConfigSerializable
public abstract class Shop {

	@Setting
	private UUID owner = null;
	@Setting
	private Location<World> location = null;

	public Shop() {
	}

	public Shop(Location<World> loc) {
		Optional<TileEntity> tile = loc.getTileEntity();
		if (!tile.isPresent() || !tile.get().supports(SignData.class))
			throw new ExceptionInInitializerError("Improbable error: managed to trigger a shop creation event from something other than a sign");
		location = loc;
	}

	public abstract void info(Player player);
	public abstract boolean trigger(Player player);

	public boolean update() {
		setOK();
		return true;
	}

	public final boolean destroy(Player player) {
		if (isOwner(player)) {
			setReset();
			ShopsData.delShop(this);
			return true;
		}
		return false;
	}

	public List<Location<World>> getLocations() {
		List<Location<World>> locations = new ArrayList<>();
		locations.add(location);
		return locations;
	}

	protected final void setOwner(Player player) {
		player.sendMessage(Text.of("set as owner"));
		owner = player.getUniqueId();
	}

	protected final UUID getOwner() {
		return owner;
	}

	protected final boolean isOwner(Player player) {
		if (owner != null) {
			if (owner.equals(player.getUniqueId()))
				return true;
		}
		return player.hasPermission("carrotshop.admin");
	}

	protected final void setOK() {
		setFirstLineColor(TextColors.DARK_BLUE);
	}

	protected final void setFail() {
		setFirstLineColor(TextColors.RED);
	}

	public final void setReset() {
		setFirstLineColor(TextColors.RESET);
	}

	private final void setFirstLineColor(TextColor color) {
		Optional<TileEntity> sign = location.getTileEntity();
		if (sign.isPresent() && sign.get().supports(SignData.class)) {
			Optional<SignData> data = sign.get().getOrCreate(SignData.class);
			if (data.isPresent()) {
				SignData signData = data.get();
				signData.set(signData.lines().set(0, Text.of(color, signData.lines().get(0).toPlain())));
				sign.get().offer(signData);
			}
		}
	}

	static public void putItemInWorld(ItemStackSnapshot itemStackSnapshop, Location<World> spawnLocation) {
		Extent extent = spawnLocation.getExtent();
		Entity item = extent.createEntity(EntityTypes.ITEM, spawnLocation.getPosition());
		item.offer(Keys.REPRESENTED_ITEM, itemStackSnapshop);
		extent.spawnEntity(item, Cause.source(EntitySpawnCause.builder()
				.entity(item).type(SpawnTypes.PLUGIN).build()).build());

	}

	static protected final int getPrice(Location<World> location) {
		Optional<TileEntity> sign = location.getTileEntity();
		if (sign.isPresent() && sign.get().supports(SignData.class)) {
			Optional<SignData> data = sign.get().get(SignData.class);
			if (data.isPresent()) {
				String priceLine = data.get().lines().get(3).toPlain().replaceAll("[^\\d]", "");
				if (priceLine.length() == 0)
					return -1;
				return Integer.parseInt(priceLine);

			}
		}
		return -1;
	}

	static public boolean hasEnough(Inventory player, Inventory needs) {
		for (Inventory item : needs.slots()) {
			if (item.peek().isPresent()) {
				ItemType type = item.peek().get().getItem();
				if (player.query(type).totalItems() < needs.query(type).totalItems())
					return false;
			}
		}
		return true;
	}

	static public boolean build(Player player, Location<World> target) {
		Optional<TileEntity> sign = target.getTileEntity();
		if (sign.isPresent() && sign.get().supports(SignData.class)) {
			Optional<SignData> data = sign.get().get(SignData.class);
			if (data.isPresent()) {
				SignData signData = data.get();
				Shop shop;
				try {
					switch (signData.lines().get(0).toPlain().toLowerCase()) {
					case "[itrade]":
						shop = new iTrade(player, target);
						break;
					case "[ibuy]":
						shop = new iBuy(player, target);
						break;
					case "[isell]":
						shop = new iSell(player, target);
						break;
					case "[trade]":
						shop = new Trade(player, target);
						break;
					case "[buy]":
						shop = new Buy(player, target);
						break;
					case "[sell]":
						shop = new Sell(player, target);
						break;
					default:
						return false;
					}
				} catch (ExceptionInInitializerError e) {
					player.sendMessage(Text.of(TextColors.DARK_RED, e.getMessage()));
					return false;
				}
				for (Location<World> loc : shop.getLocations()) {
					Optional<Shop> oldShop = ShopsData.getShop(loc);
					if (oldShop.isPresent()) {
						if (!oldShop.get().destroy(player)) {
							player.sendMessage(Text.of(TextColors.DARK_RED, "This shop would override a shop you do now own. Abort."));
							for (Location<World> loc2 : shop.getLocations()) {
								Optional<Shop> oldShop2 = ShopsData.getShop(loc2);
								if (oldShop.isPresent())
									oldShop2.get().update();
							}
							return false;
						}
					}
				}
				for (Location<World> loc : shop.getLocations()) {
					Optional<Shop> oldShop = ShopsData.getShop(loc);
					if (oldShop.isPresent()) {
						ShopsData.delShop(oldShop.get());
					}
				}
				ShopsData.addShop(shop);
				return true;
			}
		}
		return false;
	}

}
