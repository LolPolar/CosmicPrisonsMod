package me.landon.papercompanion.protocol;

public enum MessageType {
    CLIENT_HELLO_C2S(1),
    SERVER_HELLO_S2C(2),
    HUD_WIDGET_STATE_S2C(3),
    ENTITY_MARKER_DELTA_S2C(4),
    PING_INTENT_C2S(11),
    INVENTORY_ITEM_OVERLAYS_S2C(10);

    private final int id;

    MessageType(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static MessageType fromId(int id) throws BinaryDecodingException {
        for (MessageType type : values()) {
            if (type.id == id) {
                return type;
            }
        }

        throw new BinaryDecodingException("Unknown message type id: " + id);
    }
}
