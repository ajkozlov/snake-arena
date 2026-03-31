# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Dev Commands

```bash
./mvnw quarkus:dev   # hot-reload dev server at http://localhost:8080
./mvnw test          # run unit tests
./mvnw package       # build über-jar → target/quarkus-app/
```

## Architecture

**Stack**: Java 21 + Quarkus 3.15 backend, vanilla JS + HTML5 Canvas frontend.
**Communication**: WebSocket (`/ws/{roomCode}`) for real-time game events; REST (`/api/room`) for room create/check before the socket opens.
**State**: All in-memory — no database.

### Server flow

```
POST /api/room → create Room (LOBBY) → client opens WS /ws/{code}
WS "join"      → Room.addPlayer()    → broadcasts room_update
WS "start_game"→ Room.startCountdown() → 3s countdown → GameState created
                 ScheduledExecutorService fires tick() every 50 ms
                 each tick: AI decisions → move → collisions → food → win check
                 broadcasts state_update JSON to all sessions
```

### Key classes

| Class | Role |
|---|---|
| `arena.game.Room` | Room lifecycle (LOBBY→COUNTDOWN→PLAYING→FINISHED), player/bot registry, scheduler, JSON broadcast |
| `arena.game.GameState` | Single tick: move worms, wall/body/food collisions, win detection |
| `arena.game.Worm` | Float head position, `Deque<float[]>` body trail, direction, grow buffer |
| `arena.game.AiController` | Per-bot heuristic: lookahead N ticks, turn away from danger, steer to food |
| `arena.game.RoomRegistry` | `@ApplicationScoped` map of active rooms |
| `arena.ws.GameSocket` | `@WebSocket("/ws/{roomCode}")` — routes inbound JSON by `type` field |
| `arena.rest.RoomResource` | `POST /api/room`, `GET /api/room/{code}` |

### WebSocket message types

**Client → Server**: `join {name}`, `start_game`, `input {dir: N/E/S/W}`, `add_bot`, `remove_bot {botId}`, `leave`
**Server → Client**: `room_update`, `countdown`, `game_start`, `state_update`, `game_over`, `error`

### Frontend

- `index.html` + `js/lobby.js`: create/join room, manage bots, start game
- `game.html` + `js/game.js`: reconnects WS, forwards keyboard/touch input
- `js/renderer.js`: `requestAnimationFrame` loop — draws worm body as overlapping circles, food with glow, HUD overlay

### Game constants (in `Worm.java` / `GameState.java`)

```
world:      800 × 600 px
speed:      6 px/tick  (120 px/sec at 20 FPS)
tick rate:  50 ms
worm radius: 6 px
food radius: 10 px — 3 items always present
grow:        +8 ticks of tail-hold per food eaten
```
