package dev.routesim.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Pushes every simulation tick (van positions + stats) to all connected browsers. Clients only listen. */
@Component
public class SimWebSocketHandler extends TextWebSocketHandler {
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper mapper;

    public SimWebSocketHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    public void broadcast(Object payload) {
        if (sessions.isEmpty()) return;
        TextMessage message;
        try {
            message = new TextMessage(mapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            return;
        }
        for (WebSocketSession s : sessions) {
            if (!s.isOpen()) {
                sessions.remove(s);
                continue;
            }
            try {
                synchronized (s) { // a WebSocketSession is not safe for concurrent sends
                    s.sendMessage(message);
                }
            } catch (IOException e) {
                sessions.remove(s);
            }
        }
    }
}
