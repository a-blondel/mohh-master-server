package com.ea.utils;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Component
public class Props {

    @Value("${ssl.port}")
    private int sslPort;

    @Value("${ssl.protocols}")
    private String sslProtocols;

    @Value("${ssl.cipher-suites}")
    private String sslCipherSuites;

    @Value("${ssl.certificate.cipher-algorithm}")
    private String sslCertificateCipherAlgorithm;

    @Value("${ssl.debug.enabled}")
    private boolean sslDebugEnabled;

    @Value("${tcp.port}")
    private int tcpPort;

    @Value("${tcp.host}")
    private String tcpHost;

    @Value("${tcp.debug.enabled}")
    private boolean tcpDebugEnabled;

    @Value("${tcp.debug.exclusions}")
    private List<String> tcpDebugExclusions;

    @Value("${udp.port}")
    private int udpPort;

    @Value("${udp.host}")
    private String udpHost;

    @Value("${udp.connect-mode.enabled}")
    private boolean connectModeEnabled;

    @Value("${udp.enabled}")
    private boolean udpEnabled;

    @Value("${udp.debug.enabled}")
    private boolean udpDebugEnabled;

    @Value("${tos.enabled}")
    private boolean tosEnabled;

    @Value("${ssc2.key}")
    private String ssc2Key;

    @Value("${lobby.close-expired}")
    private boolean closeExpiredLobbiesEnabled;

}
