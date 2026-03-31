package arena.ws;

import arena.game.Direction;
import arena.game.Room;
import arena.game.RoomRegistry;
import arena.game.RoomState;
import arena.model.Msg;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.PathParam;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@WebSocket(path = "/ws/{roomCode}")
public class GameSocket {

    private static final Logger LOG = Logger.getLogger(GameSocket.class);

    /** Per-connection state, keyed by WebSocketConnection.id() */
    private final ConcurrentHashMap<String, Room>   connToRoom     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> connToPlayerId = new ConcurrentHashMap<>();

    @Inject RoomRegistry registry;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @OnOpen
    @Blocking
    public void onOpen(WebSocketConnection conn, @PathParam String roomCode) {
        LOG.debugf("Connection opened: %s (room: %s)", conn.id(), roomCode);
        // Player sends a "join" message immediately after connecting
    }

    @OnClose
    @Blocking
    public void onClose(WebSocketConnection conn) {
        Room   room     = connToRoom.remove(conn.id());
        String playerId = connToPlayerId.remove(conn.id());
        if (room == null || playerId == null) {
            LOG.debugf("Connection closed (no room/player): %s", conn.id());
            return;
        }
        LOG.debugf("Connection closed: %s (player: %s, room: %s)", conn.id(), playerId, room.code);
        room.removePlayer(playerId);
        registry.cleanupIfEmpty(room);
    }

    @OnTextMessage
    @Blocking
    public void onMessage(String text, WebSocketConnection conn) {
        try {
            JsonNode msg  = MAPPER.readTree(text);
            String   type = msg.path("type").asText();
            switch (type) {
                case "join"       -> handleJoin(msg, conn);
                case "start_game" -> handleStart(conn);
                case "input"      -> handleInput(msg, conn);
                case "add_bot"    -> handleAddBot(conn);
                case "remove_bot" -> handleRemoveBot(msg, conn);
                case "leave"      -> {
                    Room   room     = connToRoom.remove(conn.id());
                    String playerId = connToPlayerId.remove(conn.id());
                    if (room != null && playerId != null) {
                        room.removePlayer(playerId);
                        registry.cleanupIfEmpty(room);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warnf("Failed to handle message from %s: %s", conn.id(), e.getMessage());
            conn.sendTextAndAwait("{\"type\":\"error\",\"message\":\"Bad message\"}");
        }
    }

    private void handleJoin(JsonNode msg, WebSocketConnection conn) {
        String roomCode = conn.pathParam("roomCode").toUpperCase();
        String name     = msg.path("name").asText("Player").trim();
        if (name.isEmpty()) name = "Player";

        Room room = registry.get(roomCode);
        if (room == null) {
            conn.sendTextAndAwait("{\"type\":\"error\",\"message\":\"Room not found\"}");
            return;
        }

        // Reconnect: client sends its stored playerId
        String existingPlayerId = msg.path("playerId").asText(null);
        if (existingPlayerId != null && !existingPlayerId.isBlank()) {
            if (room.reconnectPlayer(existingPlayerId, conn)) {
                connToRoom.put(conn.id(), room);
                connToPlayerId.put(conn.id(), existingPlayerId);
                conn.sendTextAndAwait(toJson(Msg.welcome(existingPlayerId, room.getColor(existingPlayerId))));
                return;
            }
            LOG.debugf("Reconnect failed for playerId %s in room %s — falling through to new join", existingPlayerId, roomCode);
            // Reconnect failed — fall through to normal join
        }

        // Normal join
        if (room.state == RoomState.PLAYING || room.state == RoomState.FINISHED) {
            conn.sendTextAndAwait("{\"type\":\"error\",\"message\":\"Game already started\"}");
            return;
        }
        if (room.state == RoomState.COUNTDOWN) {
            conn.sendTextAndAwait("{\"type\":\"error\",\"message\":\"Game starting soon\"}");
            return;
        }

        String playerId = UUID.randomUUID().toString();
        boolean ok = room.addPlayer(playerId, name, conn);
        if (!ok) {
            conn.sendTextAndAwait("{\"type\":\"error\",\"message\":\"Room is full\"}");
            return;
        }
        connToRoom.put(conn.id(), room);
        connToPlayerId.put(conn.id(), playerId);
        conn.sendTextAndAwait(toJson(Msg.welcome(playerId, room.getColor(playerId))));
    }

    private void handleStart(WebSocketConnection conn) {
        Room   room     = connToRoom.get(conn.id());
        String playerId = connToPlayerId.get(conn.id());
        if (room == null || playerId == null) return;
        if (!playerId.equals(room.hostId)) return;
        room.startCountdown();
    }

    private void handleInput(JsonNode msg, WebSocketConnection conn) {
        Room   room     = connToRoom.get(conn.id());
        String playerId = connToPlayerId.get(conn.id());
        if (room == null || playerId == null) return;
        String dirStr = msg.path("dir").asText();
        try {
            room.handleInput(playerId, Direction.fromString(dirStr));
        } catch (IllegalArgumentException ignored) {}
    }

    private void handleAddBot(WebSocketConnection conn) {
        Room   room     = connToRoom.get(conn.id());
        String playerId = connToPlayerId.get(conn.id());
        if (room == null || !playerId.equals(room.hostId)) return;
        room.addBot();
    }

    private void handleRemoveBot(JsonNode msg, WebSocketConnection conn) {
        Room   room     = connToRoom.get(conn.id());
        String playerId = connToPlayerId.get(conn.id());
        if (room == null || !playerId.equals(room.hostId)) return;
        room.removeBot(msg.path("botId").asText());
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"type\":\"error\"}";
        }
    }
}
