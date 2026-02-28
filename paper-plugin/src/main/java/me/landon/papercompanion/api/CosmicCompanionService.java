package me.landon.papercompanion.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.bukkit.entity.Player;

public interface CosmicCompanionService {
    Optional<CompanionPlayerSession> getSession(Player player);

    Collection<CompanionPlayerSession> getActiveSessions();

    boolean isCompanionPlayer(Player player);

    void sendHudWidgets(Player player, List<HudWidgetPayload> widgets);

    void broadcastHudWidgets(List<HudWidgetPayload> widgets);

    void sendInventoryOverlays(Player player, List<InventoryOverlayPayload> overlays);

    void refreshInventoryOverlays(Player player);

    void sendEntityMarkerDelta(
            Player player, int markerType, List<Integer> addEntityIds, List<Integer> removeEntityIds);

    record HudWidgetPayload(String widgetId, List<String> lines, int ttlSeconds) {}

    record InventoryOverlayPayload(int slot, int overlayType, String displayText) {}
}
