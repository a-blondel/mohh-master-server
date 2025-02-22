package com.ea.repositories;

import com.ea.entities.PersonaConnectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PersonaConnectionRepository extends JpaRepository<PersonaConnectionEntity, Long> {

    List<PersonaConnectionEntity> findByEndTimeIsNull();

    Optional<PersonaConnectionEntity> findByVersAndSlusAndPersonaPersAndIsHostFalseAndEndTimeIsNull(
            String vers,
            String slus,
            String pers
    );

    @Transactional
    @Modifying
    @Query("""
        UPDATE PersonaConnectionEntity pc 
        SET pc.endTime = :endTime 
        WHERE pc.endTime IS NULL
    """)
    int setEndTimeForAllUnfinishedPersonaConnections(@Param("endTime") LocalDateTime endTime);

    @Query("""
        SELECT COUNT(pc) 
        FROM PersonaConnectionEntity pc 
        WHERE pc.endTime IS NULL 
        AND pc.isHost = false 
        AND pc.vers IN ( :vers ) 
        AND pc.id NOT IN (
            SELECT gr.personaConnection.id 
            FROM GameReportEntity gr 
            WHERE gr.endTime IS NULL
        )
    """)
    int countPlayersInLobby(List<String> vers);

}