package com.ea.steps;

import com.ea.dto.DatagramSocketData;
import com.ea.dto.UdpSocketWrapper;
import com.ea.repositories.GameReportRepository;
import com.ea.services.UdpSocketManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HexFormat;

@Slf4j
@RequiredArgsConstructor
public class DatagramSocketProcessor {

    /*public static final int RAW_PACKET_INIT = 1;
    public static final int RAW_PACKET_CONN = 2;
    public static final int RAW_PACKET_DISC = 3;
    public static final int RAW_PACKET_POKE = 5;
    public static final int GAME_PACKET_USER_UNRELIABLE = 7;
    public static final int GAME_PACKET_USER_UNRELIABLE_AND_GAME_PACKET_SYNC = 71; // 7 + 64
    public static final int GAME_PACKET_SYNC = 64;*/
    public static final int RAW_PACKET_DATA = 256;
    public static final int RAW_PACKET_UNREL = 128;
    private final GameReportRepository gameReportRepository;

    private static byte[] uhsIpHash = HexFormat.of().parseHex("08f43358");
    private static byte[] clientIpHash = HexFormat.of().parseHex("c6627546");

    /**
     * Prepares the output message based on request type,
     * then calls the writer
     * @param socket the socket to give to the writer
     * @param socketData the object to process
     */
    public static void process(DatagramSocket socket, DatagramSocketData socketData) {

        int port = socketData.getInputPacket().getPort();
        boolean isUHS = port == 3658;

        DatagramPacket inputPacket = socketData.getInputPacket();
        byte[] buf = Arrays.copyOf(inputPacket.getData(), inputPacket.getLength());

        int packetSeq = new BigInteger(1, buf, 0, 4).intValue();

        UdpSocketWrapper udpSocketWrapper;
        try {
            if(isUHS) {
                udpSocketWrapper = UdpSocketManager.getSocketWrapper("Client");
                socketData.getInputPacket().setPort(1110);
                socketData.getInputPacket().setAddress(InetAddress.getByName("192.168.1.13"));

                if(packetSeq < 6) {
                    System.arraycopy(clientIpHash, 0, buf, 4, 4);
                } else if (RAW_PACKET_UNREL <= packetSeq && RAW_PACKET_DATA > packetSeq) {
                    /*int packetOperation = new BigInteger(1, buf, inputPacket.getLength() - 1, 1).intValue();
                    if (0x05 == packetOperation) {
                        buf[inputPacket.getLength() - 1] = 0x07;
                    } else if (0x45 == packetOperation)  {
                        buf[inputPacket.getLength() - 1] = 0x47;
                    }*/
                }

            } else {
                udpSocketWrapper = UdpSocketManager.getSocketWrapper("UHS");
                socketData.getInputPacket().setPort(3658);
                socketData.getInputPacket().setAddress(InetAddress.getByName("192.168.1.13"));

                if(packetSeq < 6) {
                    System.arraycopy(uhsIpHash, 0, buf, 4, 4);
                    buf = Arrays.copyOf(buf, buf.length - 4);
                } else if (RAW_PACKET_UNREL <= packetSeq && RAW_PACKET_DATA > packetSeq) {
                    /*int packetOperation = new BigInteger(1, buf, inputPacket.getLength() - 1, 1).intValue();
                    if (0x07 == packetOperation) {
                        buf[inputPacket.getLength() - 1] = 0x05;
                    } else if (0x47 == packetOperation)  {
                        buf[inputPacket.getLength() - 1] = 0x45;
                    }*/
                }

            }

        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        socketData.setOutputMessage(buf);

        //DatagramSocketWriter.write(socket, socketData);
        DatagramSocketWriter.write(udpSocketWrapper.getSocket(), socketData);

    }

}
