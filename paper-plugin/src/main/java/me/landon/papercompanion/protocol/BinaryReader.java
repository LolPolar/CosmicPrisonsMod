package me.landon.papercompanion.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class BinaryReader {
    private final byte[] payload;
    private int offset;

    public BinaryReader(byte[] payload) {
        this.payload = Arrays.copyOf(payload, payload.length);
    }

    public boolean hasRemaining() {
        return offset < payload.length;
    }

    public int readUnsignedByte() throws BinaryDecodingException {
        if (!hasRemaining()) {
            throw new BinaryDecodingException("Unexpected end of payload");
        }
        return payload[offset++] & 0xFF;
    }

    public byte[] readBytes(int length) throws BinaryDecodingException {
        if (length < 0 || offset + length > payload.length) {
            throw new BinaryDecodingException("Not enough bytes remaining");
        }

        byte[] bytes = Arrays.copyOfRange(payload, offset, offset + length);
        offset += length;
        return bytes;
    }

    public int readVarInt() throws BinaryDecodingException {
        int numRead = 0;
        int result = 0;
        int read;

        do {
            if (numRead >= 5) {
                throw new BinaryDecodingException("VarInt is too big");
            }

            read = readUnsignedByte();
            int value = (read & 0x7F);
            result |= value << (7 * numRead);
            numRead++;
        } while ((read & 0x80) != 0);

        return result;
    }

    public String readString(int maxBytes) throws BinaryDecodingException {
        int length = readVarInt();
        if (length < 0 || length > maxBytes) {
            throw new BinaryDecodingException("String length out of bounds: " + length);
        }

        return new String(readBytes(length), StandardCharsets.UTF_8);
    }
}
