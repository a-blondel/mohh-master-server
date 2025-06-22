package com.ea.steps;

import com.ea.dto.SocketData;
import com.ea.dto.SocketWrapper;
import com.ea.services.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.Socket;

@Slf4j
@RequiredArgsConstructor
@Component
public class SocketProcessor {
    private final AuthService authService;
    private final AccountService accountService;
    private final PersonaService personaService;
    private final StatsService statsService;
    private final GameService gameService;
    private final SocketWriter socketWriter;
    private final SocketManager socketManager;

    /**
     * Dispatch to appropriate service based on request type
     * @param socket the socket to handle
     * @param socketData the object to process
     */
    public void process(Socket socket, SocketData socketData) {
        SocketWrapper socketWrapper = socketManager.getSocketWrapper(socket);
        switch (socketData.getIdMessage()) {
            case ("~png"):
                break;
            case ("@tic"), ("AUTH"), ("EPGT"), ("RGET"), ("PSET"), ("USCH"), ("DISC"):
                socketWriter.write(socket, socketData);
                break;
            case ("@dir"):
                authService.dir(socket, socketData);
                break;
            case ("addr"):
                authService.addr(socket, socketData);
                break;
            case ("skey"):
                authService.skey(socket, socketData);
                break;
            case ("news"):
                authService.news(socket, socketData);
                break;
            case ("sele"):
                authService.sele(socket, socketData, socketWrapper);
                break;
            case ("acct"):
                accountService.acct(socket, socketData);
                break;
            case ("edit"):
                accountService.edit(socket, socketData);
                break;
            case ("auth"):
                accountService.auth(socket, socketData, socketWrapper);
                break;
            case ("lost"):
                accountService.lost(socket, socketData);
                break;
            case ("cper"):
                personaService.cper(socket, socketData, socketWrapper);
                break;
            case ("pers"):
                personaService.pers(socket, socketData, socketWrapper);
                break;
            case ("dper"):
                personaService.dper(socket, socketData, socketWrapper);
                break;
            case ("llvl"):
                personaService.llvl(socket, socketData, socketWrapper);
                break;
            case ("cate"):
                statsService.cate(socket, socketData);
                break;
            case ("snap"):
                statsService.snap(socket, socketData, socketWrapper);
                break;
            case ("rank"):
                statsService.rank(socket, socketData);
                break;
            case ("gsea"):
                gameService.gsea(socket, socketData, socketWrapper);
                break;
            case ("gget"):
                gameService.gget(socket, socketData);
                break;
            case ("gjoi"):
                gameService.gjoi(socket, socketData, socketWrapper);
                break;
            case ("gpsc"):
                gameService.gpsc(socket, socketData, socketWrapper);
                break;
            case ("gcre"):
                gameService.gcre(socket, socketData, socketWrapper);
                break;
            case ("glea"):
                gameService.glea(socket, socketData, socketWrapper);
                break;
            case ("gpss"):
                gameService.gpss(socket, socketData);
                break;
            case ("gsta"):
                gameService.gsta(socket, socketData);
                break;
            case ("gset"):
                gameService.gset(socket, socketData, socketWrapper);
                break;
            case ("gdel"):
                gameService.gdel(socket, socketData, socketWrapper);
                break;
            case ("filt"):
                gameService.filt(socket, socketData);
                break;
            default:
                log.info("Unsupported operation: {}", socketData.getIdMessage());
                socketWriter.write(socket, socketData);
                break;
        }
    }

}
