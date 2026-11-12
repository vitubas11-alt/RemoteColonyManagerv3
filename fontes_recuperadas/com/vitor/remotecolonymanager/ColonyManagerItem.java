package com.vitor.remotecolonymanager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;

public class ColonyManagerItem extends Item {
   public ColonyManagerItem(Properties properties) {
      super(properties);
   }

   public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.m_21120_(hand);
      if (!level.f_46443_ && player instanceof ServerPlayer serverPlayer) {
         NetworkHandler.openScreen(serverPlayer);
      }

      return InteractionResultHolder.m_19092_(stack, level.f_46443_);
   }
}
