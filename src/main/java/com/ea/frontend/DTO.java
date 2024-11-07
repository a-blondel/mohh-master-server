package com.ea.frontend;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;

public class DTO {
    public record MonitorResponse(
            Instant timestamp,
            Statistics stats,
            List<GameInfo> activeGames
    ) {}

    public record Statistics(
            int activeGames,
            int playersInGame,
            int playersInLobby,
            int totalPlayers
    ) {}

    public record GameInfo(
            Long id,
            String name,
            String version,
            Instant startTime,
            Integer maxPlayers,
            String hostName,
            List<PlayerInfo> activePlayers
    ) {}

    public record PlayerInfo(
            String name,
            boolean isHost,
            Instant joinTime,
            String playTime
    ) {}

    // Database DTOs
    public record GameStatusDTO(
            Long id,
            String name,
            String version,
            LocalDateTime startTime,
            Integer maxPlayers,
            String hostName,
            Long playerCount
    ) {}

    public record PlayerInfoDTO(
            String playerName,
            boolean isHost,
            LocalDateTime startTime
    ) {}

    public record ConnectionStatsDTO(
            long totalConnections,
            long nonHostConnections,
            long uniquePersonas
    ) {}
}
