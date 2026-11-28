import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


TOOLS = Path(__file__).resolve().parent.parent
PACK_ROOT = TOOLS.parent


class IconPackRunnerTests(unittest.TestCase):
    def make_pack_copy(self, root):
        (root / "tools").mkdir(parents=True)
        (root / "icons" / "apps").mkdir(parents=True)
        shutil.copy(PACK_ROOT / "tools" / "keyword_rules.json", root / "tools" / "keyword_rules.json")
        shutil.copy(PACK_ROOT / "tools" / "map_packages.py", root / "tools" / "map_packages.py")
        shutil.copy(PACK_ROOT / "tools" / "import_appfilter.py", root / "tools" / "import_appfilter.py")
        rules = json.loads((root / "tools" / "keyword_rules.json").read_text(encoding="utf-8"))
        glyphs = {rules["default"], *(glyph for _, glyph in rules["rules"])}
        for glyph in glyphs:
            (root / "icons" / "apps" / f"icon_{glyph}.webp").write_bytes(b"test")
        base = {
            "schema_version": 1,
            "default_glyph": rules["default"],
            "glyph_dir": "icons/apps",
            "glyph_file_pattern": "icon_{glyph}.webp",
            "total_apps": 1,
            "total_glyphs": len(glyphs),
            "glyphs_used": 1,
            "icon_dir": "icons/apps_full",
            "icon_file_pattern": "{package}.webp",
            "icons_bespoke": 1,
            "icons_monogram": 0,
            "apps": [{
                "package": "com.example.existing",
                "name": "Existing",
                "glyph": rules["default"],
                "kind": "bespoke",
                "icon": "icons/apps_full/com.example.existing.webp",
            }],
        }
        (root / "app_icon_map.json").write_text(json.dumps(base), encoding="utf-8")
        return root

    def test_map_packages_parses_csv_names_and_marks_glyph_entries(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = self.make_pack_copy(Path(temporary))
            input_path = root / "packages.csv"
            output_path = root / "extra_map.json"
            input_path.write_text(
                '# comment\n'
                'com.example.existing,Existing\n'
                'com.example.musicplayer,"Music, Plus"\n'
                'com.example.unknown,Unknown App\n'
                'com.example.unknown,Duplicate\n',
                encoding="utf-8",
            )
            subprocess.run(
                [sys.executable, str(root / "tools" / "map_packages.py"),
                 str(input_path), "-o", str(output_path)],
                check=True,
                capture_output=True,
                text=True,
            )
            result = json.loads(output_path.read_text(encoding="utf-8"))
            self.assertEqual(result["schema_version"], 2)
            self.assertEqual(len(result["apps"]), 3)
            music = result["apps"][1]
            self.assertEqual(music["name"], "Music, Plus")
            self.assertEqual(music["kind"], "glyph")
            self.assertIsNone(music["icon"])
            self.assertEqual(result["apps"][0]["source"], "exact")

    def test_import_appfilter_merge_keeps_schema_and_recounts(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = self.make_pack_copy(Path(temporary))
            xml_path = root / "appfilter.xml"
            xml_path.write_text(
                '<resources>'
                '<item component="ComponentInfo{com.example.existing/com.example.Main}" name="Old"/>'
                '<item component="ComponentInfo{com.example.musicplayer/com.example.Main}" name="Music Player"/>'
                '<item component="ComponentInfo{com.example.unknown/com.example.Main}" name="Unknown"/>'
                '</resources>',
                encoding="utf-8",
            )
            subprocess.run(
                [sys.executable, str(root / "tools" / "import_appfilter.py"),
                 str(xml_path), "--merge"],
                check=True,
                capture_output=True,
                text=True,
            )
            result = json.loads((root / "app_icon_map.json").read_text(encoding="utf-8"))
            self.assertEqual(result["schema_version"], 2)
            self.assertEqual(result["total_apps"], 3)
            self.assertEqual(len(result["apps"]), 3)
            added = result["apps"][1:]
            self.assertTrue(all(item["kind"] == "glyph" for item in added))
            self.assertTrue(all(item["icon"] is None for item in added))
            self.assertEqual(result["icons_bespoke"], 1)
            self.assertEqual(result["icons_monogram"], 0)
            self.assertGreaterEqual(result["glyphs_used"], 1)

    def test_invalid_package_rows_fail_without_output(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = self.make_pack_copy(Path(temporary))
            input_path = root / "bad.txt"
            output_path = root / "should_not_exist.json"
            input_path.write_text("not-a-package,Invalid\n", encoding="utf-8")
            result = subprocess.run(
                [sys.executable, str(root / "tools" / "map_packages.py"),
                 str(input_path), "-o", str(output_path)],
                capture_output=True,
                text=True,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse(output_path.exists())


if __name__ == "__main__":
    unittest.main()
