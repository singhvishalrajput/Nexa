const ts = require('typescript');
const path = require('node:path');
const config = ts.readConfigFile('tsconfig.json', ts.sys.readFile);
const parsed = ts.parseJsonConfigFileContent(config.config, ts.sys, process.cwd());
const program = ts.createProgram(parsed.fileNames, {...parsed.options, noEmit:true, noUnusedLocals:true, noUnusedParameters:true});
const files = program.getSourceFiles().filter(f => /src[\\/]features[\\/]banking[\\/]/.test(f.fileName) || /src[\\/]services[\\/]auth.ts$/.test(f.fileName));
let failures=0;
for (const file of files) {
 for (const d of [...program.getSyntacticDiagnostics(file),...program.getSemanticDiagnostics(file)]) {
  if (d.category === ts.DiagnosticCategory.Error) { console.error(ts.flattenDiagnosticMessageText(d.messageText,' ')); failures++; }
 }
 function visit(node) {
  if (ts.isDebuggerStatement(node) || (ts.isCallExpression(node) && ['eval','console.log','console.debug'].includes(node.expression.getText(file))) || (ts.isNewExpression(node) && node.expression.getText(file)==='Function')) { console.error(path.relative(process.cwd(),file.fileName)+': unsafe or debug code is not allowed'); failures++; }
  if (ts.isJsxAttribute(node) && node.name.getText(file)==='dangerouslySetInnerHTML') { console.error(file.fileName+': untrusted HTML rendering is not allowed'); failures++; }
  ts.forEachChild(node,visit);
 }
 visit(file);
}
console.log('Banking frontend lint: '+files.length+' source files checked; '+failures+' errors.');
process.exitCode=failures?1:0;
