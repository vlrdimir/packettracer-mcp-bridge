package org.apache.commons.codec.binary;

import org.apache.commons.codec.DecoderException;

public final class Hex {
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private Hex() {
    }

    public static char[] encodeHex(byte[] data) {
        if (data == null) {
            return new char[0];
        }

        char[] output = new char[data.length * 2];

        for (int index = 0; index < data.length; index++) {
            int unsignedValue = data[index] & 0xFF;
            output[index * 2] = HEX_DIGITS[unsignedValue >>> 4];
            output[(index * 2) + 1] = HEX_DIGITS[unsignedValue & 0x0F];
        }

        return output;
    }

    public static String encodeHexString(byte[] data) {
        return new String(encodeHex(data));
    }

    public static byte[] decodeHex(char[] data) throws DecoderException {
        if (data == null) {
            return new byte[0];
        }

        if ((data.length % 2) != 0) {
            throw new DecoderException("Odd number of characters in hexadecimal input.");
        }

        byte[] output = new byte[data.length / 2];

        for (int index = 0; index < data.length; index += 2) {
            int high = toDigit(data[index], index);
            int low = toDigit(data[index + 1], index + 1);
            output[index / 2] = (byte) ((high << 4) | low);
        }

        return output;
    }

    private static int toDigit(char value, int index) throws DecoderException {
        int digit = Character.digit(value, 16);

        if (digit >= 0) {
            return digit;
        }

        throw new DecoderException(String.format("Illegal hexadecimal character '%s' at index %d.", Character.valueOf(value), Integer.valueOf(index)));
    }
}
