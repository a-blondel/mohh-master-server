package com.ea.utils;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;
import java.util.zip.CRC32;

@Slf4j
public class SocketUtils {

    /**
     * Calculate length of the content to parse
     * @param buffer the request buffer (only efficient way to get the length)
     * @param lastPos the position to begin in the buffer (there can be multiple messages in a buffer)
     * @return int - the size of the content
     */
    public static int getlength(byte[] buffer, int lastPos) {
        String size = "";
        for (int i = lastPos + 8; i < lastPos + 12; i++) {
            size += String.format("%02x", buffer[i]);
        }
        return Integer.parseInt(size, 16);
    }

    /**
     * Get the value from a key in a socket data
     * E.g. : data = "key1=value1\nkey2=value
     * getValueFromSocket(data, "key1") returns "value1"
     * getValueFromSocket(data, "key2") returns "value2"
     *
     * @param data
     * @param key
     * @return
     */
    public static String getValueFromSocket(String data, String key) {
        String result = null;
        String[] entries = data.split("\\R");
        for (String entry : entries) {
            String[] parts = entry.trim().split("=");
            if(key.equals(parts[0])) {
                if (parts.length > 1) {
                    result = parts[1];
                }
                break;
            }
        }
        return result;
    }

    /**
     * Calculate the CRC32 checksum
     * E.g. : value = 1d0000001c000000fcff1f00e0ff1f00e0ff1f00e0ff1f0400, return = 21421344
     * @param value
     * @return
     */
    public String calcCRC32Checksum(String value) {
        byte[] bytes = parseHexString(value);
        CRC32 crc = new CRC32();
        crc.update(bytes);
        // The int cast is required to only have one Word instead of two with the long type
        // Still unsure if int cast is safe here... Could also use long and split the result at first Word
        return Integer.toHexString(swapEndian((int) crc.getValue()));
    }

    /**
     * Swap endianness (little endian to big endian, and the opposite)
     * E.g. : 21421344 <-> 44134221
     * @param value
     * @return
     */
    public static int swapEndian(int value) {
        return Integer.reverseBytes(value);
    }

    /**
     * Format byte array to hex string
     * @param bytes
     * @return
     */
    public static String formatHexString(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Parse hex string to byte array
     * @param hexString
     * @return
     */
    public static byte[] parseHexString(String hexString) {
        return HexFormat.of().parseHex(hexString);
    }

    /**
     * Format an int into a Word (32 bits - 4 bytes - 8 hex digits)
     * E.g. : value = 2, return = 00000002
     * @param value
     * @return
     */
    public static String formatIntToWord(int value) {
        return String.format("%08X", value);
    }

    /**
     * Handle localhost IP
     * @param socketIp
     * @return machine IP instead of 127.0.0.1, or socketIp if != 127.0.0.1
     */
    public static String handleLocalhostIp(String socketIp) {
        String ip = socketIp;
        if (socketIp.equals("127.0.0.1")) {
            try {
                ip = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                log.error(e.getMessage());
            }
        }
        return ip;
    }

}
