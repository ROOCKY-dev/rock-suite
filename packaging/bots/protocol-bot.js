// K5 "real client check": a protocol-aware client that exercises rock-protocol
// over the live rock:protocol custom-payload channel against a real server.
// Speaks the exact ProtocolCodec wire format (length-prefixed, tagged), so it
// proves the on-the-wire round trip a future rock-client will rely on.
const mineflayer = require('mineflayer')

const HOST = '127.0.0.1'
const PORT = parseInt(process.env.PROTO_PORT || '25565', 10)
const VERSION = '1.21.11'
const CHANNEL = 'rock:protocol'

const sleep = ms => new Promise(r => setTimeout(r, ms))

// --- ProtocolCodec mirror (Java DataOutputStream layout) --------------------
const int32 = n => { const b = Buffer.alloc(4); b.writeInt32BE(n); return b }
const utf = s => { const b = Buffer.from(s, 'utf8'); const len = Buffer.alloc(2); len.writeUInt16BE(b.length); return Buffer.concat([len, b]) }

function encodeHello (version, caps) {
  const parts = [Buffer.from([0]), int32(version), int32(caps.length)]
  for (const c of caps) parts.push(utf(c))
  return Buffer.concat(parts)
}
function encodeIntent (type, fields) {
  const keys = Object.keys(fields)
  const parts = [Buffer.from([3]), utf(type), int32(keys.length)]
  for (const k of keys) { parts.push(utf(k)); parts.push(utf(fields[k])) }
  return Buffer.concat(parts)
}
function decode (buf) {
  let o = 0
  const kind = buf.readUInt8(o); o += 1
  const readUTF = () => { const len = buf.readUInt16BE(o); o += 2; const s = buf.toString('utf8', o, o + len); o += len; return s }
  const readInt = () => { const n = buf.readInt32BE(o); o += 4; return n }
  if (kind === 1) { const v = readInt(); const n = readInt(); const caps = []; for (let i = 0; i < n; i++) caps.push(readUTF()); return { type: 'welcome', version: v, caps } }
  if (kind === 2) { const proj = readUTF(); const n = readInt(); const fields = {}; for (let i = 0; i < n; i++) { const k = readUTF(); fields[k] = readUTF() } return { type: 'projection', proj, fields } }
  return { type: 'kind' + kind }
}

// --- checks -----------------------------------------------------------------
const results = []
const check = (cond, msg) => { results.push(cond); console.log((cond ? '  ✓ ' : '  ✗ ') + msg) }

const bot = mineflayer.createBot({ host: HOST, port: PORT, username: 'ProtoBot', version: VERSION, auth: 'offline' })
const inbox = []
bot._client.on('custom_payload', p => { if (p.channel === CHANNEL) { try { inbox.push(decode(p.data)) } catch (e) { /* ignore */ } } })
bot.once('kicked', r => { console.log('[protocol] kicked: ' + JSON.stringify(r)); process.exit(2) })
bot.once('error', e => { console.log('[protocol] error: ' + e.message); process.exit(3) })
setTimeout(() => { console.log('[protocol] TIMEOUT'); process.exit(4) }, 90000)

bot.once('spawn', async () => {
  console.log('[protocol] spawned; registering channel')
  // Declare the channel so the server's ServerPlayNetworking may send to us.
  bot._client.write('custom_payload', { channel: 'minecraft:register', data: Buffer.from(CHANNEL + '\0', 'utf8') })
  // The player record now exists; let the orchestrator grant rock.client.*
  // before we handshake, so capability negotiation has something to grant.
  console.log('AWAITING_GRANTS')
  await sleep(5000)

  // 1) Handshake: Hello (request CLAIMS+WALLET, advertise a newer version).
  bot._client.write('custom_payload', { channel: CHANNEL, data: encodeHello(5, ['CLAIMS', 'WALLET']) })
  await sleep(1200)
  const welcome = inbox.find(m => m.type === 'welcome')
  check(!!welcome, 'received Welcome over rock:protocol')
  if (welcome) {
    check(welcome.version === 1, 'server negotiated protocol down to v1 (got v' + welcome.version + ')')
    check(JSON.stringify(welcome.caps) === JSON.stringify(['CLAIMS', 'WALLET']), 'granted capabilities ' + JSON.stringify(welcome.caps))
  }

  // 2) Keepalive intent: session.ping -> session.pong echoing the nonce.
  bot._client.write('custom_payload', { channel: CHANNEL, data: encodeIntent('session.ping', { nonce: 'pong-42' }) })
  await sleep(900)
  const pong = inbox.find(m => m.type === 'projection' && m.proj === 'session.pong')
  check(!!pong, 'received session.pong projection')
  if (pong) check(pong.fields.nonce === 'pong-42', 'pong echoed the client nonce')

  // 3) Domain intent: claims.list -> claim.list.end (count 0, ProtoBot owns none).
  bot._client.write('custom_payload', { channel: CHANNEL, data: encodeIntent('claims.list', {}) })
  await sleep(1200)
  const end = inbox.find(m => m.type === 'projection' && m.proj === 'claim.list.end')
  check(!!end, 'received claim.list.end (inbound intent -> ClaimService -> outbound)')

  const ok = results.length >= 5 && results.every(Boolean)
  console.log(ok ? '[protocol] ALL CHECKS PASSED' : '[protocol] FAILURES PRESENT')
  bot.quit()
  process.exit(ok ? 0 : 1)
})
