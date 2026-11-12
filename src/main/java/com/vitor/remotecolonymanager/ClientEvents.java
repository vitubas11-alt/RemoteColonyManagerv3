package com.vitor.remotecolonymanager;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Atalho de teclado (padrão: K) para abrir o painel sem precisar do item. */
public final class ClientEvents {
   public static final KeyMapping OPEN_KEY = new KeyMapping(
      "key.remotecolonymanager.open", InputConstants.Type.KEYSYM, InputConstants.KEY_K, "key.categories.remotecolonymanager"
   );

   private ClientEvents() {
   }

   @Mod.EventBusSubscriber(modid = RemoteColonyManager.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
   public static final class ModBus {
      @SubscribeEvent
      public static void registerKeys(RegisterKeyMappingsEvent event) {
         event.register(OPEN_KEY);
      }
   }

   @Mod.EventBusSubscriber(modid = RemoteColonyManager.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
   public static final class ForgeBus {
      @SubscribeEvent
      public static void onClientTick(TickEvent.ClientTickEvent event) {
         if (event.phase != TickEvent.Phase.END) {
            return;
         }
         Minecraft mc = Minecraft.getInstance();
         while (OPEN_KEY.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
               NetworkHandler.refresh();
            }
         }
      }
   }
}
