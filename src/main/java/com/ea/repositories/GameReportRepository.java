package com.ea.repositories;

import com.ea.entities.GameReportEntity;
import com.ea.frontend.DTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface GameReportRepository extends JpaRepository<GameReportEntity, Long> {

    /**
     * Get active game reports where players are disconnected
     * @param addresses IP addresses of currently connected players
     * @return list of game reports to stop
     */
    List<GameReportEntity> findByEndTimeIsNullAndPersonaConnectionAddressNotIn(Collection<String> addresses);

    @Query("""
        SELECT pc.address 
        FROM GameReportEntity gr 
        JOIN gr.personaConnection pc 
        WHERE gr.game.id = :gameId 
        AND gr.isHost = true 
        AND pc.isHost = true 
        AND pc.endTime IS NULL
    """)
    List<String> findHostAddressByGameId(@Param("gameId") Long gameId);

    Optional<GameReportEntity> findByPersonaConnectionIdAndEndTimeIsNull(Long personaConnectionId);

    List<GameReportEntity> findByGameIdAndEndTimeIsNull(Long gameId);

    List<GameReportEntity> findByPersonaConnectionPersonaPersAndGameStartTimeAndPlayTimeAndIsHostFalse(
            String pers,
            LocalDateTime startTime,
            int playTime
    );

    @Transactional
    @Modifying
    @Query("""
        UPDATE GameReportEntity gr 
        SET gr.endTime = :endTime 
        WHERE gr.endTime IS NULL
    """)
    int setEndTimeForAllUnfinishedGameReports(@Param("endTime") LocalDateTime endTime);

    @Query("""
        SELECT COUNT(DISTINCT gr.personaConnection.id) 
        FROM GameReportEntity gr 
        WHERE gr.endTime IS NULL 
        AND gr.isHost = false 
        AND gr.game.vers IN ( :vers )
    """)
    int countPlayersInGame(List<String> vers);

    @Query("""
        SELECT new com.ea.frontend.DTO$PlayerInfoDTO(
            gr.personaConnection.persona.pers,
            gr.isHost,
            gr.startTime
        )
        FROM GameReportEntity gr 
        WHERE gr.game.id = :gameId 
        AND gr.endTime IS NULL 
        AND gr.isHost = false
    """)
    List<DTO.PlayerInfoDTO> findActivePlayersByGameId(@Param("gameId") Long gameId);

    @Query("""
        SELECT new com.ea.frontend.DTO$GameStatusDTO(
            g.id,
            g.name,
            g.vers,
            g.params,
            g.pass,
            g.startTime,
            g.maxsize,
            h.personaConnection.persona.pers,
            COUNT(p)
        )
        FROM GameEntity g
        LEFT JOIN GameReportEntity h ON h.game = g AND h.isHost = true AND h.endTime IS NULL
        LEFT JOIN GameReportEntity p ON p.game = g AND p.isHost = false AND p.endTime IS NULL
        WHERE g.endTime IS NULL
        GROUP BY g.id, g.name, g.vers, g.startTime, g.maxsize, h.personaConnection.persona.pers
    """)
    List<DTO.GameStatusDTO> findAllActiveGamesWithStats();

}