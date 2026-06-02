import tempfile
import unittest
from dataclasses import replace
from pathlib import Path

from scripts.bulk_upload_documents import (
    ManifestDocument,
    PreflightError,
    UploadDocument,
    build_upload_body,
    load_manifest_rows,
    resolve_upload_documents,
    validate_local_batch,
    validate_plan_limits,
)


class BulkUploadDocumentsTest(unittest.TestCase):
    def test_csv_manifest_validates_and_resolves_department_metadata(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            folder = Path(temp_dir)
            (folder / "sales.txt").write_text("Lead process", encoding="utf-8")
            manifest = folder / "manifest.csv"
            manifest.write_text(
                "filename,title,department,min_role_level,visibility,category,tags,version_note\n"
                'sales.txt,"Quy trinh xu ly Lead",Sales,1,DEPARTMENT,Sales,sales;process,v1\n',
                encoding="utf-8",
            )

            local = validate_local_batch(folder, manifest, load_manifest_rows(manifest))
            resolved = resolve_upload_documents(
                local,
                [{"id": "cat-sales", "code": "SALES", "name": "Sales"}],
                [
                    {"id": "tag-sales", "code": "SALES", "name": "Sales"},
                    {"id": "tag-process", "code": "PROCESS", "name": "Process"},
                ],
                [{"id": 12, "code": "SALES", "name": "Sales"}],
            )

            self.assertEqual("SPECIFIC_DEPARTMENTS", resolved[0].api_visibility)
            self.assertEqual((12,), resolved[0].accessible_departments)
            self.assertEqual(("tag-sales", "tag-process"), resolved[0].tag_ids)

    def test_json_manifest_accepts_document_array_and_tag_list(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            folder = Path(temp_dir)
            (folder / "company.txt").write_text("Company overview", encoding="utf-8")
            manifest = folder / "manifest.json"
            manifest.write_text(
                """
                {
                  "documents": [
                    {
                      "filename": "company.txt",
                      "title": "Company overview",
                      "department": "ALL",
                      "min_role_level": 1,
                      "visibility": "COMPANY_WIDE",
                      "category": "",
                      "tags": ["company", "onboarding"],
                      "version_note": "v1"
                    }
                  ]
                }
                """,
                encoding="utf-8",
            )

            local = validate_local_batch(folder, manifest, load_manifest_rows(manifest))

            self.assertEqual(("company", "onboarding"), local[0].tags)

    def test_csv_manifest_rejects_extra_values(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            manifest = Path(temp_dir) / "manifest.csv"
            manifest.write_text(
                "filename,title,department,min_role_level,visibility,category,tags,version_note\n"
                "company.txt,Company,ALL,1,COMPANY_WIDE,,,v1,unexpected\n",
                encoding="utf-8",
            )

            with self.assertRaises(PreflightError) as raised:
                load_manifest_rows(manifest)

            self.assertIn("more values than manifest columns", str(raised.exception))

    def test_local_validation_reports_all_row_errors_before_upload(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            folder = Path(temp_dir)
            (folder / "extra.txt").write_text("not in manifest", encoding="utf-8")
            manifest = folder / "manifest.csv"
            manifest.write_text(
                "filename,title,department,min_role_level,visibility,category,tags,version_note\n"
                "missing.txt,,ALL,9,DEPARTMENT,,, \n",
                encoding="utf-8",
            )

            with self.assertRaises(PreflightError) as raised:
                validate_local_batch(folder, manifest, load_manifest_rows(manifest))

            message = str(raised.exception)
            self.assertIn("document file does not exist", message)
            self.assertIn("missing from the manifest", message)

    def test_local_validation_reports_metadata_errors_for_existing_file(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            folder = Path(temp_dir)
            (folder / "bad.txt").write_text("bad metadata", encoding="utf-8")
            manifest = folder / "manifest.csv"
            manifest.write_text(
                "filename,title,department,min_role_level,visibility,category,tags,version_note\n"
                "bad.txt,,ALL,9,DEPARTMENT,,, \n",
                encoding="utf-8",
            )

            with self.assertRaises(PreflightError) as raised:
                validate_local_batch(folder, manifest, load_manifest_rows(manifest))

            message = str(raised.exception)
            self.assertIn("title is required", message)
            self.assertIn("DEPARTMENT documents must name one department", message)
            self.assertIn("min_role_level must be an integer from 1 to 5", message)
            self.assertIn("version_note is required", message)

    def test_local_validation_rejects_text_above_database_limits(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            folder = Path(temp_dir)
            (folder / "bad.txt").write_text("bad metadata", encoding="utf-8")
            manifest = folder / "manifest.csv"
            manifest.write_text(
                "filename,title,department,min_role_level,visibility,category,tags,version_note\n"
                f"bad.txt,{'t' * 501},ALL,1,COMPANY_WIDE,,,{('v' * 501)}\n",
                encoding="utf-8",
            )

            with self.assertRaises(PreflightError) as raised:
                validate_local_batch(folder, manifest, load_manifest_rows(manifest))

            message = str(raised.exception)
            self.assertIn("title exceeds 500 characters", message)
            self.assertIn("version_note exceeds 500 characters", message)

    def test_plan_validation_includes_existing_and_incoming_storage(self):
        document = self.make_document(file_size=600 * 1024 * 1024)

        with self.assertRaises(PreflightError) as raised:
            validate_plan_limits(
                [document],
                [{"fileSize": 600 * 1024 * 1024}],
                {"maxDocuments": 10, "maxStorageGb": 1},
            )

        self.assertIn("storage plan limit exceeded", str(raised.exception))

    def test_plan_validation_rejects_projected_document_count(self):
        with self.assertRaises(PreflightError) as raised:
            validate_plan_limits(
                [self.make_document(file_size=1)],
                [{"fileSize": 1}, {"fileSize": 1}],
                {"maxDocuments": 2, "maxStorageGb": 1},
            )

        self.assertIn("document plan limit exceeded", str(raised.exception))

    def test_upload_body_contains_resolved_manifest_metadata(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            file_path = Path(temp_dir) / "sales.txt"
            file_path.write_text("Lead process", encoding="utf-8")
            source = self.make_document(file_size=file_path.stat().st_size)
            source = replace(source, file_path=file_path, title="Lead process")
            document = UploadDocument(
                source=source,
                category_id="category-id",
                tag_ids=("tag-sales", "tag-process"),
                api_visibility="SPECIFIC_DEPARTMENTS",
                accessible_departments=(12,),
            )

            body, _ = build_upload_body(document)
            payload = body.decode("utf-8")

            self.assertIn('name="documentTitle"\r\n\r\nLead process', payload)
            self.assertIn('name="versionNote"\r\n\r\nv1', payload)
            self.assertIn('name="categoryId"\r\n\r\ncategory-id', payload)
            self.assertIn('name="tagIds"\r\n\r\ntag-sales', payload)
            self.assertIn('name="tagIds"\r\n\r\ntag-process', payload)
            self.assertIn('name="accessibleDepartments"\r\n\r\n12', payload)
            self.assertIn('name="visibility"\r\n\r\nSPECIFIC_DEPARTMENTS', payload)

    @staticmethod
    def make_document(file_size):
        return ManifestDocument(
            row_number=2,
            file_path=Path("new.txt"),
            title="New",
            department="ALL",
            minimum_role_level=1,
            visibility="COMPANY_WIDE",
            category="",
            tags=(),
            version_note="v1",
            file_size=file_size,
        )


if __name__ == "__main__":
    unittest.main()
