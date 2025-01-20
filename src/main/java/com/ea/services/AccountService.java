package com.ea.services;

import com.ea.dto.SocketData;
import com.ea.dto.SocketWrapper;
import com.ea.entities.AccountEntity;
import com.ea.entities.PersonaConnectionEntity;
import com.ea.mappers.SocketMapper;
import com.ea.repositories.AccountRepository;
import com.ea.steps.SocketWriter;
import com.ea.utils.AccountUtils;
import com.ea.utils.PasswordUtils;
import com.ea.utils.SocketUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.Socket;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.ea.utils.SocketUtils.getValueFromSocket;

@Slf4j
@RequiredArgsConstructor
@Service
public class AccountService {

    private final PasswordUtils passwordUtils;
    private final SocketMapper socketMapper;
    private final AccountRepository accountRepository;
    private final PersonaService personaService;
    private final SocketWriter socketWriter;

    /**
     * Account creation
     * @param socket
     * @param socketData
     */
    public void acct(Socket socket, SocketData socketData) {
        String name = getValueFromSocket(socketData.getInputMessage(), "NAME");

        Optional<AccountEntity> accountEntityOpt = accountRepository.findByName(name);
        if (accountEntityOpt.isPresent()) {
            socketData.setIdMessage("acctdupl"); // Duplicate account error (EC_DUPLICATE)
            int alts = Integer.parseInt(getValueFromSocket(socketData.getInputMessage(), "ALTS"));
            if (alts > 0) {
                String opts = AccountUtils.suggestNames(alts, name);
                Map<String, String> content = Stream.of(new String[][]{
                        { "OPTS", opts }
                }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
                socketData.setOutputData(content);
            }
        } else {
            AccountEntity accountEntity = socketMapper.toAccountEntity(socketData.getInputMessage());
            accountRepository.save(accountEntity);
        }
        socketWriter.write(socket, socketData);
    }

    /**
     * Account update
     * @param socket
     * @param socketData
     */
    public void edit(Socket socket, SocketData socketData) {
        String name = getValueFromSocket(socketData.getInputMessage(), "NAME");

        Optional<AccountEntity> accountEntityOpt = accountRepository.findByName(name);
        if (accountEntityOpt.isPresent()) {
            AccountEntity accountEntity = accountEntityOpt.get();

            String pass = getValueFromSocket(socketData.getInputMessage(), "PASS");
            String mail = getValueFromSocket(socketData.getInputMessage(), "MAIL");
            String spam = getValueFromSocket(socketData.getInputMessage(), "SPAM");
            String chng = getValueFromSocket(socketData.getInputMessage(), "CHNG");

            boolean update = false;
            boolean error = false;

            if (mail != null && !mail.equals(accountEntity.getMail())) {
                accountEntity.setMail(mail);
                update = true;
            }

            if (!pass.equals(chng)) {
                if (passwordUtils.bCryptMatches(pass, accountEntity.getPass())) {
                    accountEntity.setPass(passwordUtils.bCryptEncode(chng));
                    update = true;
                } else {
                    socketData.setIdMessage("editpass"); // Invalid password error (EC_INV_PASS)
                    error = true;
                }
            }

            if (!error && (update || !spam.equals(accountEntity.getSpam()))) {
                accountEntity.setSpam(spam);
                accountEntity.setUpdatedOn(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
                accountRepository.save(accountEntity);
            }

        } else {
            socketData.setIdMessage("editimst"); // Inexisting error (EC_INV_MASTER)
        }

        socketWriter.write(socket, socketData);
    }

    /**
     * Account login
     * @param socket
     * @param socketData
     * @param socketWrapper
     */
    public void auth(Socket socket, SocketData socketData, SocketWrapper socketWrapper) {
        String name = getValueFromSocket(socketData.getInputMessage(), "NAME");
        String pass = getValueFromSocket(socketData.getInputMessage(), "PASS");
        String vers = getValueFromSocket(socketData.getInputMessage(), "VERS");
        String slus = getValueFromSocket(socketData.getInputMessage(), "SLUS");

        if(name.contains("@")) {
            name = name.split("@")[0] + name.split("@")[1];
        }

        Optional<AccountEntity> accountEntityOpt = accountRepository.findByName(name);
        if (accountEntityOpt.isPresent()) {
            AccountEntity accountEntity = accountEntityOpt.get();
            String decodedPass = passwordUtils.ssc2Decode(pass);
            if (passwordUtils.bCryptMatches(decodedPass, accountEntity.getPass())) {
                synchronized (this) {
                    socketWrapper.setAccountEntity(accountEntity);
                }

                String personas = accountEntity.getPersonas().stream()
                        .filter(p -> p.getDeletedOn() == null)
                        .map(p -> p.getPers())
                        .collect(Collectors.joining(","));
                Map<String, String> content = Stream.of(new String[][]{
                        { "NAME", accountEntity.getName() },
                        { "ADDR", socket.getInetAddress().getHostAddress() },
                        { "PERSONAS", personas },
                        { "LOC", accountEntity.getLoc() },
                        { "MAIL", accountEntity.getMail() },
                        { "SPAM", accountEntity.getSpam() }
                }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
                socketData.setOutputData(content);

                if(null != socketWrapper.getPersonaConnectionEntity()) {
                    log.error("User wasn't properly disconnected");
                    personaService.endPersonaConnection(socketWrapper);
                }

                PersonaConnectionEntity personaConnectionEntity = new PersonaConnectionEntity();
                personaConnectionEntity.setAddress(SocketUtils.handleLocalhostIp(socket.getRemoteSocketAddress().toString()));
                personaConnectionEntity.setStartTime(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
                personaConnectionEntity.setVers(vers);
                personaConnectionEntity.setSlus(slus);
                synchronized (this) {
                    socketWrapper.setPersonaConnectionEntity(personaConnectionEntity);
                }

            } else {
                socketData.setIdMessage("authpass"); // Invalid password error (EC_INV_PASS)
            }
        } else {
            socketData.setIdMessage("authimst"); // Inexisting error (EC_INV_MASTER)
        }

        socketWriter.write(socket, socketData);
    }


    /**
     * Lost username or password
     * If we receive 'MAIL', then we have to send the username matching (if present)
     * If we receive 'NAME', then we have to send an email to reset the password of the account linked to the username (if present)
     */
    public void lost(Socket socket, SocketData socketData) {

    }

}
