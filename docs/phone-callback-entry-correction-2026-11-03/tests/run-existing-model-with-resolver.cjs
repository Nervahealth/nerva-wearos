#!/usr/bin/env node
'use strict';
// Mechanical task-local CJS transpilation/resolution only; original test/source files unchanged.
const fs=require('node:fs');const ts=require('/home/ubuntu/nerva-for-clinicians/node_modules/typescript/lib/typescript.js');
require.extensions['.ts']=(module,filename)=>{
 const source=fs.readFileSync(filename,'utf8');
 const result=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,esModuleInterop:true},fileName:filename});
 module._compile(result.outputText,filename);
};
require('/home/ubuntu/HUGR_NATIVE_OBSERVATION_RESET_RECOVERY_2026_10_28/repos/phone/tests/receiptIncidentDiagnosticModel.test.ts');
