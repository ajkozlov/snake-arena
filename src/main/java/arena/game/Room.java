package arena.game;

import arena.model.Msg;
import arena.model.PlayerInfo;
import arena.model.WormDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.WebSocketConnection;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Room {

    private static final String[] COLORS = {"#e94560", "#4fc3f7", "#81c784", "#ffb74d"};
    private static final float WORLD_W = 800f;
    private static final float WORLD_H = 600f;
    private static final long TICK_MS  = 50L;

    private static final Logger LOG = Logger.getLogger(Room.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public final String code;
    public String hostId;
    public RoomState state = RoomState.LOBBY;

    /** Human players: playerId (stable UUID) → current connection (null if disconnected mid-game) */
    private final Map<String, WebSocketConnection> connections = new LinkedHashMap<>();
    /** All participants (humans + bots): playerId → name */
    private final Map<String, String> names = new LinkedHashMap<>();
    /** playerId → assigned color hex */
    private final Map<String, String> colorMap = new LinkedHashMap<>();
    /** Which color slots are taken (index 0-3) */
    private final boolean[] colorSlots = new boolean[4];
    private final Set<String> botIds = new LinkedHashSet<>();

    private final AtomicInteger botCounter = new AtomicInteger(1);
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> tickFuture;

    public GameState gameState;

    public Room(String code) {
        this.code = code;
    }

    // ── Lobby management ──────────────────────────────────────────────────────

    public synchronized boolean addPlayer(String playerId, String name, WebSocketConnection conn) {
        if (totalCount() >= 4) return false;
        connections.put(playerId, conn);
        names.put(playerId, name);
        colorMap.put(playerId, assignColor());
        if (hostId == null) hostId = playerId;
        LOG.infof("[%s] Player '%s' (%s) joined (total: %d)", code, name, playerId, totalCount());
        broadcastRoomUpdate();
        return true;
    }

    public synchronized void removePlayer(String playerId) {
        String name = names.getOrDefault(playerId, playerId);
        connections.remove(playerId);
        if (state == RoomState.PLAYING && gameState != null) {
            // Keep in names/colors so the player can reconnect and spectate
            gameState.worms.stream()
                    .filter(w -> w.id.equals(playerId))
                    .findFirst()
                    .ifPresent(w -> w.alive = false);
            if (playerId.equals(hostId)) {
                hostId = connections.keySet().stream().findFirst().orElse(null);
                LOG.infof("[%s] Host transferred to %s", code, hostId);
            }
            LOG.infof("[%s] Player '%s' disconnected mid-game", code, name);
        } else {
            names.remove(playerId);
            freeColor(colorMap.remove(playerId));
            if (playerId.equals(hostId)) {
                hostId = connections.keySet().stream().findFirst().orElse(null);
                LOG.infof("[%s] Host transferred to %s", code, hostId);
            }
            LOG.infof("[%s] Player '%s' left lobby (total: %d)", code, name, totalCount());
        }
        broadcastRoomUpdate();
    }

    /** Reconnect an existing player (lobby or game). Returns false if playerId is unknown. */
    public synchronized boolean reconnectPlayer(String playerId, WebSocketConnection conn) {
        if (!names.containsKey(playerId) || botIds.contains(playerId)) return false;
        connections.put(playerId, conn);
        String name = names.get(playerId);
        if (state == RoomState.PLAYING && gameState != null) {
            LOG.infof("[%s] Player '%s' reconnected mid-game", code, name);
            // Re-send game context so the client can render the ongoing game
            Map<String, String> colors = new LinkedHashMap<>(colorMap);
            send(conn, toJson(Msg.gameStart(WORLD_W, WORLD_H, colors)));
            send(conn, toJson(buildStateUpdate()));
        } else {
            LOG.infof("[%s] Player '%s' reconnected to lobby", code, name);
        }
        broadcastRoomUpdate();
        return true;
    }

    public synchronized String addBot() {
        if (totalCount() >= 4) return null;
        String botId = "bot-" + UUID.randomUUID().toString().substring(0, 4);
        String botName = "Bot " + botCounter.getAndIncrement();
        botIds.add(botId);
        names.put(botId, botName);
        colorMap.put(botId, assignColor());
        LOG.infof("[%s] Bot '%s' added (total: %d)", code, botName, totalCount());
        broadcastRoomUpdate();
        return botId;
    }

    public synchronized boolean removeBot(String botId) {
        if (!botIds.remove(botId)) return false;
        String botName = names.remove(botId);
        freeColor(colorMap.remove(botId));
        LOG.infof("[%s] Bot '%s' removed (total: %d)", code, botName, totalCount());
        broadcastRoomUpdate();
        return true;
    }

    public synchronized int playerCount() {
        return connections.size() + botIds.size();
    }

    public synchronized String getColor(String playerId) {
        return colorMap.getOrDefault(playerId, "#ffffff");
    }

    // ── Game lifecycle ─────────────────────────────────────────────────────────

    public synchronized void startCountdown() {
        // Allow start with 1 human + ≥1 bot, or ≥2 humans (with or without bots)
        boolean canStart = (connections.size() >= 2) ||
                           (connections.size() == 1 && !botIds.isEmpty());
        if (state != RoomState.LOBBY || !canStart) return;
        state = RoomState.COUNTDOWN;
        LOG.infof("[%s] Countdown started (%d players)", code, totalCount());
        scheduler.schedule(() -> sendCountdown(3), 0, TimeUnit.MILLISECONDS);
    }

    private void sendCountdown(int n) {
        broadcast(toJson(Msg.countdown(n)));
        if (n > 0) {
            scheduler.schedule(() -> sendCountdown(n - 1), 1, TimeUnit.SECONDS);
        } else {
            scheduler.schedule(this::startGame, 0, TimeUnit.MILLISECONDS);
        }
    }

    private synchronized void startGame() {
        state = RoomState.PLAYING;

        List<Worm> worms = buildWorms();
        gameState = new GameState(WORLD_W, WORLD_H, worms);
        gameState.onGameOver = this::onGameOver;

        Map<String, String> colors = new LinkedHashMap<>(colorMap);
        broadcast(toJson(Msg.gameStart(WORLD_W, WORLD_H, colors)));

        LOG.infof("[%s] Game started with %d worms", code, worms.size());
        tickFuture = scheduler.scheduleAtFixedRate(this::tick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
    }

    private void tick() {
        // Hold Room lock while computing state to prevent handleInput racing
        String stateJson;
        synchronized (this) {
            if (state != RoomState.PLAYING || gameState == null) return;
            gameState.tick();
            stateJson = toJson(buildStateUpdate());
        }
        // Send outside the lock to avoid blocking handleInput during I/O
        broadcastSnapshot(stateJson);
    }

    private synchronized void onGameOver(String winnerId) {
        state = RoomState.FINISHED;
        if (tickFuture != null) tickFuture.cancel(false);

        String winnerName = winnerId != null ? names.getOrDefault(winnerId, "?") : null;
        List<Msg.ScoreEntry> scores = gameState.worms.stream()
                .sorted(Comparator.comparingInt((Worm w) -> w.score).reversed())
                .map(w -> new Msg.ScoreEntry(w.id, w.name, w.score))
                .toList();
        if (winnerName != null) {
            LOG.infof("[%s] Game over — winner: '%s' (scores: %s)", code, winnerName,
                    scores.stream().map(s -> s.name() + "=" + s.score()).toList());
        } else {
            LOG.infof("[%s] Game over — draw (no survivors)", code);
        }
        broadcast(toJson(Msg.gameOver(winnerId, winnerName, scores)));
    }

    // ── Input ──────────────────────────────────────────────────────────────────

    public synchronized void handleInput(String playerId, Direction dir) {
        if (gameState == null) return;
        gameState.worms.stream()
                .filter(w -> w.id.equals(playerId))
                .findFirst()
                .ifPresent(w -> w.setDirection(dir));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private List<Worm> buildWorms() {
        float[][] starts = {
            {200, 150}, {600, 450}, {600, 150}, {200, 450}
        };
        Direction[] dirs = {Direction.E, Direction.W, Direction.S, Direction.N};
        List<Worm> result = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, String> e : names.entrySet()) {
            String id = e.getKey();
            result.add(new Worm(id, e.getValue(), colorMap.get(id),
                    botIds.contains(id), starts[i][0], starts[i][1], dirs[i]));
            i++;
        }
        return result;
    }

    private Msg.StateUpdate buildStateUpdate() {
        List<WormDto> dtos = gameState.worms.stream()
                .map(w -> new WormDto(w.id, w.headX, w.headY,
                        new ArrayList<>(w.body), w.alive, w.score))
                .toList();
        return Msg.stateUpdate(dtos, new ArrayList<>(gameState.food));
    }

    /** Broadcast to all current connections; tolerates individual send failures. */
    public synchronized void broadcast(String json) {
        broadcastSnapshot(json);
    }

    /** Snapshot connections under lock and send without holding the lock. */
    private void broadcastSnapshot(String json) {
        List<WebSocketConnection> snapshot;
        synchronized (this) {
            snapshot = connections.values().stream()
                    .filter(Objects::nonNull)
                    .toList();
        }
        for (WebSocketConnection c : snapshot) {
            send(c, json);
        }
    }

    public synchronized void broadcastRoomUpdate() {
        List<PlayerInfo> players = names.entrySet().stream()
                .map(e -> new PlayerInfo(e.getKey(), e.getValue(),
                        colorMap.get(e.getKey()), botIds.contains(e.getKey())))
                .toList();
        broadcast(toJson(Msg.roomUpdate(players, state)));
    }

    private void send(WebSocketConnection conn, String json) {
        try {
            conn.sendTextAndAwait(json);
        } catch (Exception ignored) {
            // Connection may have closed between snapshot and send — safe to ignore
        }
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception ex) {
            return "{\"type\":\"error\"}";
        }
    }

    private int totalCount() {
        return connections.size() + botIds.size();
    }

    private String assignColor() {
        for (int i = 0; i < colorSlots.length; i++) {
            if (!colorSlots[i]) {
                colorSlots[i] = true;
                return COLORS[i];
            }
        }
        return "#ffffff";
    }

    private void freeColor(String color) {
        if (color == null) return;
        for (int i = 0; i < COLORS.length; i++) {
            if (COLORS[i].equals(color)) {
                colorSlots[i] = false;
                return;
            }
        }
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
