const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const WebSocket = require('ws');

const PORT = Number(process.env.PORT || 8080);
const HOST = process.env.HOST || '0.0.0.0';
const DATA_FILE = path.join(__dirname, 'data.json');
const users = new Map();
const byNumber = new Map();
const sockets = new Map();
const calls = new Map();
let messages = [];

function loadData() {
  try {
    const data = JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
    (data.users || []).forEach(function (u) {
      users.set(u.id, u);
      byNumber.set(u.number, u.id);
    });
    messages = Array.isArray(data.messages) ? data.messages : [];
  } catch (e) {
    if (e.code !== 'ENOENT') console.error('Could not load data.json:', e.message);
  }
}
function saveData() {
  const temp = DATA_FILE + '.tmp';
  fs.writeFileSync(temp, JSON.stringify({
    users: Array.from(users.values()),
    messages: messages
  }, null, 2));
  fs.renameSync(temp, DATA_FILE);
}
loadData();

function json(res, code, body) {
  const data = JSON.stringify(body);
  res.writeHead(code, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(data),
    'Cache-Control': 'no-store'
  });
  res.end(data);
}
function body(req) {
  return new Promise(function (resolve, reject) {
    let b = '';
    req.on('data', function (c) {
      b += c;
      if (b.length > 100000) {
        reject(new Error('request too large'));
        req.destroy();
      }
    });
    req.on('end', function () {
      try { resolve(b ? JSON.parse(b) : {}); }
      catch (e) { reject(e); }
    });
    req.on('error', reject);
  });
}
function id() { return crypto.randomBytes(10).toString('hex'); }
function number() {
  let n;
  do {
    n = '+1 555 ' + String(Math.floor(100 + Math.random() * 900)) +
      '-' + String(Math.floor(1000 + Math.random() * 9000));
  } while (byNumber.has(n));
  return n;
}
function send(ws, obj) {
  if (ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(obj));
}
function findUserByNumber(n) {
  const uid = byNumber.get(String(n || '').trim());
  return uid ? users.get(uid) : null;
}
function callForUser(uid) {
  for (const c of calls.values()) {
    if (c.a === uid || c.b === uid) return c;
  }
  return null;
}
function peerOf(c, uid) { return c.a === uid ? c.b : c.a; }
function messageEvent(m) {
  return { type: 'message_receive', from: m.from, to: m.to, text: m.text, message: m };
}
function createMessage(from, to, text) {
  if (!users.has(from) || !users.has(to)) return { error: 'Unknown user. Check the recipient number.' };
  const cleanText = String(text || '').trim().slice(0, 4000);
  if (!cleanText) return { error: 'Message cannot be empty.' };
  const m = {
    id: id(),
    from: from,
    to: to,
    text: cleanText,
    createdAt: Date.now(),
    delivered: false,
    read: false
  };
  messages.push(m);
  const targetSocket = sockets.get(to);
  if (targetSocket && targetSocket.readyState === WebSocket.OPEN) {
    send(targetSocket, messageEvent(m));
    m.delivered = true;
  }
  saveData();
  return { message: m };
}

const webRoot = path.join(__dirname, '..', 'web');
const staticFiles = {
  '/': 'index.html',
  '/index.html': 'index.html',
  '/app.js': 'app.js',
  '/style.css': 'style.css',
  '/web/': 'index.html',
  '/web/index.html': 'index.html',
  '/web/app.js': 'app.js',
  '/web/style.css': 'style.css'
};
const mime = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml'
};
function serveWeb(pathname, res) {
  const fileName = staticFiles[pathname];
  if (!fileName) return false;
  const filePath = path.join(webRoot, fileName);
  if (!filePath.startsWith(webRoot + path.sep)) return false;
  fs.readFile(filePath, function (err, data) {
    if (err) return json(res, 404, { error: 'Web file not found.' });
    res.writeHead(200, {
      'Content-Type': mime[path.extname(filePath)] || 'application/octet-stream',
      'Cache-Control': 'no-cache'
    });
    res.end(data);
  });
  return true;
}

const server = http.createServer(async function (req, res) {
  try {
    const url = new URL(req.url, 'http://localhost');
    const pathname = url.pathname;

    if (req.method === 'GET' && serveWeb(pathname, res)) return;
    if (req.method === 'GET' && pathname === '/api/status') {
      return json(res, 200, {
        service: 'VirtualPhone',
        version: '0.4.0',
        users: users.size,
        messages: messages.length,
        audio: '16kHz PCM relay',
        note: 'VirtualPhone-to-VirtualPhone only; not PSTN'
      });
    }
    if (req.method === 'POST' && pathname === '/api/register') {
      const b = await body(req);
      const uid = id();
      const u = {
        id: uid,
        number: number(),
        name: String(b.name || 'User').trim().slice(0, 40) || 'User'
      };
      users.set(uid, u);
      byNumber.set(u.number, uid);
      saveData();
      return json(res, 200, u);
    }
    if (req.method === 'GET' && pathname === '/api/find') {
      const u = findUserByNumber(url.searchParams.get('number'));
      return json(res, u ? 200 : 404, u || { error: 'Number not found.' });
    }
    if (req.method === 'GET' && pathname.startsWith('/api/messages/')) {
      const uid = decodeURIComponent(pathname.slice('/api/messages/'.length));
      if (!users.has(uid)) return json(res, 404, { error: 'Unknown account.' });
      return json(res, 200, messages.filter(function (m) {
        return m.from === uid || m.to === uid;
      }));
    }
    if (req.method === 'POST' && pathname === '/api/message') {
      const b = await body(req);
      const result = createMessage(String(b.from || ''), String(b.to || ''), b.text);
      if (result.error) return json(res, 400, { error: result.error });
      return json(res, 200, result.message);
    }
    if (req.method === 'POST' && pathname === '/api/message/read') {
      const b = await body(req);
      const message = messages.find(function (m) { return m.id === b.messageId; });
      if (message) {
        message.read = true;
        saveData();
      }
      return json(res, 200, { ok: true });
    }
    return json(res, 404, { error: 'Not found.' });
  } catch (e) {
    console.error('Request error:', e);
    return json(res, 500, { error: 'Server error.' });
  }
});

const wss = new WebSocket.Server({ server: server, path: '/ws' });
wss.on('connection', function (ws) {
  ws.binaryType = 'arraybuffer';
  ws.on('message', function (data, isBinary) {
    try {
      if (isBinary) {
        const c = ws.callId ? calls.get(ws.callId) : null;
        if (!c || !ws.userId) return;
        const peer = sockets.get(peerOf(c, ws.userId));
        if (peer && peer.readyState === WebSocket.OPEN) {
          peer.send(data, { binary: true });
        }
        return;
      }

      const m = JSON.parse(data.toString());
      if (m.type === 'auth') {
        if (!users.has(m.userId)) {
          send(ws, { type: 'error', error: 'auth', message: 'Account not found. Register again.' });
          return;
        }
        const old = sockets.get(m.userId);
        if (old && old !== ws) old.close(4001, 'Replaced by a newer connection');
        ws.userId = m.userId;
        sockets.set(m.userId, ws);
        send(ws, { type: 'ready', userId: m.userId });
        return;
      }
      if (!ws.userId) {
        send(ws, { type: 'error', error: 'auth', message: 'Authenticate first.' });
        return;
      }

      if (m.type === 'message_send') {
        const result = createMessage(ws.userId, String(m.to || ''), m.text);
        if (result.error) {
          send(ws, { type: 'message_error', message: result.error });
        } else {
          send(ws, { type: 'message_sent', message: result.message });
        }
        return;
      }
      if (m.type === 'typing') {
        send(sockets.get(m.to), { type: 'typing', from: ws.userId, typing: !!m.typing });
        return;
      }
      if (m.type === 'call_start') {
        const target = users.get(String(m.to || ''));
        if (!target) return send(ws, { type: 'call_error', error: 'Unknown user.' });
        if (!sockets.has(target.id)) return send(ws, { type: 'call_error', error: 'That user is offline.' });
        if (callForUser(ws.userId) || callForUser(target.id)) {
          return send(ws, { type: 'call_error', error: 'One of the users is already in a call.' });
        }
        const cid = id();
        const c = { id: cid, a: ws.userId, b: target.id, state: 'ringing', createdAt: Date.now() };
        calls.set(cid, c);
        ws.callId = cid;
        send(sockets.get(target.id), { type: 'call_incoming', callId: cid, from: ws.userId });
        send(ws, { type: 'call_state', callId: cid, state: 'ringing', to: target.id });
        return;
      }
      if (m.type === 'call_answer') {
        const c = calls.get(m.callId);
        if (!c || (c.a !== ws.userId && c.b !== ws.userId)) return;
        const peerId = peerOf(c, ws.userId);
        if (m.accept) {
          c.state = 'active';
          ws.callId = c.id;
          const peer = sockets.get(peerId);
          if (peer) peer.callId = c.id;
          send(ws, { type: 'call_state', callId: c.id, state: 'active', peer: peerId });
          send(peer, { type: 'call_state', callId: c.id, state: 'active', peer: ws.userId });
        } else {
          calls.delete(c.id);
          ws.callId = null;
          const peer = sockets.get(peerId);
          if (peer) peer.callId = null;
          send(ws, { type: 'call_state', callId: c.id, state: 'ended', reason: 'declined' });
          send(peer, { type: 'call_state', callId: c.id, state: 'ended', reason: 'declined' });
        }
        return;
      }
      if (m.type === 'call_end') {
        const c = calls.get(m.callId);
        if (!c || (c.a !== ws.userId && c.b !== ws.userId)) return;
        const peer = sockets.get(peerOf(c, ws.userId));
        calls.delete(c.id);
        ws.callId = null;
        if (peer) peer.callId = null;
        send(ws, { type: 'call_state', callId: c.id, state: 'ended' });
        send(peer, { type: 'call_state', callId: c.id, state: 'ended' });
      }
    } catch (e) {
      send(ws, { type: 'error', error: 'bad_packet', message: e.message });
    }
  });
  ws.on('close', function () {
    if (ws.userId && sockets.get(ws.userId) === ws) sockets.delete(ws.userId);
    if (ws.callId) {
      const c = calls.get(ws.callId);
      if (c) {
        const peer = sockets.get(peerOf(c, ws.userId));
        calls.delete(c.id);
        if (peer) {
          peer.callId = null;
          send(peer, { type: 'call_state', callId: c.id, state: 'ended', reason: 'peer disconnected' });
        }
      }
    }
  });
});

server.listen(PORT, HOST, function () {
  console.log('VirtualPhone 0.4 listening on ' + HOST + ':' + PORT);
});
