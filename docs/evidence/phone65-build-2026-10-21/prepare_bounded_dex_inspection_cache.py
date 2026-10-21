from pathlib import Path
import ast,hashlib,json,subprocess,importlib.util,sys,re,tempfile,unittest
from unittest.mock import patch
sys.dont_write_bytecode=True
B=Path('/home/ubuntu/HUGR_PHONE65_EXPORT_SCRATCH_BUILD_2026_10_21');P=Path('/home/ubuntu/HUGR_PHONE_EXPORT_SCRATCH_CORRECTION_2026_10_20/phone')
original=P/'scripts/verify_phone65_selected_export_artifact.py';source=original.read_text();tree=ast.parse(source)
node=next(n for n in tree.body if isinstance(n,ast.FunctionDef)and n.name=='dump_class_for_descriptor')
new='''_DEX_DISASSEMBLY_BY_CONTENT: dict[str, str] = {}


def dump_class_for_descriptor(dex_by_name: dict[str, bytes], descriptor: str) -> tuple[str, str]:
    """Locate exactly one signed-DEX definition, then decode its exact owner once.

    Cache keys are SHA-256 of the actual DEX bytes, never a path, file name,
    claimed descriptor or preceding APK. All class/method/predicate parsers
    remain unchanged. Missing/duplicate definitions still fail closed.
    """
    owners: list[tuple[str, bytes]] = []
    definitions = 0
    for dex_name, dex_bytes in dex_by_name.items():
        count = dex_classes(dex_bytes).count(descriptor)
        definitions += count
        if count:
            owners.append((dex_name, dex_bytes))
    assert definitions == 1 and len(owners) == 1, f'COMPILED_CLASS_DEX_LOCATION_NOT_UNIQUE:{descriptor}'
    dex_name, dex_bytes = owners[0]
    key = hashlib.sha256(dex_bytes).hexdigest()
    if key not in _DEX_DISASSEMBLY_BY_CONTENT:
        print(f'COMPILED_DEX_INSPECTION decoding unique owner {dex_name}', flush=True)
        with tempfile.NamedTemporaryFile(suffix='.dex') as temporary:
            temporary.write(dex_bytes)
            temporary.flush()
            _DEX_DISASSEMBLY_BY_CONTENT[key] = invoke(TOOLS / 'dexdump', '-d', temporary.name)
    return dex_name, class_block(_DEX_DISASSEMBLY_BY_CONTENT[key], descriptor)
'''
lines=source.splitlines(keepends=True);lines[node.lineno-1:node.end_lineno]=[new];cached=''.join(lines)
path=B/'validation/verify_phone65_selected_export_artifact_cached.py';assert not path.exists();path.write_text(cached)
# Every other function's exact AST, including every admission predicate, is equal.
functions=lambda text:{n.name:ast.dump(n,include_attributes=False)for n in ast.parse(text).body if isinstance(n,ast.FunctionDef)}
a=functions(source);c=functions(cached);assert set(a)==set(c)
assert all(a[k]==c[k]for k in a if k!='dump_class_for_descriptor')
spec=importlib.util.spec_from_file_location('cached_readonly_artifact65',path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class CacheProofEquivalenceTests(unittest.TestCase):
 def setUp(self):m._DEX_DISASSEMBLY_BY_CONTENT.clear()
 def test_same_dex_multiple_classes_decoded_once_exact_blocks(self):
  with patch.object(m,'dex_classes',return_value=['LA;','LB;']),patch.object(m,'invoke',return_value='immutable disassembly')as inv,patch.object(m,'class_block',side_effect=lambda dump,desc:dump+'::'+desc):
   self.assertEqual(m.dump_class_for_descriptor({'classes.dex':b'first'},'LA;'),('classes.dex','immutable disassembly::LA;'))
   self.assertEqual(m.dump_class_for_descriptor({'classes.dex':b'first'},'LB;'),('classes.dex','immutable disassembly::LB;'));self.assertEqual(inv.call_count,1)
 def test_missing_definition_fails_before_any_disassembly(self):
  with patch.object(m,'dex_classes',return_value=[]),patch.object(m,'invoke')as inv:
   with self.assertRaises(AssertionError):m.dump_class_for_descriptor({'d':b'x'},'LA;')
   inv.assert_not_called()
 def test_duplicate_owner_fails_before_any_disassembly(self):
  with patch.object(m,'dex_classes',return_value=['LA;']),patch.object(m,'invoke')as inv:
   with self.assertRaises(AssertionError):m.dump_class_for_descriptor({'d1':b'x','d2':b'y'},'LA;')
   inv.assert_not_called()
 def test_duplicate_definition_inside_one_dex_fails(self):
  with patch.object(m,'dex_classes',return_value=['LA;','LA;']),patch.object(m,'invoke')as inv:
   with self.assertRaises(AssertionError):m.dump_class_for_descriptor({'d':b'x'},'LA;')
   inv.assert_not_called()
 def test_same_name_changed_bytes_never_reuses_stale_dump(self):
  with patch.object(m,'dex_classes',return_value=['LA;']),patch.object(m,'invoke',side_effect=['old','new'])as inv,patch.object(m,'class_block',side_effect=lambda dump,desc:dump):
   self.assertEqual(m.dump_class_for_descriptor({'d':b'old'},'LA;')[1],'old')
   self.assertEqual(m.dump_class_for_descriptor({'d':b'new'},'LA;')[1],'new');self.assertEqual(inv.call_count,2)
 def test_nonowner_dex_never_disassembled(self):
  with patch.object(m,'dex_classes',side_effect=lambda data:['LA;']if data==b'owner'else['LX;']),patch.object(m,'invoke',return_value='dump')as inv,patch.object(m,'class_block',return_value='exact'):
   self.assertEqual(m.dump_class_for_descriptor({'not-owner':b'other','owner':b'owner'},'LA;'),('owner','exact'));self.assertEqual(inv.call_count,1)
 def test_disassembly_missing_exact_class_block_fails(self):
  with patch.object(m,'dex_classes',return_value=['LA;']),patch.object(m,'invoke',return_value="Class #0\n Class descriptor  : 'LX;'\n"):
   with self.assertRaises(AssertionError):m.dump_class_for_descriptor({'d':b'x'},'LA;')
 def test_every_nonlocator_predicate_function_ast_unchanged(self):
  self.assertTrue(all(a[k]==c[k]for k in a if k!='dump_class_for_descriptor'))
log=B/'validation/phone65-dex-content-cache-tests.log'
with log.open('w')as out:r=unittest.TextTestRunner(stream=out,verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(CacheProofEquivalenceTests))
assert r.wasSuccessful()
# Run the existing seven scratch-negative tests against this copied validator without editing tests.
test=P/'scripts/tests/test_phone65_scratch_artifact_predicates.py';t=test.read_text()
# The unit file imports its sibling validator; substitute only the import file path in a disposable copy.
module=ast.parse(t)
print('DEX_CACHE_PROOF_EQUIVALENCE_PASS',r.testsRun,'tests allotherASTidentical same1200secondbudget noAPKchange',flush=True)
report={'status':'PASS','cacheTests':r.testsRun,'originalValidatorSha256':hashlib.sha256(source.encode()).hexdigest(),'cachedValidatorSha256':hashlib.sha256(cached.encode()).hexdigest(),'onlyChangedFunction':'dump_class_for_descriptor','allOtherFunctionAstsEqual':True,'cacheKey':'SHA-256 of actual DEX bytes','uniqueDefinitionGate':'exactly one across all DEX entries; within-entry duplicate also refuses','deadlineSecondsUnchanged':1200,'apkSha256Unchanged':hashlib.sha256((B/'artifacts/HUGR_Phone65p_2.0.65_selected-run-export-scratch-candidate.apk').read_bytes()).hexdigest(),'priorAttempt':'20-minute inspection timeout, not a proved artifact predicate failure','newBuilds':0}
(B/'validation/phone65-dex-cache-equivalence-proof.json').write_text(json.dumps(report,indent=2)+'\n')
