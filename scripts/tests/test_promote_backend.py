import importlib.util,tempfile,unittest,zipfile
from pathlib import Path
from unittest.mock import patch
from types import SimpleNamespace
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
    def test_legacy_core_does_not_require_ai_process(self):
        with tempfile.TemporaryDirectory() as folder:
            incoming=Path(folder)/'core.jar'
            with zipfile.ZipFile(incoming,'w') as jar:jar.writestr('Old.class',b'old')
            with patch.object(promote.subprocess,'run') as command:
                promote.require_extracted_ai_ready(incoming);command.assert_not_called()

    def test_extracted_core_refuses_single_jar_rollout_before_ai_is_running(self):
        with tempfile.TemporaryDirectory() as folder:
            incoming=Path(folder)/'core.jar'
            with zipfile.ZipFile(incoming,'w') as jar:jar.writestr('BOOT-INF/classes/com/hongjie/pms/bootstrap/ai/AiCoreController.class',b'new')
            with patch.object(promote.subprocess,'run',return_value=SimpleNamespace(returncode=3)):
                with self.assertRaisesRegex(RuntimeError,'coordinated'):promote.require_extracted_ai_ready(incoming)

    def test_core_update_requires_both_running_ai_and_gateway_route(self):
        with tempfile.TemporaryDirectory() as folder:
            incoming=Path(folder)/'core.jar';gateway=Path(folder)/'gateway.jar'
            with zipfile.ZipFile(incoming,'w') as jar:jar.writestr('BOOT-INF/classes/com/hongjie/pms/bootstrap/ai/AiCoreController.class',b'new')
            def route(content):
                with zipfile.ZipFile(gateway,'w') as jar:jar.writestr('BOOT-INF/classes/com/hongjie/pms/gateway/GatewayRoutes.class',content)
            with patch.object(promote.subprocess,'run',return_value=SimpleNamespace(returncode=0)),patch.object(promote.subprocess,'check_output',return_value='-jar '+str(gateway)):
                route(b'pms-core-service')
                with self.assertRaisesRegex(RuntimeError,'gateway route'):promote.require_extracted_ai_ready(incoming)
                route(b'pms-core-service pms-ai-service');promote.require_extracted_ai_ready(incoming)

if __name__=='__main__':unittest.main()
