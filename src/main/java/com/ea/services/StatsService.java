package com.ea.services;

import com.ea.dto.SocketData;
import com.ea.dto.SocketWrapper;
import com.ea.entities.GameReportEntity;
import com.ea.entities.PersonaStatsEntity;
import com.ea.enums.MapMoHH;
import com.ea.enums.MapMoHH2;
import com.ea.mappers.SocketMapper;
import com.ea.repositories.GameReportRepository;
import com.ea.repositories.PersonaStatsRepository;
import com.ea.steps.SocketWriter;
import com.ea.utils.GameVersUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.ea.enums.RankingCategory.*;
import static com.ea.utils.GameVersUtils.VERS_MOHH_PSP;
import static com.ea.utils.SocketUtils.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class StatsService {

    private final SocketMapper socketMapper;
    private final SocketWriter socketWriter;
    private final PersonaStatsRepository personaStatsRepository;
    private final GameReportRepository gameReportRepository;

    /**
     * Retrieve ranking categories
     *
     * @param socket
     * @param socketData
     */
    public void cate(Socket socket, SocketData socketData) {
        Map<String, String> content = Stream.of(new String[][]{
                {"CC", "6"}, // <total # of categories in this view>
                {"IC", "6"}, // <total # of indices in this view>
                {"VC", "6"}, // <total # of variations in this view>
                {"U", "6"},
                {"SYMS", "6"},
                {"SS", "6"},
                {"R", String.join(",", Collections.nCopies(66, "1"))}, // <comma-separated-list of category,index,view data>
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);
    }

    /**
     * Request ranking snapshot
     *
     * @param socket
     * @param socketData
     * @param socketWrapper
     */
    public void snap(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        String chan = getValueFromSocket(socketData.getInputMessage(), "CHAN");
        String seqn = getValueFromSocket(socketData.getInputMessage(), "SEQN");
        String cols = getValueFromSocket(socketData.getInputMessage(), "COLS"); // send column information or not
        String start = getValueFromSocket(socketData.getInputMessage(), "START"); // <start ranking> (index)
        String categoryIndex = getValueFromSocket(socketData.getInputMessage(), "CI"); // <category-index>

        List<String> relatedVers = GameVersUtils.getRelatedVers(socketWrapper.getPersonaConnectionEntity().getVers());
        boolean isMohh = relatedVers.equals(VERS_MOHH_PSP);
        String rankingCategory = getRankingCategory(isMohh, categoryIndex).mohh2Id;

        String columnNumber = isMohh ? "21" : "18";
        if (WEAPON_LEADERS.mohh2Id.equals(rankingCategory)) {
            columnNumber = "32";
        }

        // Must be fetched here to know the actual size
        List<PersonaStatsEntity> personaStatsEntityList = new ArrayList<>();
        long offset = 0;
        String vers = socketWrapper.getPersonaConnectionEntity().getVers();
        if (MY_LEADERBOARD.mohh2Id.equals(rankingCategory)) {
            personaStatsEntityList = personaStatsRepository.getLeaderboardByVers(vers, 100, offset);
        } else if (TOP_100.mohh2Id.equals(rankingCategory)) {
            Long rank = personaStatsRepository.getRankByPersonaIdAndVers(socketWrapper.getPersonaEntity().getId(), vers);
            offset = (rank != null) ? rank : 0;
            offset = Math.max(offset - 50, 0);
            personaStatsEntityList = personaStatsRepository.getLeaderboardByVers(vers, 100, offset);
        } else if (WEAPON_LEADERS.mohh2Id.equals(rankingCategory)) {
            personaStatsEntityList = personaStatsRepository.getWeaponLeaderboardByVers(vers, 100, offset);
        }

        Map<String, String> content = Stream.of(new String[][]{
                {"CHAN", chan}, // <matching request value>
                {"START", start}, // <actual start used>
                {"RANGE", String.valueOf(personaStatsEntityList.size())}, // <actual range used>
                {"SEQN", seqn}, // <value provided in request>
                {"CC", columnNumber}, // <number of columns>
                {"FC", "1"}, // <number of fixed columns>
                {"DESC", ""}, // <list-description>
                {"PARAMS", "1,1,1,1,1,1,1,1,1,1,1,1"}, // <comma-separated list of integer parameters>
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        if ("1".equals(cols) && Set.of(MY_LEADERBOARD.mohh2Id, TOP_100.mohh2Id).contains(rankingCategory)) {
            content.putAll(Stream.of(new String[][]{
                    {"CN0", "RNK"}, // <column-name>
                    {"CD0", "\"Leaderboard Ranking\""}, // <column-name> (selected)
                    //{ "CP0", "1" }, // <column-parameter>
                    //{ "CW0", "50" }, // <column-width>
                    //{ "CT0", "1" }, // <column-type>
                    //{ "CS0", "1" }, // <column-style>
                    {"CN1", "Persona"},
                    {"CD1", "Persona"},
                    {"CN2", "Score"},
                    {"CD2", "Score"},
                    {"CN3", "Kills"},
                    {"CD3", "\"Total Kills\""},
                    {"CN4", "Deaths"},
                    {"CD4", "\"Total Deaths\""},
                    {"CN5", isMohh ? "Time" : "Accuracy"},
                    {"CD5", isMohh ? "\"Total Time Played Online\"" : "\"Accuracy %\""},
                    {"CN6", isMohh ? "Accuracy" : "Time"},
                    {"CD6", isMohh ? "\"Accuracy %\"" : "\"Total Time Played Online\""},
                    {"CN7", "KPM"},
                    {"CD7", "\"Kills Per Minute\""},
                    {"CN8", "DPM"},
                    {"CD8", "\"Deaths Per Minute\""},
                    {"CN9", isMohh ? "\"Fav. Map\"" : "Headshots"},
                    {"CD9", isMohh ? "\"Most Played Map\"" : "\"Total Headshots\""},
                    {"CN10", isMohh ? "\"Fav. Mode\"" : "\"Fav. Map\""},
                    {"CD10", isMohh ? "\"Most Played Mode\"" : "\"Most Played Map\""},
                    {"CN11", isMohh ? "\"Fav. Team\"" : "\"Fav. Mode\""},
                    {"CD11", isMohh ? "\"Most Played Team\"" : "\"Most Played Mode\""},
                    {"CN12", isMohh ? "Headshots" : "\"Fav. Team\""},
                    {"CD12", isMohh ? "\"Total Headshots\"" : "\"Most Played Team\""},
                    {"CN13", "Wins"},
                    {"CD13", "\"Total Wins\""},
                    {"CN14", "Losses"},
                    {"CD14", "\"Total Losses\""},
                    {"CN15", "\"DM RND\""},
                    {"CD15", "\"Deathmatch Rounds Played\""},
                    {"CN16", "\"INF RND\""},
                    {"CD16", "\"Infiltration Rounds Played\""},
            }).collect(Collectors.toMap(data -> data[0], data -> data[1])));
            if (isMohh) {
                content.putAll(Stream.of(new String[][]{
                        {"CN17", "\"DOM RND\""},
                        {"CD17", "\"Domination Rounds Played\""},
                        {"CN18", "\"DEM RND\""},
                        {"CD18", "\"Demolition Rounds Played\""},
                        {"CN19", "\"HTL RND\""},
                        {"CD19", "\"Hold the Line Rounds Played\""},
                        {"CN20", "\"BL RND\""},
                        {"CD20", "\"Battle Lines Rounds Played\""},
                }).collect(Collectors.toMap(data -> data[0], data -> data[1])));
            } else {
                content.putAll(Stream.of(new String[][]{
                        {"CN17", "\"TDM RND\""},
                        {"CD17", "\"Team Deathmatch Rounds Played\""},
                }).collect(Collectors.toMap(data -> data[0], data -> data[1])));
            }
        } else if ("1".equals(cols) && WEAPON_LEADERS.mohh2Id.equals(rankingCategory)) {
            content.putAll(Stream.of(new String[][]{
                    {"CN0", "RNK"},
                    {"CD0", "\"Leaderboard Ranking\""},
                    {"CN1", "Persona"},
                    {"CD1", "Persona"},
                    {"CN2", "Kills"},
                    {"CD2", "\"Total Kills\""},
                    {"CN3", "Accuracy"},
                    {"CD3", "\"Accuracy %\""},
                    {"CN4", "\".45 Kill\""},
                    {"CD4", "\"M1911 Pistol Kills\""},
                    {"CN5", "\".45 Acc\""},
                    {"CD5", "\"M1911 Pistol Accuracy\""},
                    {"CN6", "\"THMP Kill\""},
                    {"CD6", "\"Thompson Kills\""},
                    {"CN7", "\"THMP Acc\""},
                    {"CD7", "\"Thompson Accuracy\""},
                    {"CN8", "\"BAR Kill\""},
                    {"CD8", "\"M1918 BAR Kills\""},
                    {"CN9", "\"BAR Acc\""},
                    {"CD9", "\"M1918 BAR Accuracy\""},
                    {"CN10", "\"GAR Kill\""},
                    {"CD10", "\"M1 Garand Kills\""},
                    {"CN11", "\"GAR Acc\""},
                    {"CD11", "\"M1 Garand Accuracy\""},
                    {"CN12", "\"SPFD Kill\""},
                    {"CD12", "\"Springfield Kills\""},
                    {"CN13", "\"SPFD Acc\""},
                    {"CD13", "\"Springfield Accuracy\""},
                    {"CN14", "\"SHOT Kill\""},
                    {"CD14", "\"M12 Shotgun Kills\""},
                    {"CN15", "\"SHOT Acc\""},
                    {"CD15", "\"M12 Shotgun Accuracy\""},
                    {"CN16", "\"BAZ Kill\""},
                    {"CD16", "\"M1A1 Bazooka Kills\""},
                    {"CN17", "\"BAZ Acc\""},
                    {"CD17", "\"M1A1 Bazooka Accuracy\""},
                    {"CN18", "\"P08 Kill\""},
                    {"CD18", "\"P08 Luger Kills\""},
                    {"CN19", "\"P08 Acc\""},
                    {"CD19", "\"P08 Luger Accuracy\""},
                    {"CN20", "\"MP40 Kill\""},
                    {"CD20", "\"MP40 Kills\""},
                    {"CN21", "\"MP40 Acc\""},
                    {"CD21", "\"MP40 Accuracy\""},
                    {"CN22", "\"StG44 Kill\""},
                    {"CD22", "\"StG44 AR Kills\""},
                    {"CN23", "\"StG44 Acc\""},
                    {"CD23", "\"StG44 AR Accuracy\""},
                    {"CN24", "\"KAR Kill\""},
                    {"CD24", "\"Karabiner 98K Kills\""},
                    {"CN25", "\"KAR Acc\""},
                    {"CD25", "\"Karabiner 98K Accuracy\""},
                    {"CN26", "\"GEWR Kill\""},
                    {"CD26", "\"Gewehr Kills\""},
                    {"CN27", "\"GEWR Acc\""},
                    {"CD27", "\"Gewehr Accuracy\""},
                    {"CN28", "\"PANZ Kill\""},
                    {"CD28", "\"Panzerschreck Kills\""},
                    {"CN29", "\"PANZ Acc\""},
                    {"CD29", "\"Panzerschreck Accuracy\""},
                    {"CN30", "\"GRND Kill\""},
                    {"CD30", "\"Grenade Kills\""},
                    {"CN31", "\"Melee Kill\""},
                    {"CD31", "\"Melee Kills\""},
            }).collect(Collectors.toMap(data -> data[0], data -> data[1])));
        }

        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);

        snp(socket, isMohh, rankingCategory, personaStatsEntityList, offset);
    }

    /**
     * Send ranking snapshot
     * Favorite team : 0 = Axis, 1 = Allied
     *
     * @param socket
     * @param rankingCategory
     * @param personaStatsEntityList
     * @param offset
     */
    public void snp(Socket socket, boolean isMohh, String rankingCategory, List<PersonaStatsEntity> personaStatsEntityList, long offset) {
        List<Map<String, String>> rankingList = new ArrayList<>();
        for (PersonaStatsEntity personaStatsEntity : personaStatsEntityList) {
            String name = personaStatsEntity.getPersona().getPers();
            String rank = String.valueOf(++offset);
            String points = String.valueOf(personaStatsEntity.getKill() - personaStatsEntity.getDeath());
            if (Set.of(MY_LEADERBOARD.mohh2Id, TOP_100.mohh2Id).contains(rankingCategory)) {
                String mostPlayedTeam = personaStatsEntity.getAxis() > personaStatsEntity.getAllies() ? "0" : "1";
                String playTime = String.valueOf(personaStatsEntity.getPlayTime());
                String column6 = isMohh ? playTime : getPrecision(personaStatsEntity.getHit(), personaStatsEntity.getShot()); // mohh playtime, mohh2 accuracy
                String column7 = isMohh ? getPrecision(personaStatsEntity.getHit(), personaStatsEntity.getShot()) : playTime; // mohh accuracy, mohh2 playtime
                String column10 = isMohh ? getMostPlayedMap(personaStatsEntity, isMohh) : String.valueOf(personaStatsEntity.getHead()); // mohh map, mohh2 headshots
                String column11 = isMohh ? getMostPlayedMode(personaStatsEntity) : getMostPlayedMap(personaStatsEntity, isMohh); // mohh mode, mohh2 map
                String column12 = isMohh ? mostPlayedTeam : getMostPlayedMode(personaStatsEntity); // mohh team, mohh2 mode
                String column13 = isMohh ? String.valueOf(personaStatsEntity.getHead()) : mostPlayedTeam; // mohh headshots, mohh2 team
                String stats = String.join(",", // <stats>
                        String.valueOf(offset),
                        personaStatsEntity.getPersona().getPers(),
                        String.valueOf(personaStatsEntity.getKill() - personaStatsEntity.getDeath()),
                        String.valueOf(personaStatsEntity.getKill()),
                        String.valueOf(personaStatsEntity.getDeath()),
                        column6,
                        column7,
                        playTime.equals("0") ? "0" : String.valueOf(new Formatter(Locale.US).format("%.3f", personaStatsEntity.getKill() / (personaStatsEntity.getPlayTime() / 60f))),
                        playTime.equals("0") ? "0" : String.valueOf(new Formatter(Locale.US).format("%.3f", personaStatsEntity.getDeath() / (personaStatsEntity.getPlayTime() / 60f))),
                        column10,
                        column11,
                        column12,
                        column13,
                        String.valueOf(personaStatsEntity.getWin()),
                        String.valueOf(personaStatsEntity.getLoss()),
                        String.valueOf(personaStatsEntity.getDmRnd()),
                        String.valueOf(personaStatsEntity.getCtfAllies() + personaStatsEntity.getCtfAxis()) // CTF = Infiltration
                );

                if (isMohh) {
                    stats += "," + (personaStatsEntity.getCapAllies() + personaStatsEntity.getCapAxis()); // CAP = Domination
                    stats += "," + (personaStatsEntity.getDemAllies() + personaStatsEntity.getDemAxis());
                    stats += "," + (personaStatsEntity.getKohAllies() + personaStatsEntity.getKohAxis()); // KOH = Hold the Line
                    stats += "," + (personaStatsEntity.getBlAllies() + personaStatsEntity.getBlAxis());
                } else {
                    stats += "," + (personaStatsEntity.getTdmAllies() + personaStatsEntity.getTdmAxis());
                }
                rankingList.add(Stream.of(new String[][]{
                        {"N", name}, // <persona name>
                        {"R", rank}, // <rank>
                        {"P", points}, // <points>
                        {"O", "0"}, // <online> ?
                        {"S", stats}, // <stats>
                }).collect(Collectors.toMap(data -> data[0], data -> data[1])));
            } else if (WEAPON_LEADERS.mohh2Id.equals(rankingCategory)) {
                rankingList.add(Stream.of(new String[][]{
                        {"N", name},
                        {"R", rank},
                        {"P", points},
                        {"O", "0"},
                        {"S", String.join(",",
                                String.valueOf(offset),
                                personaStatsEntity.getPersona().getPers(),
                                String.valueOf(personaStatsEntity.getKill()),
                                getPrecision(personaStatsEntity.getHit(), personaStatsEntity.getShot()),
                                String.valueOf(personaStatsEntity.getColtKill()),
                                getPrecision(personaStatsEntity.getColtHit(), personaStatsEntity.getColtShot()),
                                String.valueOf(personaStatsEntity.getTomKill()),
                                getPrecision(personaStatsEntity.getTomHit(), personaStatsEntity.getTomShot()),
                                String.valueOf(personaStatsEntity.getBarKill()),
                                getPrecision(personaStatsEntity.getBarHit(), personaStatsEntity.getBarShot()),
                                String.valueOf(personaStatsEntity.getGarKill()),
                                getPrecision(personaStatsEntity.getGarHit(), personaStatsEntity.getGarShot()),
                                String.valueOf(personaStatsEntity.getEnfieldKill()),
                                getPrecision(personaStatsEntity.getEnfieldHit(), personaStatsEntity.getEnfieldShot()),
                                String.valueOf(personaStatsEntity.getShottyKill()),
                                getPrecision(personaStatsEntity.getShottyHit(), personaStatsEntity.getShottyShot()),
                                String.valueOf(personaStatsEntity.getBazKill()),
                                getPrecision(personaStatsEntity.getBazHit(), personaStatsEntity.getBazShot()),
                                String.valueOf(personaStatsEntity.getLugerKill()),
                                getPrecision(personaStatsEntity.getLugerHit(), personaStatsEntity.getLugerShot()),
                                String.valueOf(personaStatsEntity.getMp40Kill()),
                                getPrecision(personaStatsEntity.getMp40Hit(), personaStatsEntity.getMp40Shot()),
                                String.valueOf(personaStatsEntity.getMp44Kill()),
                                getPrecision(personaStatsEntity.getMp44Hit(), personaStatsEntity.getMp44Shot()),
                                String.valueOf(personaStatsEntity.getKarKill()),
                                getPrecision(personaStatsEntity.getKarHit(), personaStatsEntity.getKarShot()),
                                String.valueOf(personaStatsEntity.getGewrKill()),
                                getPrecision(personaStatsEntity.getGewrHit(), personaStatsEntity.getGewrShot()),
                                String.valueOf(personaStatsEntity.getPanzKill()),
                                getPrecision(personaStatsEntity.getPanzHit(), personaStatsEntity.getPanzShot()),
                                String.valueOf(personaStatsEntity.getGrenKill()),
                                String.valueOf(personaStatsEntity.getMeleeKill())
                        )
                        },
                }).collect(Collectors.toMap(data -> data[0], data -> data[1])));
            }
        }

        for (Map<String, String> ranking : rankingList) {
            SocketData socketData = new SocketData("+snp", null, ranking);
            socketWriter.write(socket, socketData);
        }
    }

    /**
     * Send ranking results.
     *
     * @param socket
     * @param socketData
     */
    public void rank(Socket socket, SocketData socketData) {
        String playerName = getValueFromSocket(socketData.getInputMessage(), "REPT", TAB_CHAR);
        String startTime = getValueFromSocket(socketData.getInputMessage(), "WHEN", TAB_CHAR);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATETIME_FORMAT);
        LocalDateTime parsedStartTime = LocalDateTime.parse(startTime, formatter);
        List<GameReportEntity> gameReportEntities = gameReportRepository.findMatchingGameReports(playerName, parsedStartTime);
        if (!gameReportEntities.isEmpty()) {
            GameReportEntity gameReportEntity = gameReportEntities.get(0);
            socketMapper.toGameReportEntity(gameReportEntity, socketData.getInputMessage());
            gameReportRepository.save(gameReportEntity);

            // Update PersonaStats with the new game report (ranked only)
            if (gameReportEntity.getRnk() == 1) {
                PersonaStatsEntity personaStatsEntity = personaStatsRepository.findByPersonaIdAndVersIn(
                        gameReportEntity.getPersonaConnection().getPersona().getId(), GameVersUtils.getRelatedVers(gameReportEntity.getGame().getVers()));
                if (personaStatsEntity != null) {
                    updatePersonaStats(personaStatsEntity, gameReportEntity);
                    personaStatsRepository.save(personaStatsEntity);
                }
            }

            // This is to make sure the end time is set in case something goes wrong in 'gset'
            LocalDateTime endTime = LocalDateTime.now();
            new Thread(
                    () -> {
                        try {
                            Thread.sleep(5000);
                            gameReportEntity.setEndTime(endTime);
                            gameReportRepository.save(gameReportEntity);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
            );
        }
        socketWriter.write(socket, socketData);
    }

    private void updatePersonaStats(PersonaStatsEntity personaStatsEntity, GameReportEntity gameReportEntity) {
        Field[] personaFields = PersonaStatsEntity.class.getDeclaredFields();
        Set<String> gameReportFieldNames = Arrays.stream(GameReportEntity.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        for (Field field : personaFields) {
            if (gameReportFieldNames.contains(field.getName()) && !"id".equalsIgnoreCase(field.getName())) {
                field.setAccessible(true);
                try {
                    Field gameReportField = GameReportEntity.class.getDeclaredField(field.getName());
                    gameReportField.setAccessible(true);
                    if (field.getType().equals(int.class) || field.getType().equals(Integer.class)) {
                        field.set(personaStatsEntity, (int) field.get(personaStatsEntity) + (int) gameReportField.get(gameReportEntity));
                    } else if (field.getType().equals(long.class) || field.getType().equals(Long.class)) {
                        field.set(personaStatsEntity, (long) field.get(personaStatsEntity) + (long) gameReportField.get(gameReportEntity));
                    }
                } catch (IllegalAccessException | NoSuchFieldException e) {
                    log.error("Error updating field: " + field.getName(), e);
                }
            }
        }
    }

    /**
     * Get precision
     *
     * @param hit
     * @param shot
     * @return precision in percentage
     */
    private String getPrecision(long hit, long shot) {
        long miss = shot - hit;
        String precision = "100";
        if (0 != hit + miss) {
            precision = String.valueOf(new Formatter(Locale.US).format("%.2f", hit / ((float) hit + miss) * 100));
        }
        return precision;
    }

    /**
     * Get most played map
     *
     * @param personaStatsEntity
     * @return most played map
     */
    private String getMostPlayedMap(PersonaStatsEntity personaStatsEntity, boolean isMohh) {
        Map<String, Integer> mapPlayCounts = new HashMap<>();
        for (int i = 1; i <= 28; i++) {
            try {
                Method method = PersonaStatsEntity.class.getMethod("getMap" + i);
                int playCount = (int) method.invoke(personaStatsEntity);

                // Find the corresponding enum by matching the id attribute
                String mapKey;
                if (isMohh) {
                    mapKey = "181";
                    for (MapMoHH map : MapMoHH.values()) {
                        if (map.code.equals("MAP" + i)) {
                            mapKey = map.decimalId;
                            break;
                        }
                    }
                    mapPlayCounts.put(mapKey, mapPlayCounts.getOrDefault(mapKey, 0) + playCount);
                } else {
                    mapKey = "101";
                    for (MapMoHH2 map : MapMoHH2.values()) {
                        if (map.code.equals("MAP" + i)) {
                            mapKey = map.decimalId;
                            break;
                        }
                    }
                    mapPlayCounts.put(mapKey, mapPlayCounts.getOrDefault(mapKey, 0) + playCount);
                }
                mapPlayCounts.put(mapKey, mapPlayCounts.getOrDefault(mapKey, 0) + playCount);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                log.error("Error while getting map play count", e);
            }
        }

        // Determine the most played map
        String mostPlayedMapKey = Collections.max(mapPlayCounts.entrySet(), Map.Entry.comparingByValue()).getKey();

        // Map the most played map key to its corresponding ID
        if (isMohh) {
            return MapMoHH.getMapStatId(mostPlayedMapKey);
        } else {
            return MapMoHH2.getMapStatId(mostPlayedMapKey);
        }
    }

    /**
     * Get most played mode
     * Values : 0 = DM, 1 = TDM, 2 = INF, 3 = DEM, 4 = DOM, 5 = HTL, 6 = BL
     * Note that on MoHH value 1 = "FS_M_TDM", surely TDM was planned but cut off
     *
     * @param personaStatsEntity
     * @return most played mode
     */
    private String getMostPlayedMode(PersonaStatsEntity personaStatsEntity) {
        int totalTdm = personaStatsEntity.getTdmAllies() + personaStatsEntity.getTdmAxis();
        int totalInf = personaStatsEntity.getCtfAllies() + personaStatsEntity.getCtfAxis();
        int totalDem = personaStatsEntity.getDemAllies() + personaStatsEntity.getDemAxis();
        int totalDom = personaStatsEntity.getCapAllies() + personaStatsEntity.getCapAxis();
        int totalHtl = personaStatsEntity.getKohAllies() + personaStatsEntity.getKohAxis();
        int totalBl = personaStatsEntity.getBlAllies() + personaStatsEntity.getBlAxis();

        Map<String, Integer> modeCounts = new HashMap<>();
        modeCounts.put("0", personaStatsEntity.getDmRnd());
        modeCounts.put("1", totalTdm);
        modeCounts.put("2", totalInf);
        modeCounts.put("3", totalDem);
        modeCounts.put("4", totalDom);
        modeCounts.put("5", totalHtl);
        modeCounts.put("6", totalBl);

        return Collections.max(modeCounts.entrySet(), Map.Entry.comparingByValue()).getKey();
    }

}
