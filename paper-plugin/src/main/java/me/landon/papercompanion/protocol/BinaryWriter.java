package me.landon.papercompanion.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class BinaryWriter {
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    public void writeByte(int value) {
        output.write(value & 0xFF);
    }

    public void writeBytes(byte[] value) {
        output.writeBytes(value);
    }

    public void writeVarInt(int value) {
        int working = value;
        while ((working & -128) != 0) {
            writeByte(working & 127 | 128);
            working >>>= 7;
        }
        writeByte(working);
    }

    public void writeString(String value, int maxBytes) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException("String too large");
        }

        writeVarInt(bytes.length);
        writeBytes(bytes);
    }

    public byte[] toByteArray() {
        return output.toByteArray();
    }
}
