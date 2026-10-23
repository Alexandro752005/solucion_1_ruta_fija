"""Capture actual native exit codes; PowerShell stderr is not a test failure."""
import json
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

out = Path(__file__).resolve().parent
root = out.parents[3]
checks = {
    'typecheck': ['npm.cmd', 'run', 'typecheck'],
    'test-ci': ['npm.cmd', 'run', 'test:ci'],
    'build': ['npm.cmd', 'run', 'build'],
}
name = sys.argv[1]
command = checks[name]
start = time.monotonic()
started_at = datetime.now(timezone.utc).isoformat()
with (out / f'{name}.native.log').open('wb') as log:
    result = subprocess.run(['cmd.exe', '/d', '/c', *command], cwd=root / 'frontend',
                            stdout=log, stderr=subprocess.STDOUT, check=False)
summary = {'command': command, 'cwd': str(root / 'frontend'), 'startedAt': started_at,
           'durationSeconds': time.monotonic() - start, 'exitCode': result.returncode,
           'log': f'{name}.native.log'}
(out / f'{name}.result.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
print(json.dumps(summary))
sys.exit(result.returncode)
