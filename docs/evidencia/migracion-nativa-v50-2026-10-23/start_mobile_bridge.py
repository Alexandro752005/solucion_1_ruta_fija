import json
from pathlib import Path
import subprocess
import socket
import time

root=Path.cwd()
run=root/'.runtime/migracion-nativa-v50-20261023_1714'
state=json.loads((root/'.runtime/ruta-fija-native-runtime.json').read_text(encoding='utf-8-sig'))
assert state['mode']=='NORMAL_LOCAL'
with socket.socket() as probe:
    probe.bind(('192.168.18.4',4311))
for stream in ['stdout','stderr']:
    old=run/f'mobile-bridge.{stream}.log'
    if old.exists():
        attempt=1
        while (run/f'mobile-bridge-attempt{attempt}.{stream}.log').exists():attempt+=1
        old.rename(run/f'mobile-bridge-attempt{attempt}.{stream}.log')
with (run/'mobile-bridge.stdout.log').open('wb') as out,(run/'mobile-bridge.stderr.log').open('wb') as err:
    p=subprocess.Popen(['C:/Program Files/Java/jdk-21.0.10/bin/java.exe','-Xms16m','-Xmx64m',str(run/'NativeMobileBridge.java'),
                        '192.168.18.4','192.168.18.9',str(state['backend']['processId']),state['backend']['processStartedAtUtc']],
                       cwd=root,stdin=subprocess.DEVNULL,stdout=out,stderr=err,creationflags=subprocess.CREATE_NO_WINDOW)
    deadline=time.monotonic()+15
    while time.monotonic()<deadline:
        if p.poll() is not None:raise RuntimeError('BRIDGE_START_FAILED; inspect mobile-bridge.stderr.log')
        if 'LOCAL_MOBILE_BRIDGE=READY' in (run/'mobile-bridge.stdout.log').read_text(encoding='utf-8'):break
        time.sleep(0.2)
    else:
        p.terminate()
        raise RuntimeError('BRIDGE_START_TIMEOUT')
(run/'mobile-bridge.json').write_text(json.dumps(dict(processId=p.pid,backendProcessId=state['backend']['processId'],bind='192.168.18.4',
    phone='192.168.18.9',port=4311,upstream='http://127.0.0.1:8080',autoStop='WHEN_EXACT_BACKEND_PROCESS_EXITS'),indent=2)+'\n',encoding='utf-8')
print('MOBILE_BRIDGE_STARTED_PID='+str(p.pid))
