package me.landon.papercompanion;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.landon.papercompanion.api.CompanionPlayerSession;
import me.landon.papercompanion.api.CosmicCompanionService;
import me.landon.papercompanion.protocol.BinaryDecodingException;
import me.landon.papercompanion.protocol.ProtocolCodec;
import me.landon.papercompanion.protocol.ProtocolConstants;
import me.landon.papercompanion.protocol.ProtocolMessage;
import me.landon.papercompanion.timer.EventTimer;
import me.landon.papercompanion.timer.EventTimerService;
import me.landon.papercompanion.util.AmountFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class CosmicCompanionPlugin extends JavaPlugin
        implements Listener, PluginMessageListener, CosmicCompanionService {

    private static final String EVENTS_WIDGET_ID = "events";
    private static final int EVENT_WIDGET_TTL_SECONDS = 2;

    private final ProtocolCodec codec = new ProtocolCodec();
    private final Map<UUID, CompanionPlayerSession> sessions = new ConcurrentHashMap<>();
    private final EventTimerService eventTimers = new EventTimerService();
    private final Map<UUID, Integer> inventoryOverlayHashes = new ConcurrentHashMap<>();

    private int eventBroadcastTaskId = -1;
    private int inventoryOverlayTaskId = -1;
    private NamespacedKey amountDataKey;
    private Set<Material> moneyNoteMaterials = Set.of(Material.PAPER);
    private Set<Material> energyMaterials = Set.of(Material.GLOWSTONE_DUST);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadOverlaySettings();

        Bukkit.getMessenger().registerIncomingPluginChannel(this, ProtocolConstants.CHANNEL_ID, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, ProtocolConstants.CHANNEL_ID);
        Bukkit.getPluginManager().registerEvents(this, this);

        seedDefaultTimers();
        eventBroadcastTaskId =
                Bukkit.getScheduler()
                        .scheduleSyncRepeatingTask(this, this::broadcastEventTimers, 20L, 20L);

        int scanIntervalTicks = Math.max(1, getConfig().getInt("inventory-overlays.scan-interval-ticks", 4));
        inventoryOverlayTaskId =
                Bukkit.getScheduler()
                        .scheduleSyncRepeatingTask(this, this::refreshAllCompanionInventories, scanIntervalTicks, scanIntervalTicks);

        getLogger().info("CosmicCompanionPaper enabled on channel " + ProtocolConstants.CHANNEL_ID);
    }

    @Override
    public void onDisable() {
        if (eventBroadcastTaskId != -1) {
            Bukkit.getScheduler().cancelTask(eventBroadcastTaskId);
            eventBroadcastTaskId = -1;
        }

        if (inventoryOverlayTaskId != -1) {
            Bukkit.getScheduler().cancelTask(inventoryOverlayTaskId);
            inventoryOverlayTaskId = -1;
        }

        sessions.clear();
        inventoryOverlayHashes.clear();
    }

    private void reloadOverlaySettings() {
        String key = getConfig().getString("inventory-overlays.amount-key", "cosmic:amount");
        NamespacedKey parsed = NamespacedKey.fromString(key);
        amountDataKey = parsed == null ? new NamespacedKey("cosmic", "amount") : parsed;

        moneyNoteMaterials = parseMaterialList(getConfig().getStringList("inventory-overlays.money-note-materials"));
        if (moneyNoteMaterials.isEmpty()) {
            moneyNoteMaterials = Set.of(Material.PAPER);
        }

        energyMaterials = parseMaterialList(getConfig().getStringList("inventory-overlays.energy-materials"));
        if (energyMaterials.isEmpty()) {
            energyMaterials = Set.of(Material.GLOWSTONE_DUST);
        }
    }

    private static Set<Material> parseMaterialList(List<String> values) {
        Set<Material> materials = new HashSet<>();
        for (String value : values) {
            Material material = Material.matchMaterial(value);
            if (material != null) {
                materials.add(material);
            }
        }

        return materials;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler()
                .runTaskLater(
                        this,
                        () -> {
                            if (player.isOnline() && !isCompanionPlayer(player)) {
                                player.sendMessage(
                                        "[Companion] Install/enable the CosmicPrisons Fabric mod for server HUD features.");
                            }
                        },
                        60L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        sessions.remove(playerId);
        inventoryOverlayHashes.remove(playerId);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            scheduleInventoryOverlayRefresh(player);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        scheduleInventoryOverlayRefresh(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleInventoryOverlayRefresh(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleInventoryOverlayRefresh(player);
        }
    }

    private void scheduleInventoryOverlayRefresh(Player player) {
        Bukkit.getScheduler().runTaskLater(this, () -> refreshInventoryOverlays(player), 1L);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!ProtocolConstants.CHANNEL_ID.equals(channel)) {
            return;
        }

        ProtocolCodec.DecodedFrame frame;
        try {
            frame = codec.decode(message);
        } catch (BinaryDecodingException ex) {
            getLogger().warning("Invalid companion packet from " + player.getName() + ": " + ex.getMessage());
            return;
        }

        switch (frame.message()) {
            case ProtocolMessage.ClientHelloC2S hello -> handleClientHello(player, frame.protocolVersion(), hello);
            case ProtocolMessage.PingIntentC2S pingIntent ->
                    getLogger().info("Ping intent from " + player.getName() + " type=" + pingIntent.pingType());
            default -> {
            }
        }
    }

    private void handleClientHello(Player player, int protocolVersion, ProtocolMessage.ClientHelloC2S hello) {
        CompanionPlayerSession session =
                new CompanionPlayerSession(
                        player.getUniqueId(),
                        player.getName(),
                        protocolVersion,
                        hello.clientModVersion(),
                        hello.clientCapabilitiesBitset());
        sessions.put(player.getUniqueId(), session);

        ProtocolMessage.ServerHelloS2C serverHello =
                new ProtocolMessage.ServerHelloS2C(
                        getConfig().getString("server-id", "cosmicprisons.com"),
                        getPluginMeta().getVersion(),
                        supportedFeatureFlags(),
                        Optional.empty());
        send(player, serverHello);

        sendHudWidgets(player, List.of(buildEventWidgetPayload()));
        refreshInventoryOverlays(player);
        getLogger().info(
                "Companion handshake established for "
                        + player.getName()
                        + " protocol="
                        + protocolVersion
                        + " clientVersion="
                        + hello.clientModVersion());
    }

    private int supportedFeatureFlags() {
        return ProtocolConstants.SERVER_FEATURE_HUD_WIDGETS
                | ProtocolConstants.SERVER_FEATURE_ENTITY_MARKERS
                | ProtocolConstants.SERVER_FEATURE_INVENTORY_ITEM_OVERLAYS
                | ProtocolConstants.SERVER_FEATURE_GANG_TRUCE_PINGS;
    }

    private void send(Player player, ProtocolMessage message) {
        if (!player.isOnline()) {
            return;
        }

        player.sendPluginMessage(this, ProtocolConstants.CHANNEL_ID, codec.encode(message));
    }

    @Override
    public Optional<CompanionPlayerSession> getSession(Player player) {
        return Optional.ofNullable(sessions.get(player.getUniqueId()));
    }

    @Override
    public Collection<CompanionPlayerSession> getActiveSessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    @Override
    public boolean isCompanionPlayer(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    @Override
    public void sendHudWidgets(Player player, List<HudWidgetPayload> widgets) {
        if (!isCompanionPlayer(player)) {
            return;
        }

        List<ProtocolMessage.HudWidget> protoWidgets =
                widgets.stream()
                        .map(widget -> new ProtocolMessage.HudWidget(widget.widgetId(), widget.lines(), widget.ttlSeconds()))
                        .toList();
        send(player, new ProtocolMessage.HudWidgetStateS2C(protoWidgets));
    }

    @Override
    public void broadcastHudWidgets(List<HudWidgetPayload> widgets) {
        for (CompanionPlayerSession session : getActiveSessions()) {
            Player player = Bukkit.getPlayer(session.playerId());
            if (player != null) {
                sendHudWidgets(player, widgets);
            }
        }
    }

    @Override
    public void sendInventoryOverlays(Player player, List<InventoryOverlayPayload> overlays) {
        if (!isCompanionPlayer(player)) {
            return;
        }

        List<ProtocolMessage.InventoryItemOverlay> proto =
                overlays.stream()
                        .map(overlay -> new ProtocolMessage.InventoryItemOverlay(overlay.slot(), overlay.overlayType(), overlay.displayText()))
                        .toList();
        send(player, new ProtocolMessage.InventoryItemOverlaysS2C(proto));
    }

    @Override
    public void refreshInventoryOverlays(Player player) {
        if (!isCompanionPlayer(player)) {
            return;
        }

        List<InventoryOverlayPayload> overlays = collectInventoryOverlays(player.getInventory());
        int hash = overlays.hashCode();
        Integer previousHash = inventoryOverlayHashes.put(player.getUniqueId(), hash);
        if (previousHash != null && previousHash == hash) {
            return;
        }

        sendInventoryOverlays(player, overlays);
    }

    private List<InventoryOverlayPayload> collectInventoryOverlays(PlayerInventory inventory) {
        List<InventoryOverlayPayload> overlays = new java.util.ArrayList<>();

        for (int slot = 0; slot <= 35; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
                continue;
            }

            Integer overlayType = resolveOverlayType(stack.getType());
            if (overlayType == null) {
                continue;
            }

            PersistentDataContainer container = stack.getItemMeta().getPersistentDataContainer();
            Long amount = container.get(amountDataKey, PersistentDataType.LONG);
            if (amount == null) {
                continue;
            }

            overlays.add(new InventoryOverlayPayload(slot, overlayType, AmountFormatter.compact(amount)));
        }

        return overlays;
    }

    private Integer resolveOverlayType(Material material) {
        if (moneyNoteMaterials.contains(material)) {
            return ProtocolConstants.OVERLAY_TYPE_MONEY_NOTE;
        }

        if (energyMaterials.contains(material)) {
            return ProtocolConstants.OVERLAY_TYPE_COSMIC_ENERGY;
        }

        return null;
    }

    private void refreshAllCompanionInventories() {
        for (CompanionPlayerSession session : getActiveSessions()) {
            Player player = Bukkit.getPlayer(session.playerId());
            if (player != null) {
                refreshInventoryOverlays(player);
            }
        }
    }

    @Override
    public void sendEntityMarkerDelta(Player player, int markerType, List<Integer> addEntityIds, List<Integer> removeEntityIds) {
        if (!isCompanionPlayer(player)) {
            return;
        }

        send(player, new ProtocolMessage.EntityMarkerDeltaS2C(markerType, addEntityIds, removeEntityIds));
    }

    private void broadcastEventTimers() {
        long now = System.currentTimeMillis();
        eventTimers.purgeExpired(now);

        HudWidgetPayload payload = buildEventWidgetPayload();
        if (!payload.lines().isEmpty()) {
            broadcastHudWidgets(List.of(payload));
        }
    }

    private HudWidgetPayload buildEventWidgetPayload() {
        List<String> lines = eventTimers.buildLines(System.currentTimeMillis(), 8);
        return new HudWidgetPayload(EVENTS_WIDGET_ID, lines, EVENT_WIDGET_TTL_SECONDS);
    }

    private void seedDefaultTimers() {
        long now = System.currentTimeMillis();
        if (eventTimers.snapshot().isEmpty()) {
            eventTimers.upsertTimer("meteorite", "Meteorite", now + 3 * 60_000L);
            eventTimers.upsertTimer("koth", "KOTH", now + 7 * 60_000L);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        if ("companiondemo".equals(name)) {
            return handleCompanionDemo(sender);
        }

        if ("companiontimer".equals(name)) {
            return handleCompanionTimerCommand(sender, args);
        }

        return false;
    }

    private boolean handleCompanionDemo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (getSession(player).isEmpty()) {
            player.sendMessage("No companion session yet. Rejoin with the Fabric mod installed.");
            return true;
        }

        sendHudWidgets(player, List.of(buildEventWidgetPayload()));
        refreshInventoryOverlays(player);
        sendEntityMarkerDelta(
                player,
                ProtocolConstants.MARKER_TYPE_GANG_PING_BEACON,
                List.of(player.getEntityId()),
                List.of());

        player.sendMessage("Sent sample companion payloads.");
        return true;
    }

    private boolean handleCompanionTimerCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /companiontimer <list|add|remove>");
            return true;
        }

        String sub = args[0].toLowerCase();
        if ("list".equals(sub)) {
            List<EventTimer> timers = eventTimers.snapshot();
            sender.sendMessage("Companion timers: " + timers.size());
            long now = System.currentTimeMillis();
            for (EventTimer timer : timers) {
                sender.sendMessage(
                        "- "
                                + timer.key()
                                + " ("
                                + timer.displayName()
                                + ") "
                                + EventTimerService.formatRemaining(now, timer.endEpochMillis()));
            }
            return true;
        }

        if ("remove".equals(sub)) {
            if (args.length < 2) {
                sender.sendMessage("Usage: /companiontimer remove <key>");
                return true;
            }

            eventTimers.removeTimer(args[1]);
            sender.sendMessage("Removed timer: " + args[1]);
            return true;
        }

        if ("add".equals(sub)) {
            if (args.length < 4) {
                sender.sendMessage("Usage: /companiontimer add <key> <seconds> <display name...>");
                return true;
            }

            long seconds;
            try {
                seconds = Long.parseLong(args[2]);
            } catch (NumberFormatException ex) {
                sender.sendMessage("seconds must be a number");
                return true;
            }

            String displayName = String.join(" ", List.of(args).subList(3, args.length));
            eventTimers.upsertTimer(args[1], displayName, System.currentTimeMillis() + seconds * 1000L);
            sender.sendMessage("Added/updated timer " + args[1] + " for " + seconds + " seconds");
            broadcastEventTimers();
            return true;
        }

        sender.sendMessage("Usage: /companiontimer <list|add|remove>");
        return true;
    }
}
