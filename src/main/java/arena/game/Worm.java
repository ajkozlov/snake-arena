package arena.game;

import java.util.ArrayDeque;
import java.util.Deque;

public class Worm {

    public static final float RADIUS = 6f;
    public static final float SPEED = 6f; // pixels per tick

    public final String id;
    public final String name;
    public final String color;
    public final boolean isBot;

    public float headX;
    public float headY;
    /** Trail of past head positions, newest first. Each element is float[]{x,y}. */
    public final Deque<float[]> body = new ArrayDeque<>();

    public Direction direction;
    public Direction pendingDir;
    public boolean alive = true;
    public int score = 0;
    public int growBuffer = 0; // ticks remaining to not pop tail

    public Worm(String id, String name, String color, boolean isBot,
                float startX, float startY, Direction startDir) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.isBot = isBot;
        this.headX = startX;
        this.headY = startY;
        this.direction = startDir;
        this.pendingDir = startDir;
        // Seed an initial body so the worm is visible from the start
        for (int i = 0; i < 20; i++) {
            body.addLast(new float[]{startX - startDir.dx() * i * SPEED,
                                     startY - startDir.dy() * i * SPEED});
        }
    }

    /** Apply pendingDir (ignoring 180° reversal), then advance head one tick. */
    public void tick() {
        if (!alive) return;
        if (pendingDir != direction.opposite()) {
            direction = pendingDir;
        }
        headX += direction.dx() * SPEED;
        headY += direction.dy() * SPEED;
        body.addFirst(new float[]{headX, headY});
        if (growBuffer > 0) {
            growBuffer--;
        } else {
            body.pollLast();
        }
    }

    public void setDirection(Direction d) {
        if (d != direction.opposite()) {
            pendingDir = d;
        }
    }

    public void eat() {
        score++;
        growBuffer += 8;
    }
}
