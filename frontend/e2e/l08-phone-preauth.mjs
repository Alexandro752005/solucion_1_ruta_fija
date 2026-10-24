// Public physical-device boundary only: never connects PostgreSQL/SMTP or accepts login.
import {createServer} from 'node:http';
import {mkdir,writeFile} from 'node:fs/promises';
import {resolve,join} from 'node:path';
import {randomUUID} from 'node:crypto';
const root=resolve(import.meta.dirname,'../..');
// A new public-only observation never overwrites/reopens an archived run.
const run=randomUUID();
if(process.argv.length!==3||process.argv[2]!=='--serve-public-only')throw Error('EXPLICIT_PUBLIC_ONLY_TEST_REQUIRED');
const out=join(root,'backend/target',`l08-phone-preauth-${run}`);await mkdir(out);
const events=[];let stopped=false;let previous=Promise.resolve(),sequence=0;
function persist(state){const file='observacion-'+Date.now()+'-'+(++sequence)+'.json';previous=previous.then(()=>writeFile(join(out,file),JSON.stringify({runId:run,observedAt:new Date().toISOString(),state,scope:'PUBLIC_PHYSICAL_PREAUTH_ONLY_NO_OPERATIONAL_ACCOUNT',errorEnvelope:'ApiErrorResponse-v1',deliberateUnavailableDouble:true,events,databaseConnected:false,mailAdapterPresent:false,credentialsOrRequestBodiesRecorded:false,manualOutcome:'PENDING_USER_REPORT',pilotEnabled:false},null,2)+'\n',{flag:'wx'}));return previous;}
const server=createServer((req,res)=>{
 const started=performance.now();
 const url=new URL(req.url,'http://127.0.0.1:4311');req.resume();
 const path=['/api/v1/mobile/auth/login','/api/v1/mobile/auth/refresh'].includes(url.pathname)?url.pathname:'OTHER_PUBLIC_REQUEST';
 const correlationId=randomUUID(),timestamp=new Date().toISOString();
 res.once('finish',()=>{events.push({at:timestamp,method:req.method,path,status:503,correlationId,responseDurationMs:Number((performance.now()-started).toFixed(3))});persist('HTTP_UNAVAILABILITY_DOUBLE_OBSERVED').catch(()=>{});});
 res.writeHead(503,{'Content-Type':'application/json','Cache-Control':'no-store','X-Correlation-ID':correlationId});
 res.end(JSON.stringify({timestamp,status:503,code:'SERVICE_UNAVAILABLE',message:'Ensayo local: servicio temporalmente no disponible. No se inició sesión.',path,correlationId,errors:[]}));
});
async function stop(){if(stopped)return;stopped=true;await new Promise(r=>server.close(r));await persist('STOPPED_PUBLIC_ONLY_KEEP_MANUAL_RESULT_PENDING');console.log('L08_PHONE_PREAUTH_SERVER=STOPPED_NO_DATABASE_CHANGES');process.exit(0);}
process.on('SIGINT',()=>void stop());process.on('SIGTERM',()=>void stop());
process.stdin.setEncoding('utf8');process.stdin.on('data',text=>{if(text.trim()==='cerrar')void stop();});
server.on('error',()=>{console.log('L08_PHONE_PREAUTH_SERVER=REFUSED_PORT_UNAVAILABLE');process.exit(1);});
await new Promise(r=>server.listen(4311,'127.0.0.1',r));await persist('READY_NO_INTERACTION_YET');
console.log('L08_PHONE_PREAUTH_RUN_ID='+run);console.log('L08_PHONE_PREAUTH_SERVER=READY_LOOPBACK_ONLY_ALL_AUTHENTICATION_REFUSED');
