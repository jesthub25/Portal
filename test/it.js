/*
 * Portal eklentisi - gercek sunucu entegrasyon testi.
 * Gercek Paper sunucusu + gercek WorldEdit + iki bot oyuncu (OP ve yetkisiz) ile tum ozellikleri dener.
 *
 * usage: node it.js <server_dir> <mc_version>
 */
'use strict';
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');

const SERVER_DIR = path.resolve(process.argv[2]);
const MC = process.argv[3];
const [, minor] = MC.split('.').map(Number);
const GROUND = minor >= 18 ? -60 : 4; // superflat zemin ustu

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const results = [];
let serverLog = '';
let server;

function check(name, ok, detail = '') {
  results.push({ name, ok: !!ok, detail });
  const line = `${ok ? 'PASS' : 'FAIL'} [${MC}] ${name}${detail ? ' :: ' + detail : ''}`;
  console.log(line);
  if (process.env.GITHUB_ACTIONS) {
    const esc = (s) => String(s).replace(/%/g, '%25').replace(/\r/g, '').replace(/\n/g, '%0A');
    console.log(`::${ok ? 'notice' : 'error'} title=${ok ? 'PASS' : 'FAIL'} ${MC}::${esc(name + (detail ? ' :: ' + detail : ''))}`);
  }
}

// ------------------------------------------------------------------ sunucu

function writeServerFiles() {
  fs.writeFileSync(path.join(SERVER_DIR, 'eula.txt'), 'eula=true\n');
  fs.writeFileSync(path.join(SERVER_DIR, 'server.properties'), [
    'online-mode=false',
    'enforce-secure-profile=false',
    `level-type=${minor >= 19 ? 'minecraft\\:flat' : 'flat'}`,
    'generate-structures=false',
    'spawn-protection=0',
    'spawn-monsters=false',
    'spawn-animals=false',
    'spawn-npcs=false',
    'allow-flight=true',
    'view-distance=4',
    'simulation-distance=4',
    'server-port=25565',
    'difficulty=peaceful',
    'motd=portal-it',
    '',
  ].join('\n'));
  fs.writeFileSync(path.join(SERVER_DIR, 'bukkit.yml'), 'settings:\n  connection-throttle: -1\n');
}

function startServer() {
  server = spawn('java', ['-Xms1G', '-Xmx2G', '-DPaper.IgnoreJavaVersion=true',
    '-Dpaper.disableChannelLimit=true', '-jar', 'paper.jar', '--nogui'], { cwd: SERVER_DIR });
  const onData = (d) => {
    const s = d.toString();
    serverLog += s;
    process.stdout.write(s.split('\n').map((l) => (l ? '[server] ' + l : l)).join('\n'));
  };
  server.stdout.on('data', onData);
  server.stderr.on('data', onData);
}

function console_(cmd) {
  server.stdin.write(cmd + '\n');
}

async function waitLog(regex, timeoutMs, from = 0) {
  const end = Date.now() + timeoutMs;
  while (Date.now() < end) {
    if (regex.test(serverLog.slice(from))) return true;
    await sleep(200);
  }
  return false;
}

// ------------------------------------------------------------------ botlar

function makeBot(username) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({
      host: '127.0.0.1', port: 25565, username, auth: 'offline', version: MC,
      checkTimeoutInterval: 120000,
    });
    bot.msgs = [];
    bot.rootCommands = null;
    // Istemciye gonderilen gercek komut agaci (sohbet onerileri bundan uretilir)
    bot._client.on('declare_commands', (packet) => {
      try {
        const root = packet.nodes[packet.rootIndex];
        bot.rootCommands = root.children.map((i) => {
          const n = packet.nodes[i];
          return (n.extraNodeData && n.extraNodeData.name) || n.name || '';
        });
      } catch (e) {
        bot.rootCommands = ['<parse error ' + e.message + '>'];
      }
    });
    bot.on('messagestr', (m) => {
      bot.msgs.push(m);
      console.log(`[${username}] ${m}`);
    });
    const timer = setTimeout(() => reject(new Error(username + ' spawn timeout')), 60000);
    bot.once('spawn', () => { clearTimeout(timer); resolve(bot); });
    bot.once('error', (e) => { clearTimeout(timer); reject(e); });
    bot.once('kicked', (r) => { clearTimeout(timer); reject(new Error(username + ' kicked: ' + JSON.stringify(r))); });
  });
}

async function waitMsg(bot, regex, timeoutMs, from) {
  const end = Date.now() + timeoutMs;
  while (Date.now() < end) {
    for (let i = from; i < bot.msgs.length; i++) if (regex.test(bot.msgs[i])) return bot.msgs[i];
    await sleep(100);
  }
  return null;
}

async function cmd(bot, text, regex, timeoutMs = 5000) {
  const from = bot.msgs.length;
  bot.chat(text);
  return waitMsg(bot, regex, timeoutMs, from);
}

function countMsgs(bot, regex, from = 0) {
  let n = 0;
  for (let i = from; i < bot.msgs.length; i++) if (regex.test(bot.msgs[i])) n++;
  return n;
}

async function tab(bot, text) {
  try {
    const r = await bot.tabComplete(text, false, false, 5000);
    return (r || []).map((m) => (typeof m === 'string' ? m : m.match));
  } catch (e) {
    return ['<error ' + e.message + '>'];
  }
}

async function tpBot(name, x, y, z, yaw = 0, pitch = 0) {
  console_(`tp ${name} ${x} ${y} ${z} ${yaw} ${pitch}`);
  await sleep(900);
}

async function walkEast(bot, ms, stopRegex, from) {
  await bot.lookAt(new Vec3(50, bot.entity.position.y + 1.62, bot.entity.position.z), true);
  bot.setControlState('forward', true);
  const end = Date.now() + ms;
  let hit = null;
  while (Date.now() < end) {
    if (stopRegex) {
      hit = await waitMsg(bot, stopRegex, 50, from);
      if (hit) break;
    } else {
      await sleep(50);
    }
  }
  bot.clearControlStates();
  return hit;
}

// ------------------------------------------------------------------ testler

async function run() {
  writeServerFiles();
  startServer();
  const started = await waitLog(/Done \(/, 240000);
  check('server started', started);
  if (!started) return;
  check('plugin enabled', /Portal v[\d.]+ aktif/.test(serverLog), 'enable log line');
  check('worldedit enabled', !/WorldEdit\/FastAsyncWorldEdit bulunamadi/.test(serverLog));

  console_('op OpBot');
  await sleep(1000);

  const op = await makeBot('OpBot');
  await sleep(2500);
  const guest = await makeBot('Guest');
  await sleep(2500);

  console_('gamemode creative OpBot');
  console_('gamemode survival Guest');
  await sleep(500);

  const Y = GROUND;
  const PERM = /yetkisine ihtiyacınız var/;
  const TELEPORTED = /Işınlandınız/;
  const COOLDOWN = /saniye beklemelisiniz/;

  // --- komut gorunurlugu
  const opRoot = await tab(op, '/portal ');
  check('op tab: subcommands', ['oluştur', 'tphere', 'düzenle', 'sil', 'bilgi', 'liste', 'yenile'].every((s) => opRoot.includes(s)), JSON.stringify(opRoot));
  const guestRoot = await tab(guest, '/portal ');
  check('guest tab: no subcommands', !guestRoot.some((s) => ['oluştur', 'tphere', 'sil', 'bilgi'].includes(s)), JSON.stringify(guestRoot));
  const gTree = guest.rootCommands || [];
  check('guest command tree received', gTree.length > 0, JSON.stringify(gTree.length));
  check('guest command tree: /portal hidden', !gTree.includes('portal') && !gTree.includes('portal:portal'),
    JSON.stringify(gTree.filter((c) => /portal/.test(c))));
  const oTree = op.rootCommands || [];
  check('op command tree: /portal visible', oTree.includes('portal'), JSON.stringify(oTree.filter((c) => /portal/.test(c))));
  const help = await cmd(op, '/portal', /Portal/, 4000);
  check('op /portal help', !!help, help || 'no help');
  const gFrom = guest.msgs.length;
  guest.chat('/portal liste');
  await sleep(1500);
  check('guest /portal blocked', !guest.msgs.slice(gFrom).some((m) => /Portallar|Kayıtlı portal/.test(m)), JSON.stringify(guest.msgs.slice(gFrom)));

  // --- WorldEdit secimi ve olusturma
  const noSel = await cmd(op, '/portal oluştur test', /pos1 ve pos2|oluşturuldu/, 4000);
  check('create without selection rejected', noSel && /pos1 ve pos2/.test(noSel), noSel);

  await tpBot('OpBot', 3.5, Y, -0.5);
  check('//pos1', await cmd(op, '//pos1', /position|pozisyon|set/i, 5000));
  await tpBot('OpBot', 4.5, Y, 1.5);
  check('//pos2', await cmd(op, '//pos2', /position|pozisyon|set/i, 5000));
  check('//expand 2 up', await cmd(op, '//expand 2 up', /expand|genişlet/i, 5000));

  check('reserved name rejected', await cmd(op, '/portal oluştur admin', /ayrılmıştır/));
  check('invalid name rejected', await cmd(op, '/portal oluştur a.b', /Geçersiz portal adı/));
  const created = await cmd(op, '/portal oluştur test', /oluşturuldu|zaten|Geçersiz|seç/i);
  check('portal created', created && /oluşturuldu/.test(created), created);
  check('duplicate rejected', await cmd(op, '/portal oluştur TEST', /zaten var/));

  // --- bilgi (hedef yok)
  const infoFrom = op.msgs.length;
  op.chat('/portal bilgi test');
  await sleep(1500);
  const info1 = op.msgs.slice(infoFrom).join('\n');
  check('info pos1', info1.includes(`Pos1: X:3 Y:${Y} Z:-1`), info1);
  check('info pos2', info1.includes(`Pos2: X:4 Y:${Y + 2} Z:1`), info1);
  check('info no destination', /Ayarlanmamış/.test(info1), info1);

  // --- hedef yokken giris
  await tpBot('OpBot', 1.5, Y, 0.5, -90, 0);
  const noDest = await walkEast(op, 2500, /ışınlanma noktası ayarlanmamış/, op.msgs.length);
  check('no destination warning', !!noDest, noDest);
  await sleep(500);

  // --- tphere
  await tpBot('OpBot', -10.5, Y, 0.5, 45, 10);
  const set = await cmd(op, '/portal tphere test', /ayarlandı/);
  check('tphere set', !!set, set);
  const yml = fs.readFileSync(path.join(SERVER_DIR, 'plugins/Portal/portals.yml'), 'utf8');
  check('portals.yml destination saved', /destination:/.test(yml) && /x: -10\.5/.test(yml) && /yaw: 45\.0/.test(yml) && /pitch: 10\.0/.test(yml), yml);

  // --- tab tamamlama (koordinatlar)
  const tInfo = await tab(op, '/portal bilgi ');
  check('tab portal names', tInfo.includes('test'), JSON.stringify(tInfo));
  const tSil = await tab(op, '/portal sil t');
  check('tab portal names (prefix)', tSil.includes('test'), JSON.stringify(tSil));
  const tPos = await tab(op, '/portal düzenle test ');
  check('tab pos1/pos2', tPos.includes('pos1') && tPos.includes('pos2'), JSON.stringify(tPos));
  const tx = await tab(op, '/portal düzenle test pos1 ');
  check('tab current x1', tx[0] === '3', JSON.stringify(tx));
  const ty = await tab(op, `/portal düzenle test pos1 3 `);
  check('tab current y1', ty[0] === String(Y), JSON.stringify(ty));
  const tz = await tab(op, `/portal düzenle test pos1 3 ${Y} `);
  check('tab current z1', tz[0] === '-1', JSON.stringify(tz));
  const tz2 = await tab(op, `/portal düzenle test pos2 4 ${Y + 2} `);
  check('tab current z2', tz2[0] === '1', JSON.stringify(tz2));

  // --- yetkili oyuncu isinlanir
  await tpBot('OpBot', 1.5, Y, 0.5, -90, 0);
  let from = op.msgs.length;
  const tp1 = await walkEast(op, 4000, TELEPORTED, from);
  await sleep(800);
  let p = op.entity.position;
  check('op teleported', !!tp1, tp1);
  check('op at destination', Math.abs(p.x + 10.5) < 1.2 && Math.abs(p.z - 0.5) < 1.2 && Math.abs(p.y - Y) < 1.5, `${p}`);

  // --- isinlanma bekleme suresi (ard arda giris engeli)
  await tpBot('OpBot', 1.5, Y, 0.5, -90, 0);
  from = op.msgs.length;
  const cd = await walkEast(op, 1500, COOLDOWN, from);
  await sleep(300);
  p = op.entity.position;
  check('teleport cooldown message', !!cd, cd);
  check('cooldown blocks entry', p.x < 3.05 && countMsgs(op, TELEPORTED, from) === 0, `${p}`);

  await sleep(3500);
  await tpBot('OpBot', 1.5, Y, 0.5, -90, 0);
  from = op.msgs.length;
  const tp2 = await walkEast(op, 4000, TELEPORTED, from);
  check('teleport works again after cooldown', !!tp2, tp2);

  // --- yetkisiz oyuncu: uyari + 3 blok geri + savrulma
  await tpBot('Guest', 1.5, Y, 0.5, -90, 0);
  from = guest.msgs.length;
  const deny1 = await walkEast(guest, 4000, PERM, from);
  await sleep(1500);
  p = guest.entity.position;
  check('guest permission warning', !!deny1, deny1);
  check('guest pushed back (>=2 blocks before portal)', p.x <= 1.0, `${p}`);
  check('guest not teleported', p.x > -8 && countMsgs(guest, TELEPORTED, from) === 0, `${p}`);

  // uyari bekleme suresi: 3 sn icinde ikinci mesaj yok
  await tpBot('Guest', 1.5, Y, 0.5, -90, 0);
  const from2 = guest.msgs.length;
  await walkEast(guest, 1500, null, from2);
  await sleep(800);
  p = guest.entity.position;
  check('guest warning cooldown (no spam)', countMsgs(guest, PERM, from2) === 0, JSON.stringify(guest.msgs.slice(from2)));
  check('guest still cannot enter', p.x < 3.0, `${p}`);

  await sleep(3200);
  await tpBot('Guest', 1.5, Y, 0.5, -90, 0);
  const from3 = guest.msgs.length;
  const deny3 = await walkEast(guest, 4000, PERM, from3);
  check('guest warning repeats after cooldown', !!deny3, deny3);

  // --- duzenle
  check('edit invalid coordinate', await cmd(op, '/portal düzenle test pos1 abc 1 1', /Geçersiz koordinat/));
  check('edit invalid pos', await cmd(op, '/portal düzenle test pos3 1 1 1', /Geçersiz konum/));
  const ed = await cmd(op, `/portal duzenle test pos2 4 ${Y + 3} 2`, /güncellendi/);
  check('edit pos2', !!ed, ed);
  const infoFrom2 = op.msgs.length;
  op.chat('/portal bilgi test');
  await sleep(1500);
  const info2 = op.msgs.slice(infoFrom2).join('\n');
  check('info after edit', info2.includes(`Pos2: X:4 Y:${Y + 3} Z:2`) && /Hedef: .*-10\.50/.test(info2), info2);
  const yml2 = fs.readFileSync(path.join(SERVER_DIR, 'plugins/Portal/portals.yml'), 'utf8');
  check('edit persisted', new RegExp(`pos2:\\s*\\n\\s*x: 4\\s*\\n\\s*y: ${Y + 3}\\s*\\n\\s*z: 2`).test(yml2), yml2);

  // --- liste / yenile
  check('list', await cmd(op, '/portal liste', /Portallar.*test/));
  check('reload', await cmd(op, '/portal yenile', /yeniden yüklendi.*1 portal/));
  const infoFrom3 = op.msgs.length;
  op.chat('/portal bilgi test');
  await sleep(1500);
  check('data intact after reload', op.msgs.slice(infoFrom3).join('\n').includes(`Pos2: X:4 Y:${Y + 3} Z:2`));

  // --- konsol
  const logFrom = serverLog.length;
  console_('portal bilgi test');
  check('console info', await waitLog(/Portal: test/, 4000, logFrom));

  // --- sil
  check('delete', await cmd(op, '/portal sil test', /kalıcı olarak silindi/));
  check('deleted info', await cmd(op, '/portal bilgi test', /bulunamadı/));
  const yml3 = fs.readFileSync(path.join(SERVER_DIR, 'plugins/Portal/portals.yml'), 'utf8');
  check('delete persisted', !/\btest:/.test(yml3), yml3);
  await sleep(3200);
  await tpBot('OpBot', 1.5, Y, 0.5, -90, 0);
  from = op.msgs.length;
  await walkEast(op, 2000, null, from);
  check('deleted portal inactive', countMsgs(op, TELEPORTED, from) === 0);

  op.quit();
  guest.quit();
  await sleep(1500);
}

function scanLog() {
  const lines = serverLog.split('\n');
  const ours = lines.filter((l) => /org\.jestron\.portal/.test(l));
  check('no plugin exceptions in server log', ours.length === 0, ours.slice(0, 20).join(' | '));
  const excessive = lines.filter((l) => /Excessive velocity/i.test(l));
  check('no excessive velocity warnings', excessive.length === 0, excessive.slice(0, 5).join(' | '));
  // Geri atilan (yetkisiz) oyuncu icin sunucu hareket uyarisi olmamali
  const guestMove = lines.filter((l) => /Guest moved (too quickly|wrongly)/i.test(l));
  check('no movement warnings for pushed-back player', guestMove.length === 0, guestMove.slice(0, 5).join(' | '));
  const info = lines.filter((l) => /moved (too quickly|wrongly)/i.test(l));
  if (info.length) console.log('INFO (bot client teleport-ack timing, not plugin):', info.length, 'movement warnings');
}

async function stopServer() {
  if (!server || server.exitCode !== null) return;
  console_('stop');
  const end = Date.now() + 60000;
  while (server.exitCode === null && Date.now() < end) await sleep(300);
  if (server.exitCode === null) server.kill('SIGKILL');
}

(async () => {
  try {
    await run();
  } catch (e) {
    check('test run', false, e.stack || String(e));
  }
  await stopServer();
  scanLog();
  const failed = results.filter((r) => !r.ok);
  const summary = [`### Portal integration — Minecraft ${MC}`, '', `${results.length - failed.length}/${results.length} passed`, '',
    '| Result | Test | Detail |', '|---|---|---|',
    ...results.map((r) => `| ${r.ok ? '✅' : '❌'} | ${r.name} | ${r.ok ? '' : String(r.detail).replace(/\|/g, '/').replace(/\n/g, ' ').slice(0, 300)} |`)].join('\n');
  console.log(summary);
  if (process.env.GITHUB_STEP_SUMMARY) fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, summary + '\n');
  fs.writeFileSync(path.join(SERVER_DIR, 'results.json'), JSON.stringify(results, null, 2));
  process.exit(failed.length ? 1 : 0);
})();
