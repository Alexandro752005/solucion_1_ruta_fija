// Opt-in inspection of the built public CRM only. No backend, accounts or OTP.
import {createServer} from 'node:http';
import {spawn} from 'node:child_process';
import {existsSync} from 'node:fs';
import {readFile,writeFile,mkdir,mkdtemp,realpath,rm} from 'node:fs/promises';
import {resolve,join,dirname,basename,extname} from 'node:path';
import {tmpdir} from 'node:os';
import {createHash} from 'node:crypto';
const root=resolve(import.meta.dirname,'../..');
if(process.argv.length!==3||process.argv[2]!=='--execute-public')throw Error('EXPLICIT_PUBLIC_ONLY_MODE_REQUIRED');
const dist=join(root,'frontend/dist/ruta-fija-crm/browser');
if(!existsSync(join(dist,'index.html')))throw Error('EXISTING_CRM_BUILD_REQUIRED');
const run=Date.now().toString(),out=join(root,'backend/target',`l08-public-browser-${run}`);await mkdir(out);
const api=[];
const server=createServer(async(req,res)=>{try{
 const url=new URL(req.url,'http://127.0.0.1');res.setHeader('Cache-Control','no-store');
 if(url.pathname.startsWith('/api/')){api.push({method:req.method,path:url.pathname});res.writeHead(503,{'Content-Type':'application/json'});res.end(JSON.stringify({code:'SYNTHETIC_BACKEND_NOT_CONNECTED',message:'Ensayo público local sin backend.'}));return;}
 const path=resolve(dist,'.'+decodeURIComponent(url.pathname));
 if(path!==dist&&!path.startsWith(dist+'/')&&!path.startsWith(dist+'\\')){res.writeHead(404);res.end();return;}
 let file=path;if(!existsSync(file)||extname(file)==='')file=join(dist,'index.html');
 res.setHeader('Content-Type',({'.js':'application/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png','.ico':'image/x-icon'})[extname(file)]??'text/html; charset=utf-8');res.end(await readFile(file));
}catch{res.writeHead(500);res.end();}});
await new Promise(r=>server.listen(0,'127.0.0.1',r));const base=`http://127.0.0.1:${server.address().port}`;
const pause=ms=>new Promise(r=>setTimeout(r,ms)),sha=b=>createHash('sha256').update(b).digest('hex');
const results=[];
async function inspect(name,executable){
 let browser,socket,profile,phase='START';let sequence=0;const pending=new Map(),requests=[],checks=[];const result={browser:name,scope:'PUBLIC_LOGIN_ACTIVATION_ONLY_NO_BACKEND',checks};
 const check=(ok,id,details={})=>{checks.push({id,result:ok?'PASS':'FAIL',...details});if(!ok)throw Error(id);};
 function send(method,params={}){return new Promise((resolve,reject)=>{const id=++sequence;pending.set(id,{resolve,reject});socket.send(JSON.stringify({id,method,params}));});}
 async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error('EVALUATION_REFUSED');return r.result.value;}
 async function wait(expression){for(let i=0;i<150;i++){if(await evaluate(expression))return;await pause(100);}throw Error('PUBLIC_UI_WAIT');}
 async function key(key,code,virtualKeyCode){for(const type of ['keyDown','keyUp'])await send('Input.dispatchKeyEvent',{type,key,code,windowsVirtualKeyCode:virtualKeyCode,...(key==='Enter'&&type==='keyDown'?{text:'\r'}:{})});}
 async function screenshot(label){const capture=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});const bytes=Buffer.from(capture.data,'base64'),file=`${name}-${label}.png`;await writeFile(join(out,file),bytes);return {file,size:bytes.length,sha256:sha(bytes)};}
 try{
  if(!existsSync(executable)){result.result='UNAVAILABLE_NOT_TESTED';return result;}
  profile=await mkdtemp(join(tmpdir(),'ruta-fija-l08-browser-'));
  browser=spawn(executable,['--headless=new','--no-first-run','--no-default-browser-check','--disable-background-networking','--disable-sync','--disable-extensions','--remote-debugging-address=127.0.0.1','--remote-debugging-port=0',`--user-data-dir=${profile}`,'about:blank'],{windowsHide:true,stdio:'ignore'});
  let port;for(let i=0;i<100;i++){try{port=(await readFile(join(profile,'DevToolsActivePort'),'utf8')).split('\n')[0];break;}catch{await pause(100);}}
  check(Boolean(port),'BROWSER_STARTED');const target=await(await fetch(`http://127.0.0.1:${port}/json/new?about:blank`,{method:'PUT'})).json();socket=new WebSocket(target.webSocketDebuggerUrl);await new Promise((r,j)=>{socket.onopen=r;socket.onerror=j;});
  socket.onmessage=message=>{const entry=JSON.parse(message.data);if(entry.id&&pending.has(entry.id)){const p=pending.get(entry.id);pending.delete(entry.id);entry.error?p.reject(Error('CDP_REFUSED')):p.resolve(entry.result);return;}
   if(entry.method==='Fetch.requestPaused'){const r=entry.params,u=new URL(r.request.url);const allow=u.origin===base||['data:','blob:','about:'].includes(u.protocol);send(allow?'Fetch.continueRequest':'Fetch.failRequest',allow?{requestId:r.requestId}:{requestId:r.requestId,errorReason:'BlockedByClient'}).catch(()=>{});}
   if(entry.method==='Network.requestWillBeSent'){const r=entry.params.request;requests.push({url:r.url,method:r.method});}
  };
  await send('Page.enable');await send('Runtime.enable');await send('Network.enable');await send('Fetch.enable',{patterns:[{urlPattern:'*'}]});
  result.version=(await send('Browser.getVersion')).product;
  await send('Emulation.setDeviceMetricsOverride',{width:1280,height:720,deviceScaleFactor:1,mobile:false});
  phase='LOGIN';await send('Page.navigate',{url:base+'/login'});await wait(`!!document.querySelector('#email')&&!!document.querySelector('#password')`);
  const viewport=await evaluate('({width:innerWidth,height:innerHeight,scrollWidth:document.documentElement.scrollWidth})');check(viewport.width===1280&&viewport.height===720,'VIEWPORT_1280_720',viewport);check(viewport.scrollWidth<=1281,'LOGIN_NO_HORIZONTAL_CLIPPING');
  await evaluate(`document.querySelector('#email').focus()`);const order=[];for(let i=0;i<5;i++){order.push(await evaluate(`({tag:document.activeElement.tagName,id:document.activeElement.id,label:document.activeElement.getAttribute('aria-label')??document.activeElement.textContent.trim()})`));await key('Tab','Tab',9);}
  check(order[0].id==='email'&&order[1].id==='password'&&order[2].label==='Mostrar contraseña'&&order[3].label==='Ingresar al sistema','KEYBOARD_FORM_ORDER',{order});
  await evaluate(`document.querySelector('#email').focus()`);await key('Tab','Tab',9);
  const focus=await evaluate(`(()=>{const e=document.activeElement,s=getComputedStyle(e);return{visible:e.matches(':focus-visible'),outline:s.outlineStyle,width:s.outlineWidth,shadow:s.boxShadow};})()`);check(focus.visible&&(focus.outline!=='none'&&parseFloat(focus.width)>0||focus.shadow!=='none'),'KEYBOARD_FOCUS_INDICATOR',focus);
  const before=api.filter(r=>r.method==='POST').length;await evaluate(`document.querySelector('button[type="submit"]').focus()`);await key('Enter','Enter',13);await wait(`document.querySelectorAll('[aria-invalid="true"]').length===2&&document.activeElement.id==='email'`);
  const errors=await evaluate(`[...document.querySelectorAll('[aria-invalid="true"]')].map(e=>({id:e.id,labelled:!!document.querySelector('label[for="'+e.id+'"]'),described:!!document.getElementById(e.getAttribute('aria-describedby'))}))`);check(errors.every(e=>e.labelled&&e.described),'EMPTY_SUBMIT_LABELLED_ERRORS_AND_FIRST_INVALID_FOCUS',{fields:errors});check(api.filter(r=>r.method==='POST').length===before,'EMPTY_SUBMIT_NO_AUTHENTICATION_REQUEST');
  const ax=await send('Accessibility.getFullAXTree');const named=ax.nodes.filter(n=>!n.ignored&&['textbox','textField'].includes(n.role?.value)).map(n=>({role:n.role.value,name:n.name?.value??''}));check(named.length>=2&&named.every(n=>n.name),'LOGIN_ACCESSIBILITY_TREE_NAMED_FIELDS',{fields:named});
  result.screenshots=[await screenshot('login-errors')];
  const colors=await evaluate(`(()=>{const rows=[],walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);let n;while((n=walker.nextNode())){const e=n.parentElement;if(!n.textContent.trim()||!e||e.closest('script,style')||!e.getClientRects().length)continue;const s=getComputedStyle(e);let a=e,bg=null;while(a){const v=getComputedStyle(a);if(v.backgroundColor!=='rgba(0, 0, 0, 0)'&&v.backgroundColor!=='transparent'){bg=v.backgroundColor;break;}a=a.parentElement;}rows.push({selector:e.tagName.toLowerCase()+(e.id?'#'+e.id:''),color:s.color,background:bg??'rgb(255, 255, 255)',fontSize:parseFloat(s.fontSize),fontWeight:s.fontWeight});}return rows;})()`);
  const rgb=v=>{const p=v.match(/[\d.]+/g)?.map(Number);return p&&p.length>=3&&(p.length===3||p[3]===1)?p.slice(0,3):null;};
  const luminance=c=>c.map(x=>x/255).map(x=>x<=.04045?x/12.92:((x+.055)/1.055)**2.4).reduce((sum,x,i)=>sum+x*[.2126,.7152,.0722][i],0);
  const contrast=colors.map(c=>{const fg=rgb(c.color),bg=rgb(c.background);if(!fg||!bg)return {...c,result:'MANUAL_REVIEW_REQUIRED'};const a=luminance(fg),b=luminance(bg),ratio=(Math.max(a,b)+.05)/(Math.min(a,b)+.05),threshold=c.fontSize>=24||c.fontSize>=18.6667&&Number(c.fontWeight)>=700?3:4.5;return {...c,ratio,threshold,result:ratio>=threshold?'PASS':'FAIL'};});
  result.contrastDiagnostic=contrast;check(!contrast.some(c=>c.result==='FAIL'),'VISIBLE_LOGIN_TEXT_CONTRAST_DIAGNOSTIC',{checked:contrast.filter(c=>c.result==='PASS').length,manualReview:contrast.filter(c=>c.result==='MANUAL_REVIEW_REQUIRED').length});
  await evaluate(`document.documentElement.style.zoom='2'`);const zoomed=await evaluate('({zoom:getComputedStyle(document.documentElement).zoom,width:innerWidth,scrollWidth:document.documentElement.scrollWidth})');check(zoomed.zoom==='2','CSS_ZOOM_200_DIAGNOSTIC',zoomed);result.screenshots.push(await screenshot('css-zoom-200'));result.zoomLimit='CSS zoom/reflow diagnostic, not a manual browser-toolbar zoom or screen-reader trial';
  await evaluate(`document.documentElement.style.zoom='1'`);phase='ACTIVATION';await send('Page.navigate',{url:base+'/admin-activation'});await wait(`document.querySelectorAll('input').length>0`);
  const labels=await evaluate(`[...document.querySelectorAll('input:not([type="hidden"]),select,textarea')].filter(e=>e.getClientRects().length).map(e=>({id:e.id,type:e.type,labelled:!!e.labels?.length||!!e.getAttribute('aria-label')||!!e.getAttribute('aria-labelledby')}))`);check(labels.length>0&&labels.every(e=>e.labelled),'ACTIVATION_VISIBLE_CONTROLS_LABELLED',{fields:labels});
  check(!requests.some(r=>{const u=new URL(r.url);return !['data:','blob:','about:'].includes(u.protocol)&&u.origin!==base;}),'TESTED_PAGE_NO_EXTERNAL_REQUEST');
  check(api.filter(r=>r.method!=='GET'&&r.path!=='/api/v1/auth/refresh').length===0,'NO_COMMAND_OR_EMAIL_CALL_STARTUP_REFRESH_STUB_ONLY');result.result='PASS_PUBLIC_BOUNDED_AUTOMATED';
 }catch(error){result.result='FAIL_KEEP_OBSERVATION';result.phase=phase;result.reason=/^[A-Z0-9_]+$/.test(error.message??'')?error.message:'DETAILS_REDACTED';try{result.publicDiagnostic=await evaluate(`({activeId:document.activeElement?.id,invalidFields:document.querySelectorAll('[aria-invalid="true"]').length,errorDescriptions:document.querySelectorAll('.field-error').length})`);result.failureScreenshot=await screenshot('failure');}catch{}}
 finally{try{socket?.close();}catch{}if(browser&&!browser.killed)browser.kill();await pause(500);if(profile){const temp=await realpath(tmpdir());let actual;try{actual=await realpath(profile);}catch{}if(actual&&dirname(actual).toLowerCase()===temp.toLowerCase()&&basename(actual).startsWith('ruta-fija-l08-browser-'))await rm(actual,{recursive:true,force:true,maxRetries:5,retryDelay:200});}}
 return result;
}
try{
 for(const [name,path] of [['Edge','C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'],['Chrome','C:/Program Files/Google/Chrome/Application/chrome.exe']])results.push(await inspect(name,path));
 const buildIdentity={};
 for(const path of ['frontend/dist/ruta-fija-crm/browser/index.html','frontend/package.json','frontend/package-lock.json','frontend/src/app/features/auth/login.page.ts'])buildIdentity[path]=sha(await readFile(join(root,path)));
 const summary={observedAt:new Date().toISOString(),scope:'PUBLIC_UI_ONLY_CURRENT_LOCAL_BUILD_NO_BACKEND_NO_ACCOUNTS',buildIdentity,results,backendConnected:false,screenReaderHumanExecuted:false,pilotEnabled:false,outputDirectory:out.substring(root.length+1).replaceAll('\\','/'),apiCalls:api};
 await writeFile(join(out,'resultado.json'),JSON.stringify(summary,null,2)+'\n');console.log('L08_PUBLIC_BROWSER_EVIDENCE='+summary.outputDirectory+'/resultado.json');for(const r of results)console.log('L08_PUBLIC_BROWSER_'+r.browser.toUpperCase()+'='+r.result);if(results.some(r=>r.result!=='PASS_PUBLIC_BOUNDED_AUTOMATED'))process.exitCode=1;
}finally{await new Promise(r=>server.close(r));}
