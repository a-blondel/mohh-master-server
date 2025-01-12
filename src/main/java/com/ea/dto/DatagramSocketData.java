package com.ea.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.net.DatagramPacket;


@Getter
@Setter
@AllArgsConstructor
public class DatagramSocketData {

    private DatagramPacket inputPacket;
    private byte[] outputMessage;

}
