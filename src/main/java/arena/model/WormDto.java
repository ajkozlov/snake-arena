package arena.model;

import java.util.List;

public record WormDto(
    String id,
    float headX,
    float headY,
    List<float[]> body,
    boolean alive,
    int score
) {}
