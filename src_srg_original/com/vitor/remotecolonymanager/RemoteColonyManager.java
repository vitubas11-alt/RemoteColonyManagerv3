package com.vitor.remotecolonymanager;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod("remotecolonymanager")
public class RemoteColonyManager {
   public static final String MOD_ID = "remotecolonymanager";
   public static final Logger LOGGER = LogUtils.getLogger();

   public RemoteColonyManager() {
      ModItems.register(FMLJavaModLoadingContext.get().getModEventBus());
      NetworkHandler.register();
      LOGGER.info("Remote Colony Manager inicializado.");
   }

   @EventBusSubscriber(
      modid = "remotecolonymanager",
      bus = Bus.FORGE
   )
   public static class CommandsHandler {
      @SubscribeEvent
      public static void registerCommands(RegisterCommandsEvent event) {
         event.getDispatcher().register((LiteralArgumentBuilder)Commands.m_82127_("rcm").executes(ctx -> {
            ServerPlayer player = ((CommandSourceStack)ctx.getSource()).m_81375_();
            NetworkHandler.openScreen(player);
            return 1;
         }));
      }
   }
}
