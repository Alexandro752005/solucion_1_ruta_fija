// Automated real browser / production Angular / real backend+PG. No human claims.
import { spawn } from 'node:child_process';
import { mkdtemp, readFile, rm, realpath } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, dirname, basename } from 'node:path';
import { existsSync } from 'node:fs';
const base=process.env.RF_L04_CONNECTED_BASE,control=process.env.RF_L04_CONNECTED_CONTROL;
if(process.env.RF_L04_CONNECTED_CLIENTS!=='true'||!/^http:\/\/127\.0\.0\.1:\d+$/.test(base??'')||!control)process.exit(2);
let browser,profile,socket,seq=0,phase='INIT';const pending=new Map();
const pause=ms=>new Promise(r=>setTimeout(r,ms));const check=(v,c)=>{if(!v)throw new Error(c);};
function send(method,params={}){const id=++seq;return new Promise((resolve,reject)=>{pending.set(id,{resolve,reject});socket.send(JSON.stringify({id,method,params}));});}
async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.exceptionDetails)throw new Error('BROWSER_EXPRESSION');return r.result?.value;}
async function wait(expression){for(let i=0;i<150;i++){const r=await evaluate(expression);if(r)return r;await pause(100);}throw new Error('UI_WAIT');}
async function fill(selector,value){await wait(`!!document.querySelector(${JSON.stringify(selector)})`);await evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});e.value=${JSON.stringify(value)};e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));})()`);await evaluate('new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(()=>r(true))))');}
async function click(label){const e=`[...document.querySelectorAll('button')].find(e=>e.textContent.trim()===${JSON.stringify(label)}&&!e.disabled)`;await wait(`!!(${e})`);await evaluate(`(${e}).click()`);}
try{
  const response=await fetch(base+'/__l04/state',{headers:{'X-L04-Control':control}});check(response.ok,'CONTROL_REFUSED');const state=await response.json();
  const executable=['C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe','C:/Program Files/Google/Chrome/Application/chrome.exe'].find(existsSync);check(executable,'BROWSER_MISSING');
  profile=await mkdtemp(join(tmpdir(),'rf-l04-browser-'));browser=spawn(executable,['--headless=new','--no-first-run','--no-default-browser-check','--disable-background-networking','--disable-sync','--disable-extensions','--remote-debugging-address=127.0.0.1','--remote-debugging-port=0',`--user-data-dir=${profile}`,'about:blank'],{windowsHide:true,stdio:'ignore'});
  let port;for(let i=0;i<100;i++){try{port=(await readFile(join(profile,'DevToolsActivePort'),'utf8')).split('\n')[0];break;}catch{await pause(100);}}check(port,'BROWSER_START');
  const target=await(await fetch(`http://127.0.0.1:${port}/json/new?about:blank`,{method:'PUT'})).json();socket=new WebSocket(target.webSocketDebuggerUrl);await new Promise((r,j)=>{socket.onopen=r;socket.onerror=j;});socket.onmessage=m=>{const r=JSON.parse(m.data);if(r.id&&pending.has(r.id)){const p=pending.get(r.id);pending.delete(r.id);r.error?p.reject(new Error('CDP_REQUEST')):p.resolve(r.result);}};
  await send('Page.enable');await send('Runtime.enable');await send('Network.enable');
  await send('Network.setCookie',{name:'rf_refresh',value:state.browserRefresh,url:base+'/api/v1/auth',path:'/api/v1/auth',httpOnly:true,secure:false,sameSite:'Lax'});
  phase='ASSIGNMENTS_'+state.clientStage;await send('Page.navigate',{url:base+'/assignments'});await wait(`document.body.innerText.includes('Plan operativo')`);
  const openOffer=async()=>{const expression=`[...document.querySelectorAll('tr')].find(r=>r.textContent.includes(${JSON.stringify(state.driverId)})&&r.querySelector('button'))?.querySelector('button')`;await wait(`!!(${expression})`);await evaluate(`(${expression}).click()`);await wait(`document.querySelector('rf-scheduled-service-panel')?.innerText.includes('SERVICIO-')`);};
  if(state.clientStage==='CREATE'||state.clientStage==='REOFFER'){
    if(state.clientStage==='CREATE')await click('Nueva oferta');else {await openOffer();await click('Preparar nueva oferta y aceptaci\u00f3n');}
    await wait(`!!document.querySelector('#assignment-type option[value="${state.serviceTypeVersionId}"]')&&!!document.querySelector('#assignment-route option[value="${state.routeVersionId}"]')`);
    await fill('#assignment-type',state.serviceTypeVersionId);await fill('#assignment-route',state.routeVersionId);
    await wait(`document.querySelector('#assignment-origin')?.value==='SYNTHETIC origin'`);
    const local=d=>`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}T${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
    const offset=state.clientStage==='CREATE'?4:7;await fill('#assignment-start',local(new Date(Date.now()+offset*3600000)));await fill('#assignment-end',local(new Date(Date.now()+(offset+1)*3600000)));
    if(state.clientStage==='REOFFER')await fill('rf-modal input[maxlength="160"]','SYNTHETIC-MATERIAL-CHANGE');
    await click('Consultar candidatos');await click(`CONDUCTOR-${state.driverId} \u00b7 UNIDAD-${state.vehicleId}`);
    await click('Emitir oferta');await wait(`document.body.innerText.includes('Oferta emitida: pendiente de aceptaci\u00f3n')`);
    console.log('CONNECTED_L04_CRM_'+state.clientStage+'=PASS_REAL_UI_HTTP');
  }else if(state.clientStage==='CANCEL'){
    await openOffer();await wait(`document.querySelector('rf-scheduled-service-panel')?.innerText.includes('Pendiente de reasignaci\u00f3n')`);
    await fill('rf-scheduled-service-panel input:not([type="checkbox"])','SYNTHETIC-SERVICE-CANCEL');
    await evaluate(`(()=>{const e=document.querySelector('rf-scheduled-service-panel input[type="checkbox"]');e.checked=true;e.dispatchEvent(new Event('change',{bubbles:true}));})()`);
    await click('Cancelar servicio expresamente');await wait(`document.querySelector('rf-scheduled-service-panel')?.innerText.includes('Servicio cancelado')`);
    console.log('CONNECTED_L04_CRM_CANCEL=PASS_EXPLICIT_SEPARATE_SERVICE');
  }else throw new Error('UNKNOWN_STAGE');
  const ordinary=await evaluate('document.body.innerText');check(!ordinary.includes('SYNTHETIC L04 CONDUCTOR')&&!ordinary.includes('60000004'),'ORDINARY_PII');
  console.log('CONNECTED_L04_CRM_RESULT=PASS_REAL_BROWSER_HTTP_TEST_DB');
}catch(error){console.log('CONNECTED_L04_CRM_RESULT=FAIL; PHASE='+phase+'; REASON='+(/^[A-Z_]+$/.test(error.message??'')?error.message:'DETAILS_REDACTED'));process.exitCode=1;}
finally{try{socket?.close();}catch{}if(browser&&!browser.killed)browser.kill();await pause(500);if(profile){const root=await realpath(tmpdir());let resolved;try{resolved=await realpath(profile);}catch{}if(resolved&&dirname(resolved).toLowerCase()===root.toLowerCase()&&basename(resolved).startsWith('rf-l04-browser-'))await rm(resolved,{recursive:true,force:true,maxRetries:5,retryDelay:200});}}
