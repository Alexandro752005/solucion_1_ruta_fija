// Automated real browser / production Angular / real backend+PG. No human claims.
import { spawn } from 'node:child_process';
import { mkdtemp, readFile, rm, realpath } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, dirname, basename } from 'node:path';
import { existsSync } from 'node:fs';
const base=process.env.RF_L03_CONNECTED_BASE,control=process.env.RF_L03_CONNECTED_CONTROL;
if(process.env.RF_L03_CONNECTED_CLIENTS!=='true'||!/^http:\/\/127\.0\.0\.1:\d+$/.test(base??'')||!control)process.exit(2);
let browser,profile,socket,seq=0,phase='INIT';const pending=new Map();
const pause=ms=>new Promise(r=>setTimeout(r,ms));const check=(v,c)=>{if(!v)throw new Error(c);};
function send(method,params={}){const id=++seq;return new Promise((resolve,reject)=>{pending.set(id,{resolve,reject});socket.send(JSON.stringify({id,method,params}));});}
async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.exceptionDetails)throw new Error('BROWSER_EXPRESSION');return r.result?.value;}
async function wait(expression){for(let i=0;i<150;i++){const r=await evaluate(expression);if(r)return r;await pause(100);}throw new Error('UI_WAIT');}
async function fill(selector,value){await wait(`!!document.querySelector(${JSON.stringify(selector)})`);await evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});e.value=${JSON.stringify(value)};e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));})()`);await evaluate('new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(()=>r(true))))');}
async function click(label){const e=`[...document.querySelectorAll('button')].find(e=>e.textContent.trim()===${JSON.stringify(label)}&&!e.disabled)`;await wait(`!!(${e})`);await evaluate(`(${e}).click()`);}
try{
  const response=await fetch(base+'/__l03/state',{headers:{'X-L03-Control':control}});check(response.ok,'CONTROL_REFUSED');const state=await response.json();
  const executable=['C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe','C:/Program Files/Google/Chrome/Application/chrome.exe'].find(existsSync);check(executable,'BROWSER_MISSING');
  profile=await mkdtemp(join(tmpdir(),'rf-l03-browser-'));browser=spawn(executable,['--headless=new','--no-first-run','--no-default-browser-check','--disable-background-networking','--disable-sync','--disable-extensions','--remote-debugging-address=127.0.0.1','--remote-debugging-port=0',`--user-data-dir=${profile}`,'about:blank'],{windowsHide:true,stdio:'ignore'});
  let port;for(let i=0;i<100;i++){try{port=(await readFile(join(profile,'DevToolsActivePort'),'utf8')).split('\n')[0];break;}catch{await pause(100);}}check(port,'BROWSER_START');
  const target=await(await fetch(`http://127.0.0.1:${port}/json/new?about:blank`,{method:'PUT'})).json();socket=new WebSocket(target.webSocketDebuggerUrl);await new Promise((r,j)=>{socket.onopen=r;socket.onerror=j;});socket.onmessage=m=>{const r=JSON.parse(m.data);if(r.id&&pending.has(r.id)){const p=pending.get(r.id);pending.delete(r.id);r.error?p.reject(new Error('CDP_REQUEST')):p.resolve(r.result);}};
  await send('Page.enable');await send('Runtime.enable');await send('Network.enable');
  await send('Network.setCookie',{name:'rf_refresh',value:state.browserRefresh,url:base+'/api/v1/auth',path:'/api/v1/auth',httpOnly:true,secure:false,sameSite:'Lax'});
  phase='ROUTE_CATALOG';const name='SYNTHETIC L03 '+state.runId.replace(/[0-9]/g,'x');await send('Page.navigate',{url:base+'/routes'});await wait(`document.body.innerText.includes('Catálogo de la organización')&&!document.body.innerText.includes('Consultando…')`);
  const exists=await evaluate(`[...document.querySelectorAll('button')].some(e=>e.textContent.trim()===${JSON.stringify(name)})`);
  if(exists)await click(name);
  else{
    await click('Nueva ruta');await fill('#route-name',name);const ids=await evaluate(`[...document.querySelectorAll('[id^="route-stop-"]')].map(e=>e.id)`);check(ids.length===3,'THREE_STOPS_REQUIRED');
    for(let i=0;i<3;i++)await fill('#'+ids[i],['Synthetic start','Synthetic middle','Synthetic end'][i]);await fill('#route-reason','SYNTHETIC-CONNECTED-REVIEW');await click('Guardar nueva versión');
  }
  await wait(`document.body.innerText.includes('Historial de versiones')`);check((await evaluate(`document.querySelectorAll('details').length`))===1,'ONE_VERSION_REQUIRED');
  console.log('CONNECTED_L03_CRM_CATALOG=PASS_REAL_BROWSER_CREATE_ORDERED_VERSION');
  phase='MAINTENANCE';await send('Page.navigate',{url:base+`/vehicles/${state.vehicleId}/maintenance`});await wait(`document.body.innerText.includes('Versión 0')||document.body.innerText.includes('TRABAJO-')`);
  let body=await evaluate('document.body.innerText');
  if(!body.includes('TRABAJO-')){
    const first=new Date(Date.now()-5*3600*1000+24*3600*1000).toISOString().slice(0,10);await fill('#maintenance-reason','SYNTHETIC-CONNECTED-PLAN');await fill('#maintenance-startDate',first);await fill('#maintenance-endDate',first);
    await click('Revisar todos los compromisos desde el inicio');await wait(`document.body.innerText.includes('Sin compromisos detectados')`);
    await evaluate(`document.querySelector('#maintenance-confirmed').click()`);await click('Registrar mantenimiento');await wait(`document.body.innerText.includes('TRABAJO-')`);
  }
  body=await evaluate('document.body.innerText');
  if(!body.includes('CANCELLED')){
    await fill('#maintenance-reason','SYNTHETIC-CONNECTED-CANCEL');await evaluate(`(()=>{const e=document.querySelector('#maintenance-confirmed');if(!e.checked)e.click()})()`);await click('Cancelar solo si aún no comenzó');await wait(`document.body.innerText.includes('CANCELLED')`);
  }
  check((await evaluate('document.body.innerText')).includes('Sin bloqueo de mantenimiento ahora'),'NO_FALSE_BLOCK');
  console.log('CONNECTED_L03_CRM_MAINTENANCE=PASS_PREVIEW_CREATE_CANCEL_HISTORY');
  console.log('CONNECTED_L03_CRM_RESULT=PASS_REAL_BROWSER_HTTP_TEST_DB');
}catch(error){console.log('CONNECTED_L03_CRM_RESULT=FAIL; PHASE='+phase+'; REASON='+(/^[A-Z_]+$/.test(error.message??'')?error.message:'DETAILS_REDACTED'));process.exitCode=1;}
finally{try{socket?.close();}catch{}if(browser&&!browser.killed)browser.kill();await pause(500);if(profile){const root=await realpath(tmpdir());let resolved;try{resolved=await realpath(profile);}catch{}if(resolved&&dirname(resolved).toLowerCase()===root.toLowerCase()&&basename(resolved).startsWith('rf-l03-browser-'))await rm(resolved,{recursive:true,force:true,maxRetries:5,retryDelay:200});}}
