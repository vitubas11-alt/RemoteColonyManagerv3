package com.vitor.remotecolonymanager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RemoteColonyScreen extends Screen {
   private static final int PAGE = 4;
   private static List<NetworkHandler.OrderEntry> orders = new ArrayList<>();
   private static List<NetworkHandler.BuilderEntry> builders = new ArrayList<>();
   private static List<NetworkHandler.BuildingEntry> buildings = new ArrayList<>();
   private final Set<Integer> selectedOrders = new LinkedHashSet<>();
   private final List<Long> selectedBuilders = new ArrayList<>();
   private int tab = 1;
   private int orderPage = 0;
   private int builderPage = 0;
   private int buildingPage = 0;
   private int panelW;
   private int panelH;
   private int panelX;
   private int panelY;
   private int buildingPageSize = 1;
   private String message = "Carregando colônia...";

   public RemoteColonyScreen() {
      super(Component.m_237113_("Remote Colony Manager"));
   }

   public static void openOrUpdate(List<NetworkHandler.OrderEntry> o, List<NetworkHandler.BuilderEntry> b, List<NetworkHandler.BuildingEntry> bd) {
      Minecraft mc = Minecraft.m_91087_();
      orders = new ArrayList<>(o);
      builders = new ArrayList<>(b);
      buildings = new ArrayList<>(bd);
      if (!(mc.f_91080_ instanceof RemoteColonyScreen)) {
         mc.m_91152_(new RemoteColonyScreen());
      }

      if (mc.f_91080_ instanceof RemoteColonyScreen screen) {
         screen.selectedOrders.removeIf(id -> orders.stream().noneMatch(v -> v.id == id));
         screen.selectedBuilders.removeIf(pos -> builders.stream().noneMatch(v -> v.positionId == pos));
         screen.clampPages();
         screen.message = "Dados atualizados.";
         screen.m_232761_();
      }
   }

   private void calculateLayout() {
      this.panelW = Math.min(700, Math.max(240, this.f_96543_ - 12));
      this.panelH = Math.min(430, Math.max(190, this.f_96544_ - 12));
      this.panelX = (this.f_96543_ - this.panelW) / 2;
      this.panelY = (this.f_96544_ - this.panelH) / 2;
      this.buildingPageSize = Math.max(1, Math.min(4, (this.panelH - 155) / 76));
   }

   private void clampPages() {
      this.calculateLayout();
      this.orderPage = Math.max(0, Math.min(this.orderPage, Math.max(0, (orders.size() - 1) / 4)));
      this.builderPage = Math.max(0, Math.min(this.builderPage, Math.max(0, (builders.size() - 1) / 4)));
      this.buildingPage = Math.max(0, Math.min(this.buildingPage, Math.max(0, (buildings.size() - 1) / this.buildingPageSize)));
   }

   protected void m_7856_() {
      this.calculateLayout();
      this.m_169413_();
      int x = this.panelX;
      int y = this.panelY;
      int tabWidth = (this.panelW - 28) / 2;
      this.m_142416_(Button.m_253074_(Component.m_237113_("Ordens e construtores"), b -> {
         this.tab = 0;
         this.m_232761_();
      }).m_252987_(x + 8, y + 39, tabWidth, 22).m_253136_());
      this.m_142416_(Button.m_253074_(Component.m_237113_("Prédios e ações"), b -> {
         this.tab = 1;
         this.m_232761_();
      }).m_252987_(x + 12 + tabWidth, y + 39, tabWidth, 22).m_253136_());
      if (this.tab == 0) {
         this.initOrdersTab(x, y);
      } else {
         this.initBuildingsTab(x, y);
      }

      int bottom = y + this.panelH - 29;
      this.m_142416_(Button.m_253074_(Component.m_237113_("Atualizar"), b -> {
         this.message = "Atualizando...";
         NetworkHandler.refresh();
      }).m_252987_(x + 8, bottom, 85, 20).m_253136_());
      this.m_142416_(Button.m_253074_(Component.m_237113_("Fechar"), b -> this.m_7379_()).m_252987_(x + this.panelW - 83, bottom, 75, 20).m_253136_());
   }

   private void initBuildingsTab(int x, int y) {
      int bottom = y + this.panelH - 55;
      int first = this.buildingPage * this.buildingPageSize;
      int buttonGap = 3;
      int buttonWidth = (this.panelW - 28 - buttonGap * 3) / 4;

      for (int i = first; i < Math.min(buildings.size(), first + this.buildingPageSize); i++) {
         NetworkHandler.BuildingEntry building = buildings.get(i);
         int row = i - first;
         int ry = y + 91 + row * 76;
         String[] labels = new String[]{"Construir", "Evoluir", "Reparar", "Remover"};
         String[] actions = new String[]{"BUILD", "UPGRADE", "REPAIR", "REMOVE"};
         boolean[] enabled = new boolean[]{building.build, building.upgrade, building.repair, building.remove};

         for (int a = 0; a < 4; a++) {
            String action = actions[a];
            Button button = Button.m_253074_(Component.m_237113_(labels[a]), b -> this.act(building, action))
               .m_252987_(x + 8 + a * (buttonWidth + buttonGap), ry + 39, buttonWidth, 20)
               .m_253136_();
            button.f_93623_ = enabled[a];
            this.m_142416_(button);
         }
      }

      Button previous = Button.m_253074_(Component.m_237113_("<"), b -> {
         this.buildingPage = Math.max(0, this.buildingPage - 1);
         this.m_232761_();
      }).m_252987_(x + this.panelW / 2 - 48, bottom, 35, 20).m_253136_();
      previous.f_93623_ = this.buildingPage > 0;
      this.m_142416_(previous);
      Button next = Button.m_253074_(Component.m_237113_(">"), b -> {
         this.buildingPage++;
         this.clampPages();
         this.m_232761_();
      }).m_252987_(x + this.panelW / 2 + 12, bottom, 35, 20).m_253136_();
      next.f_93623_ = (this.buildingPage + 1) * this.buildingPageSize < buildings.size();
      this.m_142416_(next);
   }

   private void initOrdersTab(int x, int y) {
      int bottom = y + this.panelH - 55;
      int colWidth = (this.panelW - 28) / 2;
      int firstOrder = this.orderPage * 4;
      int firstBuilder = this.builderPage * 4;

      for (int i = firstOrder; i < Math.min(orders.size(), firstOrder + 4); i++) {
         NetworkHandler.OrderEntry order = orders.get(i);
         int row = i - firstOrder;
         Button button = Button.m_253074_(
               Component.m_237113_((this.selectedOrders.contains(order.id) ? "[x] " : "[ ] ") + order.type + " #" + order.id), b -> {
                  if (order.available) {
                     if (!this.selectedOrders.add(order.id)) {
                        this.selectedOrders.remove(order.id);
                     }

                     this.message = this.selectedOrders.size() + " ordem(ns) selecionada(s).";
                     this.m_232761_();
                  }
               }
            )
            .m_252987_(x + 8, y + 103 + row * 48, colWidth, 22)
            .m_253136_();
         button.f_93623_ = order.available;
         this.m_142416_(button);
      }

      for (int i = firstBuilder; i < Math.min(builders.size(), firstBuilder + 4); i++) {
         NetworkHandler.BuilderEntry builder = builders.get(i);
         int row = i - firstBuilder;
         long selectedCount = this.selectedBuilders.stream().filter(pos -> pos == builder.positionId).count();
         Button button = Button.m_253074_(Component.m_237113_((selectedCount > 0L ? "[" + selectedCount + "x] " : "[+] ") + builder.position), b -> {
            if (builder.hasWorker) {
               this.selectedBuilders.add(builder.positionId);
               this.message = this.selectedBuilders.size() + " vaga(s) de construtor selecionada(s).";
               this.m_232761_();
            }
         }).m_252987_(x + 16 + colWidth, y + 103 + row * 48, colWidth, 22).m_253136_();
         button.f_93623_ = builder.hasWorker;
         this.m_142416_(button);
      }

      this.m_142416_(Button.m_253074_(Component.m_237113_("Atribuir manual"), b -> this.manualAssign()).m_252987_(x + 8, bottom, colWidth, 20).m_253136_());
      this.m_142416_(
         Button.m_253074_(Component.m_237113_("Distribuir automático"), b -> this.automaticAssign())
            .m_252987_(x + 16 + colWidth, bottom, colWidth, 20)
            .m_253136_()
      );
      this.m_142416_(Button.m_253074_(Component.m_237113_("Limpar seleção"), b -> {
         this.selectedBuilders.clear();
         this.message = "Seleção de construtores limpa.";
         this.m_232761_();
      }).m_252987_(x + this.panelW / 2 - 42, y + this.panelH - 83, 84, 19).m_253136_());
      this.m_142416_(Button.m_253074_(Component.m_237113_("<"), b -> {
         this.orderPage = Math.max(0, this.orderPage - 1);
         this.m_232761_();
      }).m_252987_(x + 8, y + this.panelH - 83, 30, 19).m_253136_());
      this.m_142416_(Button.m_253074_(Component.m_237113_(">"), b -> {
         this.orderPage++;
         this.clampPages();
         this.m_232761_();
      }).m_252987_(x + colWidth - 22, y + this.panelH - 83, 30, 19).m_253136_());
      this.m_142416_(Button.m_253074_(Component.m_237113_("<"), b -> {
         this.builderPage = Math.max(0, this.builderPage - 1);
         this.m_232761_();
      }).m_252987_(x + 16 + colWidth, y + this.panelH - 83, 30, 19).m_253136_());
      this.m_142416_(Button.m_253074_(Component.m_237113_(">"), b -> {
         this.builderPage++;
         this.clampPages();
         this.m_232761_();
      }).m_252987_(x + this.panelW - 38, y + this.panelH - 83, 30, 19).m_253136_());
   }

   private void act(NetworkHandler.BuildingEntry building, String action) {
      NetworkHandler.requestBuildingAction(building.positionId, action);
      this.message = "Solicitação enviada: " + action + " - " + building.name;
   }

   private void manualAssign() {
      if (!this.selectedOrders.isEmpty() && !this.selectedBuilders.isEmpty()) {
         if (this.selectedOrders.size() != this.selectedBuilders.size()) {
            this.message = "Selecione a mesma quantidade dos dois lados.";
         } else {
            NetworkHandler.assignManual(new ArrayList<>(this.selectedOrders), new ArrayList<>(this.selectedBuilders));
            this.message = "Enviando atribuições...";
         }
      } else {
         this.message = "Selecione ordens e construtores.";
      }
   }

   private void automaticAssign() {
      if (this.selectedOrders.isEmpty()) {
         this.message = "Selecione as ordens.";
      } else {
         NetworkHandler.autoDistribute(new ArrayList<>(this.selectedOrders));
         this.message = "Enviando distribuição automática...";
      }
   }

   public void m_88315_(GuiGraphics g, int mx, int my, float pt) {
      this.m_280273_(g);
      int x = this.panelX;
      int y = this.panelY;
      g.m_280509_(x, y, x + this.panelW, y + this.panelH, -266656720);
      g.m_280509_(x, y, x + this.panelW, y + 30, -13350045);
      g.m_280137_(this.f_96547_, "REMOTE COLONY MANAGER", this.f_96543_ / 2, y + 10, -1);
      if (this.tab == 1) {
         g.m_280488_(this.f_96547_, "PRÉDIOS DA COLÔNIA", x + 9, y + 72, -11414);
         if (buildings.isEmpty()) {
            g.m_280488_(this.f_96547_, "Nenhum prédio registrado encontrado.", x + 10, y + 105, -1);
         }

         int first = this.buildingPage * this.buildingPageSize;

         for (int i = first; i < Math.min(buildings.size(), first + this.buildingPageSize); i++) {
            NetworkHandler.BuildingEntry b = buildings.get(i);
            int ry = y + 88 + (i - first) * 76;
            g.m_280488_(this.f_96547_, b.name + " | Nível " + b.level, x + 9, ry, -1);
            g.m_280488_(this.f_96547_, b.position + " | " + b.reason, x + 9, ry + 25, -4602157);
         }

         g.m_280488_(this.f_96547_, "Prédios encontrados: " + buildings.size() + " | Página " + (this.buildingPage + 1), x + 9, y + this.panelH - 69, -1);
      } else {
         int colWidth = (this.panelW - 28) / 2;
         g.m_280488_(this.f_96547_, "ORDENS", x + 9, y + 76, -11414);
         g.m_280488_(this.f_96547_, "CONSTRUTORES", x + 16 + colWidth, y + 76, -11414);
         int first = this.orderPage * 4;

         for (int i = first; i < Math.min(orders.size(), first + 4); i++) {
            NetworkHandler.OrderEntry order = orders.get(i);
            g.m_280488_(this.f_96547_, order.location, x + 12, y + 130 + (i - first) * 48, -4602157);
         }

         first = this.builderPage * 4;

         for (int i = first; i < Math.min(builders.size(), first + 4); i++) {
            NetworkHandler.BuilderEntry builder = builders.get(i);
            String status = !builder.hasWorker ? "Sem trabalhador" : (builder.free ? "Livre" : "Ocupado");
            g.m_280488_(this.f_96547_, status + " | " + builder.citizens, x + 16 + colWidth, y + 130 + (i - first) * 48, -4602157);
         }

         g.m_280488_(
            this.f_96547_, "Ordens: " + this.selectedOrders.size() + " | Construtores: " + this.selectedBuilders.size(), x + 9, y + this.panelH - 69, -1
         );
      }

      g.m_280488_(this.f_96547_, this.message, x + 9, y + this.panelH - 14, -11414);
      super.m_88315_(g, mx, my, pt);
   }

   public boolean m_7043_() {
      return false;
   }
}
