import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';

const evidence = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(evidence, '../../..');
const profile = path.join(root, '.runtime', `qa-edge-${Date.now()}`);
const edge = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const debugPort = 9228;
const server = `http://127.0.0.1:${debugPort}`;
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const log = fs.openSync(path.join(evidence, 'browser-platform.log'), 'w');
const process = spawn(edge, ['--headless=new', '--no-first-run', '--disable-background-networking',
  '--disable-component-update', '--no-default-browser-check', `--remote-debugging-port=${debugPort}`,
  `--user-data-dir=${profile}`, 'about:blank'], { windowsHide: true, stdio: ['ignore', log, log] });
let ws;
const events = [];
const pending = new Map();
let counter = 0;
const send = (method, params = {}) => new Promise((resolve, reject) => {
  const id = ++counter;
  const timeout = setTimeout(() => { pending.delete(id); reject(new Error(`CDP timeout ${method}`)); }, 15000);
  pending.set(id, { resolve, reject, timeout });
  ws.send(JSON.stringify({ id, method, params }));
});
let result;
try {
  let page;
  for (let attempt = 0; attempt < 60; attempt++) {
    try { page = (await (await fetch(`${server}/json/list`)).json()).find(p => p.type === 'page'); } catch {}
    if (page) break;
    await sleep(500);
  }
  if (!page) throw new Error('Owned headless browser did not start');
  ws = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }); });
  ws.addEventListener('message', event => {
    const message = JSON.parse(event.data);
    if (message.id && pending.has(message.id)) {
      const request = pending.get(message.id); pending.delete(message.id); clearTimeout(request.timeout);
      if (message.error) request.reject(new Error(JSON.stringify(message.error))); else request.resolve(message.result);
    } else if (message.method) events.push(message);
  });
  await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable'); await send('Log.enable');
  await send('Emulation.setDeviceMetricsOverride', { width: 1366, height: 900, deviceScaleFactor: 1, mobile: false });
  await send('Page.navigate', { url: 'http://127.0.0.1:4300/login' });
  let rendered;
  for (let attempt = 0; attempt < 40; attempt++) {
    const evaluated = await send('Runtime.evaluate', { expression: 'JSON.stringify({url:location.href,title:document.title,text:document.body.innerText,inputs:[...document.querySelectorAll("input")].map(e=>({type:e.type,name:e.name})),buttons:[...document.querySelectorAll("button")].map(e=>e.innerText)})', returnByValue: true });
    rendered = JSON.parse(evaluated.result.value);
    if (rendered.inputs.length >= 2 && rendered.text.length > 50) break;
    await sleep(500);
  }
  const screenshot = await send('Page.captureScreenshot', { format: 'png' });
  fs.writeFileSync(path.join(evidence, 'browser-login.png'), Buffer.from(screenshot.data, 'base64'));
  const exceptions = events.filter(e => e.method === 'Runtime.exceptionThrown');
  const consoleErrors = events.filter(e => e.method === 'Runtime.consoleAPICalled' && e.params.type === 'error');
  const failedRequests = events.filter(e => e.method === 'Network.loadingFailed');
  const responses = events.filter(e => e.method === 'Network.responseReceived').map(e => ({ url: e.params.response.url, status: e.params.response.status }));
  result = { capturedAt: new Date().toISOString(), result: exceptions.length === 0 && consoleErrors.length === 0 && failedRequests.length === 0 && rendered.inputs.length >= 2 ? 'PASS' : 'FAIL',
    rendered, exceptions, consoleErrors, failedRequests, responses, databaseWritesRequested: false,
    signedIn: false, browser: 'Microsoft Edge headless, dedicated temporary profile' };
  fs.writeFileSync(path.join(evidence, 'browser-smoke.json'), JSON.stringify(result, null, 2));
} finally {
  if (ws?.readyState === WebSocket.OPEN) { try { await send('Browser.close'); } catch {} ws.close(); }
  if (process.exitCode === null) await new Promise(resolve => { process.once('exit', resolve); setTimeout(resolve, 5000); });
  if (process.exitCode === null) process.kill();
  fs.closeSync(log);
}
console.log(JSON.stringify(result));
if (result?.result !== 'PASS') globalThis.process.exitCode = 1;
