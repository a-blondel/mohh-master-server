package com.ea.repositories;

import com.ea.entities.LobbyReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LobbyReportRepository extends JpaRepository<LobbyReportEntity, Long> {

    @Query("SELECT lr FROM LobbyReportEntity lr " +
            "JOIN  lr.persona.personaConnections pc " +
            "WHERE pc.ip = :ip AND pc.endTime IS NULL AND lr.endTime IS NULL")
    Optional<LobbyReportEntity> findCurrentLobbyReportByIP(String ip);

}
