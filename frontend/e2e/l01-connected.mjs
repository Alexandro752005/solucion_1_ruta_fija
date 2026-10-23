// Opt-in runner owned by ConnectedL01ClientsIT. No packages, downloads or production hooks.
import { spawn } from 'node:child_process';
import { mkdtemp, readFile, rm, realpath } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, dirname, basename } from 'node:path';
import { existsSync } from 'node:fs';

const base = process.env.RF_L01_CONNECTED_BASE;
const control = process.env.RF_L01_CONNECTED_CONTROL;
const stage = process.argv[2];
if (!base?.startsWith('http://127.0.0.1:') || !control || process.env.RF_L01_CONNECTED_CLIENTS !== 'true') process.exit(2);
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
let step = 'bootstrap', socket, browser, profile, bearer, activation;
let sequence = 0;
const pending = new Map();
const responses = [];
const secretState = async () => (await fetch(`${base}/__l01/state`, { headers: { 'X-Synthetic-Control': control } })).json();
const save = async data => fetch(`${base}/__l01/state`, {method:'POST', headers:{'X-Synthetic-Control':control,'Content-Type':'application/json'},body:JSON.stringify(data)});
const otp = async role => (await (await fetch(`${base}/__l01/otp?${role}`, {headers:{'X-Synthetic-Control':control}})).json()).code;
function assert(value, name) { if (!value) throw new Error(name); }
async function send(method, params={}) {
  const id=++sequence;
  return new Promise((resolve,reject)=>{pending.set(id,{resolve,reject});socket.send(JSON.stringify({id,method,params}));});
}
async function evaluate(expression) {
  const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});
  if(r.exceptionDetails)throw new Error('browser_expression');
  return r.result?.value;
}
async function wait(expression) {
  const end=Date.now()+18000;
  while(Date.now()<end){try{const value=await evaluate(expression);if(value)return value;}catch{}await pause(100);}
  throw new Error('condition_timeout');
}
async function fill(selector,value) {
  await wait(`!!document.querySelector(${JSON.stringify(selector)})`);
  await wait(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});return e.tagName!=='SELECT'||[...e.options].some(o=>o.value===${JSON.stringify(value)})})()`);
  await evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});e.value=${JSON.stringify(value)};e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));return true})()`);
}
async function click(text,selector='button') {
  const expr=`[...document.querySelectorAll(${JSON.stringify(selector)})].find(e=>e.textContent.trim()===${JSON.stringify(text)}&&!e.disabled)`;
  await wait(`!!(${expr})`);await evaluate(`(${expr}).click()`);
}
async function text(value) { await wait(`document.body.innerText.includes(${JSON.stringify(value)})`); }
async function go(path) { await send('Page.navigate',{url:base+path});await wait('document.readyState === "complete"'); }
async function login(email,password) {
  const context=step;
  step=context+'_login_form';await go('/login');
  await wait('!!document.querySelector("#email") || [...document.querySelectorAll("button")].some(e=>e.textContent.trim()==="Cerrar sesión")');
  if(!(await evaluate('!!document.querySelector("#email")')))await click('Cerrar sesión');
  await fill('#email',email);await fill('#password',password);await click('Ingresar al sistema');
  step=context+'_login_response';
  await wait('!!document.querySelector("rf-app-shell") || !!document.querySelector(".app-shell") || !location.pathname.startsWith("/login")');
  step=context;
}
async function check(selector) {
  await wait(`!!document.querySelector(${JSON.stringify(selector)})`);
  await evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});if(!e.checked)e.click()})()`);
}
const pass = name => console.log(`CONNECTED_CRM_${name}=PASS`);
async function api(path,method='GET',body,extra={}) {
  const response=await fetch(base+'/api/v1'+path,{method,headers:{Origin:base,...(bearer?{Authorization:bearer}:{}),'Content-Type':'application/json',...extra},body:body===undefined?undefined:JSON.stringify(body)});
  responses.push({path:'/api/v1'+path,status:response.status,channel:'direct-test'});
  return {status:response.status,body:await response.json()};
}
async function identity(document,owner=false) {
  await fill('#admin-identity-document',document);
  await fill('#admin-identity-reference','SYNTHETIC visual inspection');
  await check('[formcontrolname="originalReviewed"]');await check('[formcontrolname="identityConfirmed"]');
  if(owner){await fill('#owner-current-password',(await secretState()).password);await check('[formcontrolname="ownerDeclaration"]');}
  else await fill('#admin-decision-reason','SYNTHETIC authorized test action');
}
async function activate(credential,role,data) {
  await go('/admin-activation');await fill('#activation-credential',credential);
  await fill('#activation-password',data.password);await fill('#activation-confirmation',data.password);await click('Registrar contraseña');
  await click('Enviar código de verificación');await wait('!!document.querySelector("#activation-otp")');
  await fill('#activation-otp',await otp(role));await click('Verificar correo');
  await click('Consultar documento vigente');await text('SYNTHETIC-TEST-ONLY');
  assert(await evaluate('document.querySelector("[formcontrolname=accepted]").checked===false'),'premarked_terms');
  await check('[formcontrolname="accepted"]');await click('Registrar aceptación');await text('Aceptación registrada.');
  await click('Completar activación');await text('Activación completada');
}
async function openDossier(requestId) {
  await go('/onboarding-requests');
  const row=`[...document.querySelectorAll('tbody tr')].find(e=>e.innerText.includes(${JSON.stringify(requestId)}))`;
  await wait(`!!(${row})`);await evaluate(`(${row}).querySelector('button').click()`);
  await click('Revisar documentación');await text('Revisión personal');
}
async function reviewDocuments() {
  const fields=[{documentType:'DNI',documentNumber:'50000001',fullName:'SYNTHETIC APPLICANT',issuer:'SYNTHETIC'},
    {licenseNumber:'SYN-LICENSE',licenseClass:'A',licenseCategory:'A-IIA',restrictions:'NINGUNA',issuer:'SYNTHETIC'}];
  for(let i=0;i<5;i++){
    if(i<2)for(const [key,value] of Object.entries(fields[i]))await fill(`#document-${i}-${key}`,value);
    await fill(`#expiry-mode-${i}`,i===1?'DATED':'NO_EXPIRY');
    if(i===1)await fill('#valid-until-1','2027-10-15');
    await fill(`#applicability-${i}`,'CONFIRMED');await fill(`#condition-${i}`,'SYNTHETIC applicability decision');
    await fill(`#source-${i}`,'SYNTHETIC title reference; no actual habilitation');
    await fill(`#result-${i}`,i<2?'CONFORME':'NO_APLICA_JUSTIFICADO');
    await fill(`#review-reference-${i}`,'SYNTHETIC controlled visual inspection');
    await fill(`#observation-${i}`,'Regla sintetica de prueba explicitamente no aplicable');
  }
  await click('Guardar cotejo');await text('Cambio confirmado por el servidor.');
}

try {
  const executable=['C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe','C:/Program Files/Google/Chrome/Application/chrome.exe'].find(existsSync);
  assert(executable,'browser_missing');
  profile=await mkdtemp(join(tmpdir(),'rf-l01-browser-'));
  browser=spawn(executable,['--headless=new','--no-first-run','--no-default-browser-check','--disable-background-networking','--disable-sync','--disable-extensions','--remote-debugging-address=127.0.0.1','--remote-debugging-port=0',`--user-data-dir=${profile}`,'about:blank'],{windowsHide:true,stdio:'ignore'});
  let port;
  for(let i=0;i<100;i++){try{port=(await readFile(join(profile,'DevToolsActivePort'),'utf8')).split('\n')[0];break;}catch{await pause(100);}}
  assert(port,'browser_start');
  const target=await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`,{method:'PUT'})).json();
  socket=new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject;});
  socket.onmessage=event=>{
    const m=JSON.parse(event.data);
    if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p?.reject(new Error('cdp_failure')):p?.resolve(m.result);}
    if(m.method==='Network.requestWillBeSent'){
      const headers=m.params.request.headers;
      if(headers.Authorization)bearer=headers.Authorization;
      if(headers['X-Admin-Activation-Token'])activation=headers['X-Admin-Activation-Token'];
    }
    if(m.method==='Network.responseReceived'&&m.params.response.url.startsWith(base+'/api/'))responses.push({path:new URL(m.params.response.url).pathname,status:m.params.response.status});
  };
  await send('Page.enable');await send('Runtime.enable');await send('Network.enable');
  const data=await secretState();
  if(stage==='setup'){
    step='owner_login_identity';await login(data.ownerEmail,data.password);await go('/platform-admins');
    await click('Revisar mi identidad como titular');await identity('90000001',true);await click('Confirmar operación');
    await text('El servidor confirmó la declaración');pass('OWNER_IDENTITY');
    step='create_admin';await click('Preparar alta ADMIN');await fill('#admin-organization',data.organizationId);
    await fill('#admin-registration-email',data.adminEmail);await fill('#admin-registration-name','SYNTHETIC ADMIN');
    await identity('40000001');await click('Confirmar operación');
    const credential=await wait('document.querySelector("[aria-labelledby=activation-credential-title] code")?.textContent.trim()');
    pass('ADMIN_PENDING_WITH_IDENTITY');
    step='activate_admin';await activate(credential,'admin',data);pass('ADMIN_ACTIVATION_SYNTHETIC_TERMS');
    step='issue_invitation';await login(data.adminEmail,data.password);await go('/invitations');
    await fill('#invitation-recipient-email',data.applicantEmail);await click('Emitir invitación');
    const invitationCode=await wait('document.querySelector(".invitation-code")?.textContent.trim()');
    assert(/^\d{6}$/.test(invitationCode),'invitation_shape');await save({invitationCode});
    await click('Ocultar código');assert(!(await evaluate('document.body.innerText')).includes(data.applicantEmail),'ordinary_email');
    pass('INVITATION_ISSUED_HIDDEN_LIST');
    await fill('#invitation-recipient-email',data.rejectionEmail);await click('Emitir invitación');
    const rejectionInvitationCode=await wait('document.querySelector(".invitation-code")?.textContent.trim()');
    await save({rejectionInvitationCode});await click('Ocultar código');
  } else if(stage==='return'){
    step='review_return';await login(data.adminEmail,data.password);await openDossier(data.requestId);
    await fill('#review-reason','Complete los requisitos observados.');await click('Devolver para subsanación');
    await text('Devuelta para subsanación');pass('RESTRICTED_REVIEW_RETURN_HISTORY');
  } else if(stage==='approve'){
    step='review_approve';await login(data.adminEmail,data.password);await openDossier(data.requestId);await reviewDocuments();
    await fill('#review-reason','Revision personal sintetica conforme.');await click('Aprobar personalmente');
    step='approval_ui_confirmation';await text('Aprobación personal registrada.');
    step='approval_without_group_api';
    const current=await api(`/onboarding/requests/${data.requestId}/documentary-expediente`);
    assert(current.status===200&&current.body.personalApproved===true,'personal_approval_state');
    const before=await api(`/onboarding/requests/${data.requestId}/provision`,'POST',{expectedVersion:current.body.version,operationKey:crypto.randomUUID(),groupId:null,reason:'SYNTHETIC'});
    assert(before.status===409&&before.body.code==='RESOURCE_CONFLICT','group_required');pass('PERSONAL_APPROVAL_WITHOUT_PROVISION');
    await click('Consultar grupos activos');await fill('#provision-group',data.groupId);await click('Crear cuenta y conductor');await text('Cuenta vinculada:');
    pass('PROVISION_ACTIVE_GROUP_DRIVER_NOT_OPERATIONAL');
  } else if(stage==='recover'){
    step='assisted_recovery';await login(data.adminEmail,data.password);await go('/users');await click('Recuperar cuenta');
    await fill('#recovery-new-email',data.recoveredEmail);await fill('#recovery-evidence-method','IN_PERSON');
    await fill('#recovery-evidence-reference','SYNTHETIC controlled visual inspection');await fill('#recovery-reason','SYNTHETIC lost access case');
    await check('[formcontrolname="expedienteReviewed"]');await check('[formcontrolname="independentEvidenceConfirmed"]');
    await click('Autorizar y enviar OTP');
    const recoveryToken=await wait('document.querySelector(".recovery-secret")?.textContent.trim()');
    const recoveryId=await evaluate('document.querySelector("code:not(.recovery-secret)").textContent.trim()');
    await save({recoveryToken,recoveryId});pass('ASSISTED_RECOVERY_AUTHORIZED');
  } else if(stage==='reject'){
    step='review_reject';await login(data.adminEmail,data.password);await openDossier(data.rejectionRequestId);
    await fill('#review-reason','SYNTHETIC review rejection with reason.');await click('Rechazar con motivo');await text('Rechazada');
    const rejected=await api(`/onboarding/requests/${data.rejectionRequestId}/documentary-expediente`);
    assert(rejected.body.status==='REJECTED'&&!rejected.body.provisionedUserId,'rejected_without_account');pass('REJECTION_RETAINED_WITH_HISTORY_NO_ACCOUNT');
  } else if(stage==='replace'){
    step='replacement_prepare';await login(data.ownerEmail,data.password);await go('/platform-admins');await click('Preparar reemplazo');
    await fill('#admin-registration-email',data.replacementEmail);await fill('#admin-registration-name','SYNTHETIC SUBSTITUTE');await identity('40000002');
    await fill('#admin-replacement-reference','SYNTHETIC controlled independent check');await check('[formcontrolname="identityChecked"]');await check('[formcontrolname="replacementAuthorized"]');
    await click('Confirmar operación');const credential=await wait('document.querySelector("[aria-labelledby=activation-credential-title] code")?.textContent.trim()');
    step='capture_old_session';await login(data.adminEmail,data.password);await go('/invitations');await text('Historial de invitaciones');const outgoing=bearer;
    step='replacement_activate';await activate(credential,'replacement',data);
    const denied=await api('/invitations','GET',undefined,{Authorization:outgoing});assert(denied.status===403||denied.status===401,'old_session_not_revoked');
    await login(data.replacementEmail,data.password);await go('/invitations');await text('Historial de invitaciones');pass('REPLACEMENT_REVOKES_OUTGOING_SESSION');
    const wrongRole=await api('/platform/admins');assert(wrongRole.status===403,'admin_platform_denial');pass('DIRECT_API_ROLE_DENIAL');
    const foreign=await api(`/groups/${data.otherGroupId}/drivers`);assert(foreign.status===404,'tenant_object_denial');pass('DIRECT_API_OTHER_ORGANIZATION_OBJECT_DENIED');
    step='conductor_crm_denial';const result=await api('/auth/login','POST',{email:data.recoveredEmail,password:data.password+'-Recovered'});
    assert(result.status===403,'conductor_crm_denial');pass('CONDUCTOR_CRM_DENIED');
  } else throw new Error('unknown_stage');
  console.log(`CONNECTED_CRM_STAGE_${stage.toUpperCase()}=PASS`);
} catch {
  console.error(`CONNECTED_CRM_FAILURE_STAGE=${stage};STEP=${step}`);process.exitCode=1;
  try{console.error('CONNECTED_CRM_DIAGNOSTIC='+JSON.stringify({ui:await evaluate('({path:location.pathname,inputIds:[...document.querySelectorAll("input")].map(e=>e.id),buttons:[...document.querySelectorAll("button")].map(e=>e.textContent.trim())})'),responses}));}catch{}
} finally {
  try{await send('Browser.close');}catch{}socket?.close();
  if(browser&&!browser.killed)browser.kill();
  // Only this fresh, resolved browser profile is eligible for recursive cleanup.
  if(profile){await pause(500);try{const p=await realpath(profile);const t=await realpath(tmpdir());if(dirname(p)===t&&basename(p).startsWith('rf-l01-browser-'))await rm(p,{recursive:true,force:true,maxRetries:3,retryDelay:200});}catch{}}
}
