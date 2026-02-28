package me.landon.papercompanion.protocol;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public interface ProtocolMessage {

    MessageType type();

    record SignatureBytes(byte[] value) {
        public SignatureBytes {
            value = Objects.requireNonNull(value, "value").clone();
        }
    }

    record ClientHelloC2S(String clientModVersion, int clientCapabilitiesBitset)
            implements ProtocolMessage {
        public ClientHelloC2S {
            clientModVersion = Objects.requireNonNull(clientModVersion, "clientModVersion");
        }

        @Override
        public MessageType type() {
            return MessageType.CLIENT_HELLO_C2S;
        }
    }

    record ServerHelloS2C(
            String serverId,
            String serverPluginVersion,
            int serverFeatureFlagsBitset,
            Optional<SignatureBytes> signature)
            implements ProtocolMessage {
        public ServerHelloS2C {
            serverId = Objects.requireNonNull(serverId, "serverId");
            serverPluginVersion = Objects.requireNonNull(serverPluginVersion, "serverPluginVersion");
            signature = Objects.requireNonNull(signature, "signature");
        }

        @Override
        public MessageType type() {
            return MessageType.SERVER_HELLO_S2C;
        }
    }

    record HudWidget(String widgetId, List<String> lines, int ttlSeconds) {
        public HudWidget {
            widgetId = Objects.requireNonNull(widgetId, "widgetId");
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        }

    }

    record HudWidgetStateS2C(List<HudWidget> widgets) implements ProtocolMessage {
        public HudWidgetStateS2C {
            widgets = List.copyOf(Objects.requireNonNull(widgets, "widgets"));
        }

        @Override
        public MessageType type() {
            return MessageType.HUD_WIDGET_STATE_S2C;
        }
    }

    record EntityMarkerDeltaS2C(int markerType, List<Integer> addEntityIds, List<Integer> removeEntityIds)
            implements ProtocolMessage {
        public EntityMarkerDeltaS2C {
            addEntityIds = List.copyOf(Objects.requireNonNull(addEntityIds, "addEntityIds"));
            removeEntityIds = List.copyOf(Objects.requireNonNull(removeEntityIds, "removeEntityIds"));
        }

        @Override
        public MessageType type() {
            return MessageType.ENTITY_MARKER_DELTA_S2C;
        }
    }

    record PingIntentC2S(int pingType) implements ProtocolMessage {
        @Override
        public MessageType type() {
            return MessageType.PING_INTENT_C2S;
        }
    }

    record InventoryItemOverlay(int slot, int overlayType, String displayText) {
        public InventoryItemOverlay {
            displayText = Objects.requireNonNull(displayText, "displayText");
        }

    }

    record InventoryItemOverlaysS2C(List<InventoryItemOverlay> overlays) implements ProtocolMessage {
        public InventoryItemOverlaysS2C {
            overlays = List.copyOf(Objects.requireNonNull(overlays, "overlays"));
        }

        @Override
        public MessageType type() {
            return MessageType.INVENTORY_ITEM_OVERLAYS_S2C;
        }
    }
}
