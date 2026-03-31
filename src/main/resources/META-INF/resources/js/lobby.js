'use strict';

let ws = null;
let myId = null;
let roomCode = null;
let lastRoomUpdate = null; // cached so we can re-render after welcome sets myId

// Show create panel on load
document.addEventListener('DOMContentLoaded', () => showTab('create'));

function showTab(tab) {
  document.getElementById('panel-create').classList.toggle('visible', tab === 'create');
  document.getElementById('panel-join').classList.toggle('visible', tab === 'join');
  document.getElementById('tab-create').classList.toggle('active', tab === 'create');
  document.getElementById('tab-join').classList.toggle('active', tab === 'join');
}

async function createRoom() {
  const name = document.getElementById('name-create').value.trim() || 'Player';
  try {
    const res = await fetch('/api/room', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({name})
    });
    if (!res.ok) throw new Error('Server error');
    const data = await res.json();
    roomCode = data.code;
    sessionStorage.setItem('playerName', name);
    connectWs(roomCode, name);
  } catch (e) {
    showError('Could not create room. Is the server running?');
  }
}

async function joinRoom() {
  const name = document.getElementById('name-join').value.trim() || 'Player';
  const code = document.getElementById('code-input').value.trim().toUpperCase();
  if (!code || code.length !== 4) { showError('Enter a 4-letter room code'); return; }
  try {
    const res = await fetch(`/api/room/${code}`);
    if (!res.ok) throw new Error('Server error');
    const data = await res.json();
    if (!data.exists) { showError('Room not found'); return; }
    if (data.state !== 'LOBBY') { showError('Game already started'); return; }
    roomCode = code;
    sessionStorage.setItem('playerName', name);
    connectWs(code, name);
  } catch (e) {
    showError('Could not reach server.');
  }
}

function connectWs(code, name) {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws';
  ws = new WebSocket(`${proto}://${location.host}/ws/${code}`);

  ws.onopen = () => ws.send(JSON.stringify({type: 'join', name}));

  ws.onmessage = ({data}) => {
    const msg = JSON.parse(data);
    switch (msg.type) {
      case 'welcome':      handleWelcome(msg);        break;
      case 'room_update':  handleRoomUpdate(msg);     break;
      case 'countdown':    handleCountdown(msg);      break;
      case 'game_start':   redirectToGame(msg);       break;
      case 'error':        showError(msg.message);    break;
    }
  };

  ws.onclose = (e) => {
    if (e.wasClean) return; // navigating away
    showError('Disconnected from server');
  };
}

function handleWelcome(msg) {
  myId = msg.playerId;
  sessionStorage.setItem('myId', myId);
  // room_update arrived before welcome so myId was null — re-render now
  if (lastRoomUpdate) handleRoomUpdate(lastRoomUpdate);
}

function handleRoomUpdate(msg) {
  lastRoomUpdate = msg;
  const players = msg.players;

  // Show waiting room
  document.getElementById('waiting').classList.add('visible');
  document.getElementById('room-code-display').textContent = roomCode;

  const isHost = players[0]?.id === myId;

  // Render player list
  const ul = document.getElementById('player-list');
  ul.innerHTML = '';
  players.forEach(p => {
    const li = document.createElement('li');
    const meTag = p.id === myId ? ' <span class="bot-tag" style="color:#4fc3f7">YOU</span>' : '';
    const removeBtn = p.isBot && isHost
      ? `<button class="btn-sm" style="margin-left:auto" onclick="removeBot('${p.id}')">✕</button>`
      : '';
    li.innerHTML = `<span class="player-dot" style="background:${p.color}"></span>
      ${escHtml(p.name)}${meTag}
      ${p.isBot ? '<span class="bot-tag">BOT</span>' : ''}
      ${removeBtn}`;
    ul.appendChild(li);
  });

  // Host controls
  const startBtn    = document.getElementById('start-btn');
  const botControls = document.getElementById('bot-controls');
  startBtn.style.display    = isHost ? 'block' : 'none';
  botControls.style.display = isHost ? 'flex'  : 'none';
  const humans = players.filter(p => !p.isBot).length;
  const bots   = players.filter(p =>  p.isBot).length;
  // Need ≥2 humans, OR exactly 1 human with at least 1 bot
  startBtn.disabled = !(humans >= 2 || (humans === 1 && bots >= 1));
  document.getElementById('add-bot-btn').disabled = players.length >= 4;
}

function handleCountdown(msg) {
  const startBtn = document.getElementById('start-btn');
  startBtn.disabled = true;
  startBtn.textContent = msg.seconds > 0 ? `Starting in ${msg.seconds}…` : 'Starting…';
}

function redirectToGame(msg) {
  sessionStorage.setItem('gameStart', JSON.stringify(msg));
  sessionStorage.setItem('roomCode', roomCode);
  sessionStorage.setItem('wsUrl', ws.url);
  ws.onclose = null;  // prevent disconnect handler from firing during unload
  ws.onmessage = null;
  ws.close();         // explicitly close the lobby socket
  location.href = '/game.html';
}

function addBot()         { ws?.send(JSON.stringify({type: 'add_bot'})); }
function removeBot(botId) { ws?.send(JSON.stringify({type: 'remove_bot', botId})); }
function startGame()      { ws?.send(JSON.stringify({type: 'start_game'})); }

function exitRoom() {
  if (ws) {
    ws.send(JSON.stringify({type: 'leave'}));
    ws.onclose = null;
    ws.close();
    ws = null;
  }
  roomCode = null;
  myId = null;
  lastRoomUpdate = null;
  sessionStorage.removeItem('myId');
  document.getElementById('waiting').classList.remove('visible');
  showTab('create');
}

function showError(msg) {
  // Non-intrusive inline error (avoids alert())
  let el = document.getElementById('error-msg');
  if (!el) {
    el = document.createElement('p');
    el.id = 'error-msg';
    el.style.cssText = 'color:#e94560;font-size:.85rem;margin-top:.75rem;text-align:center';
    document.querySelector('.card').appendChild(el);
  }
  el.textContent = msg;
  setTimeout(() => { el.textContent = ''; }, 4000);
}

function escHtml(s) {
  return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}
