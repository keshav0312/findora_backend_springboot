package com.findora.config;

import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures the private Netty Socket.IO listener, which is exposed through the container proxy. */
@Configuration
public class SocketIoConfig {

    @Bean
    public SocketIOServer socketIOServer(AppProperties props) {
        com.corundumstudio.socketio.Configuration cfg = new com.corundumstudio.socketio.Configuration();
        cfg.setHostname(props.socket().host());
        cfg.setPort(props.socket().port());
        String origin = props.corsOrigins().stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .findFirst()
                .orElse("http://localhost:3000");
        if ("*".equals(origin)) {
            throw new IllegalStateException("Socket.IO CORS origin must be an explicit frontend origin.");
        }
        cfg.setOrigin(origin);
        cfg.setAllowCustomRequests(true);
        return new SocketIOServer(cfg);
    }
}
