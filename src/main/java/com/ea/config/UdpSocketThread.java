package com.ea.config;

import com.ea.steps.DatagramSocketReader;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.DatagramSocket;

/**
 * Thread to handle a unique udp socket
 */
@Slf4j
public class UdpSocketThread implements Runnable {

    private final DatagramSocket clientSocket;

    public UdpSocketThread(DatagramSocket clientSocket) {
        this.clientSocket = clientSocket;
    }

    public void run() {
        try {
            DatagramSocketReader.read(clientSocket);
        } catch (IOException e) {
            log.error("Error reading from socket", e);
        } finally {
            log.info("UDP server closed");
        }
    }

}
