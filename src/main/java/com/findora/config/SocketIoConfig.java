package com.findora.config;

import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Socket.IO server (netty-socketio) so the existing socket.io-client frontend keeps working.
 * It listens on its own port (findora.socket.port) because netty-socketio cannot share Tomcat's port.
 */
@Configuration
public class SocketIoConfig {

    @Bean
    public SocketIOServer socketIOServer(AppProperties props) {
        com.corundumstudio.socketio.Configuration cfg = new com.corundumstudio.socketio.Configuration();
        cfg.setHostname(props.socket().host());
        cfg.setPort(props.socket().port());
        cfg.setOrigin(props.corsOrigins().isEmpty() ? "http://localhost:3000" : props.corsOrigins().get(0).trim());
        cfg.setAllowCustomRequests(true);
        return new SocketIOServer(cfg);
    }
}
