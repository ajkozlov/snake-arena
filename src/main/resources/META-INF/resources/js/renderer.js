'use strict';

const Renderer = (() => {
  const canvas = document.getElementById('canvas');
  const ctx    = canvas.getContext('2d');
  const W = canvas.width, H = canvas.height;

  let state   = null;  // latest state_update payload
  let colors  = {};    // id → hex
  let names   = {};    // id → name
  let running = false;
  let tick    = 0;
  let lastHudKey = ''; // for HUD change detection

  function init(gameStart) {
    colors = gameStart.colors || {};
  }

  function setNames(nameMap) {
    names = nameMap;
  }

  function update(newState) {
    state = newState;
  }

  function start() {
    if (running) return; // guard against double-start
    running = true;
    requestAnimationFrame(loop);
  }

  function stop() {
    running = false;
  }

  function loop() {
    if (!running) return;
    tick++;
    draw();
    requestAnimationFrame(loop);
  }

  function draw() {
    // Background
    ctx.fillStyle = '#1a1a2e';
    ctx.fillRect(0, 0, W, H);

    // Faint grid
    ctx.strokeStyle = 'rgba(255,255,255,.03)';
    ctx.lineWidth = 1;
    for (let x = 0; x < W; x += 40) { ctx.beginPath(); ctx.moveTo(x,0); ctx.lineTo(x,H); ctx.stroke(); }
    for (let y = 0; y < H; y += 40) { ctx.beginPath(); ctx.moveTo(0,y); ctx.lineTo(W,y); ctx.stroke(); }

    if (!state) return;

    state.food.forEach(f => drawFood(f[0], f[1]));
    state.worms.forEach(w => drawWorm(w));
    updateHud(state.worms);
  }

  function drawFood(x, y) {
    const pulse = 0.5 + 0.5 * Math.sin(tick * 0.1);
    ctx.save();
    ctx.shadowColor = '#fff';
    ctx.shadowBlur  = 6 + pulse * 8;
    ctx.fillStyle   = `hsl(${(tick * 2) % 360},100%,75%)`;
    ctx.beginPath();
    ctx.arc(x, y, 10, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }

  function drawWorm(w) {
    if (!w.body || w.body.length === 0) return;
    const color = colors[w.id] || '#fff';
    const baseAlpha = w.alive ? 1.0 : 0.3;
    const r = 6;

    ctx.save();

    // Body: overlapping circles fading from head (bright) to tail (dim)
    for (let i = w.body.length - 1; i >= 0; i--) {
      const t = 1 - i / w.body.length; // 0 at tail, 1 at head
      ctx.globalAlpha = baseAlpha * (0.25 + t * 0.75);
      ctx.fillStyle = color;
      ctx.beginPath();
      ctx.arc(w.body[i][0], w.body[i][1], r, 0, Math.PI * 2);
      ctx.fill();
    }

    if (w.alive) {
      // Head — full brightness with glow
      ctx.globalAlpha = 1.0;
      ctx.fillStyle = color;
      ctx.shadowColor = color;
      ctx.shadowBlur = 12;
      ctx.beginPath();
      ctx.arc(w.headX, w.headY, r + 1, 0, Math.PI * 2);
      ctx.fill();
      ctx.shadowBlur = 0;

      // Eyes — perpendicular to movement direction
      const dir = headDirection(w);
      const perpX = -dir.dy;
      const perpY =  dir.dx;
      ctx.globalAlpha = 1.0;
      ctx.fillStyle = '#000';
      ctx.beginPath();
      ctx.arc(w.headX + perpX * 3, w.headY + perpY * 3, 2, 0, Math.PI * 2);
      ctx.arc(w.headX - perpX * 3, w.headY - perpY * 3, 2, 0, Math.PI * 2);
      ctx.fill();
    }

    ctx.restore();
  }

  /** Returns a normalised direction vector from the worm's second body segment to its head. */
  function headDirection(w) {
    if (w.body.length < 2) return {dx: 1, dy: 0};
    const dx = w.headX - w.body[1][0];
    const dy = w.headY - w.body[1][1];
    const len = Math.sqrt(dx * dx + dy * dy) || 1;
    return {dx: dx / len, dy: dy / len};
  }

  /** Rebuild HUD only when scores / alive status change — avoids DOM churn every frame. */
  function updateHud(worms) {
    const key = worms.map(w => `${w.id}:${w.score}:${w.alive}`).join('|');
    if (key === lastHudKey) return;
    lastHudKey = key;

    const hud = document.getElementById('hud');
    hud.innerHTML = '';
    worms.forEach(w => {
      const div = document.createElement('div');
      div.className = 'hud-entry';
      div.style.color   = colors[w.id] || '#fff';
      div.style.opacity = w.alive ? '1' : '0.4';
      div.textContent   = `${names[w.id] || w.id}: ${w.score}`;
      hud.appendChild(div);
    });
  }

  function showCountdown(n) {
    const overlay  = document.getElementById('overlay');
    const title    = document.getElementById('overlay-title');
    const sub      = document.getElementById('overlay-sub');
    const scoreEl  = document.getElementById('overlay-scores');
    // Clear any leftover game-over content
    sub.textContent  = '';
    scoreEl.innerHTML = '';
    document.getElementById('back-btn')?.style && (document.getElementById('back-btn').style.display = 'none');

    if (n > 0) {
      overlay.classList.add('visible');
      title.textContent = String(n);
    } else {
      overlay.classList.remove('visible');
      title.textContent = '';
    }
  }

  function showGameOver(msg) {
    const overlay = document.getElementById('overlay');
    const title   = document.getElementById('overlay-title');
    const sub     = document.getElementById('overlay-sub');
    const scoreEl = document.getElementById('overlay-scores');
    const backBtn = document.getElementById('back-btn');

    overlay.classList.add('visible');
    title.textContent = msg.winnerName ? `\u{1F3C6} ${msg.winnerName} wins!` : "It's a draw!";
    sub.textContent   = 'Game over';
    scoreEl.innerHTML = (msg.scores || []).map((s, i) =>
      `<li>${i + 1}. ${escHtml(s.name)} \u2014 ${s.score} pts</li>`
    ).join('');

    if (backBtn) backBtn.style.display = 'inline-block';
  }

  function escHtml(s) {
    return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
  }

  return {init, setNames, update, start, stop, showCountdown, showGameOver};
})();
