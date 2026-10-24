// Automated real browser / production Angular / real backend+PG. No human claims.
import { spawn } from 'node:child_process';
import { mkdtemp, readFile, rm, realpath } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, dirname, basename } from 'node:path';
import { existsSync } from 'node:fs';
const base=process.env.RF_L05_CONNECTED_BASE,control=process.env.RF_L05_CONNECTED_CONTROL;
if(process.env.RF_L05_CONNECTED_CLIENTS!=='true'||!/^http:\/\/127\.0\.0\.1:\d+$/.test(base??'')||!control)process.exit(2);
let browser,profile,socket,seq=0,phase='INIT';const pending=new Map();
const pause=ms=>new Promise(r=>setTimeout(r,ms));const check=(v,c)=>{if(!v)throw new Error(c);};
function send(method,params={}){const id=++seq;return new Promise((resolve,reject)=>{pending.set(id,{resolve,reject});socket.send(JSON.stringify({id,method,params}));});}
async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.exceptionDetails)throw new Error('BROWSER_EXPRESSION');return r.result?.value;}
async function wait(expression){for(let i=0;i<300;i++){const r=await evaluate(expression);if(r)return r;await pause(100);}throw new Error('UI_WAIT');}
async function fill(selector,value){await wait(`!!document.querySelector(${JSON.stringify(selector)})`);await evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});e.value=${JSON.stringify(value)};e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));})()`);await evaluate('new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(()=>r(true))))');}
async function click(label){const e=`[...document.querySelectorAll('button')].find(e=>e.textContent.trim()===${JSON.stringify(label)}&&!e.disabled)`;await wait(`!!(${e})`);await evaluate(`(${e}).click()`);}
try{
  const response=await fetch(base+'/__l05/state',{headers:{'X-L05-Control':control}});check(response.ok,'CONTROL_REFUSED');const state=await response.json();
  const executable=['C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe','C:/Program Files/Google/Chrome/Application/chrome.exe'].find(existsSync);check(executable,'BROWSER_MISSING');
  profile=await mkdtemp(join(tmpdir(),'rf-l05-browser-'));browser=spawn(executable,['--headless=new','--no-first-run','--no-default-browser-check','--disable-background-networking','--disable-sync','--disable-extensions','--remote-debugging-address=127.0.0.1','--remote-debugging-port=0',`--user-data-dir=${profile}`,'about:blank'],{windowsHide:true,stdio:'ignore'});
  let port;for(let i=0;i<100;i++){try{port=(await readFile(join(profile,'DevToolsActivePort'),'utf8')).split('\n')[0];break;}catch{await pause(100);}}check(port,'BROWSER_START');
  const target=await(await fetch(`http://127.0.0.1:${port}/json/new?about:blank`,{method:'PUT'})).json();socket=new WebSocket(target.webSocketDebuggerUrl);await new Promise((r,j)=>{socket.onopen=r;socket.onerror=j;});socket.onmessage=m=>{const r=JSON.parse(m.data);if(r.id&&pending.has(r.id)){const p=pending.get(r.id);pending.delete(r.id);r.error?p.reject(new Error('CDP_REQUEST')):p.resolve(r.result);}};
  await send('Page.enable');await send('Runtime.enable');await send('Network.enable');
  await send('Network.setCookie',{name:'rf_refresh',value:state.browserRefresh,url:base+'/api/v1/auth',path:'/api/v1/auth',httpOnly:true,secure:false,sameSite:'Lax'});
  phase='ASSIGNMENTS_'+state.clientStage;await send('Page.navigate',{url:base+'/assignments'});await wait(`document.body.innerText.includes('Plan operativo')`);
  const openOffer=async()=>{const selector='tr[data-assignment-id="'+state.currentOfferId+'"] button';await wait(`!!document.querySelector(${JSON.stringify(selector)})`);await evaluate(`document.querySelector(${JSON.stringify(selector)}).click()`);await wait(`document.querySelector('rf-scheduled-service-panel')?.innerText.includes('SERVICIO-')`);};
  const checked=async(selector)=>{await evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});if(!e||e.disabled)throw new Error('CHECKBOX_NOT_READY');e.checked=true;e.dispatchEvent(new Event('change',{bubbles:true}));})()`);await pause(150);};
  const local=d=>`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}T${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
  if(state.clientStage==='CREATE'){
    await click('Nueva oferta');await wait(`!!document.querySelector('#assignment-type option[value="${state.serviceTypeVersionId}"]')&&!!document.querySelector('#assignment-route option[value="${state.routeVersionId}"]')`);
    await fill('#assignment-type',state.serviceTypeVersionId);await fill('#assignment-route',state.routeVersionId);await wait(`document.querySelector('#assignment-origin')?.value==='SYNTHETIC origin'`);
    await fill('#assignment-start',local(new Date(Date.now()+4*3600000)));await fill('#assignment-end',local(new Date(Date.now()+5*3600000)));await click('Consultar candidatos');await click(`CONDUCTOR-${state.driverId} · UNIDAD-${state.vehicleId}`);await click('Emitir oferta');await wait(`document.body.innerText.includes('Oferta emitida: pendiente de aceptaci\u00f3n')`);
    console.log('CONNECTED_L05_CRM_CREATE=PASS_REAL_UI_HTTP');
  }else if(state.clientStage==='READ'){
    await openOffer();console.log('CONNECTED_L05_CRM_READ=PASS_EXISTING_CASE_RECONCILIATION');
  }else if(state.clientStage==='PREPARE'){
    await openOffer();await wait(`!!document.querySelector('#handover-start')`);await fill('#handover-start',local(new Date(Date.now()+2*3600000)));await fill('#handover-end',local(new Date(Date.now()+3*3600000)));await click('Consultar sustitutos con unidad vinculada');
    const option=await wait(`[...document.querySelectorAll('#handover-pair option')].find(e=>e.textContent.includes(${JSON.stringify(state.replacementDriverId)})&&e.textContent.includes(${JSON.stringify(state.replacementVehicleId)}))?.value`);await fill('#handover-pair',option);await fill('#handover-point','SYNTHETIC_TRANSFER_POINT');await fill('#handover-reason','SYNTHETIC safe transfer checked');await checked('#handover-safety');await click('Emitir oferta de relevo; no iniciar');await wait(`document.body.innerText.includes('Servicio actualizado.')`);console.log('CONNECTED_L05_CRM_PREPARE=PASS_UNKNOWN_RESPONSE_RECONCILED_NO_REPLAY');
  }else if(state.clientStage==='RESOLVE'){
    await openOffer();await click('Revisar recursos de esta atención');await fill('rf-service-attention-panel [formcontrolname="vehicleDecision"]','READY');await fill('rf-service-attention-panel [formcontrolname="reason"]','SYNTHETIC reviewed driver and unit separately');await checked('rf-service-attention-panel [formcontrolname="releaseDriver"]');await checked('rf-service-attention-panel [formcontrolname="safetyAndTransferConfirmed"]');await click('Registrar revisión de recursos');await wait(`document.body.innerText.includes('Servicio actualizado.')`);console.log('CONNECTED_L05_CRM_SAFETY=PASS_EXPLICIT_RESOURCE_REVIEW');
  }else if(state.clientStage==='CANCEL'){
    await openOffer();await fill('rf-scheduled-service-panel input:not([type="checkbox"])','SYNTHETIC_CANCEL_CONTINUITY');await checked('rf-scheduled-service-panel input[type="checkbox"]');await click('Cancelar servicio expresamente');await wait(`document.querySelector('rf-scheduled-service-panel')?.innerText.includes('Servicio cancelado')`);console.log('CONNECTED_L05_CRM_CANCEL=PASS_EXPLICIT_CONTINUITY_CANCELLATION');
  }else throw new Error('UNKNOWN_STAGE');
  const ordinary=await evaluate('document.body.innerText');check(!ordinary.includes('SYNTHETIC L05 CONDUCTOR')&&!ordinary.includes('60000005')&&!ordinary.includes('60000006'),'ORDINARY_PII');
  console.log('CONNECTED_L05_CRM_RESULT=PASS_REAL_BROWSER_HTTP_TEST_DB');
}catch(error){console.log('CONNECTED_L05_CRM_RESULT=FAIL; PHASE='+phase+'; REASON='+(/^[A-Z_]+$/.test(error.message??'')?error.message:'DETAILS_REDACTED'));process.exitCode=1;}
finally{try{socket?.close();}catch{}if(browser&&!browser.killed)browser.kill();await pause(500);if(profile){const root=await realpath(tmpdir());let resolved;try{resolved=await realpath(profile);}catch{}if(resolved&&dirname(resolved).toLowerCase()===root.toLowerCase()&&basename(resolved).startsWith('rf-l05-browser-'))await rm(resolved,{recursive:true,force:true,maxRetries:5,retryDelay:200});}}
