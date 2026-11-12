package com.vitor.remotecolonymanager;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class ColonyManagerItem extends Item {
   public ColonyManagerItem(Item.Properties properties) {
      super(properties);
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
         player.getCooldowns().addCooldown(this, 10);
         NetworkHandler.openScreen(serverPlayer);
      }

      return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
   }

   @Override
   public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable("item.remotecolonymanager.colony_manager.tooltip1").withStyle(ChatFormatting.GRAY));
      tooltip.add(Component.translatable("item.remotecolonymanager.colony_manager.tooltip2").withStyle(ChatFormatting.DARK_GRAY));
      super.appendHoverText(stack, level, tooltip, flag);
   }
}
