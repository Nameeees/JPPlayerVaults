'use strict';

/**
 * Headless client test for JPPlayerVaults on a real 1.8.8 server.
 *
 * Joins with an offline-mode bot, opens /pv 1, moves a diamond into the vault,
 * closes it, disconnects (which drops the plugin's in-memory cache) and joins
 * again to prove the item came back from disk.
 */

const mineflayer = require('mineflayer');

const HOST = process.env.MC_HOST || '127.0.0.1';
const PORT = Number(process.env.MC_PORT || 25570);
const NAME = process.env.MC_NAME || 'TestBot';
const VERSION = process.env.MC_VERSION || '1.8.8';

const log = (...args) => console.log('[BOT]', ...args);
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function connect() {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({
      host: HOST,
      port: PORT,
      username: NAME,
      auth: 'offline',
      version: VERSION
    });
    const onError = (error) => { cleanup(); reject(error); };
    const onKick = (reason) => { cleanup(); reject(new Error('kicked: ' + reason)); };
    const cleanup = () => {
      bot.removeListener('error', onError);
      bot.removeListener('kicked', onKick);
    };
    bot.once('spawn', () => { cleanup(); resolve(bot); });
    bot.once('error', onError);
    bot.once('kicked', onKick);
  });
}

function openVault(bot, number) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('timed out waiting for the vault window')), 15000);
    bot.once('windowOpen', (window) => {
      clearTimeout(timer);
      resolve(window);
    });
    bot.chat('/pv ' + number);
  });
}

(async () => {
  let bot = await connect();
  log('spawned as ' + bot.username);
  await sleep(2000);

  let window = await openVault(bot, 1);
  log('window slots=' + window.slots.length + ' title=' + JSON.stringify(String(window.title)));
  if (window.slots.length !== 90) {
    throw new Error('expected 90 window slots for a 54-slot vault, got ' + window.slots.length);
  }
  if (String(window.title).indexOf('#1') < 0) {
    throw new Error('vault title does not mention #1: ' + window.title);
  }

  bot.chat('/give ' + NAME + ' diamond 5');
  await sleep(2000);
  let source = -1;
  for (let slot = 54; slot < 90; slot++) {
    const item = window.slots[slot];
    if (item && item.name === 'diamond' && item.count === 5) {
      source = slot;
      break;
    }
  }
  if (source < 0) {
    throw new Error('the /give diamond is not in the player inventory (window slots 54-89)');
  }

  log('moving the diamond from window slot ' + source + ' into vault slot 0');
  await bot.clickWindow(source, 0, 0);
  await sleep(400);
  await bot.clickWindow(0, 0, 0);
  await sleep(800);
  const placed = window.slots[0];
  if (!placed || placed.name !== 'diamond' || placed.count !== 5) {
    throw new Error('the diamond did not land in vault slot 0: ' + JSON.stringify(placed));
  }
  log('vault slot 0 now holds ' + placed.count + ' ' + placed.name);

  bot.closeWindow(window);
  await sleep(1500);
  bot.quit();
  await sleep(3000);
  log('disconnected: the plugin must drop its cache and reload from disk');

  bot = await connect();
  await sleep(2000);
  window = await openVault(bot, 1);
  const reloaded = window.slots[0];
  log('after reconnecting, vault slot 0 = '
    + JSON.stringify(reloaded && { name: reloaded.name, count: reloaded.count }));
  if (!reloaded || reloaded.name !== 'diamond' || reloaded.count !== 5) {
    throw new Error('the vault content did not survive the reconnect');
  }

  bot.closeWindow(window);
  await sleep(500);
  log('PASS');
  bot.chat('/stop');
  await sleep(3000);
  process.exit(0);
})().catch((error) => {
  log('FAIL: ' + error.message);
  process.exit(1);
});
