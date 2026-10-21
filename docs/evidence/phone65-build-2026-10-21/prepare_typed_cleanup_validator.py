from pathlib import Path
import ast,sys,importlib.util,json,hashlib,re,unittest
sys.dont_write_bytecode=True
B=Path('/home/ubuntu/HUGR_PHONE65_EXPORT_SCRATCH_BUILD_2026_10_21');old=B/'validation/verify_phone65_selected_export_artifact_cached.py';s=old.read_text()
helper='''def exact_method_signature_or_empty(block: str, name: str, signature: str) -> str:
    """Select one exact overload; never the first generic bridge or a near-match."""
    starts = [match.start() for match in re.finditer(r'(?m)^\\s*name\\s*: ', block)] + [len(block)]
    matches = []
    for left, right in zip(starts, starts[1:]):
        candidate = block[left:right]
        named = re.search(rf"(?m)^\\s*name\\s*:\\s*['\\\"]?{re.escape(name)}['\\\"]?\\s*$", candidate)
        typed = re.search(rf"(?m)^\\s*type\\s*:\\s*['\\\"]{re.escape(signature)}['\\\"]\\s*$", candidate)
        if named and typed:
            matches.append(candidate)
    return matches[0] if len(matches) == 1 else ''


def cleanup_bridge_invokes_typed_callback(block: str, name: str, typed: str) -> bool:
    erased = typed.replace('Ljava/nio/file/Path;', 'Ljava/lang/Object;', 1)
    bridge = exact_method_signature_or_empty(block, name, erased)
    return all(token in bridge for token in (
        'BRIDGE SYNTHETIC', 'check-cast', 'Ljava/nio/file/Path;',
        SCRATCH_CLEANUP_VISITOR + '.' + name + ':' + typed,
        'invoke-virtual',
    ))


'''
needle='def scratch_predicate_report(';assert needle in s;s=s.replace(needle,helper+needle,1)
a='''    visitor_pre = method_or_empty(cleanup_visitor, 'preVisitDirectory')
    visitor_file = method_or_empty(cleanup_visitor, 'visitFile')
    visitor_post = method_or_empty(cleanup_visitor, 'postVisitDirectory')'''
c='''    path_attributes = '(Ljava/nio/file/Path;Ljava/nio/file/attribute/BasicFileAttributes;)Ljava/nio/file/FileVisitResult;'
    path_exception = '(Ljava/nio/file/Path;Ljava/io/IOException;)Ljava/nio/file/FileVisitResult;'
    visitor_pre = exact_method_signature_or_empty(cleanup_visitor, 'preVisitDirectory', path_attributes)
    visitor_file = exact_method_signature_or_empty(cleanup_visitor, 'visitFile', path_attributes)
    visitor_post = exact_method_signature_or_empty(cleanup_visitor, 'postVisitDirectory', path_exception)'''
assert a in s;s=s.replace(a,c,1)
a='''        )) and all(token in (visitor_pre + visitor_file + visitor_post) for token in (
            'Lhealth/hugr/sourcejournal/SelectedRunExportScratchSpace;',
            'Ljava/nio/file/Files;.delete:',
        )),'''
c='''        )) and all(token in (visitor_pre + visitor_file + visitor_post) for token in (
            'Lhealth/hugr/sourcejournal/SelectedRunExportScratchSpace;',
            'Ljava/nio/file/Files;.delete:',
        )) and all(cleanup_bridge_invokes_typed_callback(cleanup_visitor, name, signature)
                   for name, signature in (('preVisitDirectory', path_attributes),
                                           ('visitFile', path_attributes),
                                           ('postVisitDirectory', path_exception)))
          and all('EXPORT_SCRATCH_CLEANUP_FAILED' in method
                  and '.access$getRunRoot$p:' in method
                  and 'Lkotlin/jvm/internal/Intrinsics;.areEqual:' in method
                  and 'if-eqz' in method
                  for method in (visitor_pre, visitor_file, visitor_post))
          and 'Ljava/nio/file/Path;.getParent:' in visitor_file
          and 'Ljava/nio/file/Files;.delete:' in visitor_file
          and 'Ljava/nio/file/Files;.delete:' in visitor_post
          and '.access$validateRun:' in visitor_post,'''
assert a in s;s=s.replace(a,c,1)
f=B/'validation/verify_phone65_selected_export_artifact_typed_cleanup.py';assert not f.exists();f.write_text(s);ast.parse(s)
def functions(t):return {n.name:ast.dump(n,include_attributes=False)for n in ast.parse(t).body if isinstance(n,ast.FunctionDef)}
p=functions(old.read_text());q=functions(s);assert all(p[k]==q[k]for k in p if k!='scratch_predicate_report')
spec=importlib.util.spec_from_file_location('typed_cleanup65_readonly',f);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
spec0=importlib.util.spec_from_file_location('old_cleanup65_readonly',old);m0=importlib.util.module_from_spec(spec0);spec0.loader.exec_module(m0)
R=B/'validation/phone65-cleanup-predicate-representation';scratch=(R/'scratch-class.dexdump.txt').read_text();visitor=(R/'cleanup-visitor.dexdump.txt').read_text()
attrs='(Ljava/nio/file/Path;Ljava/nio/file/attribute/BasicFileAttributes;)Ljava/nio/file/FileVisitResult;'
class TypedCallbackRepresentationTests(unittest.TestCase):
 def report(self,v=visitor,sc=scratch):return m.scratch_predicate_report(sc,v,'',b'')['boundedCleanupNoRootFollow']
 def test_old_first_overload_reproduces_false_stop_actual_signed_blocks(self):self.assertFalse(m0.scratch_predicate_report(scratch,visitor,'',b'')['boundedCleanupNoRootFollow'])
 def test_actual_signed_typed_callbacks_and_bridges_admit(self):self.assertTrue(self.report())
 def test_exact_signature_selects_guarded_body_not_first_bridge(self):
  chosen=m.exact_method_signature_or_empty(visitor,'visitFile',attrs);self.assertIn('Ljava/nio/file/Files;.delete:',chosen);self.assertNotIn('(PUBLIC BRIDGE SYNTHETIC)',chosen)
 def test_wrong_or_unknown_signature_refuses(self):self.assertEqual(m.exact_method_signature_or_empty(visitor,'visitFile',attrs.replace('Path','NotPath')),'')
 def test_duplicate_typed_signature_refuses(self):
  body=m.exact_method_signature_or_empty(visitor,'visitFile',attrs);self.assertEqual(m.exact_method_signature_or_empty(visitor+body,'visitFile',attrs),'')
 def test_missing_typed_callback_refuses(self):
  body=m.exact_method_signature_or_empty(visitor,'visitFile',attrs);self.assertFalse(self.report(visitor.replace(body,'')))
 def test_bridge_wrong_typed_target_refuses(self):self.assertFalse(self.report(visitor.replace('.visitFile:'+attrs,'.visitFile:' + attrs.replace('Path','OtherPath'))))
 def test_bridge_erasure_only_no_typed_body_refuses(self):
  body=m.exact_method_signature_or_empty(visitor,'visitFile',attrs);self.assertFalse(self.report(visitor.replace(body,'')))
 def test_removed_delete_in_each_actual_deletion_callback_refuses(self):
  for name,signature in [('visitFile',attrs),('postVisitDirectory',attrs.replace('Ljava/nio/file/attribute/BasicFileAttributes;','Ljava/io/IOException;'))]:
   body=m.exact_method_signature_or_empty(visitor,name,signature);bad=body.replace('Ljava/nio/file/Files;.delete:','REMOVED_DELETE:');self.assertFalse(self.report(visitor.replace(body,bad)))
 def test_removed_run_root_guard_refuses(self):self.assertFalse(self.report(visitor.replace('.access$getRunRoot$p:','.wrongRoot:')))
 def test_follow_links_overload_substitution_refuses(self):self.assertFalse(self.report(sc=scratch.replace('walkFileTree:(Ljava/nio/file/Path;Ljava/nio/file/FileVisitor;)','walkFileTree:(Ljava/nio/file/Path;Ljava/util/Set;ILjava/nio/file/FileVisitor;)')))
 def test_missing_previsit_root_failure_guard_refuses(self):
  body=m.exact_method_signature_or_empty(visitor,'preVisitDirectory',attrs);self.assertFalse(self.report(visitor.replace(body,body.replace('EXPORT_SCRATCH_CLEANUP_FAILED','REMOVED_FAILURE'))))
 def test_other_original_predicate_functions_unchanged(self):self.assertTrue(all(p[k]==q[k]for k in p if k!='scratch_predicate_report'))
log=B/'validation/phone65-typed-cleanup-negative-tests.log'
with log.open('w')as out:r=unittest.TextTestRunner(stream=out,verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(TypedCallbackRepresentationTests))
assert r.wasSuccessful()
proof={'status':'PASS','tests':r.testsRun,'sameSignedApkSha256':'49b5c06dc6796feb1ef777353ee567e0e9207bdfe2b5de317b3cf38763c115ae','oldFalseStopReproducedOnActualSignedBlocks':True,'cause':'name-only method parser chose Object erasure bridge instead of exact Path callback','onlyChangedExistingFunction':'scratch_predicate_report visitor overload selection plus stronger callback/bridge guards','allOtherFunctionsAstsEqual':True,'originalValidatorAndFailuresRetained':True,'fixedInspectorSha256':hashlib.sha256(s.encode()).hexdigest(),'apkRuntimeSourceSignerUnchanged':True,'compiledDeadlineSecondsUnchanged':1200,'newBuildSubmissions':0,'fullAdmissionStillPending':True}
(B/'validation/phone65-typed-cleanup-representation-proof.json').write_text(json.dumps(proof,indent=2)+'\n')
print('PHONE65_TYPED_CLEANUP_REPRESENTATION_CORRECTION_PASS',r.testsRun,'tests; exactsignedbridge/callbackguards; noAPK/source/rebuild',flush=True)
