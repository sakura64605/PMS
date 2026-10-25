import importlib.util,tempfile,unittest,zipfile
from pathlib import Path
spec=importlib.util.spec_from_file_location('promote',Path(__file__).resolve().parents[1]/'deploy/promote_backend.py')
promote=importlib.util.module_from_spec(spec);spec.loader.exec_module(promote)
class CandidateTest(unittest.TestCase):
    def test_preserves_server_configuration_without_resurrecting_old_code_or_vue(self):
        with tempfile.TemporaryDirectory() as folder:
            current,incoming,stage=[Path(folder)/n for n in ['current.jar','incoming.jar','stage.jar']]
            with zipfile.ZipFile(current,'w') as jar:
                jar.writestr('BOOT-INF/classes/application.yaml','production-config')
                jar.writestr('BOOT-INF/classes/application-prod.yaml','production-profile')
                jar.writestr('BOOT-INF/classes/static/index.html','old Vue')
                jar.writestr('BOOT-INF/classes/Old.class','old code')
            with zipfile.ZipFile(incoming,'w') as jar:
                jar.writestr('BOOT-INF/classes/application.yaml','development-config')
                jar.writestr('BOOT-INF/classes/New.class','new code')
                jar.writestr('BOOT-INF/lib/engine.jar',b'library',compress_type=zipfile.ZIP_STORED)
            promote.assemble(current,incoming,stage)
            with zipfile.ZipFile(stage) as jar:
                self.assertEqual(jar.read('BOOT-INF/classes/application.yaml'),b'production-config')
                self.assertEqual(jar.read('BOOT-INF/classes/application-prod.yaml'),b'production-profile')
                self.assertIn('BOOT-INF/classes/New.class',jar.namelist())
                self.assertNotIn('BOOT-INF/classes/Old.class',jar.namelist())
                self.assertNotIn('BOOT-INF/classes/static/index.html',jar.namelist())
                self.assertEqual(jar.getinfo('BOOT-INF/lib/engine.jar').compress_type,zipfile.ZIP_STORED)
if __name__=='__main__':unittest.main()
