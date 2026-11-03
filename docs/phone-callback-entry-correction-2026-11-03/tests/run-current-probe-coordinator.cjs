#!/usr/bin/env node
'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');const path=require('node:path');
const ts=require(process.env.TYPESCRIPT_PATH||'/home/ubuntu/nerva-for-clinicians/node_modules/typescript/lib/typescript.js');
const root='/home/ubuntu/HUGR_NATIVE_OBSERVATION_RESET_RECOVERY_2026_10_28/repos/phone';
const cache=new Map();let calls=[];
function evaluate(relative, override={}) {
 if(cache.has(relative) && Object.keys(override).length===0)return cache.get(relative);
 const source=fs.readFileSync(path.join(root,relative),'utf8');
 const result=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022},fileName:relative,reportDiagnostics:true});
 assert.equal((result.diagnostics||[]).filter(x=>x.category===ts.DiagnosticCategory.Error).length,0);
 const mod={exports:{}};
 new Function('require','module','exports',result.outputText)(request=>{
  if(Object.hasOwn(override,request))return override[request];
  if(request.startsWith('.'))return evaluate(path.posix.normalize(path.posix.join(path.posix.dirname(relative),request))+'.ts');
  throw Error('Unexpected dependency '+request);
 },mod,mod.exports);
 if(Object.keys(override).length===0)cache.set(relative,mod.exports);return mod.exports;
}
const context=evaluate('lib/receiptIncidentDiagnosticContext.ts');
cache.set('lib/receiptIncidentDiagnosticContext.ts',context);
const model=evaluate('lib/receiptIncidentDiagnosticModel.ts');
const native={checkpointCurrentReceiptProbeV1:async(outcome,coalesced)=>{calls.push({outcome,coalesced});return 'SYNTHETIC_ACK';}};
const wrapper=evaluate('modules/hugr-source-journal/index.ts',{
 'expo-modules-core':{requireNativeModule:name=>{assert.equal(name,'HUGRSourceJournal');return native;}},
 'react-native':{Platform:{OS:'android'}},
});
const {ReceiptIncidentCheckpoint}=evaluate('lib/receiptIncidentDiagnostic.ts',{'../modules/hugr-source-journal':wrapper,'./receiptIncidentDiagnosticModel':model,'./receiptIncidentDiagnosticContext':context});
const settle=async(n=32)=>{for(let i=0;i<n;i++)await Promise.resolve();};
const passed=[];
function checkpoint(writer,probe=wrapper.checkpointCurrentReceiptProbeV1){return new ReceiptIncidentCheckpoint(new model.ReceiptIncidentWindow('synthetic-probe-incident','synthetic-probe-epoch'),writer,probe);}
function begin(cp,kind='R1_WRITE',n=1){return cp.begin({operationKind:kind,localTransactionId:'hugr-receipt-2-'+n,pageOffset:kind==='R1_WRITE'?1536:undefined},1);}
(async()=>{
 let incidentWrites=0;let cp=checkpoint(async()=>{incidentWrites++;throw Error('unexpected original publisher');});
 let key=begin(cp);cp.observe(key,'SETTLED_TIMELY',2);assert.equal(calls.length,0,'no synchronous native call in outcome path');await settle();
 assert.deepEqual(calls,[{outcome:'JS_SETTLED_TIMELY',coalesced:0}]);assert.equal(incidentWrites,0);assert.equal(cp.snapshot().frozen,false);passed.push('fresh_success_actual_coordinator_and_wrapper_without_incident_publication');
 cp.observe(key,'SETTLED_TIMELY',3);cp.observe(key,'LATE_FULFILLMENT_IGNORED',4);await settle();assert.equal(calls.length,1);passed.push('duplicates_and_late_fulfillment_cannot_relabel_probe');
 cp=checkpoint(async()=>{incidentWrites++;throw Error('synthetic native refusal');});key=begin(cp,'RECEIPT_READ');cp.observe(key,'SETTLED_TIMELY',2);await settle();assert.equal(calls.length,1);passed.push('unrelated_read_never_checkpointed');
 calls=[];let firstResolve;let active=0,maxActive=0;
 cp=checkpoint(async()=>{throw Error('unused');},(outcome,coalesced)=>{active++;maxActive=Math.max(maxActive,active);calls.push({outcome,coalesced});return calls.length===1?new Promise(resolve=>{firstResolve=()=>{active--;resolve('ok');};}):Promise.resolve().then(()=>{active--;return 'ok';});});
 cp.observe(begin(cp,'R1_WRITE',1),'SETTLED_TIMELY',2);await settle(4);
 for(let i=2;i<=5;i++)cp.observe(begin(cp,'R1_WRITE',i),'SETTLED_TIMELY',i+2);
 await settle();assert.equal(calls.length,1);firstResolve();await settle();assert.equal(calls.length,2);assert.equal(calls[1].coalesced,3);assert.equal(maxActive,1);passed.push('one_in_flight_one_pending_and_explicit_coalesced_outcomes');
 calls=[];cp=checkpoint(async()=>{throw Error('original publisher refusal');});key=begin(cp);cp.observe(key,'DEADLINE',10001);await settle();assert.equal(calls.length,1);assert.equal(calls[0].outcome,'JS_DEADLINE');cp.observe(key,'SETTLED_TIMELY',10002);cp.observe(key,'LATE_REJECTION_IGNORED',10003);await settle();assert.equal(calls.length,1);assert.equal(cp.snapshot().operations[0].deadlineObserved,true);passed.push('deadline_label_survives_late_outcome');
 let attempts=0;cp=checkpoint(async()=>{throw Error('unused');},async()=>{attempts++;throw Error('synthetic probe IO rejection');});cp.observe(begin(cp),'SETTLED_TIMELY',2);await settle();assert.equal(attempts,1);assert.equal(cp.snapshot().frozen,false);passed.push('probe_refusal_is_detached_no_retry_no_transport_failure');
 const result={status:'PASS_TS_PRODUCTION_COORDINATOR_AND_WRAPPER_WITH_NATIVE_DOUBLE',cases:passed,caseCount:passed.length,ExpoBridgeRuntime:false,Android:false,RxJava:false};
 fs.writeFileSync(process.env.RESULT_FILE||'/home/ubuntu/HUGR_PHONE_CALLBACK_ENTRY_CORRECTION_2026_11_03/TS_PROBE_RESULT.json',JSON.stringify(result,null,2)+'\n');
 console.log(JSON.stringify(result,null,2));
})().catch(e=>{console.error(e.stack);process.exitCode=1;});
