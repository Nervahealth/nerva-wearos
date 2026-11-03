#!/usr/bin/env node
'use strict';
// Task-local adaptation of existing run-read-coordinator.cjs evaluation harness.
const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');const {spawn}=require('node:child_process');const readline=require('node:readline');
const ts=require('/home/ubuntu/nerva-for-clinicians/node_modules/typescript/lib/typescript.js');
const root='/home/ubuntu/HUGR_NATIVE_OBSERVATION_RESET_RECOVERY_2026_10_28/repos/phone';
const out='/home/ubuntu/HUGR_PHONE_JS_ORIGIN_CORRECTION_2026_11_03/final-two-case';
const cache=new Map();
function evaluate(relative,override={}){
 if(cache.has(relative)&&!Object.keys(override).length)return cache.get(relative);
 const source=fs.readFileSync(path.join(root,relative),'utf8');const result=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022},fileName:relative,reportDiagnostics:true});assert.equal((result.diagnostics||[]).filter(x=>x.category===ts.DiagnosticCategory.Error).length,0);
 const mod={exports:{}};new Function('require','module','exports',result.outputText)(request=>{if(Object.hasOwn(override,request))return override[request];if(request.startsWith('.'))return evaluate(path.posix.normalize(path.posix.join(path.posix.dirname(relative),request))+'.ts');throw Error('Unexpected dependency '+request);},mod,mod.exports);if(!Object.keys(override).length)cache.set(relative,mod.exports);return mod.exports;
}
const proc=spawn('java',['-cp',process.env.FRESHNESS_CP,'health.hugr.sourcejournal.FreshnessStoreAdapter'],{stdio:['pipe','pipe','pipe']});let next=0;const pending=new Map();const transcript=[];
const err=fs.createWriteStream(path.join(out,'jvm.stderr.log'));proc.stderr.pipe(err);
readline.createInterface({input:proc.stdout}).on('line',line=>{const r=JSON.parse(line);const p=pending.get(r.id);pending.delete(r.id);transcript.push({response:r});if(r.fatal)p.reject(Error(r.fatal));else p.resolve(r);});proc.on('exit',code=>{if(code)for(const p of pending.values())p.reject(Error('JVM exit '+code));});
function command(q){q={id:++next,...q};transcript.push({request:q});return new Promise((resolve,reject)=>{pending.set(q.id,{resolve,reject});proc.stdin.write(JSON.stringify(q)+'\n');});}
let bridge;const native={checkpointCurrentReceiptReadProbeV2:(...args)=>bridge(...args),checkpointCurrentReceiptProbeV1:async()=>{throw Error('unexpected WRITE');},publishReceiptIncidentDiagnostic:()=>{throw Error('unexpected default primary');}};
const wrapper=evaluate('modules/hugr-source-journal/index.ts',{'expo-modules-core':{requireNativeModule:name=>{assert.equal(name,'HUGRSourceJournal');return native;}},'react-native':{Platform:{OS:'android'}}});
const model=evaluate('lib/receiptIncidentDiagnosticModel.ts');const context=evaluate('lib/receiptIncidentDiagnosticContext.ts');
const {ReceiptIncidentCheckpoint}=evaluate('lib/receiptIncidentDiagnostic.ts',{'../modules/hugr-source-journal':wrapper,'./receiptIncidentDiagnosticModel':model,'./receiptIncidentDiagnosticContext':context});
const settle=async(n=40)=>{for(let i=0;i<n;i++)await Promise.resolve();};
function save(file,body){fs.writeFileSync(path.join(out,file),typeof body==='string'?body:JSON.stringify(body,null,2)+'\n');}
async function until(test){for(let i=0;i<200&&!test();i++){await new Promise(r=>setTimeout(r,5));}assert(test(),'bounded asynchronous completion');}
function bodyFromReport(text){return JSON.parse(text.split('\n').find(l=>l.startsWith('{')));}
(async()=>{
 const results=[];
 for(const name of ['case1-delayed-A-then-B','case2-B-refused-A-retained']){
  const refusal=name.startsWith('case2');const dir=path.join(out,name);await command({op:'init',root:dir});
  let releaseA,releaseB,firstStarted,secondStarted;let calls=[],primaries=[],jobs=[];const primaryDone=[];
  const publish=body=>{const index=primaries.length+1;primaries.push(JSON.parse(body));const p=command({op:'primary-publish',label:'primary-'+index,body});jobs.push(p);p.then(()=>primaryDone.push(index));return p;};
  bridge=(outcome,coalesced,at,originJson)=>{
   const call={outcome,coalesced,at,originJson};calls.push(call);const index=calls.length;
   if(index===1){firstStarted=true;return new Promise(resolve=>{releaseA=async()=>{const r=await command({op:'read-publish',label:'A-delayed',...call});assert.equal(r.ok,true);resolve('actual real store ack');return r;};});}
   assert.equal(index,2);secondStarted=true;
   return new Promise((resolve,reject)=>{releaseB=async()=>{if(refusal){fs.writeFileSync(path.join(dir,'B-refusal-before-native-write.txt'),'SYNTHETIC_NATIVE_BRIDGE_PUBLICATION_REFUSED_BEFORE_STORE_ENTRY\n');reject(Error('SYNTHETIC_NATIVE_BRIDGE_PUBLICATION_REFUSED_BEFORE_STORE_ENTRY'));return {ok:false,refusalBeforeWrite:true};}const r=await command({op:'read-publish',label:'B-current',...call});assert.equal(r.ok,true);resolve('actual real store ack');return r;};});
  };
  const c=new ReceiptIncidentCheckpoint(new model.ReceiptIncidentWindow('synthetic-freshness-'+(refusal?'2':'1'),'synthetic-js-epoch-'+(refusal?'2':'1')),publish,wrapper.checkpointCurrentReceiptProbeV1,wrapper.checkpointCurrentReceiptReadProbeV2);
  const a=c.begin({operationKind:'RECEIPT_READ',localTransactionId:'hugr-receipt-2-1',pageOffset:3840},100);
  c.observe(a,'SETTLED_TIMELY',198.75);assert.equal(calls.length,0);await settle();assert(firstStarted);assert.equal(calls.length,1);save(name+'/primary-after-A.in-memory.json',c.snapshot());
  const before=await command({op:'read',label:'before-B-before-delayed-A'});assert(before.text.includes('PROCESS_LOSS_UNKNOWN'));
  const b=c.begin({operationKind:'RECEIPT_READ',localTransactionId:'hugr-receipt-2-2',pageOffset:4224},224);
  await command({op:'native-stalled'});
  c.observe(b,'DEADLINE',10224.75);await settle();assert.equal(calls.length,1,'B pending while A in flight');await until(()=>primaryDone.length>=2);save(name+'/primary-B-before-delayed-A.in-memory.json',c.snapshot());
  const during=await command({op:'read',label:'after-B-before-delayed-A'});assert(during.text.includes('PROCESS_LOSS_UNKNOWN'));
  const aAck=await releaseA();await until(()=>secondStarted);assert.deepEqual(calls.map(({originJson,...call})=>call),[{outcome:'JS_SETTLED_TIMELY',coalesced:0,at:198.75},{outcome:'JS_DEADLINE',coalesced:0,at:10224.75}]);
  const afterA=await command({op:'read',label:'after-delayed-A-before-B-publication'});const aBody=bodyFromReport(afterA.text);assert.equal(aBody.observedJsOutcome,'JS_SETTLED_TIMELY');assert.equal(aBody.jsObservedAtMonotonicMs,198.75);assert.equal(aBody.checkpointRevision,1);assert.equal(aBody.operations.length,2,'delayed A snapshot contains later native fixture row; no JS mapping');
  const bAck=await releaseB();await settle();
  const afterB=await command({op:'read',label:refusal?'after-B-refusal-A-intact':'after-B-publication'});const finalBody=bodyFromReport(afterB.text);
  if(refusal){assert.equal(afterB.fileSha256,afterA.fileSha256);assert.equal(afterB.text,afterA.text);assert.equal(finalBody.observedJsOutcome,'JS_SETTLED_TIMELY');assert.equal(finalBody.checkpointRevision,1);}
  else{assert.equal(finalBody.observedJsOutcome,'JS_DEADLINE');assert.equal(finalBody.jsObservedAtMonotonicMs,10224.75);assert.equal(finalBody.checkpointRevision,2);assert.notEqual(afterB.fileSha256,afterA.fileSha256);}
  save(name+'/queue.actual-bridge-arguments.json',calls);save(name+'/primary-final.actual-window.json',c.snapshot());
  const primary=c.snapshot();assert.equal(primary.operations.length,2);assert.equal(primary.operations[0].pageOffset,3840);assert.equal(primary.operations[1].pageOffset,4224);assert.equal(primary.diagnosticContext.firstCause.operationOrdinal.value,2);assert.equal(primary.firstFailureAtMonotonicMs,10224);
  const origin=finalBody.originatingJs;assert.equal(origin.attribution,'JS_ORIGIN');assert.equal(origin.state,'VALUE');assert.equal(finalBody.format,'HUGR_PHONE_READ_CURRENT_PROBE_V2');
  assert.equal(origin.reference.incidentPseudonym,primary.incidentPseudonym);assert.equal(origin.reference.connectionEpoch,primary.connectionEpoch);
  const matches=primary.operations.filter(r=>r.localOrdinal===origin.reference.localOrdinal&&r.operationKind===origin.reference.operationKind);
  assert.equal(matches.length,1);assert.equal(matches[0].pageOffset,refusal?3840:4224);assert.equal(origin.reference.pageOffset,refusal?3840:4224);
  assert.notEqual(matches[0].settledAtMonotonicMs,finalBody.jsObservedAtMonotonicMs,'attribution independent of truncated primary clock');
  assert.equal(aBody.originatingJs.reference.pageOffset,3840);assert.equal(aBody.originatingJs.reference.localOrdinal,1);
  for(const cb of finalBody.rawReadCallbacks)assert.equal(cb.assignment,'UNASSIGNED_NO_ATT_REQUEST_ID');
  let interruption;
  if(refusal){
   const attempted=await command({op:'interrupted-read-publish',label:'B-interrupted-part-qualification',...calls[1]});assert.equal(attempted.ok,false);
   const interrupted=await command({op:'read',label:'B-interrupted-part-reader-STOP'});assert(interrupted.text.includes('INTERRUPTED_CHECKPOINT_PRIOR_NOT_CURRENT'));assert(!interrupted.text.includes('JS_SETTLED_TIMELY'));assert.equal(interrupted.fileSha256,afterA.fileSha256);
   interruption={error:attempted.error,renderedStop:true,priorAByteExact:true};
  }
  results.push({name,bridgeArguments:calls,aBodySha256:aAck.bodySha256,aFileSha256:afterA.fileSha256,finalFileSha256:afterB.fileSha256,finalOutcome:finalBody.observedJsOutcome,finalJsTime:finalBody.jsObservedAtMonotonicMs,finalRevision:finalBody.checkpointRevision,scopedJsOriginPage:matches[0].pageOffset,selfIdentifyingJsRequester:true,timeEqualityNotUsed:true,refusalRetainsA:refusal,interruption,primaryPublicationCount:primaries.length});
  await Promise.all(jobs);await settle();
 }
 save('EXECUTION_RESULT.json',{status:'PASS_TWO_OFFLINE_REAL_STORE_ACCEPTANCE_CASES_JS_ORIGIN_ONLY',cases:results,physical:false,ExpoRuntime:false,jsNativeMapped:false});save('BRIDGE_JVM_TRANSCRIPT.json',transcript);console.log(JSON.stringify(results,null,2));proc.stdin.end();
})().catch(e=>{console.error(e.stack);save('BRIDGE_JVM_TRANSCRIPT.json',transcript);proc.kill();process.exitCode=1;});
