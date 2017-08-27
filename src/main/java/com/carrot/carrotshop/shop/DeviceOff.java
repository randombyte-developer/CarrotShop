package com.carrot.carrotshop.shop;

import java.math.BigDecimal;
import java.util.List;
import java.util.Stack;

import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.service.economy.account.UniqueAccount;
import org.spongepowered.api.service.economy.transaction.ResultType;
import org.spongepowered.api.service.economy.transaction.TransactionResult;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import com.carrot.carrotshop.CarrotShop;
import com.carrot.carrotshop.ShopsData;

import ninja.leaping.configurate.objectmapping.Setting;
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable;

@ConfigSerializable
public class DeviceOff extends Shop {
	@Setting
	private Location<World> lever;
	@Setting
	private int price;

	public DeviceOff() {
	}

	public DeviceOff(Player player, Location<World> sign) throws ExceptionInInitializerError {
		super(sign);
		if (!player.hasPermission("carrotshop.create.device"))
			throw new ExceptionInInitializerError("You don't have perms to build a device sign");
		Stack<Location<World>> locations = ShopsData.getItemLocations(player);
		if (locations.isEmpty())
			throw new ExceptionInInitializerError("Device signs require a lever");
		BlockState targetBlock = locations.peek().getBlock();
		if (!targetBlock.getType().equals(BlockTypes.LEVER))
			throw new ExceptionInInitializerError("Device signs require a lever");

		lever = locations.peek();

		price = getPrice(sign);
		if (price < 0)
			throw new ExceptionInInitializerError("bad price");

		ShopsData.clearItemLocations(player);
		player.sendMessage(Text.of(TextColors.DARK_GREEN, "You have setup a device sign:"));
		info(player);
	}

	@Override
	public List<Location<World>> getLocations() {
		List<Location<World>> locations = super.getLocations();
		locations.add(lever);
		return locations;
	}
	
	@Override
	public void info(Player player) {
		player.sendMessage(Text.of("Deactivate for ", price, " ", CarrotShop.getEcoService().getDefaultCurrency().getPluralDisplayName(), "?"));
		update();
	}
	
	@Override
	public boolean trigger(Player player) {
		UniqueAccount buyerAccount = CarrotShop.getEcoService().getOrCreateAccount(player.getUniqueId()).get();
		TransactionResult result = buyerAccount.withdraw(CarrotShop.getEcoService().getDefaultCurrency(), BigDecimal.valueOf(price), Cause.source(this).build());
		if (result.getResult() != ResultType.SUCCESS) {
			player.sendMessage(Text.of(TextColors.DARK_RED, "You don't have enough money!"));
			return false;
		}
		
		lever.offer(Keys.POWERED, false, CarrotShop.getCause());
		
		player.sendMessage(Text.of("Device deactivated for ", price, " ", CarrotShop.getEcoService().getDefaultCurrency().getPluralDisplayName()));

		return true;
	}

}