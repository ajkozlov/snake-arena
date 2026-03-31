package arena.game;

import java.util.List;
import java.util.Random;

public class AiController {

    private static final int LOOKAHEAD_DANGER = 5;
    private static final int LOOKAHEAD_CLEAR  = 15;
    private static final Random RNG = new Random();

    final Worm worm;

    public AiController(Worm worm) {
        this.worm = worm;
    }

    public Direction computeNextDirection(GameState state) {
        Direction cur = worm.direction;
        int curClear  = clearTicks(cur, worm.headX, worm.headY, state);

        if (curClear >= LOOKAHEAD_DANGER) {
            // Current direction is safe — optionally steer towards food
            Direction towards = dirTowardsFood(state);
            if (towards != null && towards != cur && towards != cur.opposite()) {
                int foodClear = clearTicks(towards, worm.headX, worm.headY, state);
                if (foodClear >= curClear * 2) {
                    return towards;
                }
            }
            return cur;
        }

        // Current direction is dangerous — pick the safer turn
        Direction left  = cur.turnLeft();
        Direction right = cur.turnRight();
        int leftClear  = clearTicks(left,  worm.headX, worm.headY, state);
        int rightClear = clearTicks(right, worm.headX, worm.headY, state);

        if (leftClear == 0 && rightClear == 0) {
            return RNG.nextBoolean() ? left : right; // trapped, best-effort
        }
        return leftClear >= rightClear ? left : right;
    }

    /** Simulates up to LOOKAHEAD_CLEAR steps; returns how many are clear before hitting anything. */
    int clearTicks(Direction dir, float x, float y, GameState state) {
        for (int i = 1; i <= LOOKAHEAD_CLEAR; i++) {
            x += dir.dx() * Worm.SPEED;
            y += dir.dy() * Worm.SPEED;
            if (hitsWall(x, y, state) || hitsAnyBody(x, y, state)) {
                return i - 1;
            }
        }
        return LOOKAHEAD_CLEAR;
    }

    private boolean hitsWall(float x, float y, GameState state) {
        float r = Worm.RADIUS;
        return x - r < 0 || x + r > state.worldW || y - r < 0 || y + r > state.worldH;
    }

    private boolean hitsAnyBody(float x, float y, GameState state) {
        for (Worm other : state.worms) {
            if (!other.alive) continue;
            List<float[]> segments = other == worm
                    ? other.body.stream().skip(15).toList()
                    : other.body.stream().toList();
            for (float[] seg : segments) {
                float dx = x - seg[0], dy = y - seg[1];
                if (dx * dx + dy * dy < (Worm.RADIUS * 2) * (Worm.RADIUS * 2)) return true;
            }
        }
        return false;
    }

    private Direction dirTowardsFood(GameState state) {
        if (state.food.isEmpty()) return null;
        float[] nearest = null;
        float bestDist = Float.MAX_VALUE;
        for (float[] f : state.food) {
            float d = dist(worm.headX, worm.headY, f[0], f[1]);
            if (d < bestDist) { bestDist = d; nearest = f; }
        }
        if (nearest == null) return null;
        float dx = nearest[0] - worm.headX;
        float dy = nearest[1] - worm.headY;
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx > 0 ? Direction.E : Direction.W;
        } else {
            return dy > 0 ? Direction.S : Direction.N;
        }
    }

    private float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
