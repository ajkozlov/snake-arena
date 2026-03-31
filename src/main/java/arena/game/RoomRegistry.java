package arena.game;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class RoomRegistry {

    private static final Logger LOG = Logger.getLogger(RoomRegistry.class);

    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

    public Room create() {
        // Atomically find an unused code and insert a new Room
        while (true) {
            String code = randomCode();
            Room room = new Room(code);
            if (rooms.putIfAbsent(code, room) == null) {
                LOG.infof("Room %s created (total rooms: %d)", code, rooms.size());
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
        if (r != null) {
            r.shutdown();
            LOG.infof("Room %s removed (total rooms: %d)", code, rooms.size());
        }
    }

    public void cleanupIfEmpty(Room room) {
        if (room.playerCount() == 0) {
            LOG.infof("Room %s is empty, cleaning up", room.code);
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
