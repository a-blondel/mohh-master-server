package com.ea.ws;

import com.ea.dto.SocketData;
import com.ea.services.SocketManager;
import com.ea.steps.SocketWriter;
import com.ea.ws.dto.PacketDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Profile("dev")
@RequiredArgsConstructor
@RestController
public class PacketController {

    private final SocketManager socketManager;
    private final SocketWriter socketWriter;

    @PostMapping("/packet")
    public void sendPacket(@RequestBody PacketDTO packet) {
        if(socketManager.getHostSockets() != null && !socketManager.getHostSockets().isEmpty()) {
            SocketData socketData = new SocketData(packet.getPacketId(), null, packet.getPacketData());
            socketWriter.write(socketManager.getHostSockets().get(0), socketData);
        }
    }

}
