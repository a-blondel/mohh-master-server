package com.ea.services;

import com.ea.dto.UdpSocketWrapper;

import java.net.DatagramSocket;
import java.util.concurrent.ConcurrentHashMap;

public class UdpSocketManager {
    private static final ConcurrentHashMap<String, UdpSocketWrapper> sockets = new ConcurrentHashMap<>();

    public static void addSocket(String identifier, DatagramSocket socket) {
        UdpSocketWrapper wrapper = new UdpSocketWrapper();
        wrapper.setSocket(socket);
        wrapper.setIdentifier(identifier);
        sockets.put(identifier, wrapper);
    }

    public static void removeSocket(String identifier) {
        sockets.remove(identifier);
    }

    public static UdpSocketWrapper getSocketWrapper(String identifier) {
        return sockets.get(identifier);
    }
}