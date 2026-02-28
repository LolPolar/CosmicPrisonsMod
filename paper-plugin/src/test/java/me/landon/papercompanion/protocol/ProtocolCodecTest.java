package me.landon.papercompanion.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProtocolCodecTest {
    private final ProtocolCodec codec = new ProtocolCodec();

    @Test
    void decodesClientHello() throws Exception {
        ProtocolMessage.ClientHelloC2S message =
                new ProtocolMessage.ClientHelloC2S("1.0.0", 127);

        ProtocolCodec.DecodedFrame frame = codec.decode(codec.encode(message));

        assertEquals(ProtocolConstants.PROTOCOL_VERSION, frame.protocolVersion());
        ProtocolMessage.ClientHelloC2S decoded =
                assertInstanceOf(ProtocolMessage.ClientHelloC2S.class, frame.message());
        assertEquals("1.0.0", decoded.clientModVersion());
        assertEquals(127, decoded.clientCapabilitiesBitset());
    }

    @Test
    void roundTripsServerMessages() throws Exception {
        ProtocolMessage.ServerHelloS2C hello =
                new ProtocolMessage.ServerHelloS2C(
                        "cosmicprisons.com", "1.0.0", 7, Optional.empty());
        ProtocolMessage.HudWidgetStateS2C widgets =
                new ProtocolMessage.HudWidgetStateS2C(
                        List.of(new ProtocolMessage.HudWidget("events", List.of("KOTH: 04:20"), 10)));

        ProtocolMessage.EntityMarkerDeltaS2C markers =
                new ProtocolMessage.EntityMarkerDeltaS2C(
                        ProtocolConstants.MARKER_TYPE_GANG_PING_BEACON, List.of(1, 2), List.of(3));

        ProtocolMessage.InventoryItemOverlaysS2C overlays =
                new ProtocolMessage.InventoryItemOverlaysS2C(
                        List.of(new ProtocolMessage.InventoryItemOverlay(0, 1, "12k")));

        assertEquals(hello, codec.decode(codec.encode(hello)).message());
        assertEquals(widgets, codec.decode(codec.encode(widgets)).message());
        assertEquals(markers, codec.decode(codec.encode(markers)).message());
        assertEquals(overlays, codec.decode(codec.encode(overlays)).message());
    }
}
