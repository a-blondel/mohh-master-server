package com.ea.services;

import java.io.IOException;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.ea.entities.*;
import com.ea.repositories.*;
import com.ea.utils.GameVersUtils;
import lombok.RequiredArgsConstructor;

import com.ea.dto.SocketData;
import com.ea.dto.SocketWrapper;
import com.ea.steps.SocketWriter;
import com.ea.utils.AccountUtils;
import static com.ea.utils.GameVersUtils.VERS_MOHH_PSP_HOST;
import static com.ea.utils.SocketUtils.getValueFromSocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class PersonaService {

    private final PersonaRepository personaRepository;
    private final PersonaConnectionRepository personaConnectionRepository;
    private final PersonaStatsRepository personaStatsRepository;
    private final GameRepository gameRepository;
    private final GameReportRepository gameReportRepository;
    private final SocketWriter socketWriter;
    private final SocketManager socketManager;

    /**
     * Persona creation
     * @param socket
     * @param socketData
     * @param socketWrapper
     */
    public void cper(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        String pers = getValueFromSocket(socketData.getInputMessage(), "PERS");

        Optional<PersonaEntity> personaEntityOpt = personaRepository.findByPers(pers);
        if (personaEntityOpt.isPresent()) {
            socketData.setIdMessage("cperdupl"); // Duplicate persona error (EC_DUPLICATE)
            int alts = Integer.parseInt(getValueFromSocket(socketData.getInputMessage(), "ALTS"));
            if (alts > 0) {
                String opts = AccountUtils.suggestNames(alts, pers);
                Map<String, String> content = Stream.of(new String[][]{
                        { "OPTS", opts }
                }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
                socketData.setOutputData(content);
            }
        } else {
            PersonaEntity personaEntity = new PersonaEntity();
            personaEntity.setAccount(socketWrapper.getAccountEntity());
            personaEntity.setPers(pers);
            personaEntity.setCreatedOn(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));

            PersonaStatsEntity personaStatsEntity = new PersonaStatsEntity();
            personaStatsEntity.setPersona(personaEntity);
            personaStatsEntity.setVers(socketWrapper.getPersonaConnectionEntity().getVers());
            personaStatsEntity.setSlus(socketWrapper.getPersonaConnectionEntity().getSlus());
            Set<PersonaStatsEntity> personaStatsEntities = Set.of(personaStatsEntity);
            personaEntity.setPersonaStats(personaStatsEntities);

            personaRepository.save(personaEntity);
        }

        socketWriter.write(socket, socketData);
    }

    /**
     * Get persona
     * @param socket
     * @param socketData
     * @param socketWrapper
     */
    public void pers(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        String pers = getValueFromSocket(socketData.getInputMessage(), "PERS");
        if(pers.contains("@")) { // Remove @ from persona name (UHS naming convention)
            socketWrapper.getIsHost().set(true);
            pers = pers.split("@")[0] + pers.split("@")[1];
        }

        // Check if the persona is already connected (allowed for host only)
        Optional<PersonaConnectionEntity> personaConnectionEntityOpt =
                personaConnectionRepository.findByVersAndSlusAndPersonaPersAndIsHostFalseAndEndTimeIsNull(
                        socketWrapper.getPersonaConnectionEntity().getVers(),
                        socketWrapper.getPersonaConnectionEntity().getSlus(),
                        pers);
        if(personaConnectionEntityOpt.isPresent()) {
//            socketData.setIdMessage("perspset");
//            socketWriter.write(socket, socketData);
//            return;
            log.warn("Persona {} already connected, ending old session", pers);
            PersonaConnectionEntity personaConnectionEntity = personaConnectionEntityOpt.get();
            Socket socketToClose = socketManager.getSocketWrapper(personaConnectionEntity.getAddress()).getSocket();
            if(socketToClose != null) {
                log.info("Closing old socket {}", socketToClose.getRemoteSocketAddress());
                try {
                    socketToClose.close();
                } catch (IOException e) {
                    log.error("Error while closing socket", e);
                }
            } else {
                log.error("Socket to close not found");
                personaConnectionEntity.setEndTime(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
                personaConnectionRepository.save(personaConnectionEntity);
            }

        }

        Optional<PersonaEntity> personaEntityOpt = personaRepository.findByPers(pers);
        if (personaEntityOpt.isPresent()) {
            PersonaEntity personaEntity = personaEntityOpt.get();
            synchronized (this) {
                socketWrapper.setPersonaEntity(personaEntity);
            }

            Map<String, String> content = Stream.of(new String[][] {
                    { "PERS", personaEntity.getPers() },
                    { "LKEY", "" },
                    { "EX-ticker", "" },
                    { "LOC", personaEntity.getAccount().getLoc() },
                    { "A", socket.getInetAddress().getHostAddress() },
                    { "LA", socket.getInetAddress().getHostAddress() },
                    { "IDLE", "80000" },
            }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

            socketData.setOutputData(content);
            socketWriter.write(socket, socketData);

            startPersonaConnection(socketWrapper);

            // Check if the persona has stats for this game title ("VERS"), and create them if not
            String vers = socketWrapper.getPersonaConnectionEntity().getVers();
            if (!vers.equals(VERS_MOHH_PSP_HOST) && personaStatsRepository.findByPersonaIdAndVers(personaEntity.getId(), vers) == null) {
                PersonaStatsEntity personaStatsEntity = new PersonaStatsEntity();
                personaStatsEntity.setPersona(personaEntity);
                personaStatsEntity.setVers(vers);
                personaStatsEntity.setSlus(socketWrapper.getPersonaConnectionEntity().getSlus());
                personaStatsRepository.save(personaStatsEntity);
            }
        }
    }

    /**
     * Registers a connection of the persona
     * @param socketWrapper
     */
    private void startPersonaConnection(SocketWrapper socketWrapper) {
        PersonaEntity personaEntity = socketWrapper.getPersonaEntity();
        PersonaConnectionEntity personaConnectionEntity = socketWrapper.getPersonaConnectionEntity();
        personaConnectionEntity.setPersona(personaEntity);
        personaConnectionEntity.setHost(socketWrapper.getIsHost().get());
        personaConnectionRepository.save(personaConnectionEntity);
    }

    /**
     * Ends the current connection of the persona
     */
    public void endPersonaConnection(SocketWrapper socketWrapper) {
        PersonaConnectionEntity personaConnectionEntity = socketWrapper.getPersonaConnectionEntity();
        if (personaConnectionEntity != null && personaConnectionEntity.getPersona() != null) {
            personaConnectionEntity.setEndTime(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
            personaConnectionRepository.save(personaConnectionEntity);
        }
    }

    /**
     * Delete persona
     * @param socket
     * @param socketData
     */
    public void dper(Socket socket, SocketData socketData) {
        String pers = getValueFromSocket(socketData.getInputMessage(), "PERS");
        Optional<PersonaEntity> personaEntityOpt = personaRepository.findByPers(pers);
        if (personaEntityOpt.isPresent()) {
            PersonaEntity personaEntity = personaEntityOpt.get();
            personaEntity.setDeletedOn(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
            personaRepository.save(personaEntity);
        }
        socketWriter.write(socket, socketData);
    }

    public void llvl(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        Map<String, String> content = Stream.of(new String[][] {
                { "SKILL_PTS", "211" },
                { "SKILL_LVL", "1049601" },
                { "SKILL", "" },
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        socketData.setOutputData(content);
        socketWriter.write(socket, socketData);

        who(socket, socketWrapper);

        if(socketWrapper != null && socketWrapper.getPersonaConnectionEntity() != null) {
            List<String> vers = GameVersUtils.getRelatedVers(socketWrapper.getPersonaConnectionEntity().getVers());
            int playersInLobby = personaConnectionRepository.countPlayersInLobby(vers);
            int playersInGame = gameReportRepository.countPlayersInGame(vers);
            content = Stream.of(new String[][] {
                    { "UIL", String.valueOf(playersInLobby) },
                    { "UIG", String.valueOf(playersInGame) },
                    { "UIR", "0" },
                    { "GIP", "0" },
                    { "GCR", "0" },
                    { "GCM", "0" },
            }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
            socketWriter.write(socket, new SocketData("+sst", null, content));
        }
    }

    /**
     * Send a user update record for the current logged in user.
     * @param socket
     * @param socketWrapper
     */
    public void who(Socket socket, SocketWrapper socketWrapper) {
        PersonaEntity personaEntity = socketWrapper.getPersonaEntity();
        AccountEntity accountEntity = socketWrapper.getAccountEntity();
        String vers = socketWrapper.getPersonaConnectionEntity().getVers();

        PersonaStatsEntity personaStatsEntity = personaStatsRepository.findByPersonaIdAndVers(personaEntity.getId(), vers);
        boolean hasStats = null != personaStatsEntity;

        List<GameEntity> gameIds = gameRepository.findCurrentGameOfPersona(socketWrapper.getPersonaConnectionEntity().getId());
        if(gameIds.size() > 1) {
            log.error("Multiple current games found for persona {}", personaEntity.getPers());
        }

        long gameId = gameIds
                .stream()
                .max(Comparator.comparing(GameEntity::getStartTime))
                .map(gameEntity -> Optional.ofNullable(gameEntity.getOriginalId()).orElse(gameEntity.getId()))
                .orElse(0L);;

        String hostPrefix = socketWrapper.getIsHost().get() ? "@" : "";

        Map<String, String> content = Stream.of(new String[][] {
                { "I", String.valueOf(accountEntity.getId()) },
                { "M", hostPrefix + accountEntity.getName() },
                { "N", hostPrefix + personaEntity.getPers() },
                { "F", "U" },
                { "P", "80" },
                // Stats : kills (in hex) at 8th position, deaths (in hex) at 9th
                { "S", ",,,,,,," +
                        (hasStats ? Long.toHexString(personaStatsEntity.getKill()) : "0") +
                        "," +
                        (hasStats ? Long.toHexString(personaStatsEntity.getDeath()) : "0") },
                { "X", "0" },
                { "G", String.valueOf(gameId) },
                { "AT", "" },
                { "CL", "511" },
                { "LV", "1049601" },
                { "MD", "0" },
                // Rank (in decimal)
                { "R", hasStats ? String.valueOf(personaStatsRepository.getRankByPersonaIdAndVers(personaStatsEntity.getPersona().getId(), vers)) : "" },
                { "US", "0" },
                { "HW", "0" },
                { "RP", String.valueOf(personaEntity.getRp()) }, // Reputation (0 to 5 stars)
                { "LO", accountEntity.getLoc() }, // Locale (used to display country flag)
                { "CI", "0" },
                { "CT", "0" },
                // 0x800225E0
                { "A", socket.getInetAddress().getHostAddress() },
                { "LA", socket.getInetAddress().getHostAddress() },
                // 0x80021384
                { "C", "4000,,7,1,1,,1,1,5553" },
                { "RI", "1" },
                { "RT", "1" },
                { "RG", "0" },
                { "RGC", "0" },
                // 0x80021468 if RI != ?? then read RM and RF
                { "RM", "room" },
                { "RF", "C" },
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        socketWriter.write(socket, new SocketData("+who", null, content));
    }

}
