package arena;

import arena.game.Direction;
import arena.game.GameState;
import arena.game.Worm;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class GameStateTest {

    private Worm worm(String id, float x, float y, Direction dir) {
        return new Worm(id, "Test", "#fff", false, x, y, dir);
    }

    @Test
    void worm_moves_in_direction() {
        Worm w = worm("w1", 400, 300, Direction.E);
        w.tick();
        assertEquals(400 + Worm.SPEED, w.headX, 0.01f);
        assertEquals(300, w.headY, 0.01f);
    }

    @Test
    void worm_ignores_reverse_direction() {
        Worm w = worm("w1", 400, 300, Direction.E);
        w.setDirection(Direction.W); // 180° reversal — should be ignored
        w.tick();
        assertEquals(Direction.E, w.direction);
    }

    @Test
    void wall_collision_kills_worm() {
        Worm w = worm("w1", 795, 300, Direction.E); // near right wall
        GameState gs = new GameState(800, 600, List.of(w));
        gs.tick();
        assertFalse(w.alive);
    }

    @Test
    void worm_grows_after_eating() {
        Worm w = worm("w1", 400, 300, Direction.E);
        int sizeBefore = w.body.size();
        w.eat();
        // growBuffer = 8; after 8 ticks body should be 8 longer
        for (int i = 0; i < 8; i++) w.tick();
        assertEquals(sizeBefore + 8, w.body.size());
    }

    @Test
    void game_over_fires_with_last_survivor() {
        Worm w1 = worm("w1", 400, 300, Direction.E);
        Worm w2 = worm("w2", 790, 300, Direction.E); // will hit wall next tick
        GameState gs = new GameState(800, 600, List.of(w1, w2));
        AtomicReference<String> winner = new AtomicReference<>();
        gs.onGameOver = winner::set;
        gs.tick();
        assertEquals("w1", winner.get());
    }
}
