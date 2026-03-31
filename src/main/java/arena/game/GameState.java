package arena.game;

import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

public class GameState {

    private static final Logger LOG = Logger.getLogger(GameState.class);

    public static final float FOOD_RADIUS = 10f;
    public static final int   FOOD_COUNT  = 3;

    public final float worldW;
    public final float worldH;
    public final List<Worm> worms;
    public final List<float[]> food = new ArrayList<>();

    private final List<AiController> aiControllers = new ArrayList<>();
    private final Random rng = new Random();

    /** Called when the game ends. Receives the winner worm id, or null on draw. */
    public Consumer<String> onGameOver;

    public GameState(float worldW, float worldH, List<Worm> worms) {
        this.worldW = worldW;
        this.worldH = worldH;
        this.worms  = new ArrayList<>(worms);
        for (Worm w : this.worms) {
            if (w.isBot) aiControllers.add(new AiController(w));
        }
        for (int i = 0; i < FOOD_COUNT; i++) spawnFood();
    }

    public synchronized void tick() {
        // 1. AI decisions
        for (AiController ai : aiControllers) {
            if (ai.worm.alive) {
                ai.worm.setDirection(ai.computeNextDirection(this));
            }
        }

        // 2. Move all alive worms
        for (Worm w : worms) w.tick();

        // 3. Wall collisions
        for (Worm w : worms) {
            if (!w.alive) continue;
            float r = Worm.RADIUS;
            if (w.headX - r < 0 || w.headX + r > worldW ||
                w.headY - r < 0 || w.headY + r > worldH) {
                w.alive = false;
                LOG.debugf("Worm '%s' died: hit wall at (%.1f, %.1f)", w.name, w.headX, w.headY);
            }
        }

        // 4. Head-vs-body collisions (all worms, including self after skip)
        float diam2 = (Worm.RADIUS * 2) * (Worm.RADIUS * 2);
        for (Worm w : worms) {
            if (!w.alive) continue;
            for (Worm other : worms) {
                if (!other.alive) continue;
                Iterable<float[]> segments = other == w
                        ? other.body.stream().skip(15).toList()
                        : other.body;
                for (float[] seg : segments) {
                    float dx = w.headX - seg[0], dy = w.headY - seg[1];
                    if (dx * dx + dy * dy < diam2) {
                        w.alive = false;
                        LOG.debugf("Worm '%s' died: collided with body of '%s'", w.name, other.name);
                        break;
                    }
                }
            }
        }

        // 5. Head-vs-food
        float eatDist2 = (Worm.RADIUS + FOOD_RADIUS) * (Worm.RADIUS + FOOD_RADIUS);
        for (Worm w : worms) {
            if (!w.alive) continue;
            food.removeIf(f -> {
                float dx = w.headX - f[0], dy = w.headY - f[1];
                if (dx * dx + dy * dy < eatDist2) {
                    w.eat();
                    spawnFood();
                    return true;
                }
                return false;
            });
        }

        // 6. Win check
        long alive = worms.stream().filter(w -> w.alive).count();
        if (alive <= 1 && onGameOver != null) {
            String winnerId = worms.stream()
                    .filter(w -> w.alive)
                    .map(w -> w.id)
                    .findFirst()
                    .orElse(null);
            onGameOver.accept(winnerId);
            onGameOver = null; // fire once
        }
    }

    private void spawnFood() {
        for (int attempt = 0; attempt < 30; attempt++) {
            float x = FOOD_RADIUS + rng.nextFloat() * (worldW - FOOD_RADIUS * 2);
            float y = FOOD_RADIUS + rng.nextFloat() * (worldH - FOOD_RADIUS * 2);
            if (!overlapsAnything(x, y)) {
                food.add(new float[]{x, y});
                return;
            }
        }
        // Fallback: place anywhere (map is very crowded)
        food.add(new float[]{worldW / 2, worldH / 2});
    }

    private boolean overlapsAnything(float x, float y) {
        float r2 = (FOOD_RADIUS * 2) * (FOOD_RADIUS * 2);
        for (Worm w : worms) {
            for (float[] seg : w.body) {
                float dx = x - seg[0], dy = y - seg[1];
                if (dx * dx + dy * dy < r2) return true;
            }
        }
        for (float[] f : food) {
            float dx = x - f[0], dy = y - f[1];
            if (dx * dx + dy * dy < r2) return true;
        }
        return false;
    }
}
