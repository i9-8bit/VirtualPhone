const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const WebSocket = require('ws');

const PORT = Number(process.env.PORT || 8080);
const HOST = process.env.HOST || '0.0.0.0';
const users = new Map();
const byNumber = new Map();
const sockets = new Map();
const calls = new Map();
const messages = [];

function json(res, code, body) {
  const data = JSON.stringify(body);
  res.writeHead(code, {'Content-Type':'application/json','Content-Length':Buffer.byteLength(data)});
  res.end(data);
}
function body(req) { return new Promise((resolve,reject)=>{let b=''; req.on('data',c=>b+=c); req.on('end',()=>{try{resolve(b?JSON.parse(b):{})}catch(e){reject(e)}}); req.on('error',reject);}); }
function id(){ return crypto.randomBytes(10).toString('hex'); }
function number(){ let n; do { n='+1 555 '+String(Math.floor(100+Math.random()*900))+'-'+String(Math.floor(1000+Math.random()*9000)); } while(byNumber.has(n)); return n; }
function send(ws, obj){ if(ws && ws.readyState===WebSocket.OPEN) ws.send(JSON.stringify(obj)); }
function findUserByNumber(n){ const uid=byNumber.get(n); return uid ? users.get(uid) : null; }
function callForUser(uid){ for(const c of calls.values()) if(c.a===uid||c.b===uid) return c; return null; }
function peerOf(c, uid){ return c.a===uid?c.b:c.a; }

const server=http.createServer(async (req,res)=>{
  try {
    if(req.method==='GET' && req.url==='/'){return json(res,200,{service:'VirtualPhone',version:'0.3.0',audio:'legacy-pcm-relay',note:'app-only identities; not PSTN'});}
    if(req.method==='POST' && req.url==='/api/register'){
      const b=await body(req); const uid=id(); const u={id:uid,number:number(),name:String(b.name||'User').slice(0,40)}; users.set(uid,u); byNumber.set(u.number,uid); return json(res,200,u);
    }
    if(req.method==='GET' && req.url.startsWith('/api/find?')){
      const n=new URL(req.url,'http://x').searchParams.get('number'); const u=findUserByNumber(n); return json(res,u?200:404,u||{error:'not found'});
    }
    if(req.method==='GET' && req.url.startsWith('/api/messages/')){
      const uid=req.url.split('/').pop(); return json(res,200,messages.filter(m=>m.from===uid||m.to===uid));
    }
    if(req.method==='POST' && req.url==='/api/message'){
      const b=await body(req); if(!users.has(b.from)||!users.has(b.to)) return json(res,400,{error:'unknown user'}); const m={id:id(),from:b.from,to:b.to,text:String(b.text||'').slice(0,4000),createdAt:Date.now(),delivered:false,read:false}; messages.push(m); const s=sockets.get(m.to); send(s,{type:'message',message:m}); return json(res,200,m);
    }
    if(req.method==='POST' && req.url==='/api/message/read'){
      const b=await body(req); for(const m of messages) if(m.id===b.messageId) m.read=true; return json(res,200,{ok:true});
    }
    if(req.method==='GET' && req.url.startsWith('/web/')){
      const f=path.join(__dirname,'..',req.url); if(fs.existsSync(f)) return fs.createReadStream(f).pipe(res);
    }
    return json(res,404,{error:'not found'});
  } catch(e){ return json(res,500,{error:'server error'}); }
});

const wss=new WebSocket.Server({server,path:'/ws'});
wss.on('connection',ws=>{
  ws.binaryType='arraybuffer';
  ws.on('message',(data,isBinary)=>{
    try {
      if(isBinary){
        const c=ws.callId ? calls.get(ws.callId) : null; if(!c) return;
        const peer=sockets.get(peerOf(c,ws.userId)); if(peer && peer.readyState===WebSocket.OPEN) peer.send(data,{binary:true});
        return;
      }
      const m=JSON.parse(data.toString());
      if(m.type==='auth'){ if(!users.has(m.userId)){send(ws,{type:'error',error:'auth'});return;} ws.userId=m.userId; sockets.set(m.userId,ws); send(ws,{type:'ready',userId:m.userId}); return; }
      if(!ws.userId) return;
      if(m.type==='typing'){ send(sockets.get(m.to),{type:'typing',from:ws.userId,typing:!!m.typing}); return; }
      if(m.type==='call_start'){
        const target=users.get(m.to); if(!target) return send(ws,{type:'call_error',error:'unknown user'});
        if(callForUser(ws.userId)||callForUser(m.to)) return send(ws,{type:'call_error',error:'busy'});
        const cid=id(); const c={id:cid,a:ws.userId,b:m.to,state:'ringing',createdAt:Date.now()}; calls.set(cid,c); ws.callId=cid; send(sockets.get(m.to),{type:'call_incoming',callId:cid,from:ws.userId}); send(ws,{type:'call_state',callId:cid,state:'ringing'}); return;
      }
      if(m.type==='call_answer'){
        const c=calls.get(m.callId); if(!c||peerOf(c,ws.userId)!==m.from) return; c.state=m.accept?'active':'ended'; if(m.accept){ws.callId=c.id; const p=sockets.get(peerOf(c,ws.userId)); if(p) p.callId=c.id;} send(sockets.get(peerOf(c,ws.userId)),{type:'call_state',callId:c.id,state:c.state,from:ws.userId}); if(!m.accept){calls.delete(c.id); ws.callId=null;} return;
      }
      if(m.type==='call_end'){
        const c=calls.get(m.callId); if(!c) return; const p=sockets.get(peerOf(c,ws.userId)); send(p,{type:'call_state',callId:c.id,state:'ended',from:ws.userId}); calls.delete(c.id); ws.callId=null; if(p)p.callId=null; return;
      }
    } catch(e) { send(ws,{type:'error',error:'bad packet'}); }
  });
  ws.on('close',()=>{if(ws.userId && sockets.get(ws.userId)===ws)sockets.delete(ws.userId); if(ws.callId){const c=calls.get(ws.callId); if(c){send(sockets.get(peerOf(c,ws.userId)),{type:'call_state',callId:c.id,state:'ended'});calls.delete(c.id);}}});
});
server.listen(PORT,HOST,()=>console.log(`VirtualPhone 0.3 listening on ${HOST}:${PORT}`));
