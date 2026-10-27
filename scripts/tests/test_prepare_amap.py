import importlib.util
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location(
    "prepare_amap", Path(__file__).parents[1] / "deploy/prepare_amap.py"
)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PrepareAmapTest(unittest.TestCase):
    def test_build_cleanup_directories_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for build in ["target", "dist", "node_modules"]:
                with self.subTest(build=build):
                    with self.assertRaisesRegex(ValueError, "构建清理目录"):
                        module.prepare(root / build / "amap.env", root / "safe")
                    with self.assertRaisesRegex(ValueError, "构建清理目录"):
                        module.prepare(root / "amap.env", root / build)

    def test_security_code_only_enters_private_proxy_file(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            source = root / "input.env"
            source.write_text(
                "PMS_AMAP_JS_API_KEY=public_key_0123456789\nPMS_AMAP_JS_SECURITY_CODE=private_code_0123456789\n"
            )
            module.prepare(source, root / "out")
            core = (root / "out/core-amap.env").read_text()
            proxy = (root / "out/pms-amap.enabled.conf").read_text()
            self.assertIn("PMS_AMAP_ENABLED=true", core)
            self.assertNotIn("private_code", core)
            self.assertIn("jscode=private_code_0123456789", proxy)
            self.assertIn('if ($arg_key != "public_key_0123456789")', proxy)
            self.assertIn("proxy_ssl_verify on", proxy)
            self.assertIn("limit_req zone=pms_amap", proxy)
            self.assertNotIn("proxy_pass $", proxy)
            self.assertIn("coordinate/convert", proxy)

    def test_invalid_duplicate_placeholder_or_injectable_values_rejected(self):
        for contents in [
            "",
            "PMS_AMAP_JS_API_KEY=REPLACE_WITH_JS_API_KEY",
            "PMS_AMAP_JS_API_KEY=public_key_0123456789\nPMS_AMAP_JS_API_KEY=public_key_0123456789",
            "PMS_AMAP_JS_API_KEY=evil;return 200;\nPMS_AMAP_JS_SECURITY_CODE=private_code_0123456789",
            "PMS_AMAP_WEB_SERVICE_KEY=wrong_type_of_key",
        ]:
            with self.subTest(contents=contents), tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                source = root / "input.env"
                source.write_text(contents)
                with self.assertRaises(ValueError):
                    module.prepare(source, root / "out")
                self.assertFalse((root / "out").exists())


if __name__ == "__main__":
    unittest.main()
