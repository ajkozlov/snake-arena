package arena.game;

public enum Direction {
    N, E, S, W;

    public float dx() {
        return switch (this) { case E -> 1; case W -> -1; default -> 0; };
    }

    public float dy() {
        return switch (this) { case S -> 1; case N -> -1; default -> 0; };
    }

    public Direction opposite() {
        return switch (this) { case N -> S; case S -> N; case E -> W; case W -> E; };
    }

    public Direction turnLeft() {
        return switch (this) { case N -> W; case W -> S; case S -> E; case E -> N; };
    }

    public Direction turnRight() {
        return switch (this) { case N -> E; case E -> S; case S -> W; case W -> N; };
    }

    public static Direction fromString(String s) {
        return switch (s.toUpperCase()) {
            case "N" -> N; case "S" -> S; case "E" -> E; case "W" -> W;
            default -> throw new IllegalArgumentException("Unknown direction: " + s);
        };
    }
}
