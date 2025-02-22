package com.ea.services;

import com.ea.dto.SocketWrapper;
import com.ea.repositories.GameReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.Socket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Component
public class SocketManager {

    private final GameReportRepository gameReportRepository;
    private final ConcurrentHashMap<String, SocketWrapper> sockets = new ConcurrentHashMap<>();

    public void addSocket(String identifier, Socket socket) {
        SocketWrapper wrapper = new SocketWrapper();
        wrapper.setSocket(socket);
        wrapper.setIdentifier(identifier);
        sockets.put(identifier, wrapper);
    }

    public void removeSocket(String identifier) {
        sockets.remove(identifier);
    }

    public SocketWrapper getSocketWrapper(Socket socket) {
        return getSocketWrapper(socket.getRemoteSocketAddress().toString());
    }

    public SocketWrapper getSocketWrapper(String identifier) {
        return sockets.get(identifier);
    }

    public Set<String> getActiveSocketIdentifiers() {
        return sockets.keySet();
    }

    public SocketWrapper getHostSocketWrapperOfGame(Long gameId) {
        return gameReportRepository.findHostAddressByGameId(gameId)
                .stream()
                .findFirst()
                .map(this::getSocketWrapper)
                .orElse(null);
    }

    public SocketWrapper getAvailableGps() {
        return sockets.values().stream()
                .filter(wrapper -> wrapper.getIsGps().get() && !wrapper.getIsHosting().get())
                .findFirst()
                .orElse(null);
    }

    public List<Socket> getHostSockets() {
        return sockets.values().stream()
                .filter(wrapper -> wrapper.getIsHost().get())
                .map(SocketWrapper::getSocket)
                .toList();
    }
}