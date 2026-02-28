package me.landon.papercompanion.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ProtocolCodec {
    public record DecodedFrame(int protocolVersion, ProtocolMessage message) {}

    public byte[] encode(ProtocolMessage message) {
        BinaryWriter writer = new BinaryWriter();
        writer.writeVarInt(ProtocolConstants.PROTOCOL_VERSION);
        writer.writeVarInt(message.type().id());

        switch (message) {
            case ProtocolMessage.ServerHelloS2C hello -> {
                writer.writeString(hello.serverId(), ProtocolConstants.MAX_STRING_BYTES);
                writer.writeString(hello.serverPluginVersion(), ProtocolConstants.MAX_STRING_BYTES);
                writer.writeVarInt(hello.serverFeatureFlagsBitset());
                if (hello.signature().isPresent()) {
                    byte[] signature = hello.signature().orElseThrow().value();
                    writer.writeByte(signature.length);
                    writer.writeBytes(signature);
                }
            }
            case ProtocolMessage.HudWidgetStateS2C widgets -> {
                writeBoundedCount(writer, widgets.widgets().size(), ProtocolConstants.MAX_WIDGET_COUNT, "widgetCount");
                for (ProtocolMessage.HudWidget widget : widgets.widgets()) {
                    writer.writeString(widget.widgetId(), ProtocolConstants.MAX_STRING_BYTES);
                    writeBoundedCount(writer, widget.lines().size(), ProtocolConstants.MAX_WIDGET_LINES, "lineCount");
                    for (String line : widget.lines()) {
                        writer.writeString(line, ProtocolConstants.MAX_STRING_BYTES);
                    }
                    writer.writeVarInt(widget.ttlSeconds());
                }
            }
            case ProtocolMessage.EntityMarkerDeltaS2C delta -> {
                writer.writeVarInt(delta.markerType());
                writeIntegerList(writer, delta.addEntityIds(), ProtocolConstants.MAX_ENTITY_DELTA, "addCount");
                writeIntegerList(writer, delta.removeEntityIds(), ProtocolConstants.MAX_ENTITY_DELTA, "removeCount");
            }
            case ProtocolMessage.InventoryItemOverlaysS2C overlays -> {
                writeBoundedCount(writer, overlays.overlays().size(), ProtocolConstants.MAX_ITEM_OVERLAY_ENTRIES, "overlayCount");
                for (ProtocolMessage.InventoryItemOverlay overlay : overlays.overlays()) {
                    writer.writeVarInt(overlay.slot());
                    writer.writeVarInt(overlay.overlayType());
                    writer.writeString(overlay.displayText(), ProtocolConstants.MAX_ITEM_OVERLAY_TEXT_BYTES);
                }
            }
            case ProtocolMessage.ClientHelloC2S clientHello -> {
                writer.writeString(clientHello.clientModVersion(), ProtocolConstants.MAX_STRING_BYTES);
                writer.writeVarInt(clientHello.clientCapabilitiesBitset());
            }
            case ProtocolMessage.PingIntentC2S pingIntent -> writer.writeVarInt(pingIntent.pingType());
            default -> throw new IllegalArgumentException("Unsupported message for encode: " + message.type());
        }

        return writer.toByteArray();
    }

    public DecodedFrame decode(byte[] payload) throws BinaryDecodingException {
        BinaryReader reader = new BinaryReader(payload);
        int protocolVersion = reader.readVarInt();
        MessageType type = MessageType.fromId(reader.readVarInt());
        ProtocolMessage message = switch (type) {
            case CLIENT_HELLO_C2S -> new ProtocolMessage.ClientHelloC2S(
                    reader.readString(ProtocolConstants.MAX_STRING_BYTES),
                    reader.readVarInt());
            case PING_INTENT_C2S -> new ProtocolMessage.PingIntentC2S(reader.readVarInt());
            case SERVER_HELLO_S2C -> decodeServerHello(reader);
            case HUD_WIDGET_STATE_S2C -> decodeHudWidgetState(reader);
            case ENTITY_MARKER_DELTA_S2C -> decodeEntityMarkerDelta(reader);
            case INVENTORY_ITEM_OVERLAYS_S2C -> decodeInventoryItemOverlays(reader);
        };

        if (reader.hasRemaining()) {
            throw new BinaryDecodingException("Trailing bytes remaining");
        }

        return new DecodedFrame(protocolVersion, message);
    }

    private ProtocolMessage.ServerHelloS2C decodeServerHello(BinaryReader reader) throws BinaryDecodingException {
        String serverId = reader.readString(ProtocolConstants.MAX_STRING_BYTES);
        String pluginVersion = reader.readString(ProtocolConstants.MAX_STRING_BYTES);
        int featureFlags = reader.readVarInt();
        Optional<ProtocolMessage.SignatureBytes> signature = Optional.empty();
        if (reader.hasRemaining()) {
            int signatureLength = reader.readUnsignedByte();
            signature = Optional.of(new ProtocolMessage.SignatureBytes(reader.readBytes(signatureLength)));
        }
        return new ProtocolMessage.ServerHelloS2C(serverId, pluginVersion, featureFlags, signature);
    }

    private ProtocolMessage.HudWidgetStateS2C decodeHudWidgetState(BinaryReader reader) throws BinaryDecodingException {
        int widgetCount = readBoundedCount(reader, ProtocolConstants.MAX_WIDGET_COUNT, "widgetCount");
        List<ProtocolMessage.HudWidget> widgets = new ArrayList<>(widgetCount);
        for (int i = 0; i < widgetCount; i++) {
            String widgetId = reader.readString(ProtocolConstants.MAX_STRING_BYTES);
            int lineCount = readBoundedCount(reader, ProtocolConstants.MAX_WIDGET_LINES, "lineCount");
            List<String> lines = new ArrayList<>(lineCount);
            for (int line = 0; line < lineCount; line++) {
                lines.add(reader.readString(ProtocolConstants.MAX_STRING_BYTES));
            }
            int ttl = reader.readVarInt();
            widgets.add(new ProtocolMessage.HudWidget(widgetId, lines, ttl));
        }
        return new ProtocolMessage.HudWidgetStateS2C(widgets);
    }

    private ProtocolMessage.EntityMarkerDeltaS2C decodeEntityMarkerDelta(BinaryReader reader) throws BinaryDecodingException {
        int markerType = reader.readVarInt();
        List<Integer> add = readIntegerList(reader, ProtocolConstants.MAX_ENTITY_DELTA, "addCount");
        List<Integer> remove = readIntegerList(reader, ProtocolConstants.MAX_ENTITY_DELTA, "removeCount");
        return new ProtocolMessage.EntityMarkerDeltaS2C(markerType, add, remove);
    }

    private ProtocolMessage.InventoryItemOverlaysS2C decodeInventoryItemOverlays(BinaryReader reader) throws BinaryDecodingException {
        int overlayCount = readBoundedCount(reader, ProtocolConstants.MAX_ITEM_OVERLAY_ENTRIES, "overlayCount");
        List<ProtocolMessage.InventoryItemOverlay> overlays = new ArrayList<>(overlayCount);
        for (int i = 0; i < overlayCount; i++) {
            overlays.add(new ProtocolMessage.InventoryItemOverlay(
                    reader.readVarInt(),
                    reader.readVarInt(),
                    reader.readString(ProtocolConstants.MAX_ITEM_OVERLAY_TEXT_BYTES)));
        }
        return new ProtocolMessage.InventoryItemOverlaysS2C(overlays);
    }

    private static void writeIntegerList(BinaryWriter writer, List<Integer> values, int maxCount, String fieldName) {
        writeBoundedCount(writer, values.size(), maxCount, fieldName);
        for (int value : values) {
            writer.writeVarInt(value);
        }
    }

    private static List<Integer> readIntegerList(BinaryReader reader, int maxCount, String fieldName)
            throws BinaryDecodingException {
        int count = readBoundedCount(reader, maxCount, fieldName);
        List<Integer> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(reader.readVarInt());
        }
        return values;
    }

    private static void writeBoundedCount(BinaryWriter writer, int count, int maxCount, String fieldName) {
        if (count < 0 || count > maxCount) {
            throw new IllegalArgumentException(fieldName + " out of bounds: " + count);
        }
        writer.writeVarInt(count);
    }

    private static int readBoundedCount(BinaryReader reader, int maxCount, String fieldName)
            throws BinaryDecodingException {
        int count = reader.readVarInt();
        if (count < 0 || count > maxCount) {
            throw new BinaryDecodingException(fieldName + " out of bounds: " + count);
        }
        return count;
    }
}
