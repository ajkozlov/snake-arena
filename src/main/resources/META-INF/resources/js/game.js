'use strict';

(function () {
  const wsUrl     = sessionStorage.getItem('wsUrl');
  const gameStart = JSON.parse(sessionStorage.getItem('gameStart') || 'null');

  if (!wsUrl || !gameStart) {
    location.href = '/';
    return;
  }

  const ws = new WebSocket(wsUrl);

  // Initialise renderer with the game_start data captured in the lobby
  Renderer.init(gameStart);
  Renderer.start(); // single start — never called again

  ws.onopen = () => {
    const name     = sessionStorage.getItem('playerName') || 'Player';
    const playerId = sessionStorage.getItem('myId') || '';
    // Send stored playerId so server can reconnect this socket to the existing session
    ws.send(JSON.stringify({type: 'join', name, playerId}));
  };

  ws.onmessage = ({data}) => {
    const msg = JSON.parse(data);
    switch (msg.type) {
      case 'welcome':
        // Server confirmed reconnect; update stored id in case it changed
        sessionStorage.setItem('myId', msg.playerId);
        break;
      case 'room_update': {
        const nameMap = {};
        msg.players.forEach(p => { nameMap[p.id] = p.name; });
        Renderer.setNames(nameMap);
        break;
      }
      case 'game_start':
        // Re-init colors/world if server re-sends (e.g. spectator reconnect)
        Renderer.init(msg);
        break;
      case 'countdown':
        Renderer.showCountdown(msg.seconds);
        break;
      case 'state_update':
        Renderer.update(msg);
        break;
      case 'game_over':
        Renderer.showGameOver(msg);
        break;
    }
  };

  // ── Keyboard input ────────────────────────────────────────────────────────
  const KEY_DIR = {
    ArrowUp: 'N', KeyW: 'N',
    ArrowDown: 'S', KeyS: 'S',
    ArrowLeft: 'W', KeyA: 'W',
    ArrowRight: 'E', KeyD: 'E',
  };

  document.addEventListener('keydown', e => {
    const dir = KEY_DIR[e.code];
    if (!dir) return;
    e.preventDefault();
    if (ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({type: 'input', dir}));
    }
  });

  // ── D-pad (touch + click) ────────────────────────────────────────────────
  const dpadMap = {'btn-u': 'N', 'btn-d': 'S', 'btn-l': 'W', 'btn-r': 'E'};

  function sendDir(dir) {
    if (ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({type: 'input', dir}));
    }
  }

  Object.entries(dpadMap).forEach(([id, dir]) => {
    const btn = document.getElementById(id);
    btn.addEventListener('touchstart', e => { e.preventDefault(); sendDir(dir); }, {passive: false});
    btn.addEventListener('click', () => sendDir(dir));
  });

  // ── Back to lobby (game over screen) ─────────────────────────────────────
  document.getElementById('back-btn')?.addEventListener('click', () => {
    ws.close();
    location.href = '/';
  });

  // ── Exit Arena (mid-game button) ──────────────────────────────────────────
  document.getElementById('exit-arena-btn')?.addEventListener('click', () => {
    if (ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({type: 'leave'}));
    }
    ws.close();
    location.href = '/';
  });
})();
