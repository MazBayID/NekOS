import importlib.util
from pathlib import Path
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "tools" / "validate_skin.py"
SPEC = importlib.util.spec_from_file_location("nekos_validate_skin", SCRIPT)
validator = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(validator)


class GlyphCatalogIdentityTests(unittest.TestCase):
    def setUp(self):
        self.original = validator.DEFAULT_ID_NAMES
        validator.DEFAULT_ID_NAMES = {
            "tag": {11: "mail", 12: "chat"},
            "ui": {1: "pencil"},
        }
        self.report = validator.Report()

    def tearDown(self):
        validator.DEFAULT_ID_NAMES = self.original

    def check(self, items):
        validator.check_index_catalog(
            self.report, {"items": items}, "tag", "icons/tag/"
        )
        return "\n".join(self.report.errors)

    def test_accepts_canonical_id_and_name(self):
        self.check([{"id": 11, "name": "mail", "file": "icon_mail.webp"}])
        self.assertEqual([], self.report.errors)

    def test_rejects_id_reused_for_another_meaning(self):
        errors = self.check([{"id": 11, "name": "chat", "file": "icon_chat.webp"}])
        self.assertIn('ID 11 harus bermakna "mail"', errors)

    def test_rejects_unknown_id(self):
        errors = self.check([{"id": 99, "name": "unknown", "file": "icon_unknown.webp"}])
        self.assertIn("ID 99 tidak dikenal", errors)

    def test_rejects_duplicate_ids(self):
        errors = self.check([
            {"id": 11, "name": "mail", "file": "icon_mail.webp"},
            {"id": 11, "name": "mail", "file": "icon_mail.webp"},
        ])
        self.assertIn("ID glyph duplikat 11", errors)

    def test_rejects_filename_that_does_not_match_name(self):
        errors = self.check([{"id": 11, "name": "mail", "file": "icon_chat.webp"}])
        self.assertIn('file untuk "mail" harus "icon_mail.webp"', errors)

    def test_rejects_duplicate_names(self):
        errors = self.check([
            {"id": 11, "name": "mail", "file": "icon_mail.webp"},
            {"id": 12, "name": "mail", "file": "icon_mail.webp"},
        ])
        self.assertIn('nama glyph duplikat "mail"', errors)

    def test_applies_the_same_contract_to_ui_catalog(self):
        validator.check_index_catalog(
            self.report,
            {"items": [{"id": 1, "name": "pencil", "file": "icon_pencil.webp"}]},
            "ui",
            "icons/ui/",
        )
        self.assertEqual([], self.report.errors)


if __name__ == "__main__":
    unittest.main()
