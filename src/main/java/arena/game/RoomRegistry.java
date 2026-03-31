package arena.game;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class RoomRegistry {

    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

    public Room create() {
        // Atomically find an unused code and insert a new Room
        while (true) {
            String code = randomCode();
            Room room = new Room(code);
            if (rooms.putIfAbsent(code, room) == null) {
                return room;
            }
            // Code collision (very rare) — retry
        }
    }

    public Room get(String code) {
        return rooms.get(code.toUpperCase());
    }

    public void remove(String code) {
        Room r = rooms.remove(code);
        if (r != null) r.shutdown();
    }

    public void cleanupIfEmpty(Room room) {
        if (room.playerCount() == 0) {
            remove(room.code);
        }
    }

    private String randomCode() {
        String alpha = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(alpha.charAt((int) (Math.random() * alpha.length())));
        }
        return sb.toString();
    }
}
