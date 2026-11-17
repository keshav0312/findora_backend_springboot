package com.findora.socket;

import com.corundumstudio.socketio.SocketIOServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/** Starts/stops the Socket.IO server together with the Spring application context. */
@Component
public class SocketServerLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SocketServerLifecycle.class);
    private final SocketIOServer server;
    private volatile boolean running;

    public SocketServerLifecycle(SocketIOServer server, SocketEventHandlers handlers) {
        this.server = server; // handlers injected so listeners are registered before start
    }

    @Override
    public void start() {
        server.start();
        running = true;
        log.info("Socket.IO server listening on port {}", server.getConfiguration().getPort());
    }

    @Override
    public void stop() {
        server.stop();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
