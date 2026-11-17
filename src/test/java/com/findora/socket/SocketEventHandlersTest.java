package com.findora.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.corundumstudio.socketio.AuthTokenListener;
import com.corundumstudio.socketio.AuthTokenResult;
import com.corundumstudio.socketio.BroadcastOperations;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.SocketIONamespace;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DataListener;
import com.findora.model.Match;
import com.findora.model.User;
import com.findora.repository.MatchRepository;
import com.findora.repository.UserRepository;
import com.findora.security.AuthUser;
import com.findora.security.JwtService;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SocketEventHandlersTest {

    private static final String MATCH_ID = "0123456789abcdef01234567";

    private final SocketIOServer server = mock(SocketIOServer.class);
    private final SocketIONamespace namespace = mock(SocketIONamespace.class);
    private final PresenceService presence = mock(PresenceService.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MatchRepository matches = mock(MatchRepository.class);
    private final AtomicReference<AuthTokenListener> authListener = new AtomicReference<>();
    private final AtomicReference<ConnectListener> connectListener = new AtomicReference<>();
    private final Map<String, DataListener<?>> eventListeners = new HashMap<>();
    private final User user = new User();

    @BeforeEach
    void setUp() {
        user.setId("user-1");
        user.setName("Findora User");
        user.setAvatar("avatar-url");
        user.setRole(User.ROLE_USER);

        when(server.getNamespace("")).thenReturn(namespace);
        doAnswer(invocation -> {
            authListener.set(invocation.getArgument(0));
            return null;
        }).when(namespace).addAuthTokenListener(any(AuthTokenListener.class));
        doAnswer(invocation -> {
            connectListener.set(invocation.getArgument(0));
            return null;
        }).when(server).addConnectListener(any(ConnectListener.class));
        doAnswer(invocation -> {
            eventListeners.put(invocation.getArgument(0), invocation.getArgument(2));
            return null;
        }).when(server).addEventListener(anyString(), any(Class.class), any(DataListener.class));

        new SocketEventHandlers(server, presence, jwtService, users, matches).register();
        when(jwtService.verifyAccessToken("valid-token")).thenReturn(new AuthUser("user-1", User.ROLE_ADMIN));
        when(users.findById("user-1")).thenReturn(Optional.of(user));
    }

    @Test
    void authenticatesFromDatabaseAndDoesNotTrustClientUserOrRole() throws Exception {
        SocketIOClient client = authenticatedClient();

        fireStringEvent("user:join", client, "another-user");
        fireStringEvent("room:join", client, "admins");

        verify(client).joinRoom("user-1");
        verify(client, never()).joinRoom("another-user");
        verify(client, never()).joinRoom("admins");
        verify(presence).markOnline("user-1");
    }

    @Test
    void rejectsMissingOrExpiredTokensAndDisconnectsUnauthenticatedClients() {
        SocketIOClient client = mock(SocketIOClient.class);
        when(client.getSessionId()).thenReturn(UUID.randomUUID());
        when(jwtService.verifyAccessToken("expired-token"))
                .thenThrow(new JWTVerificationException("expired"));

        connectListener.get().onConnect(client);
        verify(client, never()).disconnect();

        AuthTokenResult missing = authListener.get().getAuthTokenResult(Map.of(), client);
        AuthTokenResult expired =
                authListener.get().getAuthTokenResult(Map.of("token", "expired-token"), client);

        assertFalse(missing.isSuccess());
        assertFalse(expired.isSuccess());
        connectListener.get().onConnect(client);
        verify(client).disconnect();
    }

    @Test
    void doesNotAllowJoiningAnotherUsersConversation() throws Exception {
        SocketIOClient client = authenticatedClient();
        Match match = new Match();
        match.setLostOwner("other-user");
        match.setFoundOwner("another-user");
        when(matches.findById(MATCH_ID)).thenReturn(Optional.of(match));

        fireStringEvent("conversation:join", client, "match_" + MATCH_ID);

        verify(client, never()).joinRoom("match_" + MATCH_ID);
    }

    @Test
    void joinsAuthorizedConversationAndRelaysTypingWithAuthenticatedIdentity() throws Exception {
        SocketIOClient client = authenticatedClient();
        String conversationId = "match_" + MATCH_ID;
        Match match = new Match();
        match.setLostOwner("user-1");
        match.setFoundOwner("peer-user");
        when(matches.findById(MATCH_ID)).thenReturn(Optional.of(match));
        when(client.getAllRooms()).thenReturn(Set.of(conversationId));

        fireStringEvent("conversation:join", client, conversationId);
        verify(client).joinRoom(conversationId);

        BroadcastOperations room = mock(BroadcastOperations.class);
        when(server.getRoomOperations(conversationId)).thenReturn(room);
        fireMapEvent("typing", client, Map.of(
                "conversationId", conversationId,
                "userId", "spoofed-user",
                "name", "Spoofed Name"));

        ArgumentCaptor<Object[]> payloadCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(room).sendEvent(eq("typing"), eq(client), payloadCaptor.capture());
        Map<?, ?> relayed = (Map<?, ?>) payloadCaptor.getValue()[0];
        assertEquals("user-1", relayed.get("userId"));
        assertEquals("Findora User", relayed.get("name"));
        assertEquals(conversationId, relayed.get("conversationId"));
    }

    private SocketIOClient authenticatedClient() {
        SocketIOClient client = mock(SocketIOClient.class);
        Map<String, Object> store = new HashMap<>();
        when(client.get(anyString())).thenAnswer(invocation -> store.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            store.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(client).set(anyString(), any());
        when(client.getSessionId()).thenReturn(UUID.randomUUID());

        connectListener.get().onConnect(client);
        verify(client, never()).disconnect();
        AuthTokenResult result = authListener.get().getAuthTokenResult(Map.of("token", "valid-token"), client);
        assertTrue(result.isSuccess());
        connectListener.get().onConnect(client);
        verify(client, never()).disconnect();
        return client;
    }

    @SuppressWarnings("unchecked")
    private void fireStringEvent(String event, SocketIOClient client, String data) throws Exception {
        DataListener<String> listener = (DataListener<String>) eventListeners.get(event);
        listener.onData(client, data, null);
    }

    @SuppressWarnings("unchecked")
    private void fireMapEvent(String event, SocketIOClient client, Map<String, Object> data) throws Exception {
        DataListener<Map<String, Object>> listener =
                (DataListener<Map<String, Object>>) eventListeners.get(event);
        listener.onData(client, data, null);
    }
}
