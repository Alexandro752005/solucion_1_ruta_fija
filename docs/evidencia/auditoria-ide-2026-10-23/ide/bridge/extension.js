const vscode = require('vscode');
const fs = require('fs');
const path = require('path');

// Temporary, local-only observer. Never creates, clears or changes diagnostics.
exports.activate = function (context) {
  const folder = (vscode.workspace.workspaceFolders || []).find(f =>
    f.uri.fsPath.replace(/\\/g, '/').endsWith('/APF3/solucion_1_ruta_fija_3'));
  if (!folder) return;
  const out = path.join(folder.uri.fsPath, 'docs/evidencia/auditoria-ide-2026-10-23/ide');
  const write = (name, value) => fs.writeFileSync(path.join(out, name), JSON.stringify(value, null, 2));
  function capture() {
    const diagnostics = vscode.languages.getDiagnostics().flatMap(([uri, values]) =>
      values.map(d => ({ file: uri.fsPath, severity: d.severity, source: d.source,
        code: typeof d.code === 'object' ? d.code.value : d.code,
        message: d.message, line: d.range.start.line + 1, column: d.range.start.character + 1 })));
    const result = { capturedAt: new Date().toISOString(), workspace: folder.uri.fsPath,
      errors: diagnostics.filter(d => d.severity === 0).length,
      warnings: diagnostics.filter(d => d.severity === 1).length,
      diagnostics,
      documents: vscode.workspace.textDocuments.map(d => ({ file: d.uri.fsPath, dirty: d.isDirty, language: d.languageId })),
      extensions: vscode.extensions.all.filter(e => e.isActive).map(e => e.id) };
    if (!fs.existsSync(path.join(out, 'diagnostics.initial.json'))) write('diagnostics.initial.json', result);
    write('diagnostics.current.json', result);
  }
  let debounce;
  context.subscriptions.push(vscode.languages.onDidChangeDiagnostics(() => {
    clearTimeout(debounce); debounce = setTimeout(capture, 1000);
  }));
  const allowed = new Set(['dart.restartAnalysisServer', 'java.projectConfiguration.update',
    'java.server.mode.switch', 'java.workspace.compile', 'typescript.restartTsServer']);
  let lastId, busy = false;
  const timer = setInterval(async () => {
    if (busy) return;
    busy = true;
    try {
      const requestPath = path.join(out, 'bridge-request.json');
      if (fs.existsSync(requestPath)) {
        const request = JSON.parse(fs.readFileSync(requestPath, 'utf8').replace(/^\uFEFF/, ''));
        if (request.id !== lastId) {
          lastId = request.id;
          if (request.action === 'capture') capture();
          else if (request.action === 'commands') write('commands.json', await vscode.commands.getCommands(true));
          else if (request.action === 'command' && allowed.has(request.command))
            await vscode.commands.executeCommand(request.command, ...(request.args || []));
          else throw new Error('Unsupported observer action');
          write('bridge-response.json', { id: lastId, success: true, at: new Date().toISOString() });
        }
      }
    } catch (error) { write('bridge-response.json', { id: lastId, success: false, error: String(error) }); }
    finally { busy = false; }
  }, 1500);
  context.subscriptions.push({ dispose() { clearInterval(timer); clearTimeout(debounce); } });
  capture();
};
