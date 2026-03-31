package arena.model;

import arena.game.RoomState;
import java.util.List;

/** All outbound server→client messages as static factory methods. */
public class Msg {

    public record RoomUpdate(String type, List<PlayerInfo> players, String state) {}
    public record Welcome(String type, String playerId, String color) {}
    public record Countdown(String type, int seconds) {}
    public record GameStart(String type, World world, java.util.Map<String, String> colors) {}
    public record World(float w, float h) {}
    public record StateUpdate(String type, List<WormDto> worms, List<float[]> food) {}
    public record GameOver(String type, String winnerId, String winnerName, List<ScoreEntry> scores) {}
    public record ScoreEntry(String id, String name, int score) {}
    public record Error(String type, String message) {}

    public static RoomUpdate roomUpdate(List<PlayerInfo> players, RoomState state) {
        return new RoomUpdate("room_update", players, state.name());
    }

    public static Welcome welcome(String playerId, String color) {
        return new Welcome("welcome", playerId, color);
    }

    public static Countdown countdown(int seconds) {
        return new Countdown("countdown", seconds);
    }

    public static GameStart gameStart(float w, float h, java.util.Map<String, String> colors) {
        return new GameStart("game_start", new World(w, h), colors);
    }

    public static StateUpdate stateUpdate(List<WormDto> worms, List<float[]> food) {
        return new StateUpdate("state_update", worms, food);
    }

    public static GameOver gameOver(String winnerId, String winnerName, List<ScoreEntry> scores) {
        return new GameOver("game_over", winnerId, winnerName, scores);
    }

    public static Error error(String message) {
        return new Error("error", message);
    }
}
