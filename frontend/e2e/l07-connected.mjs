// Automated browser / production Angular / real backend + PostgreSQL test fixture.
// Does not represent a human action or authorize an external delivery.
import { spawn } from 'node:child_process';
import { mkdtemp, mkdir, readFile, readdir, rm, realpath } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { tmpdir } from 'node:os';
import { join, dirname, basename } from 'node:path';
import { existsSync } from 'node:fs';
const base=process.env.RF_L07_CONNECTED_BASE,control=process.env.RF_L07_CONNECTED_CONTROL;
const run=process.env.RF_L07_CONNECTED_RUN_ID,requestedStage=process.env.RF_L07_CONNECTED_STAGE;
if(process.env.RF_L07_CONNECTED_CLIENTS!=='true'||!/^http:\/\/127\.0\.0\.1:\d+$/.test(base??'')||!control||run!=='6b496135-b86b-4c5b-938f-a60ddcfe32db')process.exit(2);
let browser,profile,socket,seq=0,phase='INIT';const pending=new Map(),posts=[],exports=[],requests=[];
const pause=ms=>new Promise(r=>setTimeout(r,ms));const check=(value,code)=>{if(!value)throw new Error(code);};
function send(method,params={}){const id=++seq;return new Promise((resolve,reject)=>{pending.set(id,{resolve,reject});socket.send(JSON.stringify({id,method,params}));});}
async function evaluate(expression){const response=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(response.exceptionDetails)throw new Error('BROWSER_EXPRESSION');return response.result?.value;}
async function wait(expression){for(let i=0;i<300;i++){const value=await evaluate(expression);if(value)return value;await pause(100);}throw new Error('UI_WAIT');}
async function fill(selector,value){await wait(`!!document.querySelector(${JSON.stringify(selector)})`);await evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});e.value=${JSON.stringify(value)};e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));})()`);await pause(100);}
async function click(label){const expression=`[...document.querySelectorAll('button')].find(e=>e.textContent.trim()===${JSON.stringify(label)}&&!e.disabled)`;await wait(`!!(${expression})`);await evaluate(`(${expression}).click()`);}
const contains=text=>`document.body.innerText.includes(${JSON.stringify(text)})`;
try {
  phase='CONTROL';
  const actor=requestedStage==='PLATFORM_AUDIT'?'?actor=SUPER_ADMIN':'';
  const response=await fetch(base+'/__l07/state'+actor,{headers:{'X-L07-Control':control}});check(response.ok,'CONTROL_REFUSED');const state=await response.json();
  check(!state.runId||state.runId===run,'WRONG_CASE');const stage=requestedStage||state.clientStage;check(stage,'MISSING_STAGE');
  const executable=['C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe','C:/Program Files/Google/Chrome/Application/chrome.exe'].find(existsSync);check(executable,'BROWSER_MISSING');
  profile=await mkdtemp(join(tmpdir(),'rf-l07-browser-'));browser=spawn(executable,['--headless=new','--no-first-run','--no-default-browser-check','--disable-background-networking','--disable-sync','--disable-extensions','--remote-debugging-address=127.0.0.1','--remote-debugging-port=0',`--user-data-dir=${profile}`,'about:blank'],{windowsHide:true,stdio:'ignore'});
  let port;for(let i=0;i<100;i++){try{port=(await readFile(join(profile,'DevToolsActivePort'),'utf8')).split('\n')[0];break;}catch{await pause(100);}}check(port,'BROWSER_START');
  const target=await(await fetch(`http://127.0.0.1:${port}/json/new?about:blank`,{method:'PUT'})).json();socket=new WebSocket(target.webSocketDebuggerUrl);await new Promise((r,j)=>{socket.onopen=r;socket.onerror=j;});
  socket.onmessage=message=>{const entry=JSON.parse(message.data);if(entry.id&&pending.has(entry.id)){const p=pending.get(entry.id);pending.delete(entry.id);entry.error?p.reject(new Error('CDP_REQUEST')):p.resolve(entry.result);return;}
    if(entry.method==='Network.requestWillBeSent'){const request=entry.params.request;const url=new URL(request.url);if(url.pathname.startsWith('/api/v1/'))requests.push({path:url.pathname,method:request.method});if(request.method==='POST'&&url.pathname==='/api/v1/reports/services/emissions'){let body;try{body=JSON.parse(request.postData??'{}');}catch{}posts.push({operationKey:body?.operationKey,from:body?.from,to:body?.to});}}
    if(entry.method==='Network.responseReceived'){const r=entry.params.response;if(/\/api\/v1\/reports\/services\/emissions\/[^/]+\/export/.test(r.url)){const h=Object.fromEntries(Object.entries(r.headers).map(([key,value])=>[key.toLowerCase(),value]));exports.push({url:r.url,status:r.status,id:h['x-report-emission-id'],sha:h['x-report-content-sha256']});}}
  };
  await send('Page.enable');await send('Runtime.enable');await send('Network.enable');
  const downloads=join(profile,'downloads');await mkdir(downloads);await send('Browser.setDownloadBehavior',{behavior:'allow',downloadPath:downloads});
  await send('Network.setCookie',{name:'rf_refresh',value:state.browserRefresh,url:base+'/api/v1/auth',path:'/api/v1/auth',httpOnly:true,secure:false,sameSite:'Lax'});
  phase=stage;
  if(stage==='REPORT_BEFORE'||stage==='REPORT_AFTER') {
    await send('Page.navigate',{url:base+'/reports'});await wait(`!!document.querySelector('rf-service-reports')`);await fill('#service-report-from',state.reportFrom);await fill('#service-report-to',state.reportTo);await click('Consultar vista vigente');await wait(contains('Vista vigente'));
    if(stage==='REPORT_BEFORE') {
      check(posts.length===0,'UNEXPECTED_AUTOMATIC_ISSUE');await click('Emitir nueva versión');await wait(contains('Resultado pendiente de confirmar'));check(posts.length===1,'ISSUE_NOT_SINGLE');await click('Consultar o recuperar emisión');await wait(`!!document.querySelector('[data-selected-emission-id]')`);
      check(posts.length===2&&posts[0].operationKey===posts[1].operationKey&&posts[0].from===posts[1].from&&posts[0].to===posts[1].to,'RECOVERY_CHANGED_KEY');
      console.log('CONNECTED_L07_CRM_ISSUE_RECOVERY=PASS_SAME_OPERATION_KEY_AFTER_LOST_RESPONSE');
    } else {
      const oldSelector=state.currentEmissionId?`button[data-emission-id="${state.currentEmissionId}"]`:'button[data-emission-revision="1"]';await wait(`!!document.querySelector(${JSON.stringify(oldSelector)})`);await evaluate(`document.querySelector(${JSON.stringify(oldSelector)}).click()`);await wait(`!!document.querySelector('[data-selected-emission-id]')`);
      const previous=await evaluate(`(()=>{const e=document.querySelector('[data-selected-emission-id]');return{id:e.getAttribute('data-selected-emission-id'),sha:e.getAttribute('data-selected-emission-sha256'),revision:e.getAttribute('data-selected-emission-revision')};})()`);
      await click('Emitir nueva versión');await wait(`document.querySelector('[data-selected-emission-id]')?.getAttribute('data-selected-emission-id')!==${JSON.stringify(previous.id)}&&!!document.querySelector('[data-selected-emission-id]')`);
      await wait(contains('Anterior '+previous.id));const newer=await evaluate(`document.querySelector('[data-selected-emission-id]').getAttribute('data-selected-emission-id')`);
      check(newer!==previous.id,'EMISSION_REPLACED_IN_PLACE');await evaluate(`document.querySelector('button[data-emission-id="${previous.id}"]').click()`);await wait(`document.querySelector('[data-selected-emission-id]')?.getAttribute('data-selected-emission-id')===${JSON.stringify(previous.id)}`);check(await evaluate(`document.querySelector('[data-selected-emission-id]').getAttribute('data-selected-emission-sha256')===${JSON.stringify(previous.sha)}`),'OLD_HASH_CHANGED');
      await evaluate(`document.querySelector('button[data-emission-id="${newer}"]').click()`);await wait(`document.querySelector('[data-selected-emission-id]')?.getAttribute('data-selected-emission-id')===${JSON.stringify(newer)}`);
      console.log('CONNECTED_L07_CRM_VERSIONS=PASS_NEW_EMISSION_PREVIOUS_HASH_PRESERVED');
    }
    const selected=await evaluate(`(()=>{const e=document.querySelector('[data-selected-emission-id]');return{id:e.getAttribute('data-selected-emission-id'),sha:e.getAttribute('data-selected-emission-sha256')};})()`);
    for(const label of ['CSV de esta emisión','Excel de esta emisión','PDF de esta emisión']){await click(label);await wait(`![...document.querySelectorAll('button')].find(e=>e.textContent.trim()===${JSON.stringify(label)})?.disabled`);}
    for(let i=0;i<100&&exports.length<3;i++)await pause(100);check(exports.length===3,'MISSING_FORMAT_RESPONSES');check(exports.every(r=>r.status===200&&r.id===selected.id&&r.sha===selected.sha),'FORMATS_DIFFERENT_SNAPSHOT');
    console.log('CONNECTED_L07_CRM_EXPORTS=PASS_CSV_XLSX_PDF_SAME_PERSISTED_ID_AND_SHA');
    let files=[];for(let i=0;i<100;i++){files=(await readdir(downloads)).filter(name=>/\.(csv|xlsx|pdf)$/.test(name));if(files.length===3)break;await pause(100);}check(files.length===3,'DOWNLOAD_FILES_NOT_COMPLETE');
    const fileProof=[];for(const name of files.sort()){const bytes=await readFile(join(downloads,name));check(bytes.length>0,'EMPTY_DOWNLOADED_FILE');fileProof.push({format:name.split('.').at(-1),size:bytes.length,sha256:createHash('sha256').update(bytes).digest('hex')});}
    console.log('CONNECTED_L07_CRM_FILE_HASHES='+JSON.stringify(fileProof));
  } else if(stage==='DASHBOARD') {
    await send('Page.navigate',{url:base+'/dashboard'});await wait(contains('Atención prioritaria'));await wait(`document.querySelectorAll('.priority-list li').length===8`);const text=await evaluate('document.body.innerText');check(text.indexOf('Incidencias críticas abiertas')<text.indexOf('Servicios próximos'),'WRONG_PRIORITY');check(requests.some(r=>r.path==='/api/v1/dashboard/admin'),'ADMIN_SUMMARY_NOT_CONNECTED');check(!requests.some(r=>r.path==='/api/v1/platform/dashboard'),'TENANT_PLATFORM_CROSSOVER');
    console.log('CONNECTED_L07_CRM_DASHBOARD=PASS_ORDERED_TENANT_PRIORITY_QUEUES');
  } else if(stage==='PLATFORM_AUDIT') {
    await send('Page.navigate',{url:base+'/dashboard'});await wait(contains('Control de plataforma'));await wait(contains('Activaciones ADMIN pendientes'));await send('Page.navigate',{url:base+'/platform-audit'});await wait(contains('Auditoría de plataforma'));await wait(`!document.body.innerText.includes('Cargando auditoría')`);
    check(requests.some(r=>r.path==='/api/v1/platform/dashboard')&&requests.some(r=>r.path==='/api/v1/platform/audit-events'),'PLATFORM_NOT_CONNECTED');check(!requests.some(r=>r.path==='/api/v1/dashboard/admin'||r.path==='/api/v1/audit-events'||r.path==='/api/v1/reports/availability'),'PLATFORM_READ_TENANT_ENDPOINT');
    console.log('CONNECTED_L07_CRM_PLATFORM=PASS_AGGREGATE_AND_PLATFORM_AUDIT_NO_TENANT_FALLBACK');
  } else throw new Error('UNKNOWN_STAGE');
  const ordinary=await evaluate('document.body.innerText');check(!/\bconductor\.l07\.[^\s@]*@|\bsa\.human\.[^\s@]*@|SYNTHETIC L07 CONDUCTOR/.test(ordinary),'ORDINARY_PII');
  console.log('CONNECTED_L07_CRM_RESULT=PASS_REAL_BROWSER_HTTP_TEST_DB');
} catch(error) { console.log('CONNECTED_L07_CRM_RESULT=FAIL; PHASE='+phase+'; REASON='+(/^[A-Z_]+$/.test(error.message??'')?error.message:'DETAILS_REDACTED'));process.exitCode=1; }
finally {try{socket?.close();}catch{}if(browser&&!browser.killed)browser.kill();await pause(500);if(profile){const root=await realpath(tmpdir());let resolved;try{resolved=await realpath(profile);}catch{}if(resolved&&dirname(resolved).toLowerCase()===root.toLowerCase()&&basename(resolved).startsWith('rf-l07-browser-'))await rm(resolved,{recursive:true,force:true,maxRetries:5,retryDelay:200});}}
