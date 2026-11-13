'use strict';
// 1.8.8 client checks for LegacyFolia through ViaVersion, ViaBackwards and ViaRewind.
// Usage: node test.js --jar <paperclip jar> [--port 25621] [--cache <dir>] [--out <json>] [--keep]
// It downloads the Via plugins, starts the server in a fresh temp folder (offline mode, EULA accepted
// only there), joins two 1.8.8 bots and writes the results as JSON.

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawn } = require('node:child_process');
const mineflayer = require('mineflayer');

const args = parseArgs(process.argv.slice(2));
const JAR = path.resolve(args.jar || '');
const PORT = Number(args.port || 25621);
const CACHE = path.resolve(args.cache || path.join(__dirname, '..', '.cache'));
const OUT = path.resolve(args.out || path.join(__dirname, '..', 'results', 'bot18.json'));
const KEEP = Boolean(args.keep);

const PLUGINS = [
  ['ViaVersion', '5.12.2', 'ViaVersion/ViaVersion'],
  ['ViaBackwards', '5.12.2', 'ViaVersion/ViaBackwards'],
  ['ViaRewind', '4.2.0', 'ViaVersion/ViaRewind'],
];

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const checks = [];
const notes = {};
const serverLog = [];

function parseArgs(list) {
  const out = {};
  for (let i = 0; i < list.length; i++) {
    if (!list[i].startsWith('--')) continue;
    const key = list[i].slice(2);
    if (i + 1 < list.length && !list[i + 1].startsWith('--')) out[key] = list[++i];
    else out[key] = true;
  }
  return out;
}

function record(name, pass, detail) {
  checks.push({ name, result: pass ? 'PASS' : 'FAIL', detail });
  console.log(`${pass ? 'PASS' : 'FAIL'}  ${name}  ${detail}`);
}

async function download(url, file) {
  const res = await fetch(url, { redirect: 'follow' });
  if (!res.ok) throw new Error(`${url}: HTTP ${res.status}`);
  fs.writeFileSync(file, Buffer.from(await res.arrayBuffer()));
}

async function ensurePlugins() {
  const dir = path.join(CACHE, 'plugins');
  fs.mkdirSync(dir, { recursive: true });
  const files = [];
  for (const [name, version, repo] of PLUGINS) {
    const file = path.join(dir, `${name}-${version}.jar`);
    if (!fs.existsSync(file) || fs.statSync(file).size < 100000) {
      const url = `https://github.com/${repo}/releases/download/${version}/${name}-${version}.jar`;
      console.log(`downloading ${url}`);
      await download(url, file);
    }
    files.push(file);
  }
  return files;
}

function startServer(plugins) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'lf-bot18-'));
  fs.mkdirSync(path.join(dir, 'plugins'));
  for (const p of plugins) fs.copyFileSync(p, path.join(dir, 'plugins', path.basename(p)));
  fs.writeFileSync(
    path.join(dir, 'server.properties'),
    [
      `server-port=${PORT}`,
      'online-mode=false',
      'enable-rcon=false',
      'level-type=minecraft\\:flat',
      'generate-structures=false',
      'spawn-monsters=false',
      'difficulty=normal',
      'gamemode=survival',
      'pvp=true',
      '',
    ].join('\n'),
  );
  const proc = spawn('java', ['-Xmx2G', '-Dcom.mojang.eula.agree=true', '-jar', JAR, 'nogui'], {
    cwd: dir,
    stdio: ['pipe', 'pipe', 'pipe'],
  });
  const onData = (buf) => {
    for (const line of buf.toString().split(/\r?\n/)) if (line) serverLog.push(line);
  };
  proc.stdout.on('data', onData);
  proc.stderr.on('data', onData);
  return { dir, proc };
}

async function waitForLog(re, timeoutMs, from = 0) {
  const end = Date.now() + timeoutMs;
  while (Date.now() < end) {
    for (let i = from; i < serverLog.length; i++) if (re.test(serverLog[i])) return serverLog[i];
    await sleep(250);
  }
  return null;
}

function makeBot(username) {
  const bot = mineflayer.createBot({
    host: '127.0.0.1',
    port: PORT,
    username,
    version: '1.8.8',
    auth: 'offline',
    checkTimeoutInterval: 120000,
  });
  bot.lastHealth = null;
  bot.healthLog = [];
  bot.on('health', () => {
    bot.healthLog.push({ t: Date.now(), health: bot.health });
  });
  bot.on('error', (e) => console.log(`[${username}] error ${e.message}`));
  bot.on('kicked', (r) => console.log(`[${username}] kicked ${String(r).slice(0, 200)}`));
  return bot;
}

function spawned(bot, ms = 60000) {
  return new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error('spawn timeout')), ms);
    bot.once('spawn', () => {
      clearTimeout(t);
      resolve();
    });
    bot.once('end', () => {
      clearTimeout(t);
      reject(new Error('connection ended before spawn'));
    });
  });
}

function say(proc, line) {
  proc.stdin.write(line + '\n');
}

function nbtSimplify(n) {
  if (n === null || n === undefined) return n;
  if (Array.isArray(n)) return n.map(nbtSimplify);
  if (typeof n === 'object') {
    if ('value' in n && 'type' in n) {
      if (n.type === 'list') return (n.value.value || []).map(nbtSimplify);
      return nbtSimplify(n.value);
    }
    const o = {};
    for (const k of Object.keys(n)) o[k] = nbtSimplify(n[k]);
    return o;
  }
  return n;
}

async function waitHealthDrop(bot, since, ms) {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    for (let i = 0; i < bot.healthLog.length; i++) {
      const cur = bot.healthLog[i];
      const prev = i > 0 ? bot.healthLog[i - 1].health : 20;
      if (cur.t >= since && cur.health < prev) return { drop: prev - cur.health, health: cur.health };
    }
    await sleep(25);
  }
  return null;
}

// Knockback pushes the victim out of reach, so put it back next to the attacker, both facing each other.
async function regroup(proc, attacker, victim) {
  say(proc, `execute at ${attacker.username} run tp ${victim.username} ~ ~ ~1.2`);
  await sleep(600);
  await attacker.lookAt(victim.entity.position.offset(0, 1.6, 0), true);
  await victim.lookAt(attacker.entity.position.offset(0, 1.6, 0), true);
}

async function healFull(proc, bot, name) {
  for (let i = 0; i < 20 && bot.health < 20; i++) {
    say(proc, `effect give ${name} minecraft:instant_health 1 5 true`);
    await sleep(500);
  }
  await sleep(300);
}

async function hitUntilDamage(proc, attacker, victim, victimName, tries = 15) {
  for (let i = 0; i < tries; i++) {
    await healFull(proc, victim, victimName);
    await regroup(proc, attacker, victim);
    const since = Date.now();
    attacker.attack(attacker.players[victimName].entity);
    const r = await waitHealthDrop(victim, since, 1200);
    if (r) return r;
    await sleep(500);
  }
  return null;
}

async function main() {
  if (!args.jar || !fs.existsSync(JAR)) throw new Error('pass --jar <paperclip jar>');
  const plugins = await ensurePlugins();
  const { dir, proc } = startServer(plugins);
  notes.serverDir = dir;
  notes.jar = path.basename(JAR);
  notes.plugins = plugins.map((p) => path.basename(p));
  notes.port = PORT;
  let bots = [];
  try {
    const done = await waitForLog(/Done \(/, 360000);
    if (!done) throw new Error('server did not finish starting');
    notes.serverStarted = done.replace(/^\[[^\]]*\]\s*/, '');
    const A = 'LF18A';
    const B = 'LF18B';
    const a = makeBot(A);
    const b = makeBot(B);
    bots = [a, b];
    let joined = true;
    try {
      await Promise.all([spawned(a), spawned(b)]);
    } catch (e) {
      joined = false;
      record('join', false, e.message);
    }
    if (joined) {
      const loginLine = serverLog.find((l) => l.includes(`${A}[/`));
      record('join', true, `both bots spawned as ${a.version}${loginLine ? '' : ' (login line not found)'}`);
      await sleep(1500);
      say(proc, `op ${A}`);
      say(proc, `op ${B}`);
      await sleep(1500);
      for (const n of [A, B]) {
        say(proc, `gamemode survival ${n}`);
        say(proc, `clear ${n}`);
        say(proc, `give ${n} minecraft:diamond_sword`);
      }
      say(proc, `execute at ${A} run tp ${B} ~ ~ ~1.2`);
      await sleep(2500);
      const sword = (bot) => bot.inventory.items().find((i) => i.name === 'diamond_sword');
      notes.swordA = !!sword(a);
      notes.swordB = !!sword(b);
      if (sword(a)) await a.equip(sword(a), 'hand');
      if (sword(b)) await b.equip(sword(b), 'hand');
      await sleep(500);
      const dist = a.entity.position.distanceTo(b.entity.position);
      notes.distance = Number(dist.toFixed(2));

      // Melee: a diamond sword deals 8 in 1.8 (4 for the sword plus 3 for diamond plus 1 of the fist) (no cooldown, no attribute scaling).
      await a.lookAt(b.entity.position.offset(0, 1.6, 0), true);
      const first = await hitUntilDamage(proc, a, b, B);
      record(
        'melee_damage',
        !!first && Math.abs(first.drop - 8) < 0.01,
        first ? `diamond sword dealt ${first.drop} (expected 8)` : 'no damage received',
      );

      // No attack cooldown. A diamond axe is used because its 26.3 cooldown is a full second: a hit at ~550 ms would
      // be scaled down to roughly 3 if the cooldown leaked into 1.8 combat, while 1.8 deals its flat 7 every time
      // (a sword's 0.625 s cooldown is already over at 600 ms, so a sword could not show the difference).
      say(proc, `give ${A} minecraft:diamond_axe`);
      await sleep(600);
      const axe = a.inventory.items().find((i) => i.name === 'diamond_axe');
      if (axe) await a.equip(axe, 'hand');
      await sleep(500);
      await healFull(proc, b, B);
      await regroup(proc, a, b);
      const s1 = Date.now();
      a.attack(a.players[B].entity);
      const d1 = await waitHealthDrop(b, s1, 1200);
      await sleep(Math.max(0, 550 - (Date.now() - s1)));
      const s2 = Date.now();
      a.attack(a.players[B].entity);
      const d2 = await waitHealthDrop(b, s2, 1200);
      record(
        'no_attack_cooldown',
        !!d1 && !!d2 && Math.abs(d1.drop - 7) < 0.01 && Math.abs(d2.drop - 7) < 0.01,
        `diamond axe hits ${d1 ? d1.drop : 'none'} then ${d2 ? d2.drop : 'none'} at ~550 ms (expected 7 and 7; a leaked 26.3 cooldown gives about 3 on the second)`,
      );
      if (sword(a)) await a.equip(sword(a), 'hand');
      await sleep(300);

      // Sword blocking: B right-clicks with the sword facing A; damage becomes (1 + 8) / 2 = 4.5.
      await healFull(proc, b, B);
      await regroup(proc, a, b);
      await b.lookAt(a.entity.position.offset(0, 1.6, 0), true);
      await a.lookAt(b.entity.position.offset(0, 1.6, 0), true);
      b.activateItem();
      await sleep(600);
      const seenB = a.players[B] && a.players[B].entity;
      const flags = seenB && seenB.metadata ? seenB.metadata[0] : undefined;
      const blockingFlag = typeof flags === 'number' ? (flags & 0x10) !== 0 : null;
      const sb = Date.now();
      a.attack(a.players[B].entity);
      const blocked = await waitHealthDrop(b, sb, 1500);
      b.deactivateItem();
      record(
        'sword_blocking',
        !!blocked && Math.abs(blocked.drop - 4.5) < 0.01,
        `blocked hit dealt ${blocked ? blocked.drop : 'none'} (expected 4.5); using-item flag seen by A: ${blockingFlag}`,
      );
      notes.blockingFlagSeenByA = blockingFlag;

      // Tooltip: the item as the 1.8 client receives it.
      const held = sword(b);
      const nbt = held ? nbtSimplify(held.nbt) : null;
      notes.swordNbt = nbt;
      // A 1.8 client draws "+N Attack Damage" straight from the attackDamage modifier, so the diamond sword's
      // modifier must be 7 for the tooltip to read +7, the 1.8 figure (hits land for 8: 7 plus 1 of the fist).
      const mods = (nbt && Array.isArray(nbt.AttributeModifiers) ? nbt.AttributeModifiers : []).filter(
        (m) => m.AttributeName === 'generic.attackDamage',
      );
      const amount = mods.length ? Number(mods[0].Amount) : null;
      notes.tooltipAttackDamageAmount = amount;
      record(
        'tooltip_damage',
        amount === 7,
        amount === null
          ? 'no attackDamage modifier in the item NBT the 1.8 client receives'
          : `attackDamage modifier ${amount} on the 1.8 client; a 1.8 client draws it as +${amount} (it adds nothing for the fist), expected +7`,
      );
    }
  } catch (e) {
    record('harness', false, e.message);
  } finally {
    for (const bot of bots) {
      try {
        bot.quit();
      } catch {
        // ignore
      }
    }
    await sleep(500);
    say(proc, 'stop');
    await Promise.race([new Promise((r) => proc.once('exit', r)), sleep(40000)]);
    if (proc.exitCode === null) proc.kill();
    const failed = checks.some((c) => c.result === 'FAIL');
    notes.serverLogTail = serverLog.slice(-40);
    fs.mkdirSync(path.dirname(OUT), { recursive: true });
    fs.writeFileSync(
      OUT,
      JSON.stringify(
        { suite: 'bot18', date: new Date().toISOString(), passed: checks.length > 0 && !failed, checks, notes },
        null,
        2,
      ),
    );
    if (!failed && !KEEP) fs.rmSync(notes.serverDir, { recursive: true, force: true });
    else console.log(`server folder kept: ${notes.serverDir}`);
    console.log(`wrote ${OUT}`);
    process.exit(failed || checks.length === 0 ? 1 : 0);
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(2);
});
