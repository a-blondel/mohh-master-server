package com.ea.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.net.DatagramSocket;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UdpSocketWrapper {
    private DatagramSocket socket;
    private String identifier;

}
