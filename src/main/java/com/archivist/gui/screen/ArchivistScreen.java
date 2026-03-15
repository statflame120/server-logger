package com.archivist.gui.screen;

import com.archivist.ArchivistMod;
import com.archivist.ServerDataCollector;
import com.archivist.command.CommandRegistry;
import com.archivist.command.commands.ThemeCommand;
import com.archivist.config.ArchivistConfig;
import com.archivist.config.GuiConfig;
import com.archivist.data.EventBus;
import com.archivist.data.LogEvent;
import com.archivist.data.LogExporter;
import com.archivist.JsonLogger;
import com.archivist.ExceptionResolver;
import com.archivist.database.ApiConfig;
import com.archivist.database.ApiSyncManager;
import com.archivist.database.DatabaseManager;
import com.archivist.gui.render.ColorScheme;
import com.archivist.gui.render.GradientConfig;
import com.archivist.gui.render.RenderUtils;
import com.archivist.gui.render.ThemeManager;
import com.archivist.gui.ServerLogData;
import com.archivist.gui.ServerLogReader;
import com.archivist.gui.widgets.*;
import com.archivist.gui.widgets.Button;
import com.archivist.gui.widgets.CheckBox;
import com.archivist.gui.widgets.Label;
import com.archivist.gui.widgets.TextField;
import com.archivist.fingerprint.*;
import com.archivist.scraper.GuiScraper;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Consumer;

/**
 * Main Archivist GUI screen. Extends Minecraft's Screen as a bridge —
 * all rendering and input is forwarded to the custom widget system.
 * No vanilla widgets (addDrawableChild) are used.
 *
 * Creates and manages: Server Info, Plugin List,
 * Connection Log, Console, Settings windows, and the Taskbar.
 */
public class ArchivistScreen extends Screen {

    private final List<DraggableWindow> windows = new ArrayList<>();
    private final List<DraggableWindow> taskbarOrder = new ArrayList<>();
    private final Taskbar taskbar = new Taskbar();
    private GuiConfig guiConfig;

    // Global search overlay
    private final GlobalSearchOverlay globalSearch = new GlobalSearchOverlay();

    // Keyboard shortcut state
    private boolean shortcutConsumedThisFrame = false;

    // Settings tab persistence
    private int settingsActiveTab = 0;

    // Windows
    private DraggableWindow serverInfoWindow;
    private DraggableWindow pluginListWindow;
    private DraggableWindow connectionLogWindow;
    private DraggableWindow consoleWindow;
    private DraggableWindow settingsWindow;
    private DraggableWindow inspectorWindow;
    private DraggableWindow serverListWindow;
    private DraggableWindow manualLogWindow;

    // Console state
    private ScrollableList consoleOutput;
    private TextField consoleInput;
    private int lastEventCount = 0;

    // Plugin list state
    private ScrollableList pluginList;
    private TextField pluginSearch;

    // Connection log state
    private ScrollableList connectionLogList;

    // Inspector state
    private ScrollableList inspectorList;
    private GuiCapture lastBuiltCapture;

    private final Screen parent;
    private boolean closedIntentionally = false;

    public ArchivistScreen() {
        this(null);
    }

    public ArchivistScreen(Screen parent) {
        super(Component.literal("Archivist"));
        this.parent = parent;
    }

    //? if <1.21.9 {
    /*@Override
    public boolean isPauseScreen() {
        return false;
    }
    *///?}

    @Override
    protected void init() {
        // Propagate resize to parent so it renders correctly underneath
        if (parent != null) {
            parent.width = width;
            parent.height = height;
        }

        windows.clear();

        // Load GUI config for window positions
        guiConfig = new GuiConfig();
        guiConfig.load();

        // Apply saved theme
        applyTheme(guiConfig.activeTheme);

        // Sync animation setting — suppress animations during setup
        ArchivistConfig extCfg = getExtConfig();
        DraggableWindow.animationsEnabled = extCfg == null || extCfg.guiAnimations;
        DraggableWindow.resetAnimReady();

        // ── Create Windows ──────────────────────────────────────────────────
        serverInfoWindow = createWindow("server_info", "Server Info", 10, 10, 200, 340);
        pluginListWindow = createWindow("plugin_list", "Plugins", 220, 10, 180, 240);
        connectionLogWindow = createWindow("connection_log", "Connection Log", 10, 260, 280, 200);
        consoleWindow = createWindow("console", "Console", 300, 260, 300, 200);
        settingsWindow = createWindow("settings", "Settings", 610, 10, 220, 300);
        if (guiConfig.getWindowState("settings") == null) settingsWindow.setVisible(false);
        inspectorWindow = createWindow("inspector", "GUI Inspector", 610, 320, 250, 250);
        if (guiConfig.getWindowState("inspector") == null) inspectorWindow.setVisible(false);
        serverListWindow = createWindow("server_list", "Server Logs", 10, 10, 400, 350);
        if (guiConfig.getWindowState("server_list") == null) serverListWindow.setVisible(false);
        manualLogWindow = createWindow("manual_log", "Manual Log", 410, 320, 220, 160);
        if (guiConfig.getWindowState("manual_log") == null) manualLogWindow.setVisible(false);

        buildServerInfoWindow();
        buildPluginListWindow();
        buildConnectionLogWindow();
        buildConsoleWindow();
        buildSettingsWindow();
        buildInspectorWindow();
        buildServerListWindow();
        buildManualLogWindow();

        // Initial reflow so anchored children get correct positions
        pluginListWindow.reflowChildren();
        connectionLogWindow.reflowChildren();
        consoleWindow.reflowChildren();
        settingsWindow.reflowChildren();
        inspectorWindow.reflowChildren();
        serverListWindow.reflowChildren();
        manualLogWindow.reflowChildren();

        windows.add(serverInfoWindow);
        windows.add(pluginListWindow);
        windows.add(connectionLogWindow);
        windows.add(consoleWindow);
        windows.add(settingsWindow);
        windows.add(inspectorWindow);
        windows.add(serverListWindow);
        windows.add(manualLogWindow);

        // Stable order for taskbar (never reordered by bringToFront)
        taskbarOrder.clear();
        taskbarOrder.addAll(windows);

        // Give each window the full list for snapping + taskbar reference
        for (DraggableWindow w : windows) {
            w.setAllWindows(windows);
            w.setTaskbar(taskbar);
        }

        // Taskbar
        taskbar.setup(taskbarOrder, width);
        taskbar.updatePosition(width, height);

        // Setup global search
        globalSearch.setVisible(false);
        globalSearch.setSearchProvider(this::performGlobalSearch);
        globalSearch.setOnResultSelected((windowId, matchText) -> {
            for (DraggableWindow w : windows) {
                if (w.getId().equals(windowId)) {
                    w.setVisible(true);
                    w.setMinimized(false);
                    bringToFront(w);
                    // Scroll to matching item in the window's ScrollableList
                    for (Widget child : w.getChildren()) {
                        if (child instanceof ScrollableList list) {
                            for (int i = 0; i < list.getItems().size(); i++) {
                                if (list.getItems().get(i).text.equals(matchText)) {
                                    list.scrollToItem(i);
                                    list.setSelectedIndex(i);
                                    break;
                                }
                            }
                            break;
                        }
                    }
                    break;
                }
            }
        });
    }

    private DraggableWindow createWindow(String id, String title, int defX, int defY, int defW, int defH) {
        GuiConfig.WindowState saved = guiConfig.getWindowState(id);
        if (saved != null) {
            DraggableWindow w = new DraggableWindow(id, title, saved.x, saved.y, saved.width, saved.height);
            w.setVisible(saved.visible);
            w.setMinimized(saved.minimized);
            return w;
        }
        return new DraggableWindow(id, title, defX, defY, defW, defH);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Window Builders
    // ══════════════════════════════════════════════════════════════════════════

    private void buildServerInfoWindow() {
        serverInfoWindow.clearChildren();
        ServerDataCollector dc = getDataCollector();
        Minecraft mc = Minecraft.getInstance();

        // ── Connection section ──
        serverInfoWindow.addChild(new Label(0, 0, 180, "\u2500\u2500 Connection \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500", ColorScheme.get().accent()));
        addKV(serverInfoWindow, "IP", dc != null ? dc.ip : "N/A");
        addKV(serverInfoWindow, "Port", dc != null ? String.valueOf(dc.port) : "N/A");
        addKV(serverInfoWindow, "Domain", dc != null ? dc.domain : "N/A");
        addKV(serverInfoWindow, "Version", dc != null ? dc.version : "N/A");
        addKV(serverInfoWindow, "Brand", dc != null ? dc.brand : "N/A");
        addKV(serverInfoWindow, "Players", dc != null ? String.valueOf(dc.playerCount) : "N/A");

        // ── World section ──
        serverInfoWindow.addChild(new Label(0, 0, 180, ""));
        serverInfoWindow.addChild(new Label(0, 0, 180, "\u2500\u2500 World \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500", ColorScheme.get().accent()));
        addKV(serverInfoWindow, "Dimension", dc != null ? dc.dimension : "N/A");

        if (mc.level != null) {
            addKV(serverInfoWindow, "Difficulty", mc.level.getDifficulty().name());
            addKV(serverInfoWindow, "Day Time", String.valueOf(mc.level.getDayTime() % 24000));

            //? if >=1.21.6
            addKV(serverInfoWindow, "Raining", String.valueOf(mc.level.isRaining()));
            //? if <1.21.6
            //addKV(serverInfoWindow, "Raining", String.valueOf(mc.level.isRaining()));

            try {
                //? if <1.21.10 {
                /*var spawnPos = mc.level.getSharedSpawnPos();
                addKV(serverInfoWindow, "Spawn", spawnPos.getX() + ", " + spawnPos.getY() + ", " + spawnPos.getZ());
                *///?}
            } catch (Exception ignored) {}

            var border = mc.level.getWorldBorder();
            addKV(serverInfoWindow, "Border Size", String.format("%.0f", border.getSize()));
        }

        if (mc.gameMode != null) {
            addKV(serverInfoWindow, "Gamemode", mc.gameMode.getPlayerMode().name());
        }

        if (dc != null && dc.resourcePack != null) {
            addKV(serverInfoWindow, "Resource Pack", dc.resourcePack);
        }

        // MOTD from server data if available
        if (mc.getCurrentServer() != null && mc.getCurrentServer().motd != null) {
            serverInfoWindow.addChild(new Label(0, 0, 180, ""));
            serverInfoWindow.addChild(new Label(0, 0, 180, "MOTD:", ColorScheme.get().accent()));
            serverInfoWindow.addChild(new Label(0, 0, 180, mc.getCurrentServer().motd.getString(), ColorScheme.get().textSecondary()));
        }

        serverInfoWindow.addChild(new Label(0, 0, 180, ""));
        serverInfoWindow.addChild(new Button(0, 0, 80, "Export", () -> {
            String path = LogExporter.exportJson();
            if (path != null) EventBus.post(LogEvent.Type.SYSTEM, "Exported: " + path);
        }));
    }

    private void buildPluginListWindow() {
        pluginListWindow.clearChildren();
        ServerDataCollector dc = getDataCollector();

        List<String> plugins = dc != null ? dc.getPlugins() : Collections.emptyList();
        pluginListWindow.setTitle("Plugins (" + plugins.size() + ")");

        pluginList = new ScrollableList(0, 0, 160, 160);
        pluginList.setAnchor(Widget.Anchor.FILL_ABOVE);
        pluginList.setMargins(0, 0, 34, 0); // leave 34px at bottom for search + button
        for (String p : plugins) {
            pluginList.addItem(p, ColorScheme.get().eventPlugin());
        }

        // Add GUI-scraped plugins if available
        if (ArchivistMod.INSTANCE != null) {
            GuiScraper scraper = ArchivistMod.INSTANCE.guiScraper;
            if (scraper != null && !scraper.getIdentifiedPlugins().isEmpty()) {
                pluginList.addItem("--- GUI Scraped ---", ColorScheme.get().textSecondary());
                for (String p : scraper.getIdentifiedPlugins()) {
                    pluginList.addItem(p, ColorScheme.get().accent());
                }
            }
        }

        // Add GUI-fingerprinted plugins
        Set<String> fpPlugins = GuiFingerprintEngine.getInstance().getDetectedPluginNames();
        if (!fpPlugins.isEmpty()) {
            pluginList.addItem("--- GUI Fingerprint ---", ColorScheme.get().textSecondary());
            for (String p : fpPlugins) {
                pluginList.addItem(p, ColorScheme.get().eventWorld());
            }
        }

        pluginListWindow.addChild(pluginList);

        pluginSearch = new TextField(0, 0, 160, "Filter plugins...");
        pluginSearch.setAnchor(Widget.Anchor.BOTTOM);
        pluginSearch.setFixedHeight(14);
        pluginSearch.setMargins(0, 0, 16, 0); // 16px from bottom for button below
        pluginSearch.setOnChange(query -> filterPlugins(query, dc));
        pluginListWindow.addChild(pluginSearch);

        Button copyAllBtn = new Button(0, 0, 80, "Copy All", () -> {
            List<String> all = dc != null ? new ArrayList<>(dc.getPlugins()) : new ArrayList<>();
            if (ArchivistMod.INSTANCE != null && ArchivistMod.INSTANCE.guiScraper != null) {
                all.addAll(ArchivistMod.INSTANCE.guiScraper.getIdentifiedPlugins());
            }
            Set<String> unique = new LinkedHashSet<>(all);
            Minecraft.getInstance().keyboardHandler.setClipboard(String.join(", ", unique));
            EventBus.post(LogEvent.Type.SYSTEM, "Plugins copied to clipboard");
        });
        copyAllBtn.setAnchor(Widget.Anchor.BOTTOM);
        copyAllBtn.setFixedHeight(14);
        pluginListWindow.addChild(copyAllBtn);
    }

    private void filterPlugins(String query, ServerDataCollector dc) {
        if (pluginList == null) return;
        pluginList.clearItems();
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<String> plugins = dc != null ? dc.getPlugins() : Collections.emptyList();
        for (String p : plugins) {
            if (q.isEmpty() || p.toLowerCase(Locale.ROOT).contains(q)) {
                pluginList.addItem(p, ColorScheme.get().eventPlugin());
            }
        }
    }

    private void buildConnectionLogWindow() {
        connectionLogWindow.clearChildren();

        // Session timeline bar at the top
        TimelineBar timeline = new TimelineBar(0, 0, 260, 14);
        timeline.setAnchor(Widget.Anchor.TOP);
        timeline.setFixedHeight(14);
        timeline.setMargins(4, 0, 0, 0);
        connectionLogWindow.addChild(timeline);

        connectionLogList = new ScrollableList(0, 0, 260, 170);
        connectionLogList.setAnchor(Widget.Anchor.FILL);
        connectionLogList.setMargins(20, 0, 0, 0);
        connectionLogList.setAutoScroll(true);

        // Populate with existing events
        for (LogEvent event : EventBus.getEvents()) {
            connectionLogList.addItem(event.formatted(), ColorScheme.get().eventColor(event.getType()));
        }
        lastEventCount = EventBus.size();

        connectionLogWindow.addChild(connectionLogList);
    }

    private void buildConsoleWindow() {
        consoleWindow.clearChildren();

        consoleOutput = new ScrollableList(0, 0, 280, 140);
        consoleOutput.setAnchor(Widget.Anchor.FILL_ABOVE);
        consoleOutput.setMargins(0, 0, 18, 0); // leave 18px for input row
        consoleOutput.setAutoScroll(true);

        // Welcome message
        consoleOutput.addItem("[Archivist Console]", ColorScheme.get().eventSystem());
        consoleOutput.addItem("Type !help for commands.", ColorScheme.get().textSecondary());
        consoleOutput.addItem("", 0);

        consoleWindow.addChild(consoleOutput);

        // Input field — bottom, leaves room on right for send button
        consoleInput = new TextField(0, 0, 240, "Type !command...");
        consoleInput.setAnchor(Widget.Anchor.BOTTOM);
        consoleInput.setFixedHeight(14);
        consoleInput.setMargins(0, 55, 0, 0); // 55px right margin for send button
        consoleWindow.addChild(consoleInput);

        // Wire autocomplete
        consoleInput.setAutoCompleteProvider(input -> CommandRegistry.getCompletions(input));
        consoleInput.setOnShowSuggestions(suggestions -> {
            for (String s : suggestions) {
                consoleOutput.addItem("  " + s, ColorScheme.get().textSecondary());
            }
        });

        Button sendBtn = new Button(0, 0, 50, "Send", this::submitConsoleCommand);
        sendBtn.setAnchor(Widget.Anchor.BOTTOM_RIGHT);
        sendBtn.setFixedWidth(50);
        sendBtn.setFixedHeight(14);
        consoleWindow.addChild(sendBtn);
    }

    private void submitConsoleCommand() {
        if (consoleInput == null) return;
        String input = consoleInput.getText().trim();
        if (input.isEmpty()) return;

        consoleOutput.addItem("> " + input, ColorScheme.get().textPrimary());

        Consumer<String> output = line -> consoleOutput.addItem(line, ColorScheme.get().eventSystem());
        CommandRegistry.dispatch(input, output);

        consoleInput.clear();
    }

    private void buildSettingsWindow() {
        // Preserve active tab across rebuilds
        for (Widget child : settingsWindow.getChildren()) {
            if (child instanceof TabContainer tc) {
                settingsActiveTab = tc.getActiveTab();
                break;
            }
        }
        settingsWindow.clearChildren();

        TabContainer tabs = new TabContainer(0, 0, 210, 260);
        tabs.setAnchor(Widget.Anchor.FILL);

        // ── General Tab ─────────────────────────────────────────────────────
        Panel generalTab = tabs.addTab("General");
        String keyName = ArchivistMod.INSTANCE != null
                ? KeyBindingHelper.getBoundKeyOf(ArchivistMod.INSTANCE.openGuiKey).getDisplayName().getString()
                : "Z";
        generalTab.addChild(new Label(0, 0, 200, "Keybind: " + keyName + " (rebind in Options > Controls)", ColorScheme.get().textSecondary()));

        ArchivistConfig cfg = getExtConfig();
        generalTab.addChild(new CheckBox(0, 0, 200, "Auto probe GUIs on join (breaks buttons for 2 seconds)",
                cfg != null && cfg.autoScrapeOnJoin,
                v -> {
                    if (cfg != null) { cfg.autoScrapeOnJoin = v; cfg.save(); }
                    if (!v && ArchivistMod.INSTANCE != null) ArchivistMod.INSTANCE.guiScraper.reset();
                }));
        generalTab.addChild(new CheckBox(0, 0, 200, "Smart probe GUIs (only if survival items found)",
                cfg == null || cfg.smartProbeOnJoin,
                v -> {
                    if (cfg != null) { cfg.smartProbeOnJoin = v; cfg.save(); }
                    if (!v && ArchivistMod.INSTANCE != null) ArchivistMod.INSTANCE.guiScraper.reset();
                }));
        generalTab.addChild(new CheckBox(0, 0, 200, "Silent scraper (hide chat)",
                cfg == null || cfg.silentScraper,
                v -> {
                    if (cfg != null) { cfg.silentScraper = v; cfg.save(); }
                    if (ArchivistMod.INSTANCE != null) {
                        ArchivistMod.INSTANCE.guiScraper.setSilentMode(v);
                    }
                }));
        generalTab.addChild(new CheckBox(0, 0, 200, "Log plugins",
                cfg == null || cfg.logPlugins,
                v -> { if (cfg != null) { cfg.logPlugins = v; cfg.save(); } }));
        generalTab.addChild(new CheckBox(0, 0, 200, "Log world info",
                cfg == null || cfg.logWorldInfo,
                v -> { if (cfg != null) { cfg.logWorldInfo = v; cfg.save(); } }));
        generalTab.addChild(new CheckBox(0, 0, 200, "Log connection metadata",
                cfg == null || cfg.logConnectionMeta,
                v -> { if (cfg != null) { cfg.logConnectionMeta = v; cfg.save(); } }));
        generalTab.addChild(new CheckBox(0, 0, 200, "Show HUD summary",
                cfg == null || cfg.showHudSummary,
                v -> { if (cfg != null) { cfg.showHudSummary = v; cfg.save(); } }));
        generalTab.addChild(new CheckBox(0, 0, 200, "Show scan overlay",
                cfg == null || cfg.showScanOverlay,
                v -> { if (cfg != null) { cfg.showScanOverlay = v; cfg.save(); } }));
        generalTab.addChild(new CheckBox(0, 0, 200, "Search bar pop-up in Menu",
                cfg == null || cfg.searchBarPopup,
                v -> { if (cfg != null) { cfg.searchBarPopup = v; cfg.save(); } }));
        generalTab.addChild(new CheckBox(0, 0, 200, "GUI Animations",
                cfg == null || cfg.guiAnimations,
                v -> {
                    if (cfg != null) { cfg.guiAnimations = v; cfg.save(); }
                    DraggableWindow.animationsEnabled = v;
                }));

        GuiFingerprintEngine fpEngine = GuiFingerprintEngine.getInstance();
        generalTab.addChild(new CheckBox(0, 0, 200, "Auto GUI Inspect (adds a tiny delay to GUIs)",
                fpEngine.isInspectorEnabled(),
                v -> fpEngine.setInspectorEnabled(v)));
        generalTab.addChild(new Label(0, 0, 200, ""));
        generalTab.addChild(new Button(0, 0, 120, "Reset Window Positions", () -> {
            String savedTheme = guiConfig.activeTheme;
            guiConfig = new GuiConfig();
            guiConfig.activeTheme = savedTheme;
            guiConfig.save();
            init();
        }));

        // ── Theme Tab (live preview + URL import) ─────────────────────────
        Panel themeTab = tabs.addTab("Theme");
        themeTab.addChild(new Label(0, 0, 200, "Select a theme:", ColorScheme.get().textSecondary()));

        // Theme dropdown with live preview
        List<String> themeNames = new ArrayList<>(ThemeCommand.getThemes().keySet());
        String currentThemeName = ColorScheme.get().name().toLowerCase(Locale.ROOT);
        Dropdown themeDropdown = new Dropdown(0, 0, 200, "",
                themeNames, currentThemeName,
                v -> {
                    // Live preview: apply theme immediately
                    ColorScheme theme = ThemeCommand.getThemes().get(v);
                    if (theme != null) {
                        ColorScheme.setActive(theme);
                        guiConfig.activeTheme = theme.name();
                        guiConfig.save();
                        init(); // Rebuild to apply new colors
                    }
                });
        themeTab.addChild(themeDropdown);

        themeTab.addChild(new Label(0, 0, 200, "Current: " + ColorScheme.get().name(), ColorScheme.get().accent()));

        // ── Connections Tab (Unified DB + REST API) ────────────────────────
        Panel connTab = tabs.addTab("Connections");
        String adapterType = cfg != null ? cfg.databaseAdapterType : "None";
        ApiConfig apiCfg = ArchivistMod.INSTANCE != null ? ArchivistMod.INSTANCE.apiConfig : null;
        ApiSyncManager apiSync = ArchivistMod.INSTANCE != null ? ArchivistMod.INSTANCE.apiSyncManager : null;

        connTab.addChild(new Label(0, 0, 200, "Connection type:", ColorScheme.get().textSecondary()));
        Dropdown adapterDropdown = new Dropdown(0, 0, 200, "",
                List.of("None", "Archivist", "REST API", "Discord Bot", "Custom"),
                adapterType,
                v -> {
                    if (cfg != null) { cfg.databaseAdapterType = v; cfg.save(); }
                    if (apiCfg != null) { apiCfg.enabled = "REST API".equals(v) || "Archivist".equals(v); apiCfg.save(); }
                    buildSettingsWindow(); // rebuild to show type-specific fields
                });
        connTab.addChild(adapterDropdown);

        if ("Archivist".equals(adapterType)) {
            // ── Archivist fields (simplified REST API) ──
            if (apiCfg != null) {
                connTab.addChild(new Label(0, 0, 200, "Base URL:", ColorScheme.get().textSecondary()));
                TextField baseUrlField = new TextField(0, 0, 200, "https://example.com/api");
                baseUrlField.setText(apiCfg.baseUrl);
                baseUrlField.setOnChange(v -> {
                    apiCfg.baseUrl = v.replaceAll("/+$", "");
                    apiCfg.save();
                    if (apiSync != null) apiSync.refreshClient();
                });
                connTab.addChild(baseUrlField);

                connTab.addChild(new Label(0, 0, 200, "— Auth Headers —", ColorScheme.get().accent()));

                ScrollableList headerList = new ScrollableList(0, 0, 200, 50);
                List<String> headerNames = new ArrayList<>(apiCfg.getAuthHeaderNames());
                for (String name : headerNames) {
                    Map<String, String> decoded = apiCfg.getDecodedAuthHeaders();
                    String masked = ApiConfig.maskSecret(decoded.getOrDefault(name, ""));
                    headerList.addItem(name + ": " + masked, ColorScheme.get().textPrimary());
                }
                if (headerNames.isEmpty()) {
                    headerList.addItem("(no headers)", ColorScheme.get().textSecondary());
                }
                // Right-click to rename/remove headers
                headerList.setOnRightClick((item, index, mx, my) -> {
                    if (index >= headerNames.size()) return;
                    String hdrName = headerNames.get(index);
                    ContextMenu menu = new ContextMenu(mx, my);
                    menu.addItem("Rename", () -> {
                        TextField renameField = new TextField(0, 0, 140, hdrName);
                        renameField.setText(hdrName);
                        Button confirmBtn = new Button(0, 0, 60, "OK", () -> {
                            String newName = renameField.getText().trim();
                            if (!newName.isEmpty() && !newName.equals(hdrName)) {
                                Map<String, String> decoded = apiCfg.getDecodedAuthHeaders();
                                String val = decoded.getOrDefault(hdrName, "");
                                apiCfg.removeAuthHeader(hdrName);
                                apiCfg.setAuthHeader(newName, val);
                                apiCfg.save();
                                if (apiSync != null) apiSync.refreshClient();
                                buildSettingsWindow();
                            }
                            PopupLayer.close();
                        });
                        Panel renamePanel = new Panel(0, 0, 160, 40);
                        renamePanel.addChild(renameField);
                        renamePanel.addChild(confirmBtn);
                        PopupLayer.open(renamePanel, () -> new int[]{mx, my}, null);
                    });
                    menu.addItem("Remove", () -> {
                        apiCfg.removeAuthHeader(hdrName);
                        apiCfg.save();
                        if (apiSync != null) apiSync.refreshClient();
                        buildSettingsWindow();
                    });
                    PopupLayer.open(menu, () -> new int[]{mx, my}, null);
                });
                connTab.addChild(headerList);

                TextField headerName = new TextField(0, 0, 95, "Header name");
                TextField headerValue = new TextField(0, 0, 95, "Value", true);
                connTab.addChild(headerName);
                connTab.addChild(headerValue);
                connTab.addChild(new Button(0, 0, 80, "Add Header", () -> {
                    String hn = headerName.getText().trim();
                    String hv = headerValue.getText().trim();
                    if (!hn.isEmpty() && !hv.isEmpty()) {
                        apiCfg.setAuthHeader(hn, hv);
                        apiCfg.save();
                        if (apiSync != null) apiSync.refreshClient();
                        buildSettingsWindow();
                    }
                }));

                connTab.addChild(new Label(0, 0, 200, "Reset endpoint:", ColorScheme.get().textSecondary()));
                TextField resetEpField = new TextField(0, 0, 200, "/reset");
                resetEpField.setText(apiCfg.resetEndpoint);
                resetEpField.setOnChange(v -> { apiCfg.resetEndpoint = v; apiCfg.save(); });
                connTab.addChild(resetEpField);

                connTab.addChild(new Label(0, 0, 200, "Reset key:", ColorScheme.get().textSecondary()));
                TextField resetKeyField = new TextField(0, 0, 200, "Reset key", true);
                if (!apiCfg.getDecodedResetKey().isEmpty()) resetKeyField.setText(apiCfg.getDecodedResetKey());
                resetKeyField.setOnChange(v -> { apiCfg.setResetKey(v); apiCfg.save(); });
                connTab.addChild(resetKeyField);

                connTab.addChild(new CheckBox(0, 0, 200, "Auto-push on leave",
                        apiCfg.autoPush,
                        v -> { apiCfg.autoPush = v; apiCfg.save(); }));

                Label connStatus = new Label(0, 0, 200, apiCfg.isConfigured() ? "Status: Ready" : "Status: Not configured",
                        ColorScheme.get().textSecondary());
                connTab.addChild(connStatus);

                connTab.addChild(new Button(0, 0, 100, "Test Connection", () -> {
                    if (apiSync == null) return;
                    connStatus.setText("Status: Testing...");
                    connStatus.setColor(ColorScheme.get().textSecondary());
                    apiSync.testConnection(r -> {
                        Minecraft.getInstance().execute(() -> {
                            if (r.success()) {
                                connStatus.setText("Status: Connected (" + r.statusCode() + " OK)");
                                connStatus.setColor(ColorScheme.get().eventConnect());
                            } else {
                                String err = r.statusCode() > 0 ? "HTTP " + r.statusCode() : "Connection error";
                                connStatus.setText("Status: Failed (" + err + ")");
                                connStatus.setColor(ColorScheme.get().eventError());
                            }
                        });
                    });
                }));
                connTab.addChild(new Button(0, 0, 100, "Push Now", () -> {
                    if (apiSync == null) return;
                    connStatus.setText("Status: Pushing...");
                    connStatus.setColor(ColorScheme.get().textSecondary());
                    apiSync.pushSession(ArchivistMod.INSTANCE.dataCollector, r -> {
                        Minecraft.getInstance().execute(() -> {
                            if (r.success()) {
                                connStatus.setText("Status: Push OK (" + r.statusCode() + ")");
                                connStatus.setColor(ColorScheme.get().eventConnect());
                            } else {
                                String err = r.statusCode() > 0 ? "HTTP " + r.statusCode() : "Connection error";
                                connStatus.setText("Status: Push failed (" + err + ")");
                                connStatus.setColor(ColorScheme.get().eventError());
                            }
                        });
                    });
                }));
                connTab.addChild(new Button(0, 0, 100, "Download Logs", () -> {
                    if (apiSync == null) return;
                    connStatus.setText("Status: Downloading...");
                    connStatus.setColor(ColorScheme.get().textSecondary());
                    apiSync.downloadLogs(r -> {
                        Minecraft.getInstance().execute(() -> {
                            if (r.success()) {
                                connStatus.setText("Status: Download OK (" + r.statusCode() + ")");
                                connStatus.setColor(ColorScheme.get().eventConnect());
                            } else {
                                String err = r.statusCode() > 0 ? "HTTP " + r.statusCode() : "Connection error";
                                connStatus.setText("Status: Download failed (" + err + ")");
                                connStatus.setColor(ColorScheme.get().eventError());
                            }
                        });
                    });
                }));
                connTab.addChild(new Button(0, 0, 100, "Reset Logs", () -> {
                    if (apiSync == null) return;
                    connStatus.setText("Status: Resetting...");
                    connStatus.setColor(ColorScheme.get().textSecondary());
                    apiSync.resetLogs(r -> {
                        Minecraft.getInstance().execute(() -> {
                            if (r.success()) {
                                connStatus.setText("Status: Reset OK (" + r.statusCode() + ")");
                                connStatus.setColor(ColorScheme.get().eventConnect());
                            } else {
                                String err = r.statusCode() > 0 ? "HTTP " + r.statusCode() : "Connection error";
                                connStatus.setText("Status: Reset failed (" + err + ")");
                                connStatus.setColor(ColorScheme.get().eventError());
                            }
                        });
                    });
                }));
            }
        } else if ("REST API".equals(adapterType)) {
            // ── REST API fields ──
            if (apiCfg != null) {
                connTab.addChild(new Label(0, 0, 200, "Base URL:", ColorScheme.get().textSecondary()));
                TextField baseUrlField = new TextField(0, 0, 200, "https://example.com/api");
                baseUrlField.setText(apiCfg.baseUrl);
                baseUrlField.setOnChange(v -> {
                    apiCfg.baseUrl = v.replaceAll("/+$", ""); // strip trailing slashes
                    apiCfg.save();
                    if (apiSync != null) apiSync.refreshClient();
                });
                connTab.addChild(baseUrlField);

                connTab.addChild(new Label(0, 0, 200, "Endpoints:", ColorScheme.get().textSecondary()));
                TextField pushEp = new TextField(0, 0, 200, "/push");
                pushEp.setText(apiCfg.pushEndpoint);
                pushEp.setOnChange(v -> { apiCfg.pushEndpoint = v; apiCfg.save(); });
                connTab.addChild(pushEp);

                TextField dlEp = new TextField(0, 0, 200, "/download");
                dlEp.setText(apiCfg.downloadEndpoint);
                dlEp.setOnChange(v -> { apiCfg.downloadEndpoint = v; apiCfg.save(); });
                connTab.addChild(dlEp);

                TextField resetEp = new TextField(0, 0, 200, "/reset");
                resetEp.setText(apiCfg.resetEndpoint);
                resetEp.setOnChange(v -> { apiCfg.resetEndpoint = v; apiCfg.save(); });
                connTab.addChild(resetEp);

                connTab.addChild(new Label(0, 0, 200, "— Auth Headers —", ColorScheme.get().accent()));

                ScrollableList headerList2 = new ScrollableList(0, 0, 200, 50);
                List<String> headerNames2 = new ArrayList<>(apiCfg.getAuthHeaderNames());
                for (String name : headerNames2) {
                    Map<String, String> decoded = apiCfg.getDecodedAuthHeaders();
                    String masked = ApiConfig.maskSecret(decoded.getOrDefault(name, ""));
                    headerList2.addItem(name + ": " + masked, ColorScheme.get().textPrimary());
                }
                if (headerNames2.isEmpty()) {
                    headerList2.addItem("(no headers)", ColorScheme.get().textSecondary());
                }
                // Right-click to rename/remove headers
                headerList2.setOnRightClick((item, index, mx, my) -> {
                    if (index >= headerNames2.size()) return;
                    String hdrName = headerNames2.get(index);
                    ContextMenu menu = new ContextMenu(mx, my);
                    menu.addItem("Rename", () -> {
                        TextField renameField = new TextField(0, 0, 140, hdrName);
                        renameField.setText(hdrName);
                        Button confirmBtn = new Button(0, 0, 60, "OK", () -> {
                            String newName = renameField.getText().trim();
                            if (!newName.isEmpty() && !newName.equals(hdrName)) {
                                Map<String, String> decoded2 = apiCfg.getDecodedAuthHeaders();
                                String val = decoded2.getOrDefault(hdrName, "");
                                apiCfg.removeAuthHeader(hdrName);
                                apiCfg.setAuthHeader(newName, val);
                                apiCfg.save();
                                if (apiSync != null) apiSync.refreshClient();
                                buildSettingsWindow();
                            }
                            PopupLayer.close();
                        });
                        Panel renamePanel = new Panel(0, 0, 160, 40);
                        renamePanel.addChild(renameField);
                        renamePanel.addChild(confirmBtn);
                        PopupLayer.open(renamePanel, () -> new int[]{mx, my}, null);
                    });
                    menu.addItem("Remove", () -> {
                        apiCfg.removeAuthHeader(hdrName);
                        apiCfg.save();
                        if (apiSync != null) apiSync.refreshClient();
                        buildSettingsWindow();
                    });
                    PopupLayer.open(menu, () -> new int[]{mx, my}, null);
                });
                connTab.addChild(headerList2);

                TextField headerName = new TextField(0, 0, 95, "Header name");
                TextField headerValue = new TextField(0, 0, 95, "Value", true);
                connTab.addChild(headerName);
                connTab.addChild(headerValue);
                connTab.addChild(new Button(0, 0, 80, "Add Header", () -> {
                    String hn = headerName.getText().trim();
                    String hv = headerValue.getText().trim();
                    if (!hn.isEmpty() && !hv.isEmpty()) {
                        apiCfg.setAuthHeader(hn, hv);
                        apiCfg.save();
                        if (apiSync != null) apiSync.refreshClient();
                        buildSettingsWindow();
                    }
                }));

                connTab.addChild(new Label(0, 0, 200, "Reset key:", ColorScheme.get().textSecondary()));
                TextField resetKeyField = new TextField(0, 0, 200, "Reset key", true);
                if (!apiCfg.getDecodedResetKey().isEmpty()) resetKeyField.setText(apiCfg.getDecodedResetKey());
                resetKeyField.setOnChange(v -> { apiCfg.setResetKey(v); apiCfg.save(); });
                connTab.addChild(resetKeyField);

                connTab.addChild(new CheckBox(0, 0, 200, "Auto-push on leave",
                        apiCfg.autoPush,
                        v -> { apiCfg.autoPush = v; apiCfg.save(); }));

                // Status label for inline feedback
                Label connStatus = new Label(0, 0, 200, apiCfg.isConfigured() ? "Status: Ready" : "Status: Not configured",
                        ColorScheme.get().textSecondary());
                connTab.addChild(connStatus);

                connTab.addChild(new Button(0, 0, 100, "Test Connection", () -> {
                    if (apiSync == null) return;
                    connStatus.setText("Status: Testing...");
                    connStatus.setColor(ColorScheme.get().textSecondary());
                    apiSync.testConnection(r -> {
                        Minecraft.getInstance().execute(() -> {
                            if (r.success()) {
                                connStatus.setText("Status: Connected (" + r.statusCode() + " OK)");
                                connStatus.setColor(ColorScheme.get().eventConnect());
                            } else {
                                String err = r.statusCode() > 0 ? "HTTP " + r.statusCode() : "Connection error";
                                connStatus.setText("Status: Failed (" + err + ")");
                                connStatus.setColor(ColorScheme.get().eventError());
                            }
                        });
                    });
                }));
                connTab.addChild(new Button(0, 0, 100, "Push Now", () -> {
                    if (apiSync == null) return;
                    connStatus.setText("Status: Pushing...");
                    connStatus.setColor(ColorScheme.get().textSecondary());
                    apiSync.pushSession(ArchivistMod.INSTANCE.dataCollector, r -> {
                        Minecraft.getInstance().execute(() -> {
                            if (r.success()) {
                                connStatus.setText("Status: Push OK (" + r.statusCode() + ")");
                                connStatus.setColor(ColorScheme.get().eventConnect());
                            } else {
                                String err = r.statusCode() > 0 ? "HTTP " + r.statusCode() : "Connection error";
                                connStatus.setText("Status: Push failed (" + err + ")");
                                connStatus.setColor(ColorScheme.get().eventError());
                            }
                        });
                    });
                }));
                connTab.addChild(new Button(0, 0, 100, "Download Logs", () -> {
                    if (apiSync == null) return;
                    connStatus.setText("Status: Downloading...");
                    connStatus.setColor(ColorScheme.get().textSecondary());
                    apiSync.downloadLogs(r -> {
                        Minecraft.getInstance().execute(() -> {
                            if (r.success()) {
                                connStatus.setText("Status: Download OK (" + r.statusCode() + ")");
                                connStatus.setColor(ColorScheme.get().eventConnect());
                            } else {
                                String err = r.statusCode() > 0 ? "HTTP " + r.statusCode() : "Connection error";
                                connStatus.setText("Status: Download failed (" + err + ")");
                                connStatus.setColor(ColorScheme.get().eventError());
                            }
                        });
                    });
                }));
            }
        } else if (!"None".equals(adapterType)) {
            // ── Database fields (Discord Bot, Custom) ──
            connTab.addChild(new Label(0, 0, 200, "Webhook URL:", ColorScheme.get().textSecondary()));
            TextField connStr = new TextField(0, 0, 200, "https://discord.com/api/webhooks/...");
            if (cfg != null) connStr.setText(cfg.databaseConnectionString);
            connStr.setOnChange(v -> { if (cfg != null) { cfg.databaseConnectionString = v; cfg.save(); } });
            connTab.addChild(connStr);

            connTab.addChild(new Label(0, 0, 200, "Bot Token:", ColorScheme.get().textSecondary()));
            TextField authToken = new TextField(0, 0, 200, "Bot token (optional)", true);
            if (cfg != null && !cfg.databaseAuthToken.isEmpty()) authToken.setText(cfg.databaseAuthToken);
            authToken.setOnChange(v -> { if (cfg != null) { cfg.databaseAuthToken = v; cfg.save(); } });
            connTab.addChild(authToken);

            connTab.addChild(new CheckBox(0, 0, 200, "Auto-upload on log",
                    cfg != null && cfg.autoUploadOnLog,
                    v -> { if (cfg != null) { cfg.autoUploadOnLog = v; cfg.save(); } }));

            Label dbStatus = new Label(0, 0, 200, "Status: " +
                    (ArchivistMod.INSTANCE != null ? ArchivistMod.INSTANCE.databaseManager.getStatusMessage() : "N/A"),
                    ColorScheme.get().textSecondary());
            connTab.addChild(dbStatus);

            connTab.addChild(new Button(0, 0, 100, "Test Connection", () -> {
                if (ArchivistMod.INSTANCE != null) {
                    DatabaseManager dbm = ArchivistMod.INSTANCE.databaseManager;
                    if (cfg != null) {
                        dbStatus.setText("Status: Connecting...");
                        dbm.connect(cfg.databaseAdapterType, cfg.databaseConnectionString, cfg.databaseAuthToken);
                        EventBus.post(LogEvent.Type.DB_SYNC, "Testing " + cfg.databaseAdapterType + " connection...");
                    }
                }
            }));
            connTab.addChild(new Button(0, 0, 100, "Push Now", () -> {
                if (ArchivistMod.INSTANCE == null) return;
                DatabaseManager dbm = ArchivistMod.INSTANCE.databaseManager;
                if (dbm.getActiveAdapter() == null) {
                    dbStatus.setText("Status: No active adapter");
                    dbStatus.setColor(ColorScheme.get().eventError());
                    return;
                }
                dbStatus.setText("Status: Pushing...");
                dbStatus.setColor(ColorScheme.get().textSecondary());
                ServerLogData snapshot = ServerLogData.fromCollector(ArchivistMod.INSTANCE.dataCollector);
                dbm.upload(snapshot);
                EventBus.post(LogEvent.Type.DB_SYNC, "Push initiated");
            }));
        } else {
            connTab.addChild(new Label(0, 0, 200, "Select a connection type above.", ColorScheme.get().textSecondary()));
        }

        // ── Exceptions Tab ──────────────────────────────────────────────────
        Panel exceptionsTab = tabs.addTab("Exceptions");
        ExceptionResolver exResolver = ArchivistMod.INSTANCE != null ? ArchivistMod.INSTANCE.exceptionResolver : null;

        exceptionsTab.addChild(new Label(0, 0, 200, "Proxy/hub servers where the domain", ColorScheme.get().textSecondary()));
        exceptionsTab.addChild(new Label(0, 0, 200, "is resolved from tab & scoreboard:", ColorScheme.get().textSecondary()));

        ScrollableList exList = new ScrollableList(0, 0, 200, 80);
        if (exResolver != null) {
            for (String srv : exResolver.getServers()) {
                exList.addItem(srv, ColorScheme.get().textPrimary());
            }
            if (exResolver.getServers().isEmpty()) {
                exList.addItem("(none)", ColorScheme.get().textSecondary());
            }
        }
        // Right-click to rename/remove
        exList.setOnRightClick((item, index, mx, my) -> {
            if (exResolver == null || item.text.equals("(none)")) return;
            String serverName = item.text;
            ContextMenu menu = new ContextMenu(mx, my);
            menu.addItem("Rename", () -> {
                TextField renameField = new TextField(0, 0, 140, serverName);
                renameField.setText(serverName);
                Button confirmBtn = new Button(0, 0, 60, "OK", () -> {
                    String newName = renameField.getText().trim();
                    if (!newName.isEmpty()) {
                        Set<String> updated = new LinkedHashSet<>();
                        for (String s : exResolver.getServers()) {
                            updated.add(s.equals(serverName) ? newName.toLowerCase(Locale.ROOT) : s);
                        }
                        exResolver.setServers(updated);
                        exResolver.save();
                        buildSettingsWindow();
                    }
                    PopupLayer.close();
                });
                Panel renamePanel = new Panel(0, 0, 160, 40);
                renamePanel.addChild(renameField);
                renamePanel.addChild(confirmBtn);
                PopupLayer.open(renamePanel, () -> new int[]{mx, my}, null);
            });
            menu.addItem("Remove", () -> {
                Set<String> updated = new LinkedHashSet<>(exResolver.getServers());
                updated.remove(serverName.toLowerCase(Locale.ROOT));
                exResolver.setServers(updated);
                exResolver.save();
                buildSettingsWindow();
            });
            PopupLayer.open(menu, () -> new int[]{mx, my}, null);
        });
        exceptionsTab.addChild(exList);

        TextField addExField = new TextField(0, 0, 140, "e.g. minehut.com");
        exceptionsTab.addChild(addExField);
        exceptionsTab.addChild(new Button(0, 0, 80, "Add", () -> {
            String srv = addExField.getText().trim();
            if (!srv.isEmpty() && exResolver != null) {
                Set<String> updated = new LinkedHashSet<>(exResolver.getServers());
                updated.add(srv.toLowerCase(Locale.ROOT));
                exResolver.setServers(updated);
                exResolver.save();
                buildSettingsWindow();
            }
        }));

        // ── Export Tab ──────────────────────────────────────────────────────
        Panel exportTab = tabs.addTab("Export");
        exportTab.addChild(new Label(0, 0, 200, "Export current data:", ColorScheme.get().textSecondary()));

        exportTab.addChild(new Button(0, 0, 100, "Export JSON", () -> {
            String path = LogExporter.exportJson();
            EventBus.post(LogEvent.Type.SYSTEM, path != null ? "Exported: " + path : "Export failed");
        }));
        exportTab.addChild(new Button(0, 0, 100, "Export CSV", () -> {
            String path = LogExporter.exportCsv();
            EventBus.post(LogEvent.Type.SYSTEM, path != null ? "Exported: " + path : "Export failed");
        }));
        exportTab.addChild(new Button(0, 0, 100, "Copy to Clipboard", () -> {
            LogExporter.exportToClipboard();
            EventBus.post(LogEvent.Type.SYSTEM, "Copied to clipboard");
        }));

        tabs.setActiveTab(settingsActiveTab);
        settingsWindow.addChild(tabs);
    }

    private void buildInspectorWindow() {
        inspectorWindow.clearChildren();

        inspectorList = new ScrollableList(0, 0, 230, 170);
        inspectorList.setAnchor(Widget.Anchor.FILL_ABOVE);
        inspectorList.setMargins(0, 0, 50, 0); // leave room for buttons

        GuiFingerprintEngine engine = GuiFingerprintEngine.getInstance();
        GuiCapture capture = engine.getLastInspectorCapture();
        if (capture != null) {
            inspectorList.addItem("Title: " + capture.titleRaw, ColorScheme.get().accent());
            inspectorList.addItem("Type: " + capture.containerType + " (" + capture.items.size() + " items)", ColorScheme.get().textSecondary());
            inspectorList.addItem("Captured: " + capture.timestamp, ColorScheme.get().textSecondary());
            inspectorList.addItem("", 0);

            for (GuiItemData item : capture.items) {
                inspectorList.addItem("[" + item.slot() + "] " + item.materialId(), ColorScheme.get().accent());
                inspectorList.addItem("  Name: " + item.displayName(), ColorScheme.get().textPrimary());
                for (String line : item.lore()) {
                    inspectorList.addItem("  Lore: " + line, ColorScheme.get().textSecondary());
                }
                inspectorList.addItem("  Count: " + item.count() + " | Glint: " + (item.hasEnchantGlint() ? "yes" : "no"), ColorScheme.get().textSecondary());
            }
        } else {
            inspectorList.addItem("No capture yet.", ColorScheme.get().textSecondary());
            inspectorList.addItem("Enable with !inspector then", ColorScheme.get().textSecondary());
            inspectorList.addItem("open any server GUI.", ColorScheme.get().textSecondary());
        }

        inspectorWindow.addChild(inspectorList);

        Button saveBtn = new Button(0, 0, 100, "Save Capture", () -> {
            GuiCapture saveCap = engine.getLastInspectorCapture();
            if (saveCap != null) {
                engine.saveCapture(saveCap);
            }
        });
        saveBtn.setAnchor(Widget.Anchor.BOTTOM);
        saveBtn.setFixedHeight(14);
        saveBtn.setMargins(0, 0, 32, 0);
        inspectorWindow.addChild(saveBtn);

        Button genFpBtn = new Button(0, 0, 130, "Copy to Clipboard", () -> {
            GuiCapture genCap = engine.getLastInspectorCapture();
            if (genCap != null) {
                String json = FingerprintGenerator.generate(genCap);
                Minecraft.getInstance().keyboardHandler.setClipboard(json);
                EventBus.post(LogEvent.Type.SYSTEM, "Fingerprint template copied to clipboard");
            }
        });
        genFpBtn.setAnchor(Widget.Anchor.BOTTOM);
        genFpBtn.setFixedHeight(14);
        genFpBtn.setMargins(0, 0, 16, 0);
        inspectorWindow.addChild(genFpBtn);

        Button probeBtn = new Button(0, 0, 120, "Run GUI Probe", () -> {
            AutoProbeSystem probe = AutoProbeSystem.getInstance();
            if (!probe.isProbing()) {
                var commands = GuiFingerprintEngine.getInstance().getDatabase().getAllProbeCommands();
                if (!commands.isEmpty()) {
                    probe.startProbing(commands);
                }
            }
        });
        probeBtn.setAnchor(Widget.Anchor.BOTTOM);
        probeBtn.setFixedHeight(14);
        inspectorWindow.addChild(probeBtn);
    }

    private void buildServerListWindow() {
        serverListWindow.clearChildren();

        ServerListPanel panel = new ServerListPanel(0, 0, 380, 310);
        panel.setAnchor(Widget.Anchor.FILL);

        List<ServerLogData> logs = ServerLogReader.readAll();
        serverListWindow.setTitle("Server Logs (" + logs.size() + ")");

        for (ServerLogData log : logs) {
            panel.addServer(
                    log.getDisplayName(),
                    log.version,
                    log.brand,
                    log.plugins.size(),
                    log.worlds.size(),
                    log.timestamp
            );
        }

        panel.setOnServerSelected(addr -> {
            EventBus.post(LogEvent.Type.SYSTEM, "Selected: " + addr);
        });

        panel.setOnViewDetails(addr -> {
            // Find the log data and populate info windows
            for (ServerLogData log : logs) {
                if (log.getDisplayName().equals(addr)) {
                    showServerLogDetail(log);
                    break;
                }
            }
        });

        panel.setOnExportServer(addr -> {
            for (ServerLogData log : logs) {
                if (log.getDisplayName().equals(addr)) {
                    LogExporter.exportServerLog(log);
                    break;
                }
            }
        });

        panel.setOnQuickConnect(addr -> {
            // Quick-connect: close the GUI and connect to the server
            for (ServerLogData log : logs) {
                if (log.getDisplayName().equals(addr)) {
                    String connectAddr = !"unknown".equals(log.domain) ? log.domain : log.ip;
                    if (log.port != 25565) connectAddr += ":" + log.port;
                    final String serverAddr = connectAddr;
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> {
                        try {
                            net.minecraft.client.multiplayer.ServerData serverData =
                                    new net.minecraft.client.multiplayer.ServerData(addr, serverAddr, net.minecraft.client.multiplayer.ServerData.Type.OTHER);
                            net.minecraft.client.multiplayer.resolver.ServerAddress parsed =
                                    net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(serverAddr);
                            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
                                    mc.screen, mc, parsed, serverData, false, null);
                        } catch (Exception e) {
                            EventBus.post(LogEvent.Type.ERROR, "Quick-connect failed: " + e.getMessage());
                        }
                    });
                    break;
                }
            }
        });

        panel.setOnDeleteServer(addr -> {
            // Delete the log file
            for (ServerLogData log : logs) {
                if (log.getDisplayName().equals(addr)) {
                    try {
                        java.nio.file.Path logDir = net.fabricmc.loader.api.FabricLoader.getInstance()
                                .getGameDir().resolve(ArchivistMod.INSTANCE.config.logFolder);
                        java.nio.file.Files.deleteIfExists(logDir.resolve(log.fileName));
                        EventBus.post(LogEvent.Type.SYSTEM, "Deleted: " + log.fileName);
                        buildServerListWindow(); // rebuild
                    } catch (Exception e) {
                        EventBus.post(LogEvent.Type.ERROR, "Delete failed: " + e.getMessage());
                    }
                    break;
                }
            }
        });

        serverListWindow.addChild(panel);
    }

    // Manual Log: stored previous values for undo
    private String manualLogPrevIp;
    private String manualLogPrevDomain;
    private int    manualLogPrevPort;
    private boolean manualLogHasUndo = false;

    private void buildManualLogWindow() {
        manualLogWindow.clearChildren();

        manualLogWindow.addChild(new Label(0, 0, 200, "Override server details:"));

        TextField ipField = new TextField(0, 0, 200, "IP (leave blank to keep)");
        TextField domainField = new TextField(0, 0, 200, "Domain (leave blank to keep)");
        TextField portField = new TextField(0, 0, 200, "Port (leave blank to keep)");

        manualLogWindow.addChild(ipField);
        manualLogWindow.addChild(domainField);
        manualLogWindow.addChild(portField);

        manualLogWindow.addChild(new Button(0, 0, 200, "Apply & Re-log", () -> {
            ServerDataCollector dc = getDataCollector();
            if (dc == null) return;
            // Save current values for undo
            manualLogPrevIp = dc.ip;
            manualLogPrevDomain = dc.domain;
            manualLogPrevPort = dc.port;
            manualLogHasUndo = true;
            // Apply overrides
            dc.applyManualOverrides(ipField.getText(), domainField.getText(), portField.getText());
            JsonLogger.write(dc);
            EventBus.post(LogEvent.Type.SYSTEM,
                    "[MANUAL] Server details updated \u2192 " + dc.domain + " (" + dc.ip + ":" + dc.port + ")");
            buildServerInfoWindow();
            buildManualLogWindow(); // rebuild to enable undo button
            ipField.clear();
            domainField.clear();
            portField.clear();
        }));

        Button undoBtn = new Button(0, 0, 200, "Undo", () -> {
            ServerDataCollector dc = getDataCollector();
            if (dc == null || !manualLogHasUndo) return;
            dc.ip = manualLogPrevIp;
            dc.domain = manualLogPrevDomain;
            dc.port = manualLogPrevPort;
            manualLogHasUndo = false;
            JsonLogger.write(dc);
            EventBus.post(LogEvent.Type.SYSTEM,
                    "[MANUAL] Reverted \u2192 " + dc.domain + " (" + dc.ip + ":" + dc.port + ")");
            buildServerInfoWindow();
            buildManualLogWindow(); // rebuild to disable undo button
        });
        undoBtn.setEnabled(manualLogHasUndo);
        manualLogWindow.addChild(undoBtn);
    }

    private void showServerLogDetail(ServerLogData log) {
        // Populate server info window with historical data
        serverInfoWindow.clearChildren();
        serverInfoWindow.addChild(new Label(0, 0, 180, "\u2500\u2500 Connection \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500", ColorScheme.get().accent()));
        addKV(serverInfoWindow, "IP", log.ip);
        addKV(serverInfoWindow, "Port", String.valueOf(log.port));
        addKV(serverInfoWindow, "Domain", log.domain);
        addKV(serverInfoWindow, "Version", log.version);
        addKV(serverInfoWindow, "Brand", log.brand);
        addKV(serverInfoWindow, "Players", String.valueOf(log.playerCount));
        addKV(serverInfoWindow, "Last Seen", log.timestamp);

        // ── Worlds section ──
        serverInfoWindow.addChild(new Label(0, 0, 180, ""));
        serverInfoWindow.addChild(new Label(0, 0, 180, "\u2500\u2500 Worlds (" + log.worlds.size() + ") \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500", ColorScheme.get().accent()));
        for (ServerLogData.WorldSession ws : log.worlds) {
            serverInfoWindow.addChild(new Label(0, 0, 180, ws.dimension, ColorScheme.get().accent()));
            serverInfoWindow.addChild(new Label(0, 0, 180, "  " + ws.timestamp, ColorScheme.get().textSecondary()));
            if (ws.resourcePack != null) {
                serverInfoWindow.addChild(new Label(0, 0, 180, "  RP: " + ws.resourcePack, ColorScheme.get().textSecondary()));
            }
        }
        if (!log.detectedAddresses.isEmpty()) {
            serverInfoWindow.addChild(new Label(0, 0, 180, ""));
            serverInfoWindow.addChild(new Label(0, 0, 180, "Detected Addresses:", ColorScheme.get().accent()));
            for (String addr : log.detectedAddresses) {
                serverInfoWindow.addChild(new Label(0, 0, 180, "  " + addr, ColorScheme.get().textSecondary()));
            }
        }
        serverInfoWindow.setVisible(true);

        // Populate plugin list
        pluginListWindow.clearChildren();
        pluginListWindow.setTitle("Plugins (" + log.plugins.size() + ")");
        ScrollableList pList = new ScrollableList(0, 0, 160, 160);
        for (String p : log.plugins) {
            pList.addItem(p, ColorScheme.get().eventPlugin());
        }
        pluginListWindow.addChild(pList);
        pluginListWindow.setVisible(true);
    }

    private String buildServerLogJson(ServerLogData log) {
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.addProperty("timestamp", log.timestamp);
        com.google.gson.JsonObject info = new com.google.gson.JsonObject();
        info.addProperty("ip", log.ip);
        info.addProperty("port", log.port);
        info.addProperty("domain", log.domain);
        info.addProperty("brand", log.brand);
        info.addProperty("version", log.version);
        info.addProperty("player_count", log.playerCount);
        root.add("server_info", info);
        com.google.gson.JsonArray plugins = new com.google.gson.JsonArray();
        for (String p : log.plugins) {
            com.google.gson.JsonObject po = new com.google.gson.JsonObject();
            po.addProperty("name", p);
            plugins.add(po);
        }
        root.add("plugins", plugins);
        return root.toString();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Render
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // ── Layer 0: Render parent screen underneath ──
        if (parent != null) {
            parent.render(g, -1, -1, delta);
        }

        // ── Layer 1: Screen overlay ──
        g.fill(0, 0, width, height, ColorScheme.get().screenOverlay());

        // ── Layer 2: Gradient overlay ──
        GradientConfig grad = ColorScheme.get().getBackgroundGradient();
        if (grad != null) {
            g.fillGradient(0, 0, this.width, this.height, grad.topColor(), grad.bottomColor());
        }

        // Update live data
        updateLiveData();

        // Update active state
        for (DraggableWindow w : windows) w.setActive(false);
        for (int i = windows.size() - 1; i >= 0; i--) {
            if (windows.get(i).isVisible() && !windows.get(i).isAnimating()) {
                windows.get(i).setActive(true);
                break;
            }
        }

        // ── Layer 4: Tooltip begin frame ──
        TooltipManager.beginFrame();

        // Render windows (first = back, last = front)
        for (DraggableWindow w : windows) {
            w.render(g, mouseX, mouseY, delta);
        }

        // Taskbar always on top
        taskbar.updatePosition(width, height);
        taskbar.render(g, mouseX, mouseY, delta);

        // Search impression (between windows and popup layer)
        ArchivistConfig searchCfg = getExtConfig();
        if (!globalSearch.isOpen() && (searchCfg == null || searchCfg.searchBarPopup)) {
            renderSearchImpression(g, mouseX, mouseY);
        }

        // Popup overlay (above windows and taskbar)
        PopupLayer.render(g, mouseX, mouseY, delta);

        // Global search overlay (above everything except tooltips)
        if (globalSearch.isOpen()) {
            globalSearch.render(g, mouseX, mouseY, delta);
        }

        // ── Layer 5: Tooltips (topmost) ──
        TooltipManager.render(g);
    }

    private static final int IMPRESSION_WIDTH = 150;
    private static final int IMPRESSION_HEIGHT = 14;
    private boolean wasHoveringImpression = false;

    private void renderSearchImpression(GuiGraphics g, int mouseX, int mouseY) {
        // Auto-collapse search overlay when no query typed and mouse is outside
        if (globalSearch.isOpen() && !globalSearch.hasQuery() && !globalSearch.containsPoint(mouseX, mouseY)) {
            globalSearch.collapse();
        }

        ColorScheme cs = ColorScheme.get();
        int ix = (width - IMPRESSION_WIDTH) / 2;
        // Dent above screen: bar is half off-screen
        int iy = -(IMPRESSION_HEIGHT / 2);
        int visibleTop = 0; // top of visible portion
        int visibleH = IMPRESSION_HEIGHT / 2; // only bottom half is visible

        // Hover detection: only the visible half, centered horizontally
        int hoverPadX = IMPRESSION_WIDTH / 4; // shrink hover zone to center half
        boolean hovered = mouseX >= ix + hoverPadX && mouseX < ix + IMPRESSION_WIDTH - hoverPadX
                && mouseY >= visibleTop && mouseY < visibleTop + visibleH;

        // Draw the impression (semi-transparent recessed bar, clipped to screen)
        int bgAlpha = hovered ? 0x25 : 0x10;
        int bg = (bgAlpha << 24) | (cs.tooltipBg() & 0x00FFFFFF);
        int borderColor = (0x20 << 24) | (cs.tooltipBorder() & 0x00FFFFFF);
        RenderUtils.drawRect(g, ix, visibleTop, IMPRESSION_WIDTH, visibleH, bg);
        // Draw only the visible borders (bottom + sides of visible part)
        g.fill(ix, visibleTop + visibleH - 1, ix + IMPRESSION_WIDTH, visibleTop + visibleH, borderColor); // bottom
        g.fill(ix, visibleTop, ix + 1, visibleTop + visibleH, borderColor); // left
        g.fill(ix + IMPRESSION_WIDTH - 1, visibleTop, ix + IMPRESSION_WIDTH, visibleTop + visibleH, borderColor); // right

        // Text inside visible portion
        String displayText = globalSearch.getLastQuery().isEmpty() ? "Search..." : globalSearch.getLastQuery();
        int textAlpha = 0x35;
        int textColor = (textAlpha << 24) | (cs.textSecondary() & 0x00FFFFFF);
        int textY = visibleTop + (visibleH - RenderUtils.scaledFontHeight()) / 2;
        String trimmed = RenderUtils.trimToWidth(displayText, IMPRESSION_WIDTH - 10);
        RenderUtils.drawText(g, trimmed, ix + 5, textY, textColor);

        // Single downward arrow below the bar (decorative, non-interactive)
        int arrowY = visibleTop + visibleH + 1;
        int arrowColor = (0x25 << 24) | (cs.textSecondary() & 0x00FFFFFF);
        int arrowX = ix + IMPRESSION_WIDTH / 2 - 2;
        RenderUtils.drawTextAtScale(g, "\u25BC", arrowX, arrowY, arrowColor, 0.5f);

        // Hover: open search overlay only on hover-entry transition
        boolean effectiveHover = hovered && !PopupLayer.isOpen();
        if (effectiveHover) {
            boolean anyDragging = false;
            for (DraggableWindow w : windows) {
                if (w.isDragging()) { anyDragging = true; break; }
            }
            if (anyDragging) {
                effectiveHover = false;
            } else {
                for (int i = windows.size() - 1; i >= 0; i--) {
                    DraggableWindow w = windows.get(i);
                    if (w.isVisible() && w.containsPoint(mouseX, mouseY)) {
                        effectiveHover = false;
                        break;
                    }
                }
            }
        }

        if (effectiveHover && !wasHoveringImpression) {
            globalSearch.restore();
        }
        wasHoveringImpression = effectiveHover;
    }

    private void updateLiveData() {
        // Push new events to connection log and console
        List<LogEvent> events = EventBus.getEvents();
        int currentSize = events.size();
        if (currentSize > lastEventCount) {
            for (int i = lastEventCount; i < currentSize; i++) {
                LogEvent event = events.get(i);
                int color = ColorScheme.get().eventColor(event.getType());
                if (connectionLogList != null) {
                    connectionLogList.addItem(event.formatted(), color);
                }
            }
            lastEventCount = currentSize;
        }

        // Update plugin count in title
        ServerDataCollector dc = getDataCollector();
        if (dc != null && pluginListWindow != null) {
            pluginListWindow.setTitle("Plugins (" + dc.getPlugins().size() + ")");
        }

        // Refresh inspector when a new capture arrives
        GuiFingerprintEngine engine = GuiFingerprintEngine.getInstance();
        if (engine.isInspectorEnabled() && engine.getLastInspectorCapture() != null && inspectorWindow != null) {
            if (!inspectorWindow.isVisible()) {
                inspectorWindow.setVisible(true);
            }
            // Only rebuild inspector content if capture actually changed
            GuiCapture currentCapture = engine.getLastInspectorCapture();
            if (currentCapture != lastBuiltCapture) {
                lastBuiltCapture = currentCapture;
                buildInspectorWindow();
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Input
    // ══════════════════════════════════════════════════════════════════════════

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean bl) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        return handleMouseClicked(mouseX, mouseY, button) || super.mouseClicked(event, bl);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return handleMouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }
    *///?}

    private boolean handleMouseClicked(double mouseX, double mouseY, int button) {
        // Popup overlay first (dropdown menus etc.)
        if (PopupLayer.mouseClicked(mouseX, mouseY, button)) return true;

        // Global search overlay first
        if (globalSearch.isOpen()) {
            if (globalSearch.containsPoint(mouseX, mouseY)) {
                return globalSearch.onMouseClicked(mouseX, mouseY, button);
            } else {
                globalSearch.close();
            }
        }

        // Taskbar first (always on top)
        if (taskbar.containsPoint(mouseX, mouseY)) {
            if (taskbar.onMouseClicked(mouseX, mouseY, button)) {
                // Bring the clicked window to front
                DraggableWindow active = taskbar.getActiveWindow();
                if (active != null) {
                    bringToFront(active);
                }
                return true;
            }
        }

        // Windows in reverse order (top gets priority)
        for (int i = windows.size() - 1; i >= 0; i--) {
            DraggableWindow w = windows.get(i);
            if (w.isVisible() && w.containsPoint(mouseX, mouseY)) {
                if (w.onMouseClicked(mouseX, mouseY, button)) {
                    bringToFront(w);
                    taskbar.setActiveWindow(w);
                    return true;
                }
            }
        }

        return false;
    }

    //? if >=1.21.10 {

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        double mouseX = event.x(); double mouseY = event.y(); int button = event.button();
        if (PopupLayer.mouseReleased(mouseX, mouseY, button)) return true;
        for (int i = windows.size() - 1; i >= 0; i--) {
            if (windows.get(i).onMouseReleased(mouseX, mouseY, button)) return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double deltaX, double deltaY) {
        double mouseX = event.x(); double mouseY = event.y(); int button = event.button();
        if (PopupLayer.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        for (int i = windows.size() - 1; i >= 0; i--) {
            if (windows.get(i).onMouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (PopupLayer.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;
        for (int i = windows.size() - 1; i >= 0; i--) {
            if (windows.get(i).onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key(); int scanCode = event.scancode(); int modifiers = event.modifiers();
        if (handleKeyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        char chr = (char) event.codepoint(); int modifiers = event.modifiers();
        if (handleCharTyped(chr, modifiers)) return true;
        return super.charTyped(event);
    }

    //?} else {

    /*@Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (PopupLayer.mouseReleased(mouseX, mouseY, button)) return true;
        for (int i = windows.size() - 1; i >= 0; i--) {
            if (windows.get(i).onMouseReleased(mouseX, mouseY, button)) return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (PopupLayer.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        for (int i = windows.size() - 1; i >= 0; i--) {
            if (windows.get(i).onMouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (PopupLayer.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;
        for (int i = windows.size() - 1; i >= 0; i--) {
            if (windows.get(i).onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (handleKeyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (handleCharTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
    }
    *///?}

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════

    // ══════════════════════════════════════════════════════════════════════════
    // Unified Input Handlers (shared by both version branches)
    // ══════════════════════════════════════════════════════════════════════════

    private boolean handleKeyPressed(int keyCode, int scanCode, int modifiers) {
        // Bound key toggles the screen closed
        if (ArchivistMod.INSTANCE != null && keyCode == KeyBindingHelper.getBoundKeyOf(ArchivistMod.INSTANCE.openGuiKey).getValue()) {
            onClose();
            return true;
        }

        // Popup layer intercepts first (Escape closes popup)
        if (PopupLayer.keyPressed(keyCode, scanCode, modifiers)) return true;

        // Global search intercepts first when open
        if (globalSearch.isOpen()) {
            if (globalSearch.onKeyPressed(keyCode, scanCode, modifiers)) return true;
        }

        // Console Enter
        if (keyCode == GLFW.GLFW_KEY_ENTER && consoleInput != null && consoleInput.isFocused()) {
            submitConsoleCommand();
            return true;
        }

        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;

        if (ctrl) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_1 -> { toggleAndFocus(serverInfoWindow); shortcutConsumedThisFrame = true; return true; }
                case GLFW.GLFW_KEY_2 -> { toggleAndFocus(pluginListWindow); shortcutConsumedThisFrame = true; return true; }
                case GLFW.GLFW_KEY_3 -> { toggleAndFocus(connectionLogWindow); shortcutConsumedThisFrame = true; return true; }
                case GLFW.GLFW_KEY_4 -> { toggleAndFocus(consoleWindow); shortcutConsumedThisFrame = true; return true; }
                case GLFW.GLFW_KEY_5 -> { toggleAndFocus(manualLogWindow); shortcutConsumedThisFrame = true; return true; }
                case GLFW.GLFW_KEY_F -> { openGlobalSearch(); shortcutConsumedThisFrame = true; return true; }
                case GLFW.GLFW_KEY_S -> { saveConfig(); shortcutConsumedThisFrame = true; return true; }
            }
        }

        for (int i = windows.size() - 1; i >= 0; i--) {
            if (windows.get(i).onKeyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    private boolean handleCharTyped(char chr, int modifiers) {
        // Popup layer intercepts first
        if (PopupLayer.charTyped(chr, modifiers)) return true;

        // Suppress stray charTyped after shortcut consumption
        if (shortcutConsumedThisFrame) {
            shortcutConsumedThisFrame = false;
            return true;
        }
        if (globalSearch.isOpen()) {
            if (globalSearch.onCharTyped(chr, modifiers)) return true;
        }
        for (int i = windows.size() - 1; i >= 0; i--) {
            if (windows.get(i).onCharTyped(chr, modifiers)) return true;
        }
        return false;
    }

    private void toggleAndFocus(DraggableWindow window) {
        if (window == null) return;
        if (!window.isVisible()) {
            window.setVisible(true);
        }
        window.setMinimized(false);
        bringToFront(window);
    }

    private void openGlobalSearch() {
        if (globalSearch.isOpen()) {
            globalSearch.close();
        } else {
            globalSearch.open();
        }
    }

    private void saveConfig() {
        saveWindowStates();
        EventBus.post(LogEvent.Type.SYSTEM, "Config saved");
    }

    private List<GlobalSearchOverlay.SearchResult> performGlobalSearch(GlobalSearchOverlay.SearchQuery sq) {
        List<GlobalSearchOverlay.SearchResult> results = new ArrayList<>();
        ColorScheme cs = ColorScheme.get();
        String query = sq.query();
        GlobalSearchOverlay.SearchFilter filter = sq.filter();

        // Search plugin list
        if ((filter == GlobalSearchOverlay.SearchFilter.ALL || filter == GlobalSearchOverlay.SearchFilter.PLUGINS)
                && pluginList != null) {
            for (ScrollableList.ListItem item : pluginList.getItems()) {
                if (item.text.toLowerCase().contains(query)) {
                    results.add(new GlobalSearchOverlay.SearchResult("plugin_list", "Plugins", item.text, cs.eventPlugin()));
                }
            }
        }

        // Search connection log
        if (filter == GlobalSearchOverlay.SearchFilter.ALL && connectionLogList != null) {
            for (ScrollableList.ListItem item : connectionLogList.getItems()) {
                if (item.text.toLowerCase().contains(query)) {
                    results.add(new GlobalSearchOverlay.SearchResult("connection_log", "Log", item.text, item.color));
                }
            }
        }

        // Search console
        if (filter == GlobalSearchOverlay.SearchFilter.ALL && consoleOutput != null) {
            for (ScrollableList.ListItem item : consoleOutput.getItems()) {
                if (item.text.toLowerCase().contains(query)) {
                    results.add(new GlobalSearchOverlay.SearchResult("console", "Console", item.text, item.color));
                }
            }
        }

        // Search server info for brand/version
        if (ArchivistMod.INSTANCE != null) {
            var dc = ArchivistMod.INSTANCE.dataCollector;
            if ((filter == GlobalSearchOverlay.SearchFilter.ALL || filter == GlobalSearchOverlay.SearchFilter.BRAND)
                    && dc.brand != null && dc.brand.toLowerCase().contains(query)) {
                results.add(new GlobalSearchOverlay.SearchResult("server_info", "Brand", dc.brand, cs.eventBrand()));
            }
            if ((filter == GlobalSearchOverlay.SearchFilter.ALL || filter == GlobalSearchOverlay.SearchFilter.VERSION)
                    && dc.version != null && dc.version.toLowerCase().contains(query)) {
                results.add(new GlobalSearchOverlay.SearchResult("server_info", "Version", dc.version, cs.textPrimary()));
            }
        }

        // Cross-server plugin search from stored logs
        if (filter == GlobalSearchOverlay.SearchFilter.ALL || filter == GlobalSearchOverlay.SearchFilter.PLUGINS) {
            try {
                List<ServerLogData> logs = ServerLogReader.readAll();
                for (ServerLogData log : logs) {
                    for (String plugin : log.plugins) {
                        if (plugin.toLowerCase().contains(query)) {
                            String serverName = log.getDisplayName();
                            results.add(new GlobalSearchOverlay.SearchResult(
                                    "server_list", serverName, plugin, 0xFFFFAA00, true));
                        }
                    }
                    // Also search brand in stored logs
                    if ((filter == GlobalSearchOverlay.SearchFilter.ALL || filter == GlobalSearchOverlay.SearchFilter.BRAND)
                            && log.brand != null && log.brand.toLowerCase().contains(query)) {
                        results.add(new GlobalSearchOverlay.SearchResult(
                                "server_list", log.getDisplayName(), "Brand: " + log.brand, 0xFFFFAA00, true));
                    }
                }
            } catch (Exception ignored) {}
        }

        return results;
    }

    private void bringToFront(DraggableWindow window) {
        windows.remove(window);
        windows.add(window);
    }

    private void addKV(DraggableWindow window, String key, String value) {
        window.addChild(new Label(0, 0, 180, key + ": " + value, ColorScheme.get().textPrimary()));
    }

    private ServerDataCollector getDataCollector() {
        return ArchivistMod.INSTANCE != null ? ArchivistMod.INSTANCE.dataCollector : null;
    }

    private ArchivistConfig getExtConfig() {
        return ArchivistMod.INSTANCE != null ? ArchivistMod.INSTANCE.extendedConfig : null;
    }

    private void applyTheme(String name) {
        if (name == null) return;
        ColorScheme theme = ThemeManager.getInstance().getTheme(name);
        if (theme != null) ColorScheme.setActive(theme);
    }

    @Override
    public void onClose() {
        ArchivistMod.LOGGER.info("[Archivist DEBUG] onClose() called", new Throwable("stack trace"));
        closedIntentionally = true;
        saveWindowStates();
        if (parent != null) {
            Minecraft.getInstance().setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public void removed() {
        ArchivistMod.LOGGER.info("[Archivist DEBUG] removed() called, closedIntentionally={}, newScreen={}",
                closedIntentionally,
                Minecraft.getInstance().screen != null ? Minecraft.getInstance().screen.getClass().getSimpleName() : "null",
                new Throwable("stack trace"));
        if (!closedIntentionally) {
            // Screen was forcibly replaced (e.g., server opened a container)
            // Re-open Archivist on top of the new screen on next tick
            Minecraft mc = Minecraft.getInstance();
            Screen newScreen = mc.screen;
            mc.execute(() -> {
                if (mc.screen == newScreen && !(newScreen instanceof ArchivistScreen)) {
                    mc.setScreen(new ArchivistScreen(newScreen));
                }
            });
        }
        saveWindowStates();
        super.removed();
    }

    private void saveWindowStates() {
        if (guiConfig == null) return;
        for (DraggableWindow w : windows) {
            guiConfig.setWindowState(w.getId(), new GuiConfig.WindowState(
                    w.getX(), w.getY(), w.getWidth(), w.getHeight(),
                    w.isVisible(), w.isMinimized()
            ));
        }
        guiConfig.activeTheme = ColorScheme.get().name();
        guiConfig.save();
    }
}
