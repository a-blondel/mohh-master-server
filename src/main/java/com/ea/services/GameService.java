package com.ea.services;

import com.ea.dto.SocketData;
import com.ea.dto.SocketWrapper;
import com.ea.entities.*;
import com.ea.mappers.SocketMapper;
import com.ea.repositories.*;
import com.ea.steps.SocketWriter;
import com.ea.utils.GameVersUtils;
import com.ea.utils.SocketUtils;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.ea.utils.GameVersUtils.*;
import static com.ea.utils.HexUtils.*;
import static com.ea.utils.SocketUtils.DATETIME_FORMAT;
import static com.ea.utils.SocketUtils.getValueFromSocket;

@Slf4j
@RequiredArgsConstructor
@Service
public class GameService {

    private final GameRepository gameRepository;
    private final GameReportRepository gameReportRepository;
    private final PersonaConnectionRepository personaConnectionRepository;
    private final PersonaStatsRepository personaStatsRepository;
    private final AccountRepository accountRepository;
    private final BlacklistRepository blacklistRepository;
    private final SocketMapper socketMapper;
    private final PersonaService personaService;
    private final SocketWriter socketWriter;
    private final SocketManager socketManager;

    /**
     * Distribute room change updates
     * @param socket
     * @param socketData
     */
    public void rom(Socket socket, SocketData socketData) {
        Map<String, String> content = Stream.of(new String[][] {
                { "I", "1" }, // Room identifier
                { "N", "room" }, // Room name
//                { "H", socketManager.getSocketWrapper(socket.getRemoteSocketAddress().toString()).getPers() }, // Room Host
//                { "D", "" }, // Room description
//                { "F", "CK" }, // Attribute flags
//                { "T", "1" }, // Current room population
//                { "L", "33" }, // Max users allowed in room
//                { "P", "0" }, // Room ping
//                { "A", props.getTcpHost() }, // Room address
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        socketData.setOutputData(content);
        socketData.setIdMessage("+rom");
        socketWriter.write(socket, socketData);
    }

    public void gsta(Socket socket, SocketData socketData) {
        socketWriter.write(socket, socketData);
    }

    public void gset(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        //String name = getValueFromSocket(socketData.getInputMessage(), "NAME");
        String params = getValueFromSocket(socketData.getInputMessage(), "PARAMS");
        String sysflags = getValueFromSocket(socketData.getInputMessage(), "SYSFLAGS");

        GameEntity gameEntity = gameReportRepository.findByPersonaConnectionIdAndEndTimeIsNull(
                socketWrapper.getPersonaConnectionEntity().getId())
                .filter(GameReportEntity::isHost)
                .map(GameReportEntity::getGame).orElse(null);

        socketWriter.write(socket, socketData);

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        new Thread(() -> {
            try {
                Thread.sleep(2000);
                if(gameEntity != null) {
                    List<GameReportEntity> gameReports = gameReportRepository.findByGameIdAndEndTimeIsNull(gameEntity.getId());
                    for(GameReportEntity gameReportEntity : gameReports) {
                        gameReportEntity.setEndTime(now);
                        gameReportRepository.save(gameReportEntity);
                    }
                    gameEntity.setEndTime(now);
                    gameRepository.save(gameEntity);

                    GameEntity newGameEntity = new GameEntity();
                    newGameEntity.setOriginalId(Optional.ofNullable(gameEntity.getOriginalId()).orElse(gameEntity.getId()));
                    newGameEntity.setVers(gameEntity.getVers());
                    newGameEntity.setSlus(gameEntity.getSlus());
                    newGameEntity.setName(gameEntity.getName());
                    newGameEntity.setParams(params);
                    newGameEntity.setSysflags(sysflags);
                    newGameEntity.setStartTime(now);
                    newGameEntity.setPass(gameEntity.getPass());
                    newGameEntity.setMinsize(gameEntity.getMinsize());
                    newGameEntity.setMaxsize(gameEntity.getMaxsize());
                    gameRepository.save(newGameEntity);

                    for (GameReportEntity gameReportEntity : gameReports) {
                        GameReportEntity newGameReportEntity = new GameReportEntity();
                        newGameReportEntity.setGame(newGameEntity);
                        newGameReportEntity.setPersonaConnection(gameReportEntity.getPersonaConnection());
                        newGameReportEntity.setHost(gameReportEntity.isHost());
                        newGameReportEntity.setStartTime(now);
                        gameReportRepository.save(newGameReportEntity);
                    }
                    updateHostInfo(newGameEntity);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * Game search
     * @param socket
     * @param socketData
     * @param socketWrapper
     */
    public void gsea(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        String vers = socketWrapper.getPersonaConnectionEntity().getVers();
        List<String> relatedVers = GameVersUtils.getRelatedVers(vers);
        List<GameEntity> gameEntities = gameRepository.findByVersInAndEndTimeIsNull(relatedVers);

        Map<String, String> paramsMap = SocketUtils.getMapFromSocket(socketData.getInputMessage());
        List<GameEntity> filteredGameEntities = filterGameEntities(gameEntities, paramsMap, vers);

        Map<String, String> content = Collections.singletonMap("COUNT", String.valueOf(filteredGameEntities.size()));
        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);

        gam(socket, filteredGameEntities);
    }

    private List<GameEntity> filterGameEntities(List<GameEntity> gameEntities, Map<String, String> paramsMap, String vers) {
        int count = Integer.parseInt(paramsMap.get("COUNT"));
        return gameEntities.stream()
                .filter(gameEntity -> matchesCriteria(gameEntity, paramsMap, vers))
                .limit(count)
                .toList();
    }

    private boolean matchesCriteria(GameEntity gameEntity, Map<String, String> paramsMap, String vers) {
        String name = paramsMap.get("NAME");
        String available = paramsMap.getOrDefault("AVAILABLE", paramsMap.get("AVAIL")); // Game not full (AVAIL on MoHH2)
        String mode = paramsMap.get("MODE");
        String map = paramsMap.get("MAP");
        String ff = paramsMap.get("FF"); // Friendly fire
        String aim = paramsMap.get("AIM"); // Aim assist
        String ctrl = paramsMap.get("CTRL"); // Elite or Wii Zapper
        String maxsize = paramsMap.get("MAXSIZE");
        String sysmaskParam = paramsMap.get("SYSMASK");
        String sysflagsParam = paramsMap.get("SYSFLAGS");
        String tb = paramsMap.get("TB"); // Team balance
        String ak = paramsMap.get("AK"); // Auto-kick
        String smg = paramsMap.get("SMG");
        String hmg = paramsMap.get("HMG");
        String rif = paramsMap.get("RIF");
        String snip = paramsMap.get("SNIP");
        String shotg = paramsMap.get("SHOTG");
        String baz = paramsMap.get("BAZ");
        String gren = paramsMap.get("GREN");

        String nameDb = gameEntity.getName().replaceAll("\"", "").toLowerCase();
        boolean availableDb = gameEntity.getGameReports().stream().filter(report -> null == report.getEndTime()).count() < gameEntity.getMaxsize();
        String[] params = gameEntity.getParams().split(",");
        String modeDb = params[0];
        int mapDb = Integer.parseInt(params[1], 16); // The map in db is in hex, so we need to convert it to decimal
        boolean hasPwDb = gameEntity.getPass() != null;
        String ffDb = params[2];

        String aimDb = null, ctrlDb = null, tbDb = null, akDb = null;
        if (vers.equals(PSP_MOH_07)) {
            aimDb = params[3];
        } else if (vers.equals(PSP_MOH_08)) {
            aimDb = params[3];
            tbDb = params[4];
            akDb = params[9];
        } else if (vers.equals(WII_MOH_08)) {
            tbDb = params[3];
            akDb = params[8];
            ctrlDb = params[9];
        }

        boolean isRankedDb = vers.equals(PSP_MOH_07) ? StringUtils.isNotEmpty(params[8]) : StringUtils.isNotEmpty(params[17]);

        String smgDb = null, hmgDb = null, rifDb = null, snipDb = null, shotgDb = null, bazDb = null, grenDb = null;
        if (vers.equals(PSP_MOH_08) || vers.equals(WII_MOH_08)) {
            smgDb = params[10];
            hmgDb = params[11];
            rifDb = params[12];
            snipDb = params[13];
            shotgDb = params[14];
            bazDb = params[15];
            grenDb = params[16];
        }

        boolean matchesFlags = matchesFlags(sysmaskParam, sysflagsParam, hasPwDb, isRankedDb);

        return (name == null || nameDb.contains(name.replaceAll("\"", "").toLowerCase()))
                && (available.equals("-1") || availableDb)
                && (mode.equals("-1") || modeDb.equals(mode))
                && (map.equals("-1") || mapDb == Integer.parseInt(map))
                && (ff.equals("-1") || (ff.equals("0") && ffDb.equals("")) || ffDb.equals(ff))
                && (aim == null || aim.equals("-1") || (aim.equals("0") && aimDb.equals("")) || aimDb.equals(aim))
                && (ctrl == null || ctrl.equals("-1") || (ctrl.equals("0") && ctrlDb.equals("")) || ctrlDb.equals(ctrl))
                && (maxsize == null || gameEntity.getMaxsize() == Integer.parseInt(maxsize))
                && matchesFlags
                && (tb == null || tb.equals("-1") || (tb.equals("0") && tbDb.equals("")) || tbDb.equals(tb))
                && (ak == null || ak.equals("-1") || (ak.equals("0") && akDb.equals("")) || akDb.equals(ak))
                && (smg == null || smg.equals("-1") || (smg.equals("0") && smgDb.equals("")) || smgDb.equals(smg))
                && (hmg == null || hmg.equals("-1") || (hmg.equals("0") && hmgDb.equals("")) || hmgDb.equals(hmg))
                && (rif == null || rif.equals("-1") || (rif.equals("0") && rifDb.equals("")) || rifDb.equals(rif))
                && (snip == null || snip.equals("-1") || (snip.equals("0") && snipDb.equals("")) || snipDb.equals(snip))
                && (shotg == null || shotg.equals("-1") || (shotg.equals("0") && shotgDb.equals("")) || shotgDb.equals(shotg))
                && (baz == null || baz.equals("-1") || (baz.equals("0") && bazDb.equals("")) || bazDb.equals(baz))
                && (gren == null || gren.equals("-1") || (gren.equals("0") && grenDb.equals("")) || grenDb.equals(gren));
    }


    private boolean matchesFlags(String sysmaskParam, String sysflagsParam, boolean hasPwDb, boolean isRankedDb) {
        if (sysmaskParam == null) {
            return true;
        }

        int clientMask = Integer.parseInt(sysmaskParam);
        int clientFlags = sysflagsParam != null ? Integer.parseInt(sysflagsParam) : 0;

        // Calculate game's flags based on ranked and password status
        int gameFlags = 0;
        if (hasPwDb) {
            gameFlags |= (1 << 16);  // Set bit 16 for password
        }
        if (isRankedDb) {
            gameFlags |= (1 << 18);  // Set bit 18 for ranked
        }

        // Only check the bits that the client cares about (specified in mask)
        return (gameFlags & clientMask) == (clientFlags & clientMask);
    }

    /**
     * A game row
     * @param socket
     * @param gameEntities
     */
    public void gam(Socket socket, List<GameEntity> gameEntities) {
        List<Map<String, String>> games = new ArrayList<>();

        for(GameEntity gameEntity : gameEntities) {
            String sysflags = gameEntity.getSysflags();
            if(StringUtils.isNotEmpty(gameEntity.getPass())) {
                sysflags = String.valueOf(Integer.parseInt(sysflags) | (1 << 16)); // Add password flag (16th bit)
            }
            games.add(Stream.of(new String[][] {
                    { "IDENT", String.valueOf(gameEntity.getId()) },
                    { "NAME", gameEntity.getName() },
                    { "PARAMS", gameEntity.getParams() },
                    { "SYSFLAGS", sysflags },
                    { "COUNT", String.valueOf(gameEntity.getGameReports().stream().filter(report -> null == report.getEndTime()).count()) },
                    { "MAXSIZE", String.valueOf(gameEntity.getMaxsize()) },
            }).collect(Collectors.toMap(data -> data[0], data -> data[1])));
        }

        for (Map<String, String> game : games) {
            SocketData socketData = new SocketData("+gam", null, game);
            socketWriter.write(socket, socketData);
        }
    }

    /**
     * Join a game
     * @param socket
     * @param socketData
     * @param socketWrapper
     */
    public void gjoi(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        AccountEntity accountEntity = accountRepository.findById(socketWrapper.getAccountEntity().getId()).orElse(null);
        if (blacklistRepository.existsByIp(socket.getInetAddress().getHostAddress())
                || Objects.requireNonNull(accountEntity).isBanned()) {
            socketData.setIdMessage("gjoiblak"); // IP is blacklisted or account is banned (can also use gjoiband)
            socketWriter.write(socket, socketData);
            return;
        }

        String ident = getValueFromSocket(socketData.getInputMessage(), "IDENT");
        Optional<GameEntity> gameEntityOpt = gameRepository.findById(Long.valueOf(ident));
        if(gameEntityOpt.isPresent()) {
            GameEntity gameEntity = gameEntityOpt.get();
            String pass = getValueFromSocket(socketData.getInputMessage(), "PASS");
            if(StringUtils.isNotEmpty(pass) && !pass.equals(gameEntity.getPass())) {
                socketWriter.write(socket, new SocketData("gjoipass", null, null)); // Wrong password
                return;
            }
            if(gameEntity.getEndTime() == null) {
                startGameReport(socketWrapper, gameEntity);
                socketWriter.write(socket, socketData);
                updateHostInfo(gameEntity);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                ses(socket, gameEntity);
            } else {
                socketWriter.write(socket, new SocketData("gjoiugam", null, null)); // Game closed
            }
        } else {
            socketWriter.write(socket, new SocketData("gjoiugam", null, null)); // Game unknown
        }
    }

    /**
     * Create a game on a persistent game spawn service for a user
     * @param socket
     * @param socketData
     */
    public void gpsc(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        AccountEntity accountEntity = accountRepository.findById(socketWrapper.getAccountEntity().getId()).orElse(null);
        if (blacklistRepository.existsByIp(socket.getInetAddress().getHostAddress())
                || Objects.requireNonNull(accountEntity).isBanned()) {
            socketData.setIdMessage("gpscblak"); // IP is blacklisted or account is banned (can also use gpscband)
            socketWriter.write(socket, socketData);
            return;
        }

        String vers = socketWrapper.getPersonaConnectionEntity().getVers();
        String slus = socketWrapper.getPersonaConnectionEntity().getSlus();
        List<String> relatedVers = GameVersUtils.getRelatedVers(vers);
        boolean isMohh = relatedVers.equals(VERS_MOHH_PSP);
        GameEntity gameEntityToCreate = socketMapper.toGameEntity(socketData.getInputMessage(), vers, slus);

        boolean duplicateName = gameRepository.existsByNameAndVersInAndEndTimeIsNull(gameEntityToCreate.getName(), relatedVers);
        if(duplicateName) {
            socketData.setIdMessage("gpscdupl");
            socketWriter.write(socket, socketData);
        } else if(isMohh) {
            SocketWrapper gpsSocketWrapper = socketManager.getAvailableGps();
            if(gpsSocketWrapper == null) {
                socketData.setIdMessage("gpscnfnd");
                socketWriter.write(socket, socketData);
            } else {
                socketWriter.write(socket, socketData);
                Map<String, String> content = Stream.of(new String[][] {
                        { "NAME", gameEntityToCreate.getName() },
                        { "PARAMS", gameEntityToCreate.getParams() },
                        { "SYSFLAGS", gameEntityToCreate.getSysflags() },
                        { "MINSIZE", String.valueOf(gameEntityToCreate.getMinsize()) },
                        { "MAXSIZE", String.valueOf(gameEntityToCreate.getMaxsize()) },
                        { "PASS", null != gameEntityToCreate.getPass() ? gameEntityToCreate.getPass() : "" },
                }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
                socketWriter.write(gpsSocketWrapper.getSocket(), new SocketData("$cre", null, content));

                new Thread(() -> {
                    int retries = 0;
                    while(retries < 5) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        Optional<GameEntity> gameEntityOpt = gameRepository.findByNameAndVersInAndEndTimeIsNull(gameEntityToCreate.getName(), relatedVers);
                        if(gameEntityOpt.isPresent()) {
                            GameEntity gameEntity = gameEntityOpt.get();
                            startGameReport(socketWrapper, gameEntity);
                            ses(socket, gameEntity);
                            updateHostInfo(gameEntity);
                            break;
                        }
                        retries++;
                    }
                }).start();
            }
        } else {
            socketWriter.write(socket, socketData);

            // Set a game server port for MoHH2 if it's not already set (the game set it if there are other games...)
            String params = gameEntityToCreate.getParams();
            int serverPortPos = StringUtils.ordinalIndexOf(params, ",", 20);
            if (serverPortPos != -1 && serverPortPos < params.length()) {
                String[] paramArray = params.split(",");
                if (paramArray.length > 19 && paramArray[19].isEmpty()) {
                    paramArray[19] = Integer.toHexString(1); // Set game server port to 1, so it doesn't conflict with other games
                    params = String.join(",", paramArray);
                }
            }
            gameEntityToCreate.setParams(params);

            gameRepository.save(gameEntityToCreate);
            startGameReport(socketWrapper, gameEntityToCreate);
            ses(socket, gameEntityToCreate);
        }
    }

    /**
     * Send game updates to the host (player list, params)
     * @param gameEntity
     */
    public void updateHostInfo(GameEntity gameEntity) {
        SocketWrapper hostSocketWrapper = socketManager.getHostSocketWrapperOfGame(gameEntity.getId());
        if(hostSocketWrapper != null) {
            Map<String, String> content = getGameInfo(gameEntity);
            socketWriter.write(hostSocketWrapper.getSocket(), new SocketData("+mgm", null, content));
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            socketWriter.write(hostSocketWrapper.getSocket(), new SocketData("+ses", null, content));
        }
    }

    /**
     * Create a new game
     * @param socket
     * @param socketData
     * @param socketWrapper
     */
    public void gcre(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        String vers = socketWrapper.getPersonaConnectionEntity().getVers();
        String slus = socketWrapper.getPersonaConnectionEntity().getSlus();
        GameEntity gameEntity = socketMapper.toGameEntity(socketData.getInputMessage(), vers, slus);

        List<String> relatedVers = GameVersUtils.getRelatedVers(vers);
        boolean duplicateName = gameRepository.existsByNameAndVersInAndEndTimeIsNull(gameEntity.getName(), relatedVers);

        if(duplicateName) {
            socketData.setIdMessage("gcredupl");
            socketWriter.write(socket, socketData);
        } else {
            gameRepository.save(gameEntity);
            socketWriter.write(socket, socketData);

            startGameReport(socketWrapper, gameEntity);
            personaService.who(socket, socketWrapper); // Used to set the game id
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            socketWriter.write(socket, new SocketData("+mgm", null, getGameInfo(gameEntity)));
        }
    }

    /**
     * Leave game
     * @param socket
     * @param socketData
     * @param socketWrapper
     */
    public void glea(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        endGameReport(socketWrapper);
        socketWriter.write(socket, socketData);
    }

    /**
     * Update the status of a persistent game spawn service.
     *
     * If STATUS is "A", then the GPS is available to host a game.
     * If STATUS is "G", then the GPS is hosting a game.
     *
     * @param socket
     * @param socketData
     */
    public void gpss(Socket socket, SocketData socketData) {
        socketWriter.write(socket, socketData);

        String status = getValueFromSocket(socketData.getInputMessage(), "STATUS");

        SocketWrapper socketWrapper = socketManager.getSocketWrapper(socket);
        // Add a flag to indicate that the game is hosted
        if(("A").equals(status)) {
            socketWrapper.getIsGps().set(true);
            socketWrapper.getIsHosting().set(false);
        } else if(("G").equals(status)) {
            socketWrapper.getIsHosting().set(true);
        }

    }

    /**
     * Get periodic status from the GPS
     *
     * @param socket
     * @param socketData
     */
    private void gps(Socket socket, SocketData socketData) {
            Map<String, String> content = Stream.of(new String[][] {
                { "PING", "EA60" },
            }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
            socketData.setOutputData(content);
            socketData.setIdMessage("$gps");
            socketWriter.write(socket, socketData);
    }

    /**
     * Delete a game
     * @param socket
     * @param socketData
     * @param socketWrapper
     */
    public void gdel(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        List<GameEntity> gameEntity = gameRepository.findCurrentGameOfPersona(socketWrapper.getPersonaConnectionEntity().getId());
        if(gameEntity.size() > 0) {
            GameEntity game = gameEntity.get(0);
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            game.setEndTime(now);
            gameRepository.save(game);
            game.getGameReports().stream().filter(report -> null == report.getEndTime()).forEach(report -> {
                report.setEndTime(now);
                gameReportRepository.save(report);
            });
        }
        socketWriter.write(socket, socketData);
    }

    /**
     * Start session
     * @param socket
     * @param gameEntity
     */
    public void ses(Socket socket, GameEntity gameEntity) {
        socketWriter.write(socket, new SocketData("+ses", null, getGameInfo(gameEntity)));
    }

    /**
     * Game details (current opponents, ...)
     * @param socket
     * @param socketData
     */
    public void gget(Socket socket, SocketData socketData) {
        SocketWrapper socketWrapper = socketManager.getSocketWrapper(socket);
        String vers = socketWrapper.getPersonaConnectionEntity().getVers();
        String ident = getValueFromSocket(socketData.getInputMessage(), "IDENT");
        Optional<GameEntity> gameEntityOpt = gameRepository.findById(Long.valueOf(ident));
        if(gameEntityOpt.isPresent()) {
            GameEntity gameEntity = gameEntityOpt.get();
            socketWriter.write(socket, new SocketData("gget", null, getGameInfo(gameEntity, vers)));
        } else {
            socketWriter.write(socket, new SocketData("gget", null, null));
        }
    }

    /**
     * Get game info without version requirement
     * @param gameEntity
     * @return
     */
    public Map<String, String> getGameInfo(GameEntity gameEntity) {
        return getGameInfo(gameEntity, "");
    }

    /**
     * Get game info
     * @param gameEntity
     * @param vers
     * @return
     */
    public Map<String, String> getGameInfo(GameEntity gameEntity, String vers) {
        Long gameId = gameEntity.getId();
        SocketWrapper hostSocketWrapperOfGame = socketManager.getHostSocketWrapperOfGame(gameId);

        List<GameReportEntity> gameReports = gameReportRepository.findByGameIdAndEndTimeIsNull(gameId);

        // Workaround when there is no host (serverless patch)
        boolean hasHost = hostSocketWrapperOfGame != null;
        String host = hasHost ? "@" + hostSocketWrapperOfGame.getPersonaEntity().getPers() : "@brobot1";
        int count = gameReports.size();
        if (!hasHost) count++;

        Map<String, String> content = Stream.of(new String[][] {
                { "IDENT", String.valueOf(Optional.ofNullable(gameEntity.getOriginalId()).orElse(gameEntity.getId())) },
                { "NAME", gameEntity.getName() },
                { "HOST", host },
                // { "GPSHOST", hostSocketWrapperOfGame.getPers() },
                { "PARAMS", gameEntity.getParams() },
                { "PLATPARAMS", "0" },  // ???
                { "ROOM", "1" },
                { "CUSTFLAGS", "413082880" },
                { "SYSFLAGS", gameEntity.getSysflags() },
                { "COUNT", String.valueOf(count) },
                // { "GPSREGION", "2" },
                { "PRIV", "0" },
                { "MINSIZE", String.valueOf(gameEntity.getMinsize()) },
                { "MAXSIZE", String.valueOf(gameEntity.getMaxsize()) },
                { "NUMPART", "1" },
                { "SEED", "3" }, // random seed
                { "WHEN", DateTimeFormatter.ofPattern(DATETIME_FORMAT).format(gameEntity.getStartTime()) },
                // { "GAMEPORT", String.valueOf(props.getUdpPort())},
                // { "VOIPPORT", "9667" },
                // { "GAMEMODE", "0" }, // ???
                { "AUTH", gameEntity.getSysflags().equals("262656") ? "098f6bcd4621d373cade4e832627b4f6" : "" }, // Required for ranked

                // loop 0x80022058 only if COUNT>=0

                // another loop 0x8002225C only if NUMPART>=0
                // { "SELF", "" },

                { "SESS", "0" }, // %s-%s-%08x 0--498ea96f

                { "EVID", "0" },
                { "EVGID", "0" },
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        int[] idx = { 0 };

        if(!hasHost) {
            content.putAll(Stream.of(new String[][] {
                    { "OPID" + idx[0], "0" },
                    { "OPPO" + idx[0], "@brobot1" },
                    { "ADDR" + idx[0], "127.0.0.1" },
                    { "LADDR" + idx[0], "127.0.0.1" },
                    { "MADDR" + idx[0], "" },
            }).collect(Collectors.toMap(data -> data[0], data -> data[1])));
            idx[0]++;
        }

        gameReports.stream()
                .sorted(Comparator.comparing(GameReportEntity::getId))
                .forEach(gameReportEntity -> {
                    PersonaConnectionEntity personaConnectionEntity = gameReportEntity.getPersonaConnection();
                    PersonaEntity personaEntity = personaConnectionEntity.getPersona();
                    String ipAddr = personaConnectionEntity.getAddress().replace("/", "").split(":")[0];
                    String hostPrefix = gameReportEntity.isHost() ? "@" : "";
                    content.putAll(Stream.of(new String[][] {
                            { "OPID" + idx[0], String.valueOf(personaEntity.getId()) },
                            { "OPPO" + idx[0], hostPrefix + personaEntity.getPers() },
                            { "ADDR" + idx[0], ipAddr },
                            { "LADDR" + idx[0], ipAddr },
                            { "MADDR" + idx[0], "" },
                            { "OPPART" + idx[0], "0" },
                            { "OPPARAM" + idx[0], generateOpParam(personaEntity, vers) },
                            { "OPFLAG" + idx[0], "413082880" },
                            { "OPFLAGS" + idx[0], "413082880" },
                            { "PRES" + idx[0], "0" },
                            { "PARTSIZE" + idx[0], String.valueOf(gameEntity.getMaxsize()) },
                            { "PARTPARAMS" + idx[0], "" },
                    }).collect(Collectors.toMap(data -> data[0], data -> data[1])));
                    idx[0]++;
                });
        return content;
    }

    /**
     * Generate the OPPARAM for a player
     * @param personaEntity
     * @param vers
     * @return
     */
    private String generateOpParam(PersonaEntity personaEntity, String vers) {
        PersonaStatsEntity personaStatsEntity = personaStatsRepository.findByPersonaIdAndVers(personaEntity.getId(), vers);
        Long rankLong = personaStatsRepository.getRankByPersonaIdAndVers(personaEntity.getId(), vers);
        int rank = (rankLong != null) ? rankLong.intValue() : 0;
        String loc = personaEntity.getAccount().getLoc();
        String killHex = personaStatsEntity != null ? reverseEndianness(formatIntToWord(personaStatsEntity.getKill())) : "00000000";
        String deathHex = personaStatsEntity != null ? reverseEndianness(formatIntToWord(personaStatsEntity.getDeath())) : "00000000";
        String rankHex = reverseEndianness(formatIntToWord(rank));
        String locHex = reverseEndianness(formatIntToWord(Integer.parseInt(stringToHex(loc.substring(loc.length() - 2)), 16)));
        String repHex = reverseEndianness(formatIntToWord(personaEntity.getRp()));
        String lastHex = reverseEndianness(formatIntToWord(1));

        String concatenatedHex = killHex + deathHex + rankHex + locHex + repHex + lastHex;
        byte[] byteArray = parseHexString(concatenatedHex);
        return Base64.getEncoder().encodeToString(byteArray);
    }

    /**
     * Registers a game entry
     * @param socketWrapper
     * @param gameEntity
     */
    private void startGameReport(SocketWrapper socketWrapper, GameEntity gameEntity) {
        // Close any game report that wasn't property ended (e.g. use Dolphin save state to leave)
        endGameReport(socketWrapper);

        GameReportEntity gameReportEntity = new GameReportEntity();
        gameReportEntity.setGame(gameEntity);
        gameReportEntity.setPersonaConnection(socketWrapper.getPersonaConnectionEntity());
        gameReportEntity.setHost(socketWrapper.getIsHost().get());
        gameReportEntity.setStartTime(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        gameReportRepository.save(gameReportEntity);
    }

    /**
     * Ends the game report because the player has left the game
     */
    public void endGameReport(SocketWrapper socketWrapper) {
        Optional<GameReportEntity> gameReportEntityOpt =
                gameReportRepository.findByPersonaConnectionIdAndEndTimeIsNull(socketWrapper.getPersonaConnectionEntity().getId());
        if(gameReportEntityOpt.isPresent()) {
            GameReportEntity gameReportEntity = gameReportEntityOpt.get();
            GameEntity gameEntity = gameReportEntity.getGame();
            if(socketWrapper.getIsHost().get()) {
                for(GameReportEntity gameReportToClose : gameReportRepository.findByGameIdAndEndTimeIsNull(gameEntity.getId())) {
                    gameReportToClose.setEndTime(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
                    gameReportRepository.save(gameReportToClose);
                }
                gameEntity.setEndTime(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
                gameRepository.save(gameEntity);
            } else {
                gameReportEntity.setEndTime(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
                gameReportRepository.save(gameReportEntity);
                updateHostInfo(gameEntity);
            }
        }
    }

    /**
     * Set an end time to all unfinished connections and games
     */
    @PreDestroy
    public void closeUnfinishedConnectionsAndGames() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        int gameReportsCleaned = gameReportRepository.setEndTimeForAllUnfinishedGameReports(now);
        int gameCleaned = gameRepository.setEndTimeForAllUnfinishedGames(now);
        int personaConnectionsCleaned = personaConnectionRepository.setEndTimeForAllUnfinishedPersonaConnections(now);
        log.info("Data cleaned: {} games, {} game reports, {} persona connections", gameCleaned, gameReportsCleaned, personaConnectionsCleaned);
    }

    /**
     * Close expired games (applies to mohh2 only as games aren't hosted)
     * If no one is in the game after 2 minutes, close it
     */
    public void closeExpiredGames() {
        List<GameEntity> gameEntities = gameRepository.findByEndTimeIsNull();
        gameEntities.forEach(gameEntity -> {
            Set<GameReportEntity> gameReports = gameEntity.getGameReports();
            if(gameReports.stream().noneMatch(report -> report.isHost() && null == report.getEndTime())) {
                if(gameReports.stream().allMatch(report -> report.getEndTime().plusSeconds(90).isBefore(LocalDateTime.now()))) {
                    log.info("Closing expired game: {} - {}", gameEntity.getId(), gameEntity.getName());
                    gameEntity.setEndTime(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
                    gameRepository.save(gameEntity);
                }
            }
        });
    }

    /**
     * Profanity filter a string
     * @param socket
     * @param socketData
     */
    public void filt(Socket socket, SocketData socketData) {
        Map<String, String> content = Stream.of(new String[][] {
                { "TEXT", getValueFromSocket(socketData.getInputMessage(), "TEXT") },
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);
    }

}
