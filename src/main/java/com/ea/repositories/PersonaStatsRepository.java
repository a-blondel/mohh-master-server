package com.ea.repositories;

import com.ea.entities.PersonaStatsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PersonaStatsRepository extends JpaRepository<PersonaStatsEntity, Long> {

    PersonaStatsEntity findByPersonaIdAndVers(Long id, String vers);

    PersonaStatsEntity findByPersonaIdAndVersIn(Long id, List<String> vers);

    @Query(value = """
            SELECT RANK FROM
                (SELECT PERSONA_ID, ROW_NUMBER() OVER(ORDER BY (KILL - DEATH) DESC, PERSONA_ID ASC) AS RANK
                FROM PERSONA_STATS PS
                JOIN PERSONA P ON PS.PERSONA_ID = P.ID
                JOIN ACCOUNT A ON P.ACCOUNT_ID = A.ID
                WHERE PS.VERS = ?2 AND PS.PLAYTIME > 0 AND P.DELETED_ON IS NULL AND A.IS_BANNED = FALSE) AS STATS
            WHERE STATS.PERSONA_ID = ?1
            """, nativeQuery = true)
    Long getRankByPersonaIdAndVers(long id, String vers);

    @Query(value = """
            FROM PersonaStatsEntity ps
            WHERE ps.vers = :vers AND ps.playTime > 0
            AND ps.persona.deletedOn IS NULL
            AND ps.persona.account.isBanned = FALSE
            ORDER BY (kill - death) DESC, persona.id ASC LIMIT :limit OFFSET :offset
            """)
    List<PersonaStatsEntity> getLeaderboardByVers(String vers, long limit, long offset);

    @Query(value = """
            FROM PersonaStatsEntity ps
            WHERE ps.vers = :vers AND ps.playTime > 0
            AND ps.persona.deletedOn IS NULL
            AND ps.persona.account.isBanned = FALSE
            ORDER BY kill DESC, persona.id ASC
            LIMIT :limit OFFSET :offset
            """)
    List<PersonaStatsEntity> getWeaponLeaderboardByVers(String vers, long limit, long offset);

}
