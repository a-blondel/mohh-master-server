package com.ea.repositories;

import com.ea.entities.GameEntity;
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
public interface GameRepository extends JpaRepository<GameEntity, Long> {

    Optional<GameEntity> findById(Long id);

    @Query("SELECT g FROM GameEntity g JOIN g.gameReports gr JOIN gr.personaConnection pc WHERE pc.id = :personaConnectionId AND gr.endTime IS NULL")
    List<GameEntity> findCurrentGameOfPersona(long personaConnectionId);

    List<GameEntity> findByEndTimeIsNull();

    List<GameEntity> findByVersInAndEndTimeIsNull(List<String> vers);

    Optional<GameEntity> findByNameAndVersInAndEndTimeIsNull(String name, List<String> vers);

    boolean existsByNameAndVersInAndEndTimeIsNull(String name, List<String> vers);

    @Transactional
    @Modifying
    @Query("UPDATE GameEntity g SET g.endTime = :endTime WHERE g.endTime IS NULL")
    int setEndTimeForAllUnfinishedGames(@Param("endTime") LocalDateTime endTime);
}
