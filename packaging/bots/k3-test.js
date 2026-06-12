// K3 on-server test: two real protocol clients against the Fabric server.
// Alice claims land, Bob tries to grief it; chat, mute, economy, homes — the
// full story, over the real wire.
const mineflayer = require('mineflayer')
const { Vec3 } = require('vec3')

const HOST = '127.0.0.1'
const PORT = 25565
const VERSION = '1.21.11'

let passed = 0
let failed = 0
const check = (cond, msg) => {
  if (cond) { passed++; console.log(`  ✔ ${msg}`) }
  else { failed++; console.log(`  ✘ FAILED: ${msg}`) }
}
const sleep = (ms) => new Promise(r => setTimeout(r, ms))

function connect (username) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: HOST, port: PORT, username, version: VERSION, auth: 'offline' })
    bot.once('spawn', () => resolve(bot))
    bot.once('kicked', reason => reject(new Error(`${username} kicked: ${JSON.stringify(reason)}`)))
    bot.once('error', reject)
    setTimeout(() => reject(new Error(`${username} spawn timeout`)), 60000)
  })
}

// Collect whispers/system messages a bot sees (for command replies + chat assertions)
function tapMessages (bot) {
  bot.rockMessages = []
  bot.on('messagestr', (msg) => { bot.rockMessages.push(msg) })
}
const sawMessage = (bot, needle) => bot.rockMessages.some(m => m.includes(needle))
const clearMessages = (bot) => { bot.rockMessages.length = 0 }

async function cmd (bot, command, wait = 1200) {
  clearMessages(bot)
  bot.chat(command)
  await sleep(wait)
}

// Digs with `digger`, but takes ground truth from `observer`: a second
// client only sees block changes the SERVER broadcast — immune to the
// digger's local prediction racing a denial resync.
async function digAt (digger, observer, pos) {
  const block = digger.blockAt(pos)
  if (!block || block.name === 'air') return { ok: false, reason: 'no block client-side' }
  const before = block.name
  try {
    await digger.dig(block, 'ignore')
  } catch (e) { console.log(`  [dig] ${digger.username} at ${pos}: ${e.message}`) }
  await sleep(1500)
  const seen = observer.blockAt(pos)
  return { ok: seen && seen.name === 'air', before }
}

async function main () {
  console.log('[K3] connecting Alice…')
  const alice = await connect('Alice')
  tapMessages(alice)
  await sleep(1500)
  console.log('[K3] connecting Bob…')
  let bob = await connect('Bob')
  tapMessages(bob)
  await sleep(2000)

  console.log('[1] /rock version over the wire')
  await cmd(alice, '/rock version')
  check(sawMessage(alice, 'ROCK SUITE v1.5.0'), 'Alice received the platform version')

  console.log('[2] short alias /r works')
  await cmd(alice, '/r version')
  check(sawMessage(alice, 'ROCK SUITE v1.5.0'), '/r alias routes into the /rock tree')

  console.log('[3] permission gating before grants')
  await cmd(alice, '/rock claims claim AliceBase')
  check(sawMessage(alice, 'permission'), 'claim command denied without permission')

  // grants happen from the server console (driven by the harness runner)
  console.log('[3b] waiting for console grants (runner)…')
  await sleep(8000)

  console.log('[4] Alice claims the chunk she stands in')
  await cmd(alice, '/rock claims claim AliceBase', 2000)
  check(sawMessage(alice, 'Claimed chunk'), 'Alice claimed her chunk')

  console.log('[5] Bob digs inside the claim → protection cancels it')
  // The runner teleported Alice to (8,-60,8) and Bob to (9,-60,9): chunk (0,0),
  // standing on flat-world grass at y=-61, well within dig reach of each other.
  console.log(`  [pos] alice=${alice.entity.position} bob=${bob.entity.position}`)
  const grief = await digAt(bob, alice, new Vec3(8, -61, 9))
  check(!grief.ok, `Bob could NOT break ${grief.before || 'block'} in Alice's claim (server truth via Alice's client)`)

  console.log('[6] Alice digs her own block → allowed')
  const own = await digAt(alice, bob, new Vec3(7, -61, 8))
  check(own.ok, `Alice broke her own ${own.before}`)

  console.log('[7] trust Bob, then he can dig')
  await cmd(alice, '/rock claims trust Bob BUILD', 2000)
  check(sawMessage(alice, 'trusted as BUILD'), 'Bob trusted as BUILD')
  // Reconnect Bob: after a server-rejected dig, mineflayer's block-ack
  // sequence desyncs and silently drops the next dig. Fresh session = clean
  // state (and re-exercises the ROCK join path).
  bob.quit()
  await sleep(1500)
  bob = await connect('Bob')
  tapMessages(bob)
  console.log('[7b] waiting for console tp (runner)…')
  await sleep(5000)
  const trusted = await digAt(bob, alice, new Vec3(9, -61, 8))
  check(trusted.ok, 'trusted Bob can now dig in the claim (fresh block, server truth)')

  console.log('[8] chat flows, then mute silences Bob')
  clearMessages(alice)
  bob.chat('hello from bob')
  await sleep(1500)
  check(sawMessage(alice, 'hello from bob'), 'Alice saw Bob\'s chat')
  console.log('[8b] waiting for console mute (runner)…')
  await sleep(6000)
  clearMessages(alice)
  bob.chat('this should be silenced')
  await sleep(1500)
  check(!sawMessage(alice, 'this should be silenced'), 'muted Bob\'s chat never reached Alice')

  console.log('[9] essentials: sethome + homes via short alias')
  await cmd(alice, '/sethome base', 2000)
  check(sawMessage(alice, "Home 'base' set"), 'Alice set a home via /sethome alias')
  await cmd(alice, '/homes')
  check(sawMessage(alice, 'base'), '/homes lists it')

  console.log('[10] economy: balance via alias')
  await cmd(alice, '/balance')
  check(sawMessage(alice, 'Balance:'), '/balance alias reports formatted balance')

  console.log(`\n[K3] BOT RESULTS: ${passed} passed, ${failed} failed`)
  alice.quit(); bob.quit()
  await sleep(1000)
  process.exit(failed === 0 ? 0 : 1)
}

main().catch(e => { console.error('[K3] FATAL', e); process.exit(2) })
