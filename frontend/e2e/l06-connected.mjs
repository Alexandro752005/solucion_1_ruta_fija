// Automated real browser / production Angular / real backend+PG. No human claims.
import { spawn } from 'node:child_process';
import { mkdtemp, readFile, rm, realpath } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, dirname, basename } from 'node:path';
import { existsSync } from 'node:fs';
const base=process.env.RF_L06_CONNECTED_BASE,control=process.env.RF_L06_CONNECTED_CONTROL;
if(process.env.RF_L06_CONNECTED_CLIENTS!=='true'||!/^http:\/\/127\.0\.0\.1:\d+$/.test(base??'')||!control)process.exit(2);
let browser,profile,socket,seq=0,phase='INIT';const pending=new Map();
const pause=ms=>new Promise(r=>setTimeout(r,ms));const check=(v,c)=>{if(!v)throw new Error(c);};
function send(method,params={}){const id=++seq;return new Promise((resolve,reject)=>{pending.set(id,{resolve,reject});socket.send(JSON.stringify({id,method,params}));});}
async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.exceptionDetails)throw new Error('BROWSER_EXPRESSION');return r.result?.value;}
async function wait(expression){for(let i=0;i<300;i++){const r=await evaluate(expression);if(r)return r;await pause(100);}throw new Error('UI_WAIT');}
async function fill(selector,value){await wait(`!!document.querySelector(${JSON.stringify(selector)})`);await evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});e.value=${JSON.stringify(value)};e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));})()`);await evaluate('new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(()=>r(true))))');}
async function click(label){const e=`[...document.querySelectorAll('button')].find(e=>e.textContent.trim()===${JSON.stringify(label)}&&!e.disabled)`;await wait(`!!(${e})`);await evaluate(`(${e}).click()`);}
try{
  const response=await fetch(base+'/__l06/state',{headers:{'X-L06-Control':control}});check(response.ok,'CONTROL_REFUSED');const state=await response.json();
  const executable=['C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe','C:/Program Files/Google/Chrome/Application/chrome.exe'].find(existsSync);check(executable,'BROWSER_MISSING');
  profile=await mkdtemp(join(tmpdir(),'rf-l06-browser-'));browser=spawn(executable,['--headless=new','--no-first-run','--no-default-browser-check','--disable-background-networking','--disable-sync','--disable-extensions','--remote-debugging-address=127.0.0.1','--remote-debugging-port=0',`--user-data-dir=${profile}`,'about:blank'],{windowsHide:true,stdio:'ignore'});
  let port;for(let i=0;i<100;i++){try{port=(await readFile(join(profile,'DevToolsActivePort'),'utf8')).split('\n')[0];break;}catch{await pause(100);}}check(port,'BROWSER_START');
  const target=await(await fetch(`http://127.0.0.1:${port}/json/new?about:blank`,{method:'PUT'})).json();socket=new WebSocket(target.webSocketDebuggerUrl);await new Promise((r,j)=>{socket.onopen=r;socket.onerror=j;});socket.onmessage=m=>{const r=JSON.parse(m.data);if(r.id&&pending.has(r.id)){const p=pending.get(r.id);pending.delete(r.id);r.error?p.reject(new Error('CDP_REQUEST')):p.resolve(r.result);}};
  await send('Page.enable');await send('Runtime.enable');await send('Network.enable');
  await send('Network.setCookie',{name:'rf_refresh',value:state.browserRefresh,url:base+'/api/v1/auth',path:'/api/v1/auth',httpOnly:true,secure:false,sameSite:'Lax'});

  const checked=async(selector)=>{await evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});if(!e||e.disabled)throw new Error('CHECKBOX_NOT_READY');e.checked=true;e.dispatchEvent(new Event('change',{bubbles:true}));})()`);await pause(120);};
  const openIncident=async(id)=>{await send('Page.navigate',{url:base+'/incidents'});const selector='tr[data-incident-id="'+id+'"] button';await wait(`!!document.querySelector(${JSON.stringify(selector)})`);await evaluate(`document.querySelector(${JSON.stringify(selector)}).click()`);await wait(`!!document.querySelector('rf-incident-workflow-panel')`);};
  const openAnnouncement=async(id)=>{const selector='tr[data-announcement-id="'+id+'"] button';await wait(`!!document.querySelector(${JSON.stringify(selector)})`);await evaluate(`document.querySelector(${JSON.stringify(selector)}).click()`);await wait(`!!document.querySelector('rf-announcement-attention')`);};
  const contains=s=>`document.body.innerText.includes(${JSON.stringify(s)})`;
  phase='INCIDENT_COMMUNICATION_'+state.clientStage;
  if(state.clientStage==='ATTEND'){
    await openIncident(state.currentIncidentId);await wait(`document.querySelector('#incident-responsible option[value="${state.adminId}"]')`);
    if(await evaluate(`document.querySelector('rf-incident-workflow-panel').innerText.includes('Sin designar')`)){
      await fill('#incident-responsible',state.adminId);await fill('#incident-action-reason','SYNTHETIC connected explicit responsibility');await checked('#incident-action-confirm');await click('Registrar acción de incidencia');await wait(contains('SYNTHETIC connected explicit responsibility'));
    }
    if(!(await evaluate(contains('SYNTHETIC connected follow up')))){
      await fill('#incident-action','follow-up');await fill('#incident-action-reason','SYNTHETIC connected follow up');await checked('#incident-action-confirm');await click('Registrar acción de incidencia');await wait(contains('SYNTHETIC connected follow up'));
    }
    console.log('CONNECTED_L06_CRM_ATTEND=PASS_EXPLICIT_RESPONSIBILITY_APPEND_HISTORY');
  }else if(state.clientStage==='NOTICES'){
    await send('Page.navigate',{url:base+'/notices'});await wait(contains('Bandeja de avisos'));await click('Entregas no realizadas de la organización');await wait(contains(state.currentIncidentId));await wait(contains('Entrega externa no realizada'));check(!(await evaluate(contains('Proveedor aceptó el aviso'))),'FALSE_EXTERNAL_DELIVERY');console.log('CONNECTED_L06_CRM_NOTICES=PASS_DURABLE_EXTERNAL_NOT_CONFIGURED_VISIBLE_NO_DELIVERY_CLAIM');
  }else if(state.clientStage==='CLOSE'){
    await openIncident(state.currentIncidentId);
    if(await evaluate(`!!document.querySelector('#incident-action')`)){
      await fill('#incident-action','closure');await fill('#incident-diagnosis','SYNTHETIC connected diagnosis');await fill('#incident-measures','SYNTHETIC connected measures');await fill('#incident-result','SYNTHETIC connected result');await fill('#incident-evidence-required','YES');await fill('#incident-evidence-mode','REFERENCE');await fill('#incident-evidence-reference','SYNTHETIC_L06_REVIEW');await checked('#incident-action-confirm');await click('Registrar acción de incidencia');
    }
    await wait(contains('El cierre se conserva.'));await wait(contains('SYNTHETIC connected diagnosis'));console.log('CONNECTED_L06_CRM_CLOSE=PASS_STRUCTURED_CLOSURE_UNKNOWN_RESPONSE_RECONCILED');
  }else if(state.clientStage==='RELATED'){
    if(!state.relatedIncidentId){await openIncident(state.currentIncidentId);await click('Registrar caso posterior sin reabrir');await fill('#incident-severity','LOW');await fill('#incident-relation','RECURRENCE');await fill('#incident-description','SYNTHETIC connected recurrence');await wait(`!!document.querySelector('#incident-description')?.closest('form')?.querySelector('button[type="submit"]:not(:disabled)')`);await evaluate(`document.querySelector('#incident-description').closest('form').querySelector('button[type="submit"]').click()`);await wait(contains('Incidencia confirmada por el servidor.'));}
    console.log('CONNECTED_L06_CRM_RELATED=PASS_NEW_LINKED_CASE_OR_CONFIRMED_EXISTING');
  }else if(state.clientStage==='PUBLISH'){
    await send('Page.navigate',{url:base+'/announcements'});await wait(contains('Comunicados visibles'));
    if(!state.currentAnnouncementId){await click('Nuevo comunicado');await fill('#announcement-title','SYNTHETIC L06 important');await fill('#announcement-body','SYNTHETIC connected group notice');await fill('#announcement-audience','GROUP');await wait(`!!document.querySelector('#announcement-group option[value="${state.secondaryGroupId}"]')`);await fill('#announcement-group',state.secondaryGroupId);await checked('#announcement-read-ack');await click('Publicar comunicado');await wait(contains('Publicación sin confirmar.'));await click('Consultar publicación sin reenviar');await wait(contains('Publicación confirmada por el servidor; no se repitió el envío.'));}
    const id=state.currentAnnouncementId||await wait(`document.querySelector('tbody tr[data-announcement-id]')?.getAttribute('data-announcement-id')`);await openAnnouncement(id);await wait(contains('1 lecturas expresas pendientes'));console.log('CONNECTED_L06_CRM_PUBLISH=PASS_IMPORTANT_SECONDARY_GROUP_SNAPSHOT_NO_DUPLICATE_POST');
  }else if(state.clientStage==='WITHDRAW'){
    await send('Page.navigate',{url:base+'/announcements'});await wait(contains('Comunicados visibles'));await openAnnouncement(state.currentAnnouncementId);await wait(contains('0 lecturas expresas pendientes'));
    if(!(await evaluate(contains('Retirado por seguridad')))){await wait(contains('Leído expreso:'));await click('Retirar acceso por seguridad');await fill('#announcement-withdraw-reason','SYNTHETIC connected security withdrawal');await checked('#announcement-withdraw-confirm');await click('Confirmar retirada');}
    await wait(contains('Retirado por seguridad'));console.log('CONNECTED_L06_CRM_WITHDRAW=PASS_EXPLICIT_SECURITY_WITHDRAWAL_UNKNOWN_RESPONSE_RECONCILED');
  }else throw new Error('UNKNOWN_STAGE');
  const ordinary=await evaluate('document.body.innerText');check(!ordinary.includes('SYNTHETIC L06 CONDUCTOR')&&!ordinary.includes('60000007')&&!ordinary.includes('conductor.l06.51e69ee6@rutafija.test'),'ORDINARY_PII');
  console.log('CONNECTED_L06_CRM_RESULT=PASS_REAL_BROWSER_HTTP_TEST_DB');
}catch(error){console.log('CONNECTED_L06_CRM_RESULT=FAIL; PHASE='+phase+'; REASON='+(/^[A-Z_]+$/.test(error.message??'')?error.message:'DETAILS_REDACTED'));process.exitCode=1;}
finally{try{socket?.close();}catch{}if(browser&&!browser.killed)browser.kill();await pause(500);if(profile){const root=await realpath(tmpdir());let resolved;try{resolved=await realpath(profile);}catch{}if(resolved&&dirname(resolved).toLowerCase()===root.toLowerCase()&&basename(resolved).startsWith('rf-l06-browser-'))await rm(resolved,{recursive:true,force:true,maxRetries:5,retryDelay:200});}}
