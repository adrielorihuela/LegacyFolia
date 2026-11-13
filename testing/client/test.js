'use strict';
// Real client smoke test: HeadlessMC with Fabric 26.3 in offline mode joins a LegacyFolia server,
// reads a chat line the server says, sends one, and leaves. No Microsoft or Minecraft account is used.
// Usage: node test.js --jar <paperclip jar> [--port 25622] [--cache <dir>] [--out <json>] [--keep]
// Needs Node 24 and Java 25. Downloads (headlessmc.jar and hmc-specifics from GitHub releases, and the
// game files that HeadlessMC itself fetches from Mojang's servers) go to <cache>/hmc.

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawn, spawnSync } = require('node:child_process');

const args = parseArgs(process.argv.slice(2));
const JAR = path.resolve(args.jar || '');
const PORT = Number(args.port || 25622);
const CACHE = path.resolve(args.cache || path.join(__dirname, '..', '.cache'));
const OUT = path.resolve(args.out || path.join(__dirname, '..', 'results', 'client.json'));
const KEEP = Boolean(args.keep);
const HMC_TAG = '3.0.0-RC8';
const HMC_DIR = path.join(CACHE, 'hmc');
const MARKER = 'legacyfolia-client-check';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const checks = [];
const notes = {};
const serverLog = [];
const clientLog = [];

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

async function ensureHeadlessMc() {
  fs.mkdirSync(HMC_DIR, { recursive: true });
  const jar = path.join(CACHE, 'headlessmc.jar');
  if (!fs.existsSync(jar) || fs.statSync(jar).size < 1000000) {
    const url = `https://github.com/headlesshq/headlessmc/releases/download/${HMC_TAG}/headlessmc.jar`;
    console.log(`downloading ${url}`);
    await download(url, jar);
  }
  // hmc-specifics adds the in-game commands (msg, quit) to the headless client. RC8 has no setting to
  // fetch it, so it goes into the Fabric game folder by hand.
  const mods = path.join(HMC_DIR, 'mc', 'fabric-26.3', 'mods');
  const specifics = path.join(mods, 'hmc-specifics-26.3-fabric-latest.jar');
  if (!fs.existsSync(specifics) || fs.statSync(specifics).size < 10000) {
    fs.mkdirSync(mods, { recursive: true });
    const url = 'https://github.com/headlesshq/hmc-specifics/releases/download/26.3-latest/hmc-specifics-26.3-fabric-latest.jar';
    console.log(`downloading ${url}`);
    await download(url, specifics);
  }
  return jar;
}

function collect(proc, sink, echo) {
  let rest = '';
  const onData = (buf) => {
    // HeadlessMC draws progress bars with carriage returns: treat them as line ends.
    const parts = (rest + buf.toString()).split(/\r\n|\r|\n/);
    rest = parts.pop();
    for (const line of parts) {
      const clean = line.replace(/\u001b\[[0-9;]*[A-Za-z]/g, '');
      if (!clean.trim() || /^Downloading /.test(clean)) continue;
      sink.push(clean);
      if (echo) console.log(`${echo} ${clean}`);
    }
  };
  proc.stdout.on('data', onData);
  proc.stderr.on('data', onData);
}

async function waitFor(sink, re, timeoutMs, from = 0) {
  const end = Date.now() + timeoutMs;
  while (Date.now() < end) {
    for (let i = from; i < sink.length; i++) if (re.test(sink[i])) return sink[i];
    await sleep(250);
  }
  return null;
}

function killTree(proc) {
  if (!proc || proc.exitCode !== null) return;
  if (process.platform === 'win32') spawnSync('taskkill', ['/PID', String(proc.pid), '/T', '/F']);
  else proc.kill('SIGKILL');
}

function startServer() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'lf-client-'));
  fs.writeFileSync(
    path.join(dir, 'server.properties'),
    [`server-port=${PORT}`, 'online-mode=false', 'enable-rcon=false', 'level-type=minecraft\\:flat', 'generate-structures=false', ''].join('\n'),
  );
  const proc = spawn('java', ['-Xmx2G', '-Dcom.mojang.eula.agree=true', '-jar', JAR, 'nogui'], {
    cwd: dir,
    stdio: ['pipe', 'pipe', 'pipe'],
  });
  collect(proc, serverLog, args.verbose ? '[server]' : '');
  return { dir, proc };
}

// The client must be silent: the person running the test is at the computer. Every sound category is
// set to 0 in the game folder before each launch, and OpenAL is pointed at a library that does not
// exist (see NO_AUDIO_JVM) so that no audio device is opened at all.
const NO_AUDIO_JVM = '-Dorg.lwjgl.openal.libname=legacyfolia-no-audio.dll';

function muteClient() {
  const dir = path.join(HMC_DIR, 'mc', 'fabric-26.3');
  fs.mkdirSync(dir, { recursive: true });
  const file = path.join(dir, 'options.txt');
  const wanted = { pauseOnLostFocus: 'false', onboardAccessibility: 'false', narrator: '0' };
  for (const c of ['master', 'music', 'record', 'weather', 'block', 'hostile', 'neutral', 'player', 'ambient', 'voice', 'ui']) {
    wanted[`soundCategory_${c}`] = '0.0';
  }
  const lines = fs.existsSync(file) ? fs.readFileSync(file, 'utf8').split(/\r?\n/).filter(Boolean) : [];
  const seen = new Set();
  const out = lines.map((l) => {
    const k = l.split(':')[0];
    if (k in wanted) {
      seen.add(k);
      return `${k}:${wanted[k]}`;
    }
    return l;
  });
  for (const k of Object.keys(wanted)) if (!seen.has(k)) out.push(`${k}:${wanted[k]}`);
  fs.writeFileSync(file, `${out.join('\n')}\n`);
}

async function main() {
  if (!args.jar || !fs.existsSync(JAR)) throw new Error('pass --jar <paperclip jar>');
  const hmcJar = await ensureHeadlessMc();
  const server = startServer();
  notes.serverDir = server.dir;
  notes.jar = path.basename(JAR);
  notes.port = PORT;
  notes.headlessmc = HMC_TAG;
  let client = null;
  try {
    const done = await waitFor(serverLog, /Done \(/, 360000);
    if (!done) throw new Error('server did not finish starting');
    notes.serverStarted = done.replace(/^\[[^\]]*\]\s*/, '');

    // HeadlessMC keeps its own config, game files and mods under <cache>/hmc, never in ~/.minecraft.
    muteClient();
    const jvm = [
      `-Dhmc.files.location=${HMC_DIR}`,
      `-Dhmc.files.mc=${path.join(HMC_DIR, 'mc')}`,
      '-Dhmc.updater.enabled=false',
      '-jar',
      hmcJar,
      'launch',
      '--offline',
      '--headless',
      '--eula-accept',
      '--jvm',
      `${NO_AUDIO_JVM} ${args.jvm || ''}`.trim(),
      '--server',
      `127.0.0.1:${PORT}`,
      'fabric',
      '26.3',
    ];
    client = spawn('java', jvm, { cwd: HMC_DIR, stdio: ['pipe', 'pipe', 'pipe'] });
    collect(client, clientLog, args.verbose ? '[client]' : '');

    const joinedLine = await waitFor(serverLog, /joined the game/, Number(args.joinTimeout || 300) * 1000);
    if (!joinedLine) {
      record('join', false, `the client never joined; last client lines: ${clientLog.slice(-5).join(' | ').slice(0, 400)}`);
    } else {
      const name = (joinedLine.match(/:\s*(\S+) joined the game/) || [])[1] || 'unknown';
      notes.clientName = name;
      record('join', true, `${name} joined the offline server`);
      await sleep(5000);

      // Chat from the server, read by the client (the client log prints chat as "[CHAT] ...").
      const at = clientLog.length;
      server.proc.stdin.write(`say ${MARKER}\n`);
      const chat = await waitFor(clientLog, new RegExp(MARKER), 20000, at);
      record('read_chat', !!chat, chat ? `client log: ${chat.slice(0, 200)}` : 'the client did not log the server chat line');

      // Chat from the client, seen by the server. Needs the hmc-specifics mod's `msg` command.
      const atS = serverLog.length;
      client.stdin.write(`msg ${MARKER}-reply\n`);
      const reply = await waitFor(serverLog, new RegExp(`<${name}> ${MARKER}-reply`), 20000, atS);
      record('send_chat', !!reply, reply ? 'the server logged the client chat line' : 'no chat line from the client reached the server');

      // Leave: the specifics `quit` command, falling back to killing the process tree.
      const atL = serverLog.length;
      client.stdin.write('quit\n');
      let left = await waitFor(serverLog, /left the game/, 30000, atL);
      if (!left) {
        killTree(client);
        left = await waitFor(serverLog, /left the game/, 30000, atL);
        notes.leftByKill = true;
      }
      record('leave', !!left, left ? `${notes.leftByKill ? 'after killing the client' : 'quit command'}: ${left.replace(/^\[[^\]]*\]\s*/, '')}` : 'the server never saw the client leave');
    }
  } catch (e) {
    record('harness', false, e.message);
  } finally {
    killTree(client);
    server.proc.stdin.write('stop\n');
    await Promise.race([new Promise((r) => server.proc.once('exit', r)), sleep(40000)]);
    killTree(server.proc);
    const failed = checks.some((c) => c.result === 'FAIL');
    notes.clientLogTail = clientLog.slice(-40);
    notes.serverLogTail = serverLog.slice(-20);
    fs.mkdirSync(path.dirname(OUT), { recursive: true });
    fs.writeFileSync(OUT, JSON.stringify({ suite: 'client', date: new Date().toISOString(), passed: checks.length > 0 && !failed, checks, notes }, null, 2));
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
