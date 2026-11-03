#!/usr/bin/env node
'use strict';
const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');
const ts=require('/home/ubuntu/nerva-for-clinicians/node_modules/typescript/lib/typescript.js');
const root='/home/ubuntu/HUGR_NATIVE_OBSERVATION_RESET_RECOVERY_2026_10_28/repos/phone';const cache=new Map();let reads=[],writes=[],origins=[];
function evaluate(relative,override={}){
 if(cache.has(relative)&&!Object.keys(override).length)return cache.get(relative);
 const source=fs.readFileSync(path.join(root,relative),'utf8');const result=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022},fileName:relative,reportDiagnostics:true});assert.equal((result.diagnostics||[]).filter(x=>x.category===ts.DiagnosticCategory.Error).length,0);
 const mod={exports:{}};new Function('require','module','exports',result.outputText)(request=>{if(Object.hasOwn(override,request))return override[request];if(request.startsWith('.'))return evaluate(path.posix.normalize(path.posix.join(path.posix.dirname(relative),request))+'.ts');throw Error('Unexpected dependency '+request);},mod,mod.exports);if(!Object.keys(override).length)cache.set(relative,mod.exports);return mod.exports;
}
const context=evaluate('lib/receiptIncidentDiagnosticContext.ts');const model=evaluate('lib/receiptIncidentDiagnosticModel.ts');
const native={checkpointCurrentReceiptProbeV1:async(outcome,coalesced)=>{writes.push({outcome,coalesced});return 'SYNTHETIC_WRITE_ACK';},checkpointCurrentReceiptReadProbeV2:async(outcome,coalesced,at,originJson)=>{reads.push({outcome,coalesced,at});origins.push(JSON.parse(originJson));return 'SYNTHETIC_READ_ACK';}};
const wrapper=evaluate('modules/hugr-source-journal/index.ts',{'expo-modules-core':{requireNativeModule:name=>{assert.equal(name,'HUGRSourceJournal');return native;}},'react-native':{Platform:{OS:'android'}}});
const {ReceiptIncidentCheckpoint}=evaluate('lib/receiptIncidentDiagnostic.ts',{'../modules/hugr-source-journal':wrapper,'./receiptIncidentDiagnosticModel':model,'./receiptIncidentDiagnosticContext':context});
const settle=async(n=36)=>{for(let i=0;i<n;i++)await Promise.resolve();};const cases=[];
function cp(read=wrapper.checkpointCurrentReceiptReadProbeV2){return new ReceiptIncidentCheckpoint(new model.ReceiptIncidentWindow('synthetic-read-probe','synthetic-read-epoch'),async()=>{throw Error('SYNTHETIC_MAIN_REFUSAL');},wrapper.checkpointCurrentReceiptProbeV1,read);}
function begin(c,kind='RECEIPT_READ',n=1){return c.begin({operationKind:kind,localTransactionId:'hugr-receipt-2-'+n,pageOffset:kind==='RECEIPT_READ'||kind==='R1_WRITE'?3840:undefined},100);}
(async()=>{
 let c=cp(),key=begin(c);c.observe(key,'SETTLED_TIMELY',198);assert.equal(reads.length,0,'no synchronous bridge in critical outcome');await settle();assert.deepEqual(reads,[{outcome:'JS_SETTLED_TIMELY',coalesced:0,at:198}]);assert.equal(writes.length,0);assert.equal(c.snapshot().frozen,false);assert.deepEqual(origins[0],{format:'HUGR_PHONE_READ_JS_ORIGIN_V1',attribution:'JS_ORIGIN',state:'VALUE',reference:{incidentPseudonym:'synthetic-read-probe',connectionEpoch:'synthetic-read-epoch',localOrdinal:1,operationKind:'RECEIPT_READ',pageOffset:3840}});cases.push('read-success-fresh-specific-wrapper-not-write-no-synchronous-native');
 c.observe(key,'SETTLED_TIMELY',199);c.observe(key,'LATE_FULFILLMENT_IGNORED',200);await settle();assert.equal(reads.length,1);cases.push('duplicate-late-cannot-relabel-read-outcome');
 c=cp();key=begin(c);c.observe(key,'DEADLINE',10124);await settle();assert.equal(reads.length,2);assert.deepEqual(reads[1],{outcome:'JS_DEADLINE',coalesced:0,at:10124});assert.equal(c.snapshot().operations[0].deadlineObserved,true);c.observe(key,'CANCEL_REQUESTED',10125);c.observe(key,'LATE_REJECTION_IGNORED',10130);await settle();assert.equal(reads.length,2);cases.push('read-deadline-fresh-label-preserved-through-cancel-late-rejection');
 c=cp();key=begin(c);c.observe(key,'SETTLED_REJECTED',300);await settle();assert.equal(reads.at(-1).outcome,'JS_SETTLED_REJECTED');cases.push('read-rejection-checkpoints');
 const count=reads.length;c=cp();key=begin(c,'R1_WRITE');c.observe(key,'SETTLED_TIMELY',250);await settle();assert.equal(reads.length,count);assert.equal(writes.length,1);cases.push('write-queue-and-kind-whitelist-stay-separate');
 c=cp();key=begin(c,'SOURCE_RESUME');c.observe(key,'SETTLED_TIMELY',250);c.observe(null,'DEADLINE',300);await settle();assert.equal(reads.length,count);cases.push('unrelated-missing-row-no-read-checkpoint');
 let active=0,maxActive=0,resolveFirst;const coalesced=[],coalescedOrigins=[];c=cp((outcome,n,at,originJson)=>{coalescedOrigins.push(JSON.parse(originJson));active++;maxActive=Math.max(maxActive,active);coalesced.push({outcome,n,at});if(coalesced.length===1)return new Promise(r=>{resolveFirst=()=>{active--;r('ok');};});return Promise.resolve().then(()=>{active--;return 'ok';});});c.observe(begin(c,'RECEIPT_READ',1),'SETTLED_TIMELY',200);await settle(4);for(let n=2;n<=5;n++)c.observe(begin(c,'RECEIPT_READ',n),'SETTLED_TIMELY',200+n);await settle();assert.equal(coalesced.length,1);resolveFirst();await settle();assert.equal(maxActive,1);assert.deepEqual(coalesced[1],{outcome:'JS_SETTLED_TIMELY',n:3,at:205});assert.equal(coalescedOrigins[0].reference.localOrdinal,1);assert.equal(coalescedOrigins[1].reference.localOrdinal,5);cases.push('one-inflight-one-latest-pending-explicit-coalescing');
 let attempts=0;c=cp(async()=>{attempts++;throw Error('SYNTHETIC_READ_IO_REFUSAL');});c.observe(begin(c),'SETTLED_TIMELY',198);await settle();assert.equal(attempts,1);assert.equal(c.snapshot().frozen,false);cases.push('native-refusal-detached-no-retry-no-transport-failure');
 // Same generated local transaction key/page/ordinal in distinct epochs remains distinct JS origin.
 const scoped=[];
 for(const epoch of ['synthetic-scope-a','synthetic-scope-b']) {
  const w=new model.ReceiptIncidentWindow('synthetic-same-incident',epoch);
  const x=new ReceiptIncidentCheckpoint(w,async()=>{throw Error('synthetic-unused');},wrapper.checkpointCurrentReceiptProbeV1,async(o,n,t,j)=>{scoped.push(JSON.parse(j));return 'ok';});
  const k=begin(x);x.observe(k,'SETTLED_TIMELY',198.75);await settle();
 }
 assert.equal(scoped[0].reference.localOrdinal,scoped[1].reference.localOrdinal);assert.equal(scoped[0].reference.pageOffset,scoped[1].reference.pageOffset);assert.notEqual(scoped[0].reference.connectionEpoch,scoped[1].reference.connectionEpoch);cases.push('same-key-page-ordinal-distinct-epochs-remain-distinct');
 // Diagnostic model mutation seam after B is admitted cannot mutate its captured origin or relabel it as C.
 let release;const immutable=[];const w=new model.ReceiptIncidentWindow('synthetic-immutable','synthetic-immutable-epoch');
 const x=new ReceiptIncidentCheckpoint(w,async()=>{throw Error('synthetic-unused');},wrapper.checkpointCurrentReceiptProbeV1,(o,n,t,j)=>{immutable.push(JSON.parse(j));if(immutable.length===1)return new Promise(r=>{release=r;});return Promise.resolve('ok');});
 x.observe(begin(x),'SETTLED_TIMELY',198.75);await settle();const b=begin(x,'RECEIPT_READ',2);x.observe(b,'SETTLED_TIMELY',224.75);
 w.rows.find(r=>r.localTransactionId===b).pageOffset=4224;const exposed=w.snapshot();exposed.operations[1].localOrdinal=999;exposed.connectionEpoch='other-synthetic-epoch';
 release('ok');await settle();assert.equal(immutable[1].reference.pageOffset,3840);assert.equal(immutable[1].reference.localOrdinal,2);assert.equal(immutable[1].reference.connectionEpoch,'synthetic-immutable-epoch');cases.push('captured-origin-immutable-during-pending-model-and-snapshot-mutation');
 const unavailable=[];
 for(const fault of ['invalid','mismatch']) {
  const fw=new model.ReceiptIncidentWindow('synthetic-fault','synthetic-fault-epoch');const original=fw.snapshot.bind(fw);
  fw.snapshot=()=>{const s=original();if(fault==='invalid'&&s.operations.length)s.operations[0].localOrdinal='1';if(fault==='mismatch')s.connectionEpoch='synthetic-other-epoch';return s;};
  const fc=new ReceiptIncidentCheckpoint(fw,async()=>{throw Error('synthetic-unused');},wrapper.checkpointCurrentReceiptProbeV1,async(o,n,t,j)=>{unavailable.push(JSON.parse(j));return 'ok';});
  fc.observe(begin(fc),'SETTLED_TIMELY',198.75);await settle();
 }
 assert.equal(unavailable[0].state,'INVALID');assert.equal(unavailable[1].state,'MISMATCH');assert(unavailable.every(o=>!Object.hasOwn(o,'reference')));cases.push('invalid-and-mismatched-primary-origin-explicit-no-fallback-row');
 // Narrow typecheck actual coordinator/model/context/wrapper with two external module declaration seams.
 const roots=['lib/receiptIncidentDiagnostic.ts','lib/receiptIncidentDiagnosticModel.ts','lib/receiptIncidentDiagnosticContext.ts','modules/hugr-source-journal/index.ts'].map(p=>path.join(root,p));
 const tmp=process.env.TYPE_STUB_FILE;fs.writeFileSync(tmp,"declare module 'expo-modules-core' { export function requireNativeModule(name:string):any; }\ndeclare module 'react-native' { export const Platform:{OS:string}; }\n");
 const options={noEmit:true,strict:true,target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS,moduleResolution:ts.ModuleResolutionKind.Node10,skipLibCheck:true,types:[]};const program=ts.createProgram([...roots,tmp],options);const diagnostics=ts.getPreEmitDiagnostics(program);const formatted=ts.formatDiagnosticsWithColorAndContext(diagnostics,{getCanonicalFileName:x=>x,getCurrentDirectory:()=>root,getNewLine:()=>"\n"});if(diagnostics.length)console.error(formatted);assert.equal(diagnostics.length,0,'narrow actual TS bridge/coordinator typecheck');cases.push('narrow-production-ts-typecheck-with-external-native-declarations');
 const result={status:'PASS_TS_READ_COORDINATOR_AND_WRAPPER_NATIVE_DOUBLE',caseCount:cases.length,cases,ExpoRuntime:false,physical:false};fs.writeFileSync(process.env.RESULT_FILE,JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result,null,2));
})().catch(e=>{console.error(e.stack);process.exitCode=1;});
