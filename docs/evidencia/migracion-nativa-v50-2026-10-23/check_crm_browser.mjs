import {spawn} from 'node:child_process';
import {writeFile} from 'node:fs/promises';
import path from 'node:path';
const root=process.cwd();
const run=path.join(root,'.runtime/migracion-nativa-v50-20261023_1714');
const browser=spawn('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',[
  '--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check',
  '--remote-debugging-address=127.0.0.1','--remote-debugging-port=9223',
  '--user-data-dir='+path.join(run,'edge-ui-check'),'about:blank'
],{stdio:'ignore',windowsHide:true});
let ws;
try {
  const until=Date.now()+20000;
  let version;
  while(Date.now()<until){
    try{version=await (await fetch('http://127.0.0.1:9223/json/version')).json();break;}catch{await new Promise(r=>setTimeout(r,200));}
  }
  if(!version)throw new Error('OWN_BROWSER_START_TIMEOUT');
  const target=await(await fetch('http://127.0.0.1:9223/json/new?about:blank',{method:'PUT'})).json();
  ws=new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve,reject)=>{ws.addEventListener('open',resolve,{once:true});ws.addEventListener('error',reject,{once:true});});
  let id=0;const pending=new Map(), exceptions=[],resourceErrors=[];
  ws.addEventListener('message',event=>{
    const msg=JSON.parse(event.data);
    if(msg.id){const p=pending.get(msg.id);if(p){pending.delete(msg.id);clearTimeout(p.timeout);msg.error?p.reject(new Error(msg.error.message)):p.resolve(msg.result);}}
    if(msg.method==='Runtime.exceptionThrown')exceptions.push(msg.params.exceptionDetails.text);
    if(msg.method==='Network.loadingFailed'&&!msg.params.canceled)resourceErrors.push(msg.params.errorText);
  });
  const send=(method,params={})=>new Promise((resolve,reject)=>{const key=++id;const timeout=setTimeout(()=>{pending.delete(key);reject(new Error('CDP_TIMEOUT '+method));},10000);pending.set(key,{resolve,reject,timeout});ws.send(JSON.stringify({id:key,method,params}));});
  await send('Runtime.enable');await send('Network.enable');await send('Page.enable');
  await send('Page.navigate',{url:'http://127.0.0.1:4300/'});
  let dom;
  const deadline=Date.now()+20000;
  while(Date.now()<deadline){
    const result=await send('Runtime.evaluate',{expression:"({title:document.title,url:location.href,hasEmail:!!document.querySelector('input[type=email]'),hasPassword:!!document.querySelector('input[type=password]'),hasLoginText:document.body.innerText.includes('Iniciar sesión')})",returnByValue:true});
    dom=result.result.value;
    if(dom?.hasEmail&&dom?.hasPassword&&dom?.hasLoginText)break;
    await new Promise(r=>setTimeout(r,250));
  }
  const proof={result:dom?.hasEmail&&dom?.hasPassword&&dom?.hasLoginText&&exceptions.length===0&&resourceErrors.length===0?'PASS':'FAIL',browser:version.Browser,dom,javascriptExceptions:exceptions,resourceLoadingErrors:resourceErrors,credentialsEntered:false,formSubmitted:false,checkedAtUtc:new Date().toISOString()};
  await writeFile(path.join(run,'crm-browser.json'),JSON.stringify(proof,null,2)+'\n');
  console.log(JSON.stringify(proof));
  await send('Browser.close').catch(()=>{});
  ws.close();
  if(proof.result!=='PASS')process.exitCode=1;
} finally {if(ws?.readyState===WebSocket.OPEN)ws.close();if(browser.exitCode===null)browser.kill();}
