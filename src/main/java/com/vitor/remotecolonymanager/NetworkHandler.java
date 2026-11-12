package com.vitor.remotecolonymanager;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.workorders.IBuilderWorkOrder;
import com.minecolonies.api.colony.workorders.IServerWorkOrder;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.core.colony.buildings.AbstractSchematicProvider;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkEvent.Context;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
   private static final String PROTOCOL = "4";
   private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
      new ResourceLocation("remotecolonymanager", "main"), () -> "4", "4"::equals, "4"::equals
   );
   private static boolean registered = false;

   public static void register() {
      if (!registered) {
         registered = true;
         CHANNEL.registerMessage(
            0,
            NetworkHandler.ColonyDataPacket.class,
            NetworkHandler.ColonyDataPacket::encode,
            NetworkHandler.ColonyDataPacket::decode,
            NetworkHandler.ColonyDataPacket::handle,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
         );
         CHANNEL.registerMessage(
            1,
            NetworkHandler.AssignPacket.class,
            NetworkHandler.AssignPacket::encode,
            NetworkHandler.AssignPacket::decode,
            NetworkHandler.AssignPacket::handle,
            Optional.of(NetworkDirection.PLAY_TO_SERVER)
         );
         CHANNEL.registerMessage(
            2,
            NetworkHandler.RefreshPacket.class,
            NetworkHandler.RefreshPacket::encode,
            NetworkHandler.RefreshPacket::decode,
            NetworkHandler.RefreshPacket::handle,
            Optional.of(NetworkDirection.PLAY_TO_SERVER)
         );
         CHANNEL.registerMessage(
            3,
            NetworkHandler.BuildingActionPacket.class,
            NetworkHandler.BuildingActionPacket::encode,
            NetworkHandler.BuildingActionPacket::decode,
            NetworkHandler.BuildingActionPacket::handle,
            Optional.of(NetworkDirection.PLAY_TO_SERVER)
         );
      }
   }

   public static void openScreen(ServerPlayer player) {
      IColony colony = getPlayerColony(player);
      if (colony == null) {
         player.sendSystemMessage(Component.literal("Não encontrei uma colônia sua neste mundo."));
      } else {
         sendData(player, colony);
      }
   }

   private static IColony getPlayerColony(ServerPlayer player) {
      return IColonyManager.getInstance().getIColonyByOwner(player.serverLevel(), player);
   }

   private static List<BuildingBuilder> getBuilders(IColony colony) {
      List<BuildingBuilder> result = new ArrayList<>();

      for (IBuilding building : colony.getServerBuildingManager().getBuildings().values()) {
         if (building instanceof BuildingBuilder builder) {
            result.add(builder);
         }
      }

      result.sort(Comparator.comparingLong(b -> b.getPosition().asLong()));
      return result;
   }

   private static boolean hasWorker(BuildingBuilder builder) {
      return builder != null && builder.getAllAssignedCitizen() != null && !builder.getAllAssignedCitizen().isEmpty();
   }

   private static boolean hasOrderAt(IColony colony, BlockPos pos) {
      for (IServerWorkOrder order : colony.getWorkManager().getWorkOrders().values()) {
         if (pos.equals(order.getLocation())) {
            return true;
         }
      }

      return false;
   }

   /** Escolhe uma cabana de construtor capaz de atender ao nível solicitado.
    * A ordem é criada pelo próprio MineColonies e depois liberada para seleção manual/automática no RCM.
    */
   private static BuildingBuilder findOrderCreator(IBuilding target, WorkOrderType type, List<BuildingBuilder> builders) {
      int targetLevel = target.getBuildingLevel();
      if (type == WorkOrderType.BUILD) targetLevel = 1;
      else if (type == WorkOrderType.UPGRADE) targetLevel++;
      else if (type == WorkOrderType.REMOVE) targetLevel = 0;
      final int requiredLevel = targetLevel;
      final boolean targetAllowsLowerLevelBuilder = target.canBeBuiltByBuilder(requiredLevel);
      return builders.stream()
         .filter(b -> b.getBuildingLevel() >= requiredLevel || targetAllowsLowerLevelBuilder)
         .min(Comparator
            .comparing((BuildingBuilder b) -> !(hasWorker(b) && !b.hasWorkOrder()))
            .thenComparing(Comparator.comparingInt(BuildingBuilder::getBuildingLevel).reversed())
            .thenComparingDouble(b -> b.getPosition().distSqr(target.getPosition())))
         .orElse(null);
   }

   /** Remove a reserva temporária usada pelo MineColonies para validar a solicitação remota. */
   private static boolean releaseOrderAt(IColony colony, BlockPos pos) {
      for (IServerWorkOrder raw : colony.getWorkManager().getWorkOrders().values()) {
         if (pos.equals(raw.getLocation()) && raw instanceof IBuilderWorkOrder order) {
            order.setClaimedBy(null);
            colony.getWorkManager().setDirty(true);
            colony.markDirty();
            return true;
         }
      }
      return false;
   }

   /** Prédios colocados, mas ainda sem ordem, entram automaticamente na lista oficial do MineColonies. */
   private static void ensurePendingBuildOrders(ServerPlayer player, IColony colony) {
      List<BuildingBuilder> builders = getBuilders(colony);
      if (builders.isEmpty()) return;
      for (IBuilding building : colony.getServerBuildingManager().getBuildings().values()) {
         BlockPos pos = building.getPosition();
         if (hasOrderAt(colony, pos) || building.isBuilt() || getUpdatedBuildingLevel(building) > 0) continue;
         BuildingBuilder creator = findOrderCreator(building, WorkOrderType.BUILD, builders);
         if (creator == null) continue;
         try {
            // requestUpgrade cria uma ordem BUILD quando o nível atual é zero e valida as regras nativas.
            building.requestUpgrade(player, creator.getPosition());
            if (releaseOrderAt(colony, pos)) {
               RemoteColonyManager.LOGGER.info("[RCM] Ordem BUILD criada automaticamente para {}", pos);
            }
         } catch (Exception ex) {
            RemoteColonyManager.LOGGER.error("[RCM] Falha ao criar ordem automática para {}", pos, ex);
         }
      }
   }

   private static String buildingName(IBuilding building) {
      try {
         return building.getBuildingType().getRegistryName().getPath().replace('_', ' ');
      } catch (Exception var2) {
         return "Prédio";
      }
   }

   private static void sendData(ServerPlayer player, IColony colony) {
      ensurePendingBuildOrders(player, colony);
      List<NetworkHandler.OrderEntry> orders = new ArrayList<>();

      for (IServerWorkOrder raw : colony.getWorkManager().getWorkOrders().values()) {
         if (raw instanceof IBuilderWorkOrder order) {
            orders.add(
               new NetworkHandler.OrderEntry(
                  order.getID(),
                  order.getDisplayName().getString(),
                  WorkOrderTypeNames.getName(order.getWorkOrderType()),
                  order.getLocation().toShortString(),
                  !order.isClaimed()
               )
            );
         }
      }

      RemoteColonyManager.LOGGER
         .info("[RCM] LISTAGEM: ordens no MineColonies={}, ordens enviadas={}", colony.getWorkManager().getWorkOrders().size(), orders.size());
      orders.sort(Comparator.comparingInt(o -> o.id));
      List<NetworkHandler.BuilderEntry> builders = new ArrayList<>();

      for (BuildingBuilder builder : getBuilders(colony)) {
         StringBuilder names = new StringBuilder();

         for (ICitizenData citizen : builder.getAllAssignedCitizen()) {
            if (names.length() > 0) {
               names.append(", ");
            }

            names.append(citizen.getName());
         }

         boolean worker = names.length() > 0;
         if (!worker) {
            names.append("Sem trabalhador");
         }

         builders.add(
            new NetworkHandler.BuilderEntry(
               builder.getPosition().asLong(), builder.getPosition().toShortString(), names.toString(), !builder.hasWorkOrder(), worker, builder.getBuildingLevel()
            )
         );
      }

      List<NetworkHandler.BuildingEntry> buildings = new ArrayList<>();

      for (IBuilding building : colony.getServerBuildingManager().getBuildings().values()) {
         BlockPos pos = building.getPosition();
         boolean hasOrder = hasOrderAt(colony, pos);
         boolean built = building.isBuilt() || getUpdatedBuildingLevel(building) > 0;
         String reason = hasOrder ? "Já possui uma ordem" : (!built ? "Aguardando construção" : "Prédio construído");
         buildings.add(
            new NetworkHandler.BuildingEntry(
               pos.asLong(),
               buildingName(building),
               pos.toShortString(),
               getUpdatedBuildingLevel(building),
               !built && !hasOrder,
               built && !hasOrder,
               built && !hasOrder,
               built && !hasOrder,
               reason
            )
         );
      }

      buildings.sort(Comparator.comparing(b -> b.name.toLowerCase()));
      CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new NetworkHandler.ColonyDataPacket(colony.getName(), orders, builders, buildings));
   }

   private static BuildingBuilder findBuilder(List<BuildingBuilder> builders, long pos) {
      for (BuildingBuilder b : builders) {
         if (b.getPosition().asLong() == pos) {
            return b;
         }
      }

      return null;
   }

   private static boolean canAssign(IBuilderWorkOrder order, BuildingBuilder builder) {
      return order != null
         && builder != null
         && !order.isClaimed()
         && !builder.hasWorkOrder()
         && hasWorker(builder)
         && order.canBuildIgnoringDistance(builder, builder.getPosition(), builder.getBuildingLevel());
   }

   /**
    * Escolhe o melhor construtor livre para a ordem.
    * Critérios (nessa ordem):
    * 1) Maior nível da cabana do construtor
    * 2) Em caso de empate, o mais próximo da localização da ordem
    * Assim o "Distribuir automático" realmente escolhe bem a casa do construtor.
    */
   private static BuildingBuilder chooseBestBuilder(IBuilderWorkOrder order, List<BuildingBuilder> builders) {
      BuildingBuilder best = null;
      int bestLevel = -1;
      double bestDist = Double.MAX_VALUE;
      BlockPos orderPos = order.getLocation();

      for (BuildingBuilder candidate : builders) {
         if (!canAssign(order, candidate)) {
            continue;
         }

         int level = candidate.getBuildingLevel();
         double dist = orderPos.distSqr(candidate.getPosition());

         if (level > bestLevel || (level == bestLevel && dist < bestDist)) {
            best = candidate;
            bestLevel = level;
            bestDist = dist;
         }
      }

      return best;
   }

   private static boolean assignOne(IColony colony, IBuilderWorkOrder order, BuildingBuilder builder) {
      if (!canAssign(order, builder)) {
         return false;
      } else {
         builder.setWorkOrder(order);
         order.setClaimedBy(builder.getID());
         builder.markDirty();
         colony.getWorkManager().setDirty(true);
         colony.markDirty();
         return true;
      }
   }

   public static void assignManual(List<Integer> ids, List<Long> positions) {
      CHANNEL.sendToServer(new NetworkHandler.AssignPacket(ids, positions, false));
   }

   public static void autoDistribute(List<Integer> ids) {
      CHANNEL.sendToServer(new NetworkHandler.AssignPacket(ids, Collections.emptyList(), true));
   }

   public static void requestBuildingAction(long position, String action) {
      CHANNEL.sendToServer(new NetworkHandler.BuildingActionPacket(position, action));
   }

   public static void refresh() {
      CHANNEL.sendToServer(new NetworkHandler.RefreshPacket());
   }

   private static int getUpdatedBuildingLevel(IBuilding building) {
      try {
         if (building instanceof AbstractSchematicProvider provider) {
            provider.upgradeBuildingLevelToSchematicData();
         }
      } catch (Exception var2) {
         RemoteColonyManager.LOGGER.error("[RCM] Erro ao atualizar nível da construção", var2);
      }

      return building.getBuildingLevel();
   }


   public static class AssignPacket {
      public final List<Integer> orderIds;
      public final List<Long> builderPositions;
      public final boolean automatic;

      public AssignPacket(List<Integer> ids, List<Long> positions, boolean automatic) {
         this.orderIds = new ArrayList<>(ids);
         this.builderPositions = new ArrayList<>(positions);
         this.automatic = automatic;
      }

      public static void encode(NetworkHandler.AssignPacket p, FriendlyByteBuf b) {
         b.writeBoolean(p.automatic);
         b.writeVarInt(p.orderIds.size());

         for (int id : p.orderIds) {
            b.writeVarInt(id);
         }

         b.writeVarInt(p.builderPositions.size());

         for (long pos : p.builderPositions) {
            b.writeLong(pos);
         }
      }

      public static NetworkHandler.AssignPacket decode(FriendlyByteBuf b) {
         boolean automatic = b.readBoolean();
         List<Integer> ids = new ArrayList<>();

         for (int i = b.readVarInt(); i > 0; i--) {
            ids.add(b.readVarInt());
         }

         List<Long> positions = new ArrayList<>();

         for (int i = b.readVarInt(); i > 0; i--) {
            positions.add(b.readLong());
         }

         return new NetworkHandler.AssignPacket(ids, positions, automatic);
      }

      public static void handle(NetworkHandler.AssignPacket p, Supplier<Context> supplier) {
         Context ctx = supplier.get();
         ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
               IColony colony = NetworkHandler.getPlayerColony(player);
               if (colony != null) {
                  List<BuildingBuilder> builders = NetworkHandler.getBuilders(colony);
                  int assigned = 0;
                  int rejected = 0;
                  if (p.automatic) {
                     for (int id : p.orderIds) {
                        if (colony.getWorkManager().getWorkOrder(id) instanceof IBuilderWorkOrder order) {
                           BuildingBuilder chosen = NetworkHandler.chooseBestBuilder(order, builders);

                           if (chosen != null && NetworkHandler.assignOne(colony, order, chosen)) {
                              assigned++;
                           } else {
                              rejected++;
                           }
                        } else {
                           rejected++;
                        }
                     }
                  } else {
                     if (p.orderIds.size() != p.builderPositions.size()) {
                        player.sendSystemMessage(Component.literal("Selecione a mesma quantidade de ordens e construtores."));
                        NetworkHandler.sendData(player, colony);
                        return;
                     }

                     for (int i = 0; i < p.orderIds.size(); i++) {
                        IServerWorkOrder raw = colony.getWorkManager().getWorkOrder(p.orderIds.get(i));
                        BuildingBuilder builder = NetworkHandler.findBuilder(builders, p.builderPositions.get(i));
                        if (raw instanceof IBuilderWorkOrder order && builder != null && NetworkHandler.assignOne(colony, order, builder)) {
                           assigned++;
                        } else {
                           rejected++;
                        }
                     }
                  }

                  colony.getWorkManager().setDirty(true);
                  colony.markDirty();
                  player.sendSystemMessage(
                     Component.literal("[RCM] ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("Atribuídas: " + assigned).withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.literal("Sem construtor livre/inválidas: " + rejected).withStyle(rejected > 0 ? ChatFormatting.RED : ChatFormatting.GRAY))
                  );
                  NetworkHandler.sendData(player, colony);
               }
            }
         });
         ctx.setPacketHandled(true);
      }
   }

   public static class BuilderEntry {
      public final long positionId;
      public final String position;
      public final String citizens;
      public final boolean free;
      public final boolean hasWorker;
      public final int level;

      public BuilderEntry(long id, String pos, String citizens, boolean free, boolean worker, int level) {
         this.level = level;
         this.positionId = id;
         this.position = pos;
         this.citizens = citizens;
         this.free = free;
         this.hasWorker = worker;
      }
   }

   public static class BuildingActionPacket {
      public final long position;
      public final String action;

      public BuildingActionPacket(long position, String action) {
         this.position = position;
         this.action = action;
      }

      public static void encode(NetworkHandler.BuildingActionPacket p, FriendlyByteBuf b) {
         b.writeLong(p.position);
         b.writeUtf(p.action);
      }

      public static NetworkHandler.BuildingActionPacket decode(FriendlyByteBuf b) {
         return new NetworkHandler.BuildingActionPacket(b.readLong(), b.readUtf());
      }

      public static void handle(NetworkHandler.BuildingActionPacket p, Supplier<Context> supplier) {
         Context ctx = supplier.get();
         ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
               IColony colony = NetworkHandler.getPlayerColony(player);
               if (colony != null) {
                  BlockPos pos = BlockPos.of(p.position);
                  IBuilding common = (IBuilding)colony.getServerBuildingManager().getBuilding(pos);
                  IBuilding building = common;
                  if (common == null) {
                     player.sendSystemMessage(Component.literal("Prédio não encontrado."));
                     NetworkHandler.sendData(player, colony);
                  } else if (NetworkHandler.hasOrderAt(colony, pos)) {
                     player.sendSystemMessage(Component.literal("Esse prédio já possui uma ordem."));
                     NetworkHandler.sendData(player, colony);
                  } else {
                     try {
                        String e = p.action;
                        WorkOrderType requestedType = switch (e) {
                           case "BUILD" -> WorkOrderType.BUILD;
                           case "UPGRADE" -> WorkOrderType.UPGRADE;
                           case "REPAIR" -> WorkOrderType.REPAIR;
                           case "REMOVE" -> WorkOrderType.REMOVE;
                           default -> null;
                        };
                        if (requestedType == null) {
                           player.sendSystemMessage(Component.literal("Ação desconhecida."));
                           return;
                        }
                        BuildingBuilder creator = NetworkHandler.findOrderCreator(building, requestedType, NetworkHandler.getBuilders(colony));
                        if (creator == null) {
                           player.sendSystemMessage(Component.literal("Não há cabana de construtor com nível suficiente para essa tarefa."));
                           return;
                        }
                        BlockPos builderPos = creator.getPosition();
                        switch (e) {
                           case "BUILD":
                              if (building.isBuilt() || NetworkHandler.getUpdatedBuildingLevel(building) > 0) {
                                 player.sendSystemMessage(Component.literal("Esse prédio já está construído."));
                                 return;
                              }
                              building.requestUpgrade(player, builderPos);
                              break;
                           case "UPGRADE":
                              if (!building.isBuilt() && NetworkHandler.getUpdatedBuildingLevel(building) <= 0) {
                                 player.sendSystemMessage(Component.literal("O prédio ainda não está construído."));
                                 return;
                              }
                              building.requestUpgrade(player, builderPos);
                              break;
                           case "REPAIR":
                              if (!building.isBuilt() && NetworkHandler.getUpdatedBuildingLevel(building) <= 0) {
                                 player.sendSystemMessage(Component.literal("O prédio ainda não está construído."));
                                 return;
                              }
                              building.requestRepair(builderPos);
                              break;
                           case "REMOVE":
                              if (!building.isBuilt() && NetworkHandler.getUpdatedBuildingLevel(building) <= 0) {
                                 player.sendSystemMessage(Component.literal("O prédio não está construído."));
                                 return;
                              }
                              building.requestRemoval(player, builderPos);
                              break;
                        }
                        if (NetworkHandler.releaseOrderAt(colony, pos)) {
                           player.sendSystemMessage(Component.literal("Ordem criada pelo MineColonies e liberada para escolher o construtor na aba Ordens."));
                        } else {
                           player.sendSystemMessage(Component.literal("O MineColonies não criou a ordem. Verifique requisitos, pesquisas e área da colônia."));
                        }
                     } catch (Exception var9) {
                        RemoteColonyManager.LOGGER.error("Erro ao executar ação remota no prédio {}", pos, var9);
                        player.sendSystemMessage(Component.literal("Erro ao solicitar a ação. Consulte o latest.log."));
                     }

                     colony.markDirty();
                     NetworkHandler.sendData(player, colony);
                  }
               }
            }
         });
         ctx.setPacketHandled(true);
      }
   }

   public static class BuildingEntry {
      public final long positionId;
      public final String name;
      public final String position;
      public final String reason;
      public final int level;
      public final boolean build;
      public final boolean upgrade;
      public final boolean repair;
      public final boolean remove;

      public BuildingEntry(long id, String name, String pos, int level, boolean build, boolean upgrade, boolean repair, boolean remove, String reason) {
         this.positionId = id;
         this.name = name;
         this.position = pos;
         this.level = level;
         this.build = build;
         this.upgrade = upgrade;
         this.repair = repair;
         this.remove = remove;
         this.reason = reason;
      }
   }

   public static class ColonyDataPacket {
      public final String colonyName;
      public final List<NetworkHandler.OrderEntry> orders;
      public final List<NetworkHandler.BuilderEntry> builders;
      public final List<NetworkHandler.BuildingEntry> buildings;

      public ColonyDataPacket(String colonyName, List<NetworkHandler.OrderEntry> orders, List<NetworkHandler.BuilderEntry> builders, List<NetworkHandler.BuildingEntry> buildings) {
         this.colonyName = colonyName == null ? "" : colonyName;
         this.orders = orders;
         this.builders = builders;
         this.buildings = buildings;
      }

      public static void encode(NetworkHandler.ColonyDataPacket p, FriendlyByteBuf b) {
         b.writeUtf(p.colonyName);
         b.writeVarInt(p.orders.size());

         for (NetworkHandler.OrderEntry o : p.orders) {
            b.writeVarInt(o.id);
            b.writeUtf(o.name);
            b.writeUtf(o.type);
            b.writeUtf(o.location);
            b.writeBoolean(o.available);
         }

         b.writeVarInt(p.builders.size());

         for (NetworkHandler.BuilderEntry v : p.builders) {
            b.writeLong(v.positionId);
            b.writeUtf(v.position);
            b.writeUtf(v.citizens);
            b.writeBoolean(v.free);
            b.writeBoolean(v.hasWorker);
            b.writeVarInt(v.level);
         }

         b.writeVarInt(p.buildings.size());

         for (NetworkHandler.BuildingEntry v : p.buildings) {
            b.writeLong(v.positionId);
            b.writeUtf(v.name);
            b.writeUtf(v.position);
            b.writeVarInt(v.level);
            b.writeBoolean(v.build);
            b.writeBoolean(v.upgrade);
            b.writeBoolean(v.repair);
            b.writeBoolean(v.remove);
            b.writeUtf(v.reason);
         }
      }

      public static NetworkHandler.ColonyDataPacket decode(FriendlyByteBuf b) {
         String colonyName = b.readUtf();
         List<NetworkHandler.OrderEntry> orders = new ArrayList<>();

         for (int i = b.readVarInt(); i > 0; i--) {
            orders.add(new NetworkHandler.OrderEntry(b.readVarInt(), b.readUtf(), b.readUtf(), b.readUtf(), b.readBoolean()));
         }

         List<NetworkHandler.BuilderEntry> builders = new ArrayList<>();

         for (int i = b.readVarInt(); i > 0; i--) {
            builders.add(new NetworkHandler.BuilderEntry(b.readLong(), b.readUtf(), b.readUtf(), b.readBoolean(), b.readBoolean(), b.readVarInt()));
         }

         List<NetworkHandler.BuildingEntry> buildings = new ArrayList<>();

         for (int i = b.readVarInt(); i > 0; i--) {
            buildings.add(
               new NetworkHandler.BuildingEntry(
                  b.readLong(), b.readUtf(), b.readUtf(), b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readUtf()
               )
            );
         }

         return new NetworkHandler.ColonyDataPacket(colonyName, orders, builders, buildings);
      }

      public static void handle(NetworkHandler.ColonyDataPacket p, Supplier<Context> supplier) {
         Context ctx = supplier.get();
         ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RemoteColonyScreen.openOrUpdate(p.colonyName, p.orders, p.builders, p.buildings)));
         ctx.setPacketHandled(true);
      }
   }

   public static class OrderEntry {
      public final int id;
      public final String name;
      public final String type;
      public final String location;
      public final boolean available;

      public OrderEntry(int id, String name, String type, String location, boolean available) {
         this.id = id;
         this.name = name;
         this.type = type;
         this.location = location;
         this.available = available;
      }
   }

   public static class RefreshPacket {
      public static void encode(NetworkHandler.RefreshPacket p, FriendlyByteBuf b) {
      }

      public static NetworkHandler.RefreshPacket decode(FriendlyByteBuf b) {
         return new NetworkHandler.RefreshPacket();
      }

      public static void handle(NetworkHandler.RefreshPacket p, Supplier<Context> supplier) {
         Context ctx = supplier.get();
         ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
               IColony colony = NetworkHandler.getPlayerColony(player);
               if (colony != null) {
                  NetworkHandler.sendData(player, colony);
               }
            }
         });
         ctx.setPacketHandled(true);
      }
   }
}
