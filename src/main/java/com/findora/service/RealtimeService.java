package com.findora.service;

import com.corundumstudio.socketio.SocketIOServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Thin wrapper over the Socket.IO server (the Node app used `req.app.get("io")`). Never throws. */
@Service
public class RealtimeService {

    private static final Logger log = LoggerFactory.getLogger(RealtimeService.class);
    private final SocketIOServer server;

    public RealtimeService(SocketIOServer server) {
        this.server = server;
    }

    /** io.to(room).emit(event, payload) */
    public void toRoom(String room, String event, Object payload) {
        try {
            server.getRoomOperations(room).sendEvent(event, payload);
        } catch (Exception e) {
            log.warn("Socket.IO emit failed for event '{}'", event, e);
        }
    }

    /** io.emit(event, payload) */
    public void toAll(String event, Object payload) {
        try {
            server.getBroadcastOperations().sendEvent(event, payload);
        } catch (Exception e) {
            log.warn("Socket.IO broadcast failed for event '{}'", event, e);
        }
    }
}
