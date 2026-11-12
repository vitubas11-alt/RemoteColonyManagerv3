package com.vitor.remotecolonymanager;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "remotecolonymanager");
   public static final RegistryObject<Item> COLONY_TOOL = ITEMS.register("colony_manager", () -> new ColonyManagerItem(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

   public static void register(IEventBus bus) {
      ITEMS.register(bus);
      bus.addListener(ModItems::addToCreativeTab);
   }

   private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
      if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
         event.accept(COLONY_TOOL);
      }
   }
}
