// Bounded read-only CRM observation. Authentication is a synthetic test bootstrap,
// not a login/refresh test. All business GETs reach the real backend/security/PG.
import {createServer,request as httpRequest} from 'node:http';
import {spawn} from 'node:child_process';
import {existsSync} from 'node:fs';
import {readFile,writeFile,mkdir,mkdtemp,realpath,rm} from 'node:fs/promises';
import {resolve,join,dirname,basename,extname} from 'node:path';
import {tmpdir} from 'node:os';
import {createHash} from 'node:crypto';
const root=resolve(import.meta.dirname,'../..'),sha=b=>createHash('sha256').update(b).digest('hex');
if(process.argv.length!==3||process.argv[2]!=='--execute-read-only'||process.env.RF_L08_DESKTOP_MODE!=='EXISTING_SYNTHETIC_READ_ONLY')throw Error('EXPLICIT_READ_ONLY_MODE_REQUIRED');
const apiBase=process.env.RF_L08_DESKTOP_API;
if(apiBase!=='http://127.0.0.1:4316')throw Error('EXACT_BACKEND_REQUIRED');
const out=resolve(process.env.RF_L08_DESKTOP_OUTPUT??'');
if(!out.startsWith(join(root,'docs/evidencia/L-08/incremento-3-escritorio')+'/')&&!out.startsWith(join(root,'docs/evidencia/L-08/incremento-3-escritorio')+'\\'))throw Error('BOUNDED_OUTPUT_REQUIRED');
const dist=join(root,'frontend/dist/ruta-fija-crm/browser');
if(!existsSync(join(dist,'index.html')))throw Error('EXISTING_CRM_BUILD_REQUIRED');
await mkdir(out,{recursive:true});
const tokens={ADMIN:process.env.RF_L08_DESKTOP_ADMIN_TOKEN,SUPER_ADMIN:process.env.RF_L08_DESKTOP_SA_TOKEN};
if(Object.values(tokens).some(v=>!v))throw Error('TEST_PRINCIPALS_REQUIRED');
delete process.env.RF_L08_DESKTOP_ADMIN_TOKEN;delete process.env.RF_L08_DESKTOP_SA_TOKEN;
let currentRole='ADMIN';const requests=[],bootstrap=[],results=[],ownedConnections=new Set();
const pause=ms=>new Promise(r=>setTimeout(r,ms));
const server=createServer(async(req,res)=>{try{
 const u=new URL(req.url,'http://127.0.0.1');res.setHeader('Cache-Control','no-store');
 if(u.pathname.startsWith('/api/')){
  if(req.method==='POST'&&u.pathname==='/api/v1/auth/refresh'){
   req.resume();const me=await fetch(apiBase+'/api/v1/auth/me',{headers:{Authorization:'Bearer '+tokens[currentRole]}});
   if(me.status!==200)throw Error('REAL_PRINCIPAL_NOT_ELIGIBLE');const user=await me.json();
   bootstrap.push({at:new Date().toISOString(),role:currentRole,source:'EXPLICIT_SYNTHETIC_JWT_BOOTSTRAP_NOT_REAL_REFRESH',realMeHttpStatus:me.status});
   res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify({accessToken:tokens[currentRole],tokenType:'Bearer',expiresIn:600,user}));return;
  }
  const streamTicket=req.method==='POST'&&u.pathname==='/api/v1/operations/stream-ticket';
  if(req.method!=='GET'&&!streamTicket){req.resume();requests.push({at:new Date().toISOString(),method:req.method,path:u.pathname,status:405,source:'READ_ONLY_TEST_BOUNDARY'});res.writeHead(405);res.end();return;}
  req.resume();const started=performance.now();const response=await fetch(apiBase+u.pathname+u.search,{method:req.method,headers:{Authorization:req.headers.authorization??'',Accept:'application/json','Content-Type':'application/json'},...(streamTicket?{body:'{}'}:{}),signal:AbortSignal.timeout(20000)});
  const bytes=Buffer.from(await response.arrayBuffer());requests.push({at:new Date().toISOString(),role:currentRole,method:req.method,path:u.pathname,status:response.status,durationMs:Number((performance.now()-started).toFixed(3)),source:'REAL_BACKEND',...(streamTicket?{transientTicketNoDatabaseWrite:true}:{})});
  res.writeHead(response.status,{'Content-Type':response.headers.get('content-type')??'application/json'});res.end(bytes);return;
 }
 if(req.method!=='GET'){res.writeHead(405);res.end();return;}
 if(u.pathname==='/config/runtime-config.json'){res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify({apiBaseUrl:'/api/v1',appName:'Ruta Fija',environment:'l08-readonly-synthetic'}));return;}
 let file=resolve(dist,'.'+decodeURIComponent(u.pathname));if(file!==dist&&!file.startsWith(dist+'/')&&!file.startsWith(dist+'\\')){res.writeHead(404);res.end();return;}
 if(!existsSync(file)||extname(file)==='')file=join(dist,'index.html');
 res.setHeader('Content-Type',({'.js':'application/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png','.ico':'image/x-icon'})[extname(file)]??'text/html; charset=utf-8');res.end(await readFile(file));
}catch{res.writeHead(502);res.end();}});
server.on('connection',socket=>{ownedConnections.add(socket);socket.on('close',()=>ownedConnections.delete(socket));});
server.on('upgrade',(req,socket,head)=>{
 if(new URL(req.url,'http://127.0.0.1').pathname!=='/ws/operations'){socket.destroy();return;}
 const upstream=httpRequest(apiBase+req.url,{headers:{...req.headers,host:'127.0.0.1:4316'}});
 upstream.on('upgrade',(res,remote,remoteHead)=>{socket.write('HTTP/1.1 101 Switching Protocols\r\n'+Object.entries(res.headers).map(([k,v])=>k+': '+v).join('\r\n')+'\r\n\r\n');if(remoteHead.length)socket.write(remoteHead);if(head.length)remote.write(head);remote.pipe(socket);socket.pipe(remote);socket.on('error',()=>remote.destroy());remote.on('error',()=>socket.destroy());socket.on('close',()=>remote.destroy());});
 upstream.on('error',()=>socket.destroy());upstream.on('response',()=>socket.destroy());upstream.end();
});
await new Promise(r=>server.listen(0,'127.0.0.1',r));const base=`http://127.0.0.1:${server.address().port}`;
async function inspect(name,executable,factor,role){
 let browser,socket,profile,phase='START',sequence=0;const pending=new Map(),checks=[];
 const result={browser:name,role,requestedNativeZoom:factor,checks,pages:[]};
 const check=(ok,id,details={})=>{checks.push({id,result:ok?'PASS':'FAIL',...details});if(!ok)throw Error(id);};
 function send(method,params={}){return new Promise((resolve,reject)=>{const id=++sequence;const timer=setTimeout(()=>{pending.delete(id);reject(Error('CDP_TIMEOUT'));},20000);pending.set(id,{resolve:v=>{clearTimeout(timer);resolve(v);},reject:e=>{clearTimeout(timer);reject(e);}});socket.send(JSON.stringify({id,method,params}));});}
 async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error('EVALUATION_REFUSED');return r.result.value;}
 async function wait(expression){for(let i=0;i<150;i++){if(await evaluate(expression))return;await pause(100);}throw Error('UI_WAIT');}
 async function key(key,code,vk,shift=false){for(const type of ['keyDown','keyUp'])await send('Input.dispatchKeyEvent',{type,key,code,windowsVirtualKeyCode:vk,modifiers:shift?8:0,...(key==='Enter'&&type==='keyDown'?{text:'\r'}:{})});}
 async function screenshot(label){const r=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});const bytes=Buffer.from(r.data,'base64'),file=`${name}-${role}-${factor*100}-${label}.png`;await writeFile(join(out,file),bytes,{flag:'wx'});return{file,sha256:sha(bytes)};}
 try{
  check(existsSync(executable),'BROWSER_PRESENT');currentRole=role;
  profile=await mkdtemp(join(tmpdir(),'ruta-fija-l08-operational-'));await mkdir(join(profile,'Default'));
  // Native HostZoomMap preference, not CSS zoom, page-scale, or emulated DPR.
  await writeFile(join(profile,'Default/Preferences'),JSON.stringify({partition:{default_zoom_level:{x:Math.log(factor)/Math.log(1.2)}}}));
  browser=spawn(executable,['--headless=new','--window-size=1296,815','--force-device-scale-factor=1','--no-first-run','--no-default-browser-check','--disable-background-networking','--disable-sync','--disable-extensions','--remote-debugging-address=127.0.0.1','--remote-debugging-port=0',`--user-data-dir=${profile}`,'about:blank'],{windowsHide:true,stdio:'ignore',env:Object.fromEntries(['SystemRoot','WINDIR','TEMP','TMP','PATH','PATHEXT'].filter(k=>process.env[k]).map(k=>[k,process.env[k]]))});
  let port;for(let i=0;i<100;i++){try{port=(await readFile(join(profile,'DevToolsActivePort'),'utf8')).split('\n')[0];break;}catch{await pause(100);}}check(Boolean(port),'BROWSER_STARTED');
  const target=await(await fetch(`http://127.0.0.1:${port}/json/new?about:blank`,{method:'PUT'})).json();socket=new WebSocket(target.webSocketDebuggerUrl);await new Promise((r,j)=>{socket.onopen=r;socket.onerror=j;});
  socket.onmessage=e=>{const m=JSON.parse(e.data);if(m.id&&pending.has(m.id)){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(Error('CDP_REFUSED')):p.resolve(m.result);return;}if(m.method==='Fetch.requestPaused'){const r=m.params,u=new URL(r.request.url),allow=u.origin===base||['data:','blob:','about:'].includes(u.protocol);send(allow?'Fetch.continueRequest':'Fetch.failRequest',allow?{requestId:r.requestId}:{requestId:r.requestId,errorReason:'BlockedByClient'}).catch(()=>{});}};
  await send('Page.enable');await send('Runtime.enable');await send('Fetch.enable',{patterns:[{urlPattern:'*'}]});result.version=(await send('Browser.getVersion')).product;
  const pages=role==='ADMIN'?['dashboard','users','groups','vehicles','assignments','reports','audit']:['dashboard','organizations','platform-admins','platform-audit'];
  for(const page of pages){
   phase=page;const start=requests.length;await send('Page.navigate',{url:base+'/'+page});await wait(`!!document.querySelector('main h1')||!!document.querySelector('rf-page-header h1')`);await pause(400);
   await wait(`![...document.querySelectorAll('[role="status"]')].some(e=>/Cargando|Consultando|Calculando/.test(e.textContent))`);
   if(page==='dashboard')for(let attempt=0;attempt<3;attempt++){const v=await evaluate('({w:innerWidth,h:innerHeight})');if(Math.abs(v.w*factor-1280)<=3&&Math.abs(v.h*factor-720)<=3)break;const win=await send('Browser.getWindowForTarget');await send('Browser.setWindowBounds',{windowId:win.windowId,bounds:{width:Math.round(win.bounds.width+1280-v.w*factor),height:Math.round(win.bounds.height+720-v.h*factor)}});await pause(200);}
   const observation=await evaluate(`({path:location.pathname,dpr:devicePixelRatio,width:innerWidth,height:innerHeight,outerWidth,outerHeight,cssZoom:getComputedStyle(document.documentElement).zoom,pageScale:visualViewport.scale,documentWidth:document.documentElement.scrollWidth,title:document.querySelector('h1')?.textContent.trim(),alerts:[...document.querySelectorAll('[role="alert"]')].filter(e=>e.getClientRects().length).length,tables:[...document.querySelectorAll('table')].map(t=>({rows:t.querySelectorAll('tbody tr').length,headers:t.querySelectorAll('th').length,parentWidth:t.parentElement.clientWidth,scrollWidth:t.parentElement.scrollWidth,overflow:getComputedStyle(t.parentElement).overflowX})),unlabelled:[...document.querySelectorAll('input:not([type="hidden"]),select,textarea')].filter(e=>e.getClientRects().length&&!e.labels?.length&&!e.getAttribute('aria-label')&&!e.getAttribute('aria-labelledby')).map(e=>({tag:e.tagName,id:e.id})),piiEmailVisible:/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/i.test(document.body.innerText)})`);
   result.pages.push(observation);
   check(observation.path==='/'+page,'ROUTE_'+page.toUpperCase());check(Math.abs(observation.dpr-factor)<.02&&observation.cssZoom==='1'&&observation.pageScale===1,'NATIVE_ZOOM_'+page.toUpperCase());
   check(Math.abs(observation.width*factor-1280)<=3&&Math.abs(observation.height*factor-720)<=3,'PHYSICAL_CLIENT_1280_720_'+page.toUpperCase(),{width:observation.width,height:observation.height,factor});
   check(observation.documentWidth<=observation.width+1,'PAGE_REFLOW_'+page.toUpperCase(),{width:observation.width,documentWidth:observation.documentWidth});
   check(observation.alerts===0,'NO_OPERATIONAL_ALERT_'+page.toUpperCase());check(!observation.piiEmailVisible,'NO_EMAIL_IN_ORDINARY_VIEW_'+page.toUpperCase());
   check(observation.unlabelled.length===0,'CONTROLS_LABELLED_'+page.toUpperCase(),{unlabelled:observation.unlabelled});
   check(observation.tables.every(t=>t.headers>0&&(['auto','scroll'].includes(t.overflow)||t.scrollWidth<=t.parentWidth)),'TABLES_BOUNDED_'+page.toUpperCase());
   const ax=await send('Accessibility.getFullAXTree');observation.accessibility={tables:ax.nodes.filter(n=>!n.ignored&&n.role?.value==='table').length,unnamedControls:ax.nodes.filter(n=>!n.ignored&&['button','textbox','combobox'].includes(n.role?.value)&&!n.name?.value).map(n=>n.role.value)};
   check(observation.accessibility.unnamedControls.length===0,'AX_NAMED_CONTROLS_'+page.toUpperCase(),observation.accessibility);
   const relevant=requests.slice(start);check(relevant.some(r=>r.source==='REAL_BACKEND'&&r.status===200),'REAL_API_READ_'+page.toUpperCase());check(!relevant.some(r=>r.status>=400),'SUCCESSFUL_READS_'+page.toUpperCase());
   if(page==='groups'&&role==='ADMIN'){
    await evaluate(`document.querySelector('rf-page-header button').focus()`);await key('Enter','Enter',13);await wait(`!!document.querySelector('[role="dialog"]')`);
    const modal=await evaluate(`({labelled:!!document.getElementById(document.querySelector('[role="dialog"]').getAttribute('aria-labelledby')),focusInside:document.querySelector('[role="dialog"]').contains(document.activeElement),fieldCount:document.querySelectorAll('[role="dialog"] input,[role="dialog"] textarea').length})`);
    check(modal.labelled&&modal.focusInside&&modal.fieldCount>0,'MODAL_INITIAL_FOCUS_AND_NAME',modal);
    const count=await evaluate(`document.querySelectorAll('[role="dialog"] button:not([disabled]),[role="dialog"] input:not([disabled]),[role="dialog"] textarea:not([disabled])').length`);
    for(let i=0;i<count+2;i++){await key('Tab','Tab',9);check(await evaluate(`document.querySelector('[role="dialog"]').contains(document.activeElement)`),'MODAL_TAB_REMAINS_INSIDE_'+i);}
    await key('Escape','Escape',27);await wait(`!document.querySelector('[role="dialog"]')`);check(await evaluate(`document.activeElement===document.querySelector('rf-page-header button')`),'MODAL_ESCAPE_RETURNS_FOCUS');
   }
   if(page==='users'||page==='platform-admins')result.screenshot=await screenshot(page);
  }
  result.result='PASS_OPERATIONAL_READ_ONLY_NATIVE_BROWSER_ZOOM';
 }catch(e){result.result='FAIL_KEEP_OBSERVATION';result.phase=phase;result.reason=/^[A-Z0-9_]+$/.test(e.message??'')?e.message:'DETAILS_REDACTED';try{result.failureScreenshot=await screenshot('failure');}catch{}}
 finally{try{await send('Browser.close');}catch{}try{socket?.close();}catch{}if(browser&&browser.exitCode===null){await Promise.race([new Promise(r=>browser.once('exit',r)),pause(2000)]);if(browser.exitCode===null)browser.kill();}await pause(250);if(profile){const temp=await realpath(tmpdir());let actual;try{actual=await realpath(profile);}catch{}if(actual&&dirname(actual).toLowerCase()===temp.toLowerCase()&&basename(actual).startsWith('ruta-fija-l08-operational-'))try{await rm(actual,{recursive:true,force:true,maxRetries:5,retryDelay:200});}catch{result.temporaryProfileRetirement='PENDING_FILE_LOCK';}}}
 return result;
}
try{
 scan:for(const [name,path]of[['Edge','C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'],['Chrome','C:/Program Files/Google/Chrome/Application/chrome.exe']])for(const factor of[1,2,3])for(const role of['ADMIN','SUPER_ADMIN']){const r=await inspect(name,path,factor,role);results.push(r);await writeFile(join(out,`${name}-${role}-${factor*100}-checks.json`),JSON.stringify(r,null,2)+'\n',{flag:'wx'});console.log(`L08_DESKTOP_${name.toUpperCase()}_${role}_${factor*100}=${r.result}`);if(r.result!=='PASS_OPERATIONAL_READ_ONLY_NATIVE_BROWSER_ZOOM')break scan;}
 const sources={};for(const p of['frontend/e2e/l08-operational-accessibility.mjs','frontend/dist/ruta-fija-crm/browser/index.html','frontend/package-lock.json','frontend/src/styles.scss'])sources[p]=sha(await readFile(join(root,p)));
 const evidence={observedAt:new Date().toISOString(),scope:'EXISTING_SYNTHETIC_PRINCIPALS_READ_ONLY_BUSINESS_API',results,bootstrap,requests,sources,realLoginOrRefreshAccredited:false,screenReaderHumanExecuted:false,databaseWritesAllowed:false,accountsCreated:0,mailSent:0,pilotEnabled:false};
 await writeFile(join(out,'navegadores.json'),JSON.stringify(evidence,null,2)+'\n',{flag:'wx'});if(results.length!==12||results.some(r=>r.result!=='PASS_OPERATIONAL_READ_ONLY_NATIVE_BROWSER_ZOOM'))process.exitCode=1;
}finally{for(const socket of ownedConnections)socket.destroy();server.closeAllConnections();await new Promise(r=>server.close(r));tokens.ADMIN=null;tokens.SUPER_ADMIN=null;}
