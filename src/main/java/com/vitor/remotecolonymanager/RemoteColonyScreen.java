package com.vitor.remotecolonymanager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Tela principal do Remote Colony Manager (visual v0.3).
 * Toda a lista é desenhada manualmente (cards com rolagem), por isso não depende de muitos widgets.
 */
public class RemoteColonyScreen extends Screen {
   // ---------- paleta ----------
   private static final int C_BG = 0xF0121820;
   private static final int C_BORDER = 0xFF3A4656;
   private static final int C_HEAD_TOP = 0xFF2B3A52;
   private static final int C_HEAD_BOT = 0xFF1A2333;
   private static final int C_CARD = 0xFF1C2430;
   private static final int C_CARD_HOVER = 0xFF263142;
   private static final int C_CARD_SEL = 0xFF1F3B5E;
   private static final int C_GOLD = 0xFFF2B84B;
   private static final int C_TEXT = 0xFFE6EDF3;
   private static final int C_DIM = 0xFF8B97A6;
   private static final int C_GREEN = 0xFF56C271;
   private static final int C_RED = 0xFFE5594F;
   private static final int C_BLUE = 0xFF5AA2F0;
   private static final int C_YELLOW = 0xFFE8C14A;
   private static final int C_BTN = 0xFF2F3B4D;
   private static final int C_BTN_HOVER = 0xFF41516A;
   private static final int C_BTN_OFF = 0xFF202731;

   private static final int ROW_ORDER = 30;
   private static final int ROW_BUILDING = 48;
   private static final int MAX_LEVEL = 5;

   private static final Map<String, String> NAMES = Map.ofEntries(
      Map.entry("builder", "Construtor"), Map.entry("townhall", "Prefeitura"), Map.entry("citizen", "Residência"),
      Map.entry("home", "Residência"), Map.entry("tavern", "Taverna"), Map.entry("miner", "Mineiro"),
      Map.entry("lumberjack", "Lenhador"), Map.entry("farmer", "Fazendeiro"), Map.entry("fisherman", "Pescador"),
      Map.entry("baker", "Padeiro"), Map.entry("blacksmith", "Ferreiro"), Map.entry("stonemason", "Pedreiro"),
      Map.entry("sawmill", "Serraria"), Map.entry("cook", "Cozinheiro"), Map.entry("restaurant", "Restaurante"),
      Map.entry("warehouse", "Armazém"), Map.entry("deliveryman", "Entregador"), Map.entry("library", "Biblioteca"),
      Map.entry("university", "Universidade"), Map.entry("hospital", "Hospital"), Map.entry("guardtower", "Torre de Guarda"),
      Map.entry("barracks", "Quartel"), Map.entry("barrackstower", "Torre do Quartel"), Map.entry("graveyard", "Cemitério"),
      Map.entry("school", "Escola"), Map.entry("shepherd", "Pastor"), Map.entry("cowboy", "Vaqueiro"),
      Map.entry("swineherder", "Porqueiro"), Map.entry("chickenherder", "Galinheiro"), Map.entry("composter", "Composteiro"),
      Map.entry("smeltery", "Fundição"), Map.entry("mechanic", "Mecânico"), Map.entry("alchemist", "Alquimista"),
      Map.entry("enchanter", "Encantador"), Map.entry("florist", "Floricultor"), Map.entry("plantation", "Plantação"),
      Map.entry("netherworker", "Minerador do Nether"), Map.entry("crusher", "Triturador"), Map.entry("sifter", "Peneirador"),
      Map.entry("concretemixer", "Betoneira"), Map.entry("dyer", "Tingidor"), Map.entry("fletcher", "Flecheiro"),
      Map.entry("glassblower", "Vidraceiro"), Map.entry("combatacademy", "Academia de Combate"), Map.entry("archery", "Arquearia"),
      Map.entry("mysticalsite", "Sítio Místico"), Map.entry("stash", "Esconderijo"), Map.entry("kitchen", "Cozinha"),
      Map.entry("beekeeper", "Apicultor"), Map.entry("rabbithutch", "Coelheira"), Map.entry("herbalist", "Herborista")
   );

   // ---------- dados ----------
   private static String colonyName = "";
   private static List<NetworkHandler.OrderEntry> orders = new ArrayList<>();
   private static List<NetworkHandler.BuilderEntry> builders = new ArrayList<>();
   private static List<NetworkHandler.BuildingEntry> buildings = new ArrayList<>();

   // ---------- estado ----------
   private final Set<Integer> selectedOrders = new LinkedHashSet<>();
   private final List<Long> selectedBuilders = new ArrayList<>();
   private int tab = 0;
   private boolean onlyFreeOrders = false;
   private boolean onlyFreeBuilders = false;
   private int buildingFilter = 0; // 0 todos, 1 construídos, 2 pendentes
   private String search = "";
   private int ordersScroll = 0;
   private int buildersScroll = 0;
   private int buildingsScroll = 0;
   private String message = "Carregando colônia...";
   private int messageColor = C_DIM;
   private long armedRemove = Long.MIN_VALUE;
   private long armedUntil = 0L;
   private EditBox searchBox;

   // ---------- layout ----------
   private int panelW;
   private int panelH;
   private int panelX;
   private int panelY;
   private final List<Hit> hits = new ArrayList<>();
   private int[] ordersView = new int[4];
   private int[] buildersView = new int[4];
   private int[] buildingsView = new int[4];

   private static final class Hit {
      final int x;
      final int y;
      final int w;
      final int h;
      final boolean enabled;
      final String tip;
      final Runnable action;

      Hit(int x, int y, int w, int h, boolean enabled, String tip, Runnable action) {
         this.x = x;
         this.y = y;
         this.w = w;
         this.h = h;
         this.enabled = enabled;
         this.tip = tip;
         this.action = action;
      }

      boolean contains(double mx, double my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }

   public RemoteColonyScreen() {
      super(Component.literal("Remote Colony Manager"));
   }

   public static void openOrUpdate(String name, List<NetworkHandler.OrderEntry> o, List<NetworkHandler.BuilderEntry> b, List<NetworkHandler.BuildingEntry> bd) {
      Minecraft mc = Minecraft.getInstance();
      colonyName = name == null ? "" : name;
      orders = new ArrayList<>(o);
      builders = new ArrayList<>(b);
      buildings = new ArrayList<>(bd);
      boolean fresh = !(mc.screen instanceof RemoteColonyScreen);
      if (fresh) {
         mc.setScreen(new RemoteColonyScreen());
      }

      if (mc.screen instanceof RemoteColonyScreen screen) {
         screen.selectedOrders.removeIf(id -> orders.stream().noneMatch(v -> v.id == id));
         screen.selectedBuilders.removeIf(pos -> builders.stream().noneMatch(v -> v.positionId == pos));
         screen.setMessage(fresh ? "Colônia carregada." : "Dados atualizados.", C_GREEN);
      }
   }

   // =====================================================================
   //  Inicialização
   // =====================================================================
   protected void init() {
      this.calculateLayout();
      this.clearWidgets();
      this.searchBox = null;
      if (this.tab == 1) {
         int chips = 3 * 69;
         int w = Math.max(60, this.panelW - 16 - chips);
         this.searchBox = new EditBox(this.font, this.panelX + 8, this.panelY + 68, w, 16, Component.literal("Buscar"));
         this.searchBox.setMaxLength(32);
         this.searchBox.setHint(Component.literal("Buscar prédio..."));
         this.searchBox.setValue(this.search);
         this.searchBox.setResponder(text -> {
            this.search = text == null ? "" : text;
            this.buildingsScroll = 0;
         });
         this.addRenderableWidget(this.searchBox);
      }
   }

   private void calculateLayout() {
      this.panelW = Math.min(720, Math.max(260, this.width - 12));
      this.panelH = Math.min(440, Math.max(200, this.height - 12));
      this.panelX = (this.width - this.panelW) / 2;
      this.panelY = (this.height - this.panelH) / 2;
   }

   private void setMessage(String text, int color) {
      this.message = text;
      this.messageColor = color;
   }

   // =====================================================================
   //  Helpers de desenho
   // =====================================================================
   private void border(GuiGraphics g, int x, int y, int w, int h, int color) {
      g.fill(x, y, x + w, y + 1, color);
      g.fill(x, y + h - 1, x + w, y + h, color);
      g.fill(x, y, x + 1, y + h, color);
      g.fill(x + w - 1, y, x + w, y + h, color);
   }

   private String fit(String text, int maxWidth) {
      if (text == null) {
         return "";
      }
      if (this.font.width(text) <= maxWidth) {
         return text;
      }
      return this.font.plainSubstrByWidth(text, Math.max(0, maxWidth - this.font.width("..."))) + "...";
   }

   private void addHit(int x, int y, int w, int h, boolean enabled, String tip, Runnable action) {
      this.hits.add(new Hit(x, y, w, h, enabled, tip, action));
   }

   private void button(GuiGraphics g, int mx, int my, int x, int y, int w, int h, String label, boolean enabled, int accent, String tip, Runnable action) {
      boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + h;
      g.fill(x, y, x + w, y + h, !enabled ? C_BTN_OFF : (hover ? C_BTN_HOVER : C_BTN));
      g.fill(x, y + h - 1, x + w, y + h, enabled ? accent : 0xFF2A313B);
      int color = enabled ? C_TEXT : 0xFF5C6673;
      g.drawCenteredString(this.font, this.fit(label, w - 4), x + w / 2, y + (h - 8) / 2, color);
      this.addHit(x, y, w, h, enabled, tip, action);
   }

   private void chip(GuiGraphics g, int mx, int my, int x, int y, int w, String label, boolean on, String tip, Runnable action) {
      boolean hover = mx >= x && mx < x + w && my >= y && my < y + 14;
      g.fill(x, y, x + w, y + 14, on ? 0xFF2E5A8C : (hover ? C_BTN_HOVER : C_BTN));
      this.border(g, x, y, w, 14, on ? C_BLUE : C_BORDER);
      g.drawCenteredString(this.font, this.fit(label, w - 2), x + w / 2, y + 3, on ? 0xFFFFFFFF : C_DIM);
      this.addHit(x, y, w, 14, true, tip, action);
   }

   private void checkbox(GuiGraphics g, int x, int y, boolean checked, boolean enabled) {
      g.fill(x, y, x + 10, y + 10, enabled ? 0xFF0E1218 : 0xFF181D25);
      this.border(g, x, y, 10, 10, checked ? C_BLUE : (enabled ? C_BORDER : 0xFF2A313B));
      if (checked) {
         g.fill(x + 2, y + 2, x + 8, y + 8, C_BLUE);
      }
   }

   private void scrollbar(GuiGraphics g, int[] view, int contentH, int scroll) {
      int viewH = view[3] - view[1];
      if (contentH <= viewH) {
         return;
      }
      int barX = view[2] - 4;
      g.fill(barX, view[1], barX + 3, view[3], 0xFF0E1218);
      int thumbH = Math.max(14, viewH * viewH / contentH);
      int maxScroll = contentH - viewH;
      int thumbY = view[1] + (int)((long)(viewH - thumbH) * scroll / Math.max(1, maxScroll));
      g.fill(barX, thumbY, barX + 3, thumbY + thumbH, C_GOLD);
   }

   private static int clamp(int v, int lo, int hi) {
      return Math.max(lo, Math.min(hi, v));
   }

   // =====================================================================
   //  Render
   // =====================================================================
   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.calculateLayout();
      this.hits.clear();
      this.renderBackground(g);
      int x = this.panelX;
      int y = this.panelY;
      int w = this.panelW;
      int h = this.panelH;

      // painel
      g.fill(x - 1, y - 1, x + w + 1, y + h + 1, C_BORDER);
      g.fill(x, y, x + w, y + h, C_BG);
      this.renderHeader(g, x, y, w);
      this.renderTabs(g, mx, my, x, y, w);
      if (this.tab == 0) {
         this.renderOrdersTab(g, mx, my, x, y, w, h);
      } else {
         this.renderBuildingsTab(g, mx, my, x, y, w, h);
      }
      this.renderFooter(g, mx, my, x, y, w, h);
      super.render(g, mx, my, pt);

      // tooltip por último, acima de tudo
      for (int i = this.hits.size() - 1; i >= 0; i--) {
         Hit hit = this.hits.get(i);
         if (hit.tip != null && hit.contains(mx, my)) {
            g.renderTooltip(this.font, Component.literal(hit.tip), mx, my);
            break;
         }
      }
   }

   private void renderHeader(GuiGraphics g, int x, int y, int w) {
      g.fillGradient(x, y, x + w, y + 36, C_HEAD_TOP, C_HEAD_BOT);
      g.fill(x, y + 36, x + w, y + 37, C_GOLD);
      g.drawString(this.font, "REMOTE COLONY MANAGER", x + 9, y + 7, C_GOLD);
      if (!colonyName.isEmpty()) {
         String name = this.fit(colonyName, w / 2 - 20);
         g.drawString(this.font, name, x + w - 9 - this.font.width(name), y + 7, C_TEXT);
      }

      int freeOrders = 0;
      for (NetworkHandler.OrderEntry o : orders) {
         if (o.available) {
            freeOrders++;
         }
      }
      int freeBuilders = 0;
      int activeBuilders = 0;
      for (NetworkHandler.BuilderEntry b : builders) {
         if (b.hasWorker) {
            activeBuilders++;
            if (b.free) {
               freeBuilders++;
            }
         }
      }
      int built = 0;
      for (NetworkHandler.BuildingEntry b : buildings) {
         if (b.level > 0) {
            built++;
         }
      }
      String stats = "Ordens livres " + freeOrders + "/" + orders.size()
         + "  |  Construtores livres " + freeBuilders + "/" + activeBuilders
         + "  |  Prédios " + built + "/" + buildings.size();
      g.drawString(this.font, this.fit(stats, w - 18), x + 9, y + 21, C_DIM);

      // barra de progresso das ordens (já assumidas / total)
      int total = orders.size();
      g.fill(x, y + 33, x + w, y + 36, 0xFF0E1218);
      if (total > 0) {
         int claimed = total - freeOrders;
         g.fill(x, y + 33, x + (int)((long)w * claimed / total), y + 36, C_GREEN);
      }
   }

   private void renderTabs(GuiGraphics g, int mx, int my, int x, int y, int w) {
      int tw = (w - 20) / 2;
      this.tabButton(g, mx, my, x + 8, y + 42, tw, "Ordens e construtores", 0);
      this.tabButton(g, mx, my, x + 12 + tw, y + 42, tw, "Prédios e ações", 1);
   }

   private void tabButton(GuiGraphics g, int mx, int my, int bx, int by, int bw, String label, int index) {
      boolean active = this.tab == index;
      boolean hover = mx >= bx && mx < bx + bw && my >= by && my < by + 20;
      g.fill(bx, by, bx + bw, by + 20, active ? 0xFF2E5A8C : (hover ? C_BTN_HOVER : C_BTN));
      g.fill(bx, by + 18, bx + bw, by + 20, active ? C_GOLD : 0xFF2A313B);
      g.drawCenteredString(this.font, this.fit(label, bw - 6), bx + bw / 2, by + 6, active ? 0xFFFFFFFF : C_DIM);
      this.addHit(bx, by, bw, 20, true, null, () -> {
         if (this.tab != index) {
            this.tab = index;
            this.rebuildWidgets();
         }
      });
   }

   // ---------------------------------------------------------------------
   //  Aba 0: Ordens e construtores
   // ---------------------------------------------------------------------
   private List<NetworkHandler.OrderEntry> visibleOrders() {
      List<NetworkHandler.OrderEntry> list = new ArrayList<>();
      for (NetworkHandler.OrderEntry o : orders) {
         if (!this.onlyFreeOrders || o.available) {
            list.add(o);
         }
      }
      return list;
   }

   private List<NetworkHandler.BuilderEntry> visibleBuilders() {
      List<NetworkHandler.BuilderEntry> list = new ArrayList<>();
      for (NetworkHandler.BuilderEntry b : builders) {
         if (!this.onlyFreeBuilders || (b.hasWorker && b.free)) {
            list.add(b);
         }
      }
      return list;
   }

   private void renderOrdersTab(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
      int colW = (w - 24) / 2;
      int leftX = x + 8;
      int rightX = x + 16 + colW;
      int headY = y + 68;
      int listTop = y + 86;
      int listBottom = y + h - 54;

      // ---- cabeçalhos ----
      g.drawString(this.font, "ORDENS (" + this.selectedOrders.size() + " sel.)", leftX + 1, headY + 3, C_GOLD);
      this.chip(g, mx, my, leftX + colW - 128, headY, 62, "Todas livres", false, "Seleciona todas as ordens livres", () -> {
         for (NetworkHandler.OrderEntry o : orders) {
            if (o.available) {
               this.selectedOrders.add(o.id);
            }
         }
         this.setMessage(this.selectedOrders.size() + " ordem(ns) selecionada(s).", C_BLUE);
      });
      this.chip(g, mx, my, leftX + colW - 62, headY, 62, "Só livres", this.onlyFreeOrders, "Mostrar apenas ordens sem construtor", () -> {
         this.onlyFreeOrders = !this.onlyFreeOrders;
         this.ordersScroll = 0;
      });
      g.drawString(this.font, "CONSTRUTORES (" + this.selectedBuilders.size() + " sel.)", rightX + 1, headY + 3, C_GOLD);
      this.chip(g, mx, my, rightX + colW - 62, headY, 62, "Só livres", this.onlyFreeBuilders, "Mostrar apenas construtores disponíveis", () -> {
         this.onlyFreeBuilders = !this.onlyFreeBuilders;
         this.buildersScroll = 0;
      });

      // ---- listas ----
      List<NetworkHandler.OrderEntry> vo = this.visibleOrders();
      this.ordersView = new int[]{leftX, listTop, leftX + colW, listBottom};
      this.ordersScroll = clamp(this.ordersScroll, 0, Math.max(0, vo.size() * ROW_ORDER - (listBottom - listTop)));
      g.enableScissor(leftX, listTop, leftX + colW, listBottom);
      if (vo.isEmpty()) {
         g.drawString(this.font, orders.isEmpty() ? "Nenhuma ordem de construção." : "Nenhuma ordem livre.", leftX + 4, listTop + 6, C_DIM);
      }
      for (int i = 0; i < vo.size(); i++) {
         int ry = listTop + i * ROW_ORDER - this.ordersScroll;
         if (ry + ROW_ORDER < listTop || ry > listBottom) {
            continue;
         }
         this.renderOrderRow(g, mx, my, vo.get(i), leftX, ry, colW - 6, listTop, listBottom);
      }
      g.disableScissor();
      this.scrollbar(g, this.ordersView, vo.size() * ROW_ORDER, this.ordersScroll);

      List<NetworkHandler.BuilderEntry> vb = this.visibleBuilders();
      this.buildersView = new int[]{rightX, listTop, rightX + colW, listBottom};
      this.buildersScroll = clamp(this.buildersScroll, 0, Math.max(0, vb.size() * ROW_ORDER - (listBottom - listTop)));
      g.enableScissor(rightX, listTop, rightX + colW, listBottom);
      if (vb.isEmpty()) {
         g.drawString(this.font, builders.isEmpty() ? "Nenhuma cabana de construtor." : "Nenhum construtor livre.", rightX + 4, listTop + 6, C_DIM);
      }
      for (int i = 0; i < vb.size(); i++) {
         int ry = listTop + i * ROW_ORDER - this.buildersScroll;
         if (ry + ROW_ORDER < listTop || ry > listBottom) {
            continue;
         }
         this.renderBuilderRow(g, mx, my, vb.get(i), rightX, ry, colW - 6, listTop, listBottom);
      }
      g.disableScissor();
      this.scrollbar(g, this.buildersView, vb.size() * ROW_ORDER, this.buildersScroll);

      // ---- ações ----
      int ay = y + h - 50;
      int gap = 4;
      int bw = (w - 16 - gap * 3) / 4;
      boolean pairOk = !this.selectedOrders.isEmpty() && this.selectedOrders.size() == this.selectedBuilders.size();
      this.button(g, mx, my, x + 8, ay, bw, 18, "Atribuir manual", pairOk, C_BLUE,
         "Liga a 1ª ordem ao 1º construtor, a 2ª ao 2º... (mesma quantidade)", this::manualAssign);
      this.button(g, mx, my, x + 8 + (bw + gap), ay, bw, 18, "Distribuir auto", !this.selectedOrders.isEmpty(), C_GREEN,
         "Escolhe o melhor construtor: maior nível, depois o mais próximo", this::automaticAssign);
      this.button(g, mx, my, x + 8 + (bw + gap) * 2, ay, bw, 18, "Atribuir TODOS", orders.stream().anyMatch(o -> o.available), C_GOLD,
         "Distribui automaticamente todas as ordens livres", this::assignAllFree);
      this.button(g, mx, my, x + 8 + (bw + gap) * 3, ay, bw, 18, "Limpar seleção", !this.selectedOrders.isEmpty() || !this.selectedBuilders.isEmpty(), C_RED,
         "Limpa ordens e construtores selecionados", () -> {
            this.selectedOrders.clear();
            this.selectedBuilders.clear();
            this.setMessage("Seleção limpa.", C_DIM);
         });
   }

   private void renderOrderRow(GuiGraphics g, int mx, int my, NetworkHandler.OrderEntry o, int rx, int ry, int rw, int clipTop, int clipBottom) {
      boolean sel = this.selectedOrders.contains(o.id);
      boolean inView = my >= clipTop && my < clipBottom;
      boolean hover = o.available && inView && mx >= rx && mx < rx + rw && my >= ry && my < ry + ROW_ORDER - 2;
      g.fill(rx, ry, rx + rw, ry + ROW_ORDER - 2, sel ? C_CARD_SEL : (hover ? C_CARD_HOVER : C_CARD));
      int accent = typeColor(o.type);
      g.fill(rx, ry, rx + 3, ry + ROW_ORDER - 2, o.available ? accent : 0xFF3A4252);
      this.checkbox(g, rx + 8, ry + 9, sel, o.available);
      int tx = rx + 24;
      int tw = rw - 28;
      String head = "#" + o.id + "  " + o.type;
      g.drawString(this.font, this.fit(head, tw), tx, ry + 4, o.available ? accent : C_DIM);
      String status = o.available ? "Livre" : "Em andamento";
      int sw = this.font.width(status);
      g.drawString(this.font, this.fit(o.location, tw - sw - 8), tx, ry + 15, C_DIM);
      g.drawString(this.font, status, rx + rw - sw - 4, ry + 15, o.available ? C_GREEN : C_YELLOW);
      if (o.available && inView) {
         int visTop = Math.max(ry, clipTop);
         int visBottom = Math.min(ry + ROW_ORDER - 2, clipBottom);
         this.addHit(rx, visTop, rw, visBottom - visTop, true, o.name.isEmpty() ? null : o.name, () -> {
            if (!this.selectedOrders.add(o.id)) {
               this.selectedOrders.remove(o.id);
            }
            this.setMessage(this.selectedOrders.size() + " ordem(ns) selecionada(s).", C_BLUE);
         });
      }
   }

   private void renderBuilderRow(GuiGraphics g, int mx, int my, NetworkHandler.BuilderEntry b, int rx, int ry, int rw, int clipTop, int clipBottom) {
      boolean selectable = b.hasWorker && b.free;
      int idx = this.selectedBuilders.indexOf(b.positionId);
      boolean sel = idx >= 0;
      boolean inView = my >= clipTop && my < clipBottom;
      boolean hover = selectable && inView && mx >= rx && mx < rx + rw && my >= ry && my < ry + ROW_ORDER - 2;
      g.fill(rx, ry, rx + rw, ry + ROW_ORDER - 2, sel ? C_CARD_SEL : (hover ? C_CARD_HOVER : C_CARD));
      int statusColor = !b.hasWorker ? C_RED : (b.free ? C_GREEN : C_YELLOW);
      g.fill(rx, ry, rx + 3, ry + ROW_ORDER - 2, statusColor);
      this.checkbox(g, rx + 8, ry + 9, sel, selectable);
      int tx = rx + 24;
      int tw = rw - 28;
      String lvl = "Nv." + b.level;
      int lw = this.font.width(lvl);
      g.drawString(this.font, this.fit(b.citizens, tw - lw - 8), tx, ry + 4, selectable ? C_TEXT : C_DIM);
      g.drawString(this.font, lvl, rx + rw - lw - 4, ry + 4, C_GOLD);
      String status = !b.hasWorker ? "Sem trabalhador" : (b.free ? "Livre" : "Ocupado");
      int sw = this.font.width(status);
      g.drawString(this.font, this.fit(b.position, tw - sw - 8), tx, ry + 15, C_DIM);
      g.drawString(this.font, status, rx + rw - sw - 4, ry + 15, statusColor);
      if (inView) {
         int visTop = Math.max(ry, clipTop);
         int visBottom = Math.min(ry + ROW_ORDER - 2, clipBottom);
         String tip = selectable ? (sel ? "Selecionado (ordem " + (idx + 1) + ")" : null) : (b.hasWorker ? "Este construtor já tem uma ordem" : "Contrate um construtor nesta cabana");
         this.addHit(rx, visTop, rw, visBottom - visTop, selectable, tip, () -> {
            if (!this.selectedBuilders.remove(b.positionId)) {
               this.selectedBuilders.add(b.positionId);
            }
            this.setMessage(this.selectedBuilders.size() + " construtor(es) selecionado(s).", C_BLUE);
         });
      }
   }

   // ---------------------------------------------------------------------
   //  Aba 1: Prédios
   // ---------------------------------------------------------------------
   private List<NetworkHandler.BuildingEntry> visibleBuildings() {
      List<NetworkHandler.BuildingEntry> list = new ArrayList<>();
      String q = this.search.trim().toLowerCase(Locale.ROOT);
      for (NetworkHandler.BuildingEntry b : buildings) {
         if (this.buildingFilter == 1 && b.level <= 0) {
            continue;
         }
         if (this.buildingFilter == 2 && b.level > 0) {
            continue;
         }
         if (!q.isEmpty() && !b.name.toLowerCase(Locale.ROOT).contains(q) && !prettyName(b.name).toLowerCase(Locale.ROOT).contains(q)) {
            continue;
         }
         list.add(b);
      }
      return list;
   }

   private void renderBuildingsTab(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
      int chipW = 66;
      int cx = x + w - 8 - 3 * (chipW + 3) + 3;
      this.chip(g, mx, my, cx, y + 69, chipW, "Todos", this.buildingFilter == 0, "Mostrar todos os prédios", () -> {
         this.buildingFilter = 0;
         this.buildingsScroll = 0;
      });
      this.chip(g, mx, my, cx + chipW + 3, y + 69, chipW, "Construídos", this.buildingFilter == 1, "Apenas prédios já construídos", () -> {
         this.buildingFilter = 1;
         this.buildingsScroll = 0;
      });
      this.chip(g, mx, my, cx + 2 * (chipW + 3), y + 69, chipW, "Pendentes", this.buildingFilter == 2, "Apenas prédios aguardando construção", () -> {
         this.buildingFilter = 2;
         this.buildingsScroll = 0;
      });

      int listTop = y + 90;
      int listBottom = y + h - 54;
      int lx = x + 8;
      int lw = w - 16;
      List<NetworkHandler.BuildingEntry> vis = this.visibleBuildings();
      this.buildingsView = new int[]{lx, listTop, lx + lw, listBottom};
      this.buildingsScroll = clamp(this.buildingsScroll, 0, Math.max(0, vis.size() * ROW_BUILDING - (listBottom - listTop)));

      g.enableScissor(lx, listTop, lx + lw, listBottom);
      if (vis.isEmpty()) {
         g.drawString(this.font, buildings.isEmpty() ? "Nenhum prédio registrado encontrado." : "Nenhum prédio corresponde ao filtro.", lx + 4, listTop + 6, C_DIM);
      }
      for (int i = 0; i < vis.size(); i++) {
         int ry = listTop + i * ROW_BUILDING - this.buildingsScroll;
         if (ry + ROW_BUILDING < listTop || ry > listBottom) {
            continue;
         }
         this.renderBuildingRow(g, mx, my, vis.get(i), lx, ry, lw - 6, listTop, listBottom);
      }
      g.disableScissor();
      this.scrollbar(g, this.buildingsView, vis.size() * ROW_BUILDING, this.buildingsScroll);

      g.drawString(this.font, "Exibindo " + vis.size() + " de " + buildings.size() + " prédios  |  role com o mouse para ver mais", x + 9, y + h - 48, C_DIM);
   }

   private void renderBuildingRow(GuiGraphics g, int mx, int my, NetworkHandler.BuildingEntry b, int rx, int ry, int rw, int clipTop, int clipBottom) {
      boolean inView = my >= clipTop && my < clipBottom;
      boolean hover = inView && mx >= rx && mx < rx + rw && my >= ry && my < ry + ROW_BUILDING - 2;
      g.fill(rx, ry, rx + rw, ry + ROW_BUILDING - 2, hover ? C_CARD_HOVER : C_CARD);
      boolean built = b.level > 0;
      boolean hasOrder = "Já possui uma ordem".equals(b.reason);
      int stateColor = hasOrder ? C_YELLOW : (built ? C_GREEN : C_BLUE);
      g.fill(rx, ry, rx + 3, ry + ROW_BUILDING - 2, stateColor);

      // linha 1: nome + pips de nível
      String name = prettyName(b.name);
      g.drawString(this.font, this.fit(name, rw / 2), rx + 9, ry + 4, C_TEXT);
      int pipX = rx + rw - 8 - MAX_LEVEL * 8;
      for (int i = 0; i < MAX_LEVEL; i++) {
         g.fill(pipX + i * 8, ry + 5, pipX + i * 8 + 6, ry + 11, i < b.level ? C_GOLD : 0xFF0E1218);
         this.border(g, pipX + i * 8, ry + 5, 6, 6, i < b.level ? 0xFFB98A2E : 0xFF303846);
      }
      String lvl = "Nv." + b.level;
      g.drawString(this.font, lvl, pipX - this.font.width(lvl) - 5, ry + 4, C_GOLD);

      // linha 2: posição + estado
      String status = b.reason;
      int sw = this.font.width(status);
      g.drawString(this.font, this.fit(b.position, rw - sw - 24), rx + 9, ry + 16, C_DIM);
      g.drawString(this.font, status, rx + rw - sw - 6, ry + 16, stateColor);

      // linha 3: ações
      String[] labels = new String[]{"Construir", "Evoluir", "Reparar", "Remover"};
      String[] actions = new String[]{"BUILD", "UPGRADE", "REPAIR", "REMOVE"};
      String[] tips = new String[]{"Cria uma ordem de construção", "Pede a melhoria para o próximo nível", "Pede o reparo do prédio", "Pede a remoção do prédio (confirmação)"};
      int[] accents = new int[]{C_BLUE, C_GREEN, C_YELLOW, C_RED};
      boolean[] enabled = new boolean[]{b.build, b.upgrade, b.repair, b.remove};
      int gap = 3;
      int bw = (rw - 18 - gap * 3) / 4;
      for (int a = 0; a < 4; a++) {
         String action = actions[a];
         int bx = rx + 9 + a * (bw + gap);
         int by = ry + 29;
         boolean on = enabled[a];
         boolean armed = a == 3 && this.armedRemove == b.positionId && System.currentTimeMillis() < this.armedUntil;
         String label = armed ? "Confirmar?" : labels[a];
         boolean bHover = on && inView && mx >= bx && mx < bx + bw && my >= by && my < by + 14 && my >= clipTop && my < clipBottom;
         g.fill(bx, by, bx + bw, by + 14, !on ? C_BTN_OFF : (armed ? 0xFF7A2B2B : (bHover ? C_BTN_HOVER : C_BTN)));
         g.fill(bx, by + 13, bx + bw, by + 14, on ? accents[a] : 0xFF2A313B);
         g.drawCenteredString(this.font, this.fit(label, bw - 2), bx + bw / 2, by + 3, on ? C_TEXT : 0xFF5C6673);
         if (inView) {
            int visTop = Math.max(by, clipTop);
            int visBottom = Math.min(by + 14, clipBottom);
            if (visBottom > visTop) {
               this.addHit(bx, visTop, bw, visBottom - visTop, on, tips[a], () -> this.act(b, action));
            }
         }
      }
   }

   // =====================================================================
   //  Rodapé
   // =====================================================================
   private void renderFooter(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
      int fy = y + h - 26;
      g.fill(x, fy - 3, x + w, fy - 2, 0xFF2A313B);
      this.button(g, mx, my, x + 8, fy, 80, 18, "Atualizar", true, C_BLUE, "Recarrega os dados da colônia", () -> {
         this.setMessage("Atualizando...", C_DIM);
         NetworkHandler.refresh();
      });
      this.button(g, mx, my, x + w - 88, fy, 80, 18, "Fechar", true, C_RED, null, this::onClose);
      int msgW = w - 190;
      String msg = this.fit(this.message, msgW);
      g.drawCenteredString(this.font, msg, x + w / 2, fy + 5, this.messageColor);
   }

   // =====================================================================
   //  Entrada
   // =====================================================================
   public boolean mouseClicked(double mx, double my, int button) {
      if (super.mouseClicked(mx, my, button)) {
         return true;
      }
      if (button == 0) {
         for (int i = this.hits.size() - 1; i >= 0; i--) {
            Hit hit = this.hits.get(i);
            if (hit.contains(mx, my)) {
               if (hit.enabled && hit.action != null) {
                  hit.action.run();
               }
               return true;
            }
         }
      }
      return false;
   }

   public boolean mouseScrolled(double mx, double my, double delta) {
      int step = (int)(-delta * 20);
      if (this.tab == 0) {
         if (inside(this.ordersView, mx, my)) {
            this.ordersScroll = Math.max(0, this.ordersScroll + step);
            return true;
         }
         if (inside(this.buildersView, mx, my)) {
            this.buildersScroll = Math.max(0, this.buildersScroll + step);
            return true;
         }
      } else if (inside(this.buildingsView, mx, my)) {
         this.buildingsScroll = Math.max(0, this.buildingsScroll + step);
         return true;
      }
      return super.mouseScrolled(mx, my, delta);
   }

   private static boolean inside(int[] v, double mx, double my) {
      return mx >= v[0] && mx < v[2] && my >= v[1] && my < v[3];
   }

   // =====================================================================
   //  Ações
   // =====================================================================
   private void act(NetworkHandler.BuildingEntry building, String action) {
      if ("REMOVE".equals(action)) {
         long now = System.currentTimeMillis();
         if (this.armedRemove != building.positionId || now > this.armedUntil) {
            this.armedRemove = building.positionId;
            this.armedUntil = now + 4000L;
            this.setMessage("Clique em \"Confirmar?\" para remover " + prettyName(building.name) + ".", C_YELLOW);
            return;
         }
         this.armedRemove = Long.MIN_VALUE;
      }
      NetworkHandler.requestBuildingAction(building.positionId, action);
      this.setMessage("Pedido enviado: " + actionLabel(action) + " - " + prettyName(building.name), C_BLUE);
   }

   private void manualAssign() {
      if (!this.selectedOrders.isEmpty() && !this.selectedBuilders.isEmpty()) {
         if (this.selectedOrders.size() != this.selectedBuilders.size()) {
            this.setMessage("Selecione a mesma quantidade dos dois lados.", C_RED);
         } else {
            NetworkHandler.assignManual(new ArrayList<>(this.selectedOrders), new ArrayList<>(this.selectedBuilders));
            this.selectedOrders.clear();
            this.selectedBuilders.clear();
            this.setMessage("Enviando atribuições...", C_BLUE);
         }
      } else {
         this.setMessage("Selecione ordens e construtores.", C_RED);
      }
   }

   private void automaticAssign() {
      if (this.selectedOrders.isEmpty()) {
         this.setMessage("Selecione as ordens para distribuir automaticamente.", C_RED);
      } else {
         NetworkHandler.autoDistribute(new ArrayList<>(this.selectedOrders));
         this.selectedOrders.clear();
         this.setMessage("Distribuindo (melhor nível, depois mais próximo)...", C_BLUE);
      }
   }

   private void assignAllFree() {
      ArrayList<Integer> freeIds = new ArrayList<>();
      for (NetworkHandler.OrderEntry o : orders) {
         if (o.available) {
            freeIds.add(o.id);
         }
      }
      if (freeIds.isEmpty()) {
         this.setMessage("Nenhuma ordem livre disponível.", C_RED);
         return;
      }
      NetworkHandler.autoDistribute(freeIds);
      this.selectedOrders.clear();
      this.setMessage("Atribuindo as " + freeIds.size() + " ordens livres...", C_BLUE);
   }

   // =====================================================================
   //  Utilidades
   // =====================================================================
   private static String actionLabel(String action) {
      return switch (action) {
         case "BUILD" -> "Construir";
         case "UPGRADE" -> "Evoluir";
         case "REPAIR" -> "Reparar";
         case "REMOVE" -> "Remover";
         default -> action;
      };
   }

   private static int typeColor(String type) {
      if (type == null) {
         return C_DIM;
      }
      return switch (type) {
         case "Construção" -> C_BLUE;
         case "Melhoria" -> C_GREEN;
         case "Reparo" -> C_YELLOW;
         case "Remoção" -> C_RED;
         default -> C_DIM;
      };
   }

   private static String prettyName(String raw) {
      if (raw == null || raw.isEmpty()) {
         return "Prédio";
      }
      String key = raw.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "");
      String translated = NAMES.get(key);
      if (translated != null) {
         return translated;
      }
      StringBuilder sb = new StringBuilder();
      for (String part : raw.replace('_', ' ').split(" ")) {
         if (part.isEmpty()) {
            continue;
         }
         if (sb.length() > 0) {
            sb.append(' ');
         }
         sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
      }
      return sb.toString();
   }

   public boolean isPauseScreen() {
      return false;
   }
}
