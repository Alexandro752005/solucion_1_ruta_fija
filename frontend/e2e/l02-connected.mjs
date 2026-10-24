// Test-source only. Real Angular UI -> backend HTTP -> ruta_fija_test.
import { spawn } from 'node:child_process';
import { mkdtemp, readFile, rm, realpath } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, dirname, basename } from 'node:path';
import { existsSync } from 'node:fs';
const base=process.env.RF_L02_CONNECTED_BASE, control=process.env.RF_L02_CONNECTED_CONTROL;
if(process.env.RF_L02_CONNECTED_CLIENTS!=='true'||!/^http:\/\/127\.0\.0\.1:\d+$/.test(base??'')||!control)process.exit(2);
let action='INIT';let phase='BOOTSTRAP',browser,profile,socket,seq=0;const pending=new Map();const responses=[];
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const check=(ok,code)=>{if(!ok)throw new Error(code);};
function send(method,params={}){const id=++seq;return new Promise((resolve,reject)=>{pending.set(id,{resolve,reject});socket.send(JSON.stringify({id,method,params}));});}
async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.exceptionDetails)throw new Error('BROWSER_EXPRESSION');return r.result?.value;}
async function wait(expression){for(let i=0;i<150;i++){const value=await evaluate(expression);if(value)return value;await pause(100);}throw new Error('UI_WAIT');}
async function fill(selector,value){action=selector;await wait(`!!document.querySelector(${JSON.stringify(selector)})`);await evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});e.value=${JSON.stringify(value)};e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));})()`);await evaluate('new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(()=>r(true))))');}
async function click(label){action=label;const e=`[...document.querySelectorAll('button')].find(e=>e.textContent.trim()===${JSON.stringify(label)}&&!e.disabled)`;await wait(`!!(${e})`);await evaluate(`(${e}).click()`);}
async function version(){return evaluate(`(()=>{const d=[...document.querySelectorAll('dt')].find(x=>x.textContent.trim()==='Versión');return d?.nextElementSibling?.textContent.trim()})()`);}
async function change(label){await fill('#qualification-reason','Synthetic connected authorized review');const old=await version();await click(label);await wait(`(()=>{const d=[...document.querySelectorAll('dt')].find(x=>x.textContent.trim()==='Versión');return d?.nextElementSibling?.textContent.trim()!==${JSON.stringify(old)}&&!!d})()`);await wait(`![...document.querySelectorAll('[role="status"]')].some(e=>e.innerText.includes('Esperando confirmación'))`);}
try{
  const r=await fetch(base+'/__l02/state',{headers:{'X-L02-Control':control}});check(r.status===200,'CONTROL_REFUSED');const state=await r.json();
  const executable=['C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe','C:/Program Files/Google/Chrome/Application/chrome.exe'].find(existsSync);check(executable,'BROWSER_MISSING');
  profile=await mkdtemp(join(tmpdir(),'rf-l02-browser-'));browser=spawn(executable,['--headless=new','--no-first-run','--no-default-browser-check','--disable-background-networking','--disable-sync','--disable-extensions','--remote-debugging-address=127.0.0.1','--remote-debugging-port=0',`--user-data-dir=${profile}`,'about:blank'],{windowsHide:true,stdio:'ignore'});
  let port;for(let i=0;i<100;i++){try{port=(await readFile(join(profile,'DevToolsActivePort'),'utf8')).split('\n')[0];break;}catch{await pause(100);}}check(port,'BROWSER_START');
  const target=await(await fetch(`http://127.0.0.1:${port}/json/new?about:blank`,{method:'PUT'})).json();socket=new WebSocket(target.webSocketDebuggerUrl);await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject;});
  socket.onmessage=event=>{const m=JSON.parse(event.data);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p?.reject(new Error('CDP_FAILURE')):p?.resolve(m.result);}if(m.method==='Network.responseReceived'&&m.params.response.url.startsWith(base+'/api/'))responses.push({path:new URL(m.params.response.url).pathname,status:m.params.response.status});};
  await send('Page.enable');await send('Runtime.enable');await send('Network.enable');
  // The runner supplies an isolated test refresh family for the existing
  // synthetic ADMIN. Ordinary /auth/refresh and every backend gate remain active.
  await send('Network.setCookie',{name:'rf_refresh',value:state.browserRefresh,url:base+'/api/v1/auth',path:'/api/v1/auth',httpOnly:true,secure:false,sameSite:'Lax'});
  phase='ORDINARY_STATE';await send('Page.navigate',{url:base+`/drivers/${state.driverId}/qualification`});await wait(`document.body.innerText.includes('Estado registrado')`);
  check(!(await evaluate('document.body.innerText')).includes('SYNTHETIC L02 CONDUCTOR'),'ORDINARY_PII');check(!(await evaluate('document.body.innerText')).includes('60000002'),'ORDINARY_DNI');
  console.log('CONNECTED_L02_CRM_ORDINARY=PASS_NO_PERSONAL_DATA');
  phase='OPEN_RESTRICTED';await click('Abrir expediente restringido ADMIN');await wait(`document.body.innerText.includes('Expediente restringido · ADMIN autorizado')`);
  await fill('#qualification-reason','Synthetic connected review');
  for(const scope of ['VEHICLE','COMPANY']){
    phase='REVIEW_SCOPE_'+scope;
    await fill('#qualification-review-scope',scope);if(scope==='VEHICLE')await fill('#qualification-review-vehicle',state.vehicleId);await click('Preparar revisión del catálogo seleccionado');
    const requirements=scope==='VEHICLE'?['VEH-01','VEH-02','VEH-03','VEH-04']:['EMP-01','EMP-02','EMP-03'];
    for(let i=0;i<requirements.length;i++){
      const req=requirements[i];phase='REVIEW_'+req.replace('-','_');const keys=await evaluate(`[...document.querySelectorAll('[id^="review-field-${i}-"]')].map(x=>x.id)`);
      for(const id of keys){const key=id.split(`review-field-${i}-`)[1];const values={plate:'SYN-L02',category:'M1',bodyType:'SEDAN',brand:'SYNTHETIC',model:'L02',year:'2026',insuranceType:'SOAT',modality:'TAXI',serviceScope:'SYNTHETIC CHICLAYO'};await fill('#'+id,values[key]??'SYNTHETIC reference');}
      const na=['VEH-03','VEH-04','EMP-03'].includes(req);
      await fill(`#review-expiry-${i}`,req==='VEH-02'?'DATED':'NO_EXPIRY');if(req==='VEH-02')await fill(`#review-date-${i}`,'2027-10-20');
      await fill(`#review-applicability-${i}`,'CONFIRMED');await fill(`#review-condition-${i}`,'Synthetic conditional applicability; no real title');
      await fill(`#review-source-${i}`,'Synthetic source for test only');await fill(`#review-result-${i}`,na?'NO_APLICA_JUSTIFICADO':'CONFORME');await fill(`#review-reference-${i}`,'Synthetic visual review');
    }
    await change('Guardar revisión estructurada');
  }
  console.log('CONNECTED_L02_CRM_REVIEW=PASS_VEHICLE_COMPANY_SEPARATE');
  phase='UNIT_REQUEST_DECISION';const requests=await evaluate(`[...document.querySelectorAll('[id^="request-vehicle-"]')].map(x=>x.id)`);
  check(requests.length===1,'ONE_OWN_REQUEST_REQUIRED');await fill('#'+requests[0],state.vehicleId);await change('Aprobar solicitud');
  phase='PERIOD';await fill('#qualification-reason','Synthetic connected authorization');await fill('#qualification-vehicle',state.vehicleId);await fill('#qualification-kind','OWN');await fill('#qualification-time-mode','INSTANT');await fill('#qualification-start-at',new Date(Date.now()-1000).toISOString());await change('Autorizar período');
  check((await evaluate('document.body.innerText')).includes('Bloqueada'),'PERIOD_NOT_AUTO_ACTIVATION');
  phase='ACTIVATE';await change('Solicitar habilitación operativa');await wait(`document.body.innerText.includes('Confirmada por el servidor')`);
  console.log('CONNECTED_L02_CRM_ACTIVATION=PASS_EXPLICIT_AFTER_REVIEW_PERIOD');
  phase='SCRUB';await click('Cerrar expediente y limpiar datos');await wait(`!document.body.innerText.includes('Expediente restringido · ADMIN autorizado')`);
  check(!(await evaluate('document.body.innerText')).includes('SYNTHETIC L02 CONDUCTOR'),'SCRUB_NAME');check(!(await evaluate('document.body.innerText')).includes('60000002'),'SCRUB_DNI');
  check(responses.some(r=>r.path.endsWith('/qualification/activate')&&r.status===200),'ACTIVATION_SERVER_RESPONSE');
  console.log('CONNECTED_L02_CRM_RESULT=PASS_REAL_BROWSER_HTTP_TEST_DB');
  console.log('CONNECTED_L02_CRM_HTTP='+JSON.stringify(responses));
}catch(error){try{console.log('CONNECTED_L02_CRM_ACTION='+action);console.log('CONNECTED_L02_CRM_DOM='+JSON.stringify(await evaluate(`({ids:[...document.querySelectorAll('[id]')].map(e=>e.id).filter(x=>x.startsWith('qualification')||x.startsWith('review')),headings:[...document.querySelectorAll('h2')].map(e=>e.textContent.trim()),hasAlert:!!document.querySelector('[role=alert]')})`)));}catch{}console.log('CONNECTED_L02_CRM_RESULT=FAIL; PHASE='+phase+'; REASON='+(/^[A-Z_]+$/.test(error.message??'')?error.message:'DETAILS_REDACTED'));process.exitCode=1;}
finally{
  try{socket?.close();}catch{}if(browser&&!browser.killed)browser.kill();await pause(500);
  if(profile){const root=await realpath(tmpdir());let resolved;try{resolved=await realpath(profile);}catch{}if(resolved&&dirname(resolved).toLowerCase()===root.toLowerCase()&&basename(resolved).startsWith('rf-l02-browser-'))await rm(resolved,{recursive:true,force:true,maxRetries:5,retryDelay:200});}
}
