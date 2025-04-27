package com.ea.frontend;

import com.ea.entities.PersonaStatsEntity;
import com.ea.enums.MapMoHH;
import com.ea.repositories.PersonaStatsRepository;
import com.ea.utils.GameVersUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class LeaderboardAPI
{
    private static final Logger log = LoggerFactory.getLogger(LeaderboardAPI.class);
    @Autowired
    private final API api;

    @Autowired
    private final PersonaStatsRepository personaStatsRepository;

    @GetMapping("/api/leaderboard")
    public ResponseEntity<List<DTO.LeaderboardPlayerDTO>> getLeaderboardPlayers(
            @RequestParam(defaultValue = GameVersUtils.PSP_MOH_07) String vers,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "100") int limit)
    {
        List<PersonaStatsEntity> allPlayers = personaStatsRepository.getLeaderboardByVers(vers, limit, offset);
        List<DTO.LeaderboardPlayerDTO> leaderboardPlayers = new ArrayList<>();
        
        for (PersonaStatsEntity player : allPlayers)
        {
            // Calculate accuracy
            double accuracy = player.getShot() > 0 ? 
                (double) player.getHit() / player.getShot() * 100 : 0;
            
            // Calculate game mode counts
            int dmGames = player.getDmRnd();
            int tdmGames = player.getTdmAllies() + player.getTdmAxis();
            int domGames = player.getCapAllies() + player.getCapAxis();
            int demGames = player.getDemAllies() + player.getDemAxis();
            int htlGames = player.getKohAllies() + player.getKohAxis();
            int blGames = player.getBlAllies() + player.getBlAxis();
            int infGames = player.getCtfAllies() + player.getCtfAxis();
            
            DTO.LeaderboardPlayerDTO dto = new DTO.LeaderboardPlayerDTO(
                player.getPersona().getPers().replaceAll("\"", ""),
                personaStatsRepository.getRankByPersonaIdAndVers(player.getPersona().getId(), player.getVers()).intValue(),
                player.getKill(),
                player.getDeath(),
                player.getHead(),
                player.getPlayTime(),
                api.formatSeconds(player.getPlayTime()),
                player.getWin(),
                player.getLoss(),
                getMostPlayedMap(player),
                getMostPlayedMode(player),
                player.getAxis() > player.getAllies() ? "Axis" : "Allies",
                accuracy,
                dmGames,
                tdmGames,
                domGames,
                demGames,
                htlGames,
                blGames,
                infGames
            );
            
            leaderboardPlayers.add(dto);
        }
        
        return ResponseEntity.ok(leaderboardPlayers);
    }

    private String getMostPlayedMap(PersonaStatsEntity player) {
        int maxPlays = 0;
        int mostPlayedMap = 1;
        
        for (int i = 1; i <= 28; i++) {
            try {
                int plays = (int) player.getClass().getMethod("getMap" + i).invoke(player);
                if (plays > maxPlays) {
                    maxPlays = plays;
                    mostPlayedMap = i;
                    return MapMoHH.getMapNameByCode(mostPlayedMap);
                }
            } catch (Exception e) {
                log.info("Could not find map " + i +"\n" + e.getMessage());
            }
        }
        return "Unknown";
    }

    private String getMostPlayedMode(PersonaStatsEntity player) {
        int dmGames = player.getDmRnd();
        int tdmGames = player.getTdmAllies() + player.getTdmAxis();
        int infGames = player.getCtfAllies() + player.getCtfAxis();
        int demGames = player.getDemAllies() + player.getDemAxis();
        int domGames = player.getCapAllies() + player.getCapAxis();
        int htlGames = player.getKohAllies() + player.getKohAxis();
        int blGames = player.getBlAllies() + player.getBlAxis();

        // holy mother of god...
        int maxPlays = Math.max(Math.max(Math.max(Math.max(Math.max(dmGames, tdmGames), infGames), demGames), domGames), Math.max(htlGames, blGames));
        
        if (maxPlays == dmGames) return "Deathmatch";
        if (maxPlays == tdmGames) return "Team Deathmatch";
        if (maxPlays == infGames) return "Infiltration";
        if (maxPlays == demGames) return "Demolition";
        if (maxPlays == domGames) return "Domination";
        if (maxPlays == htlGames) return "Hold the Line";
        if (maxPlays == blGames) return "Battlelines";
        
        return "Unknown";
    }
}
