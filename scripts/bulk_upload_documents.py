#!/usr/bin/env python3
"""Preflight and bulk upload tenant documents through the backend API."""

from __future__ import annotations

import argparse
import csv
import json
import os
import sys
import time
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


DEFAULT_BACKEND_BASE_URL = (
    "https://sp26se070internalchatbotbe-production.up.railway.app"
)

MANIFEST_COLUMNS = (
    "filename",
    "title",
    "department",
    "min_role_level",
    "visibility",
    "category",
    "tags",
    "version_note",
)

SUPPORTED_CONTENT_TYPES = {
    ".pdf": "application/pdf",
    ".docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    ".xlsx": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    ".pptx": "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    ".txt": "text/plain",
    ".md": "text/markdown",
    ".csv": "text/csv",
}

MAX_FILE_SIZE_BY_EXTENSION = {
    ".pdf": 30 * 1024 * 1024,
    ".docx": 20 * 1024 * 1024,
    ".xlsx": 15 * 1024 * 1024,
    ".pptx": 30 * 1024 * 1024,
    ".txt": 5 * 1024 * 1024,
    ".md": 5 * 1024 * 1024,
    ".csv": 15 * 1024 * 1024,
}

# documents.file_name stores "{uuid}_{original filename}" in a varchar(255).
MAX_ORIGINAL_FILENAME_LENGTH = 255 - 37


class PreflightError(ValueError):
    """Raised when the batch is unsafe to upload."""

    def __init__(self, errors: Iterable[str]):
        self.errors = list(errors)
        super().__init__("\n".join(self.errors))


@dataclass(frozen=True)
class ManifestDocument:
    row_number: int
    file_path: Path
    title: str
    department: str
    minimum_role_level: int
    visibility: str
    category: str
    tags: tuple[str, ...]
    version_note: str
    file_size: int


@dataclass(frozen=True)
class UploadDocument:
    source: ManifestDocument
    category_id: str | None
    tag_ids: tuple[str, ...]
    api_visibility: str
    accessible_departments: tuple[int, ...]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Validate a folder and CSV/JSON manifest as one batch, check tenant plan "
            "limits, then upload each document with its metadata."
        )
    )
    parser.add_argument(
        "--base-url",
        default=DEFAULT_BACKEND_BASE_URL,
        help=f"Backend base URL (default: {DEFAULT_BACKEND_BASE_URL})",
    )
    parser.add_argument(
        "--email",
        default=os.getenv("BULK_UPLOAD_EMAIL"),
        help="Login email. Defaults to BULK_UPLOAD_EMAIL.",
    )
    parser.add_argument(
        "--password",
        default=os.getenv("BULK_UPLOAD_PASSWORD"),
        help="Login password. Defaults to BULK_UPLOAD_PASSWORD.",
    )
    parser.add_argument(
        "--token",
        default=os.getenv("BULK_UPLOAD_TOKEN"),
        help="Existing JWT. Defaults to BULK_UPLOAD_TOKEN and skips login when set.",
    )
    parser.add_argument(
        "--folder",
        type=Path,
        required=True,
        help="Folder containing the documents to upload.",
    )
    parser.add_argument(
        "--manifest",
        type=Path,
        help="CSV or JSON manifest. Defaults to manifest.csv or manifest.json in --folder.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Run local and remote preflight checks without uploading files.",
    )
    parser.add_argument(
        "--delay-seconds",
        type=float,
        default=1.0,
        help="Delay between uploads in seconds (default: 1.0).",
    )
    parser.add_argument(
        "--timeout-seconds",
        type=float,
        default=120.0,
        help="HTTP timeout per request in seconds (default: 120.0).",
    )
    args = parser.parse_args()

    if args.delay_seconds < 0:
        parser.error("--delay-seconds cannot be negative")
    if args.timeout_seconds <= 0:
        parser.error("--timeout-seconds must be positive")
    if not args.token and (not args.email or not args.password):
        parser.error("provide --token or both --email and --password")

    args.base_url = args.base_url.rstrip("/")
    return args


def decode_response(response: Any) -> Any:
    body = response.read().decode("utf-8", errors="replace")
    if not body:
        return None
    try:
        return json.loads(body)
    except json.JSONDecodeError:
        return body


def send_request(request: Request, timeout_seconds: float) -> Any:
    try:
        with urlopen(request, timeout=timeout_seconds) as response:
            return decode_response(response)
    except HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {error.code}: {body}") from error
    except URLError as error:
        raise RuntimeError(f"Request failed: {error.reason}") from error


def login(base_url: str, email: str, password: str, timeout_seconds: float) -> str:
    payload = json.dumps({"email": email, "password": password}).encode("utf-8")
    request = Request(
        f"{base_url}/api/v1/auth/login",
        data=payload,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    result = send_request(request, timeout_seconds)
    token = result.get("accessToken") if isinstance(result, dict) else None
    if not token:
        raise RuntimeError("Login response did not include accessToken")
    return token


def get_json(
    base_url: str,
    token: str,
    path: str,
    timeout_seconds: float,
    query: dict[str, Any] | None = None,
) -> Any:
    query_string = f"?{urlencode(query)}" if query else ""
    request = Request(
        f"{base_url}{path}{query_string}",
        headers={"Authorization": f"Bearer {token}", "Accept": "application/json"},
        method="GET",
    )
    return send_request(request, timeout_seconds)


def find_manifest(folder: Path, manifest_arg: Path | None) -> Path:
    if manifest_arg:
        return manifest_arg.expanduser().resolve()

    candidates = [
        path
        for path in (folder / "manifest.csv", folder / "manifest.json")
        if path.is_file()
    ]
    if len(candidates) == 1:
        return candidates[0]
    if not candidates:
        raise PreflightError(
            ["manifest not found: provide --manifest or add manifest.csv/manifest.json to the document folder"]
        )
    raise PreflightError(
        ["both manifest.csv and manifest.json exist: remove one or select one with --manifest"]
    )


def load_manifest_rows(manifest_path: Path) -> list[dict[str, Any]]:
    if not manifest_path.is_file():
        raise PreflightError([f"manifest does not exist or is not a file: {manifest_path}"])

    suffix = manifest_path.suffix.lower()
    try:
        if suffix == ".csv":
            with manifest_path.open("r", encoding="utf-8-sig", newline="") as handle:
                reader = csv.DictReader(handle)
                if reader.fieldnames is None:
                    raise PreflightError(["manifest CSV is empty"])
                validate_manifest_columns(reader.fieldnames)
                rows = [dict(row) for row in reader]
                for row_number, row in enumerate(rows, start=2):
                    if row.get(None):
                        raise PreflightError(
                            [f"row {row_number}: CSV row has more values than manifest columns"]
                        )
                return rows

        if suffix == ".json":
            with manifest_path.open("r", encoding="utf-8-sig") as handle:
                payload = json.load(handle)
            rows = payload.get("documents") if isinstance(payload, dict) else payload
            if not isinstance(rows, list):
                raise PreflightError(
                    ["manifest JSON must be an array or an object with a 'documents' array"]
                )
            for index, row in enumerate(rows, start=1):
                if not isinstance(row, dict):
                    raise PreflightError([f"manifest JSON item {index} must be an object"])
                validate_manifest_columns(row.keys())
            return rows
    except (OSError, json.JSONDecodeError) as error:
        raise PreflightError([f"cannot read manifest {manifest_path}: {error}"]) from error

    raise PreflightError(["manifest must use the .csv or .json extension"])


def validate_manifest_columns(columns: Iterable[str]) -> None:
    actual = {str(column).strip() for column in columns if column is not None}
    expected = set(MANIFEST_COLUMNS)
    missing = sorted(expected - actual)
    unknown = sorted(actual - expected)
    errors = []
    if missing:
        errors.append(f"manifest is missing required column(s): {', '.join(missing)}")
    if unknown:
        errors.append(f"manifest has unknown column(s): {', '.join(unknown)}")
    if errors:
        raise PreflightError(errors)


def normalize_text(value: Any) -> str:
    if value is None:
        return ""
    return str(value).strip()


def parse_tags(value: Any) -> tuple[str, ...]:
    if isinstance(value, list):
        raw_tags = value
    else:
        raw_tags = normalize_text(value).split(";")

    tags = []
    seen = set()
    for raw_tag in raw_tags:
        tag = normalize_text(raw_tag)
        key = tag.casefold()
        if tag and key not in seen:
            tags.append(tag)
            seen.add(key)
    return tuple(tags)


def validate_local_batch(
    folder: Path, manifest_path: Path, rows: list[dict[str, Any]]
) -> list[ManifestDocument]:
    errors: list[str] = []
    documents: list[ManifestDocument] = []
    seen_filenames: set[str] = set()

    if not rows:
        raise PreflightError(["manifest has no document rows"])

    for row_number, row in enumerate(rows, start=2):
        prefix = f"row {row_number}"
        filename = normalize_text(row.get("filename"))
        title = normalize_text(row.get("title"))
        department = normalize_text(row.get("department"))
        visibility = normalize_text(row.get("visibility")).upper()
        category = normalize_text(row.get("category"))
        tags = parse_tags(row.get("tags"))
        version_note = normalize_text(row.get("version_note"))
        minimum_role_level_text = normalize_text(row.get("min_role_level"))

        if not filename:
            errors.append(f"{prefix}: filename is required")
            continue
        if Path(filename).name != filename or "/" in filename or "\\" in filename:
            errors.append(f"{prefix}: filename must be a plain file name without a path: {filename!r}")
            continue
        if len(filename) > MAX_ORIGINAL_FILENAME_LENGTH:
            errors.append(
                f"{prefix}: filename exceeds {MAX_ORIGINAL_FILENAME_LENGTH} characters: {filename!r}"
            )

        filename_key = filename.casefold()
        if filename_key in seen_filenames:
            errors.append(f"{prefix}: duplicate filename in manifest: {filename!r}")
            continue
        seen_filenames.add(filename_key)

        file_path = folder / filename
        if not file_path.is_file():
            errors.append(f"{prefix}: document file does not exist: {filename!r}")
            continue

        suffix = file_path.suffix.lower()
        if suffix not in SUPPORTED_CONTENT_TYPES:
            supported = ", ".join(sorted(SUPPORTED_CONTENT_TYPES))
            errors.append(f"{prefix}: unsupported extension {suffix!r}; expected one of: {supported}")
            continue

        file_size = file_path.stat().st_size
        if file_size <= 0:
            errors.append(f"{prefix}: document file is empty: {filename!r}")
        elif file_size > MAX_FILE_SIZE_BY_EXTENSION[suffix]:
            errors.append(
                f"{prefix}: {filename!r} is {format_bytes(file_size)}, above the "
                f"{format_bytes(MAX_FILE_SIZE_BY_EXTENSION[suffix])} per-file limit for {suffix}"
            )

        if not title:
            errors.append(f"{prefix}: title is required")
        elif len(title) > 500:
            errors.append(f"{prefix}: title exceeds 500 characters")
        if not department:
            errors.append(f"{prefix}: department is required; use ALL for COMPANY_WIDE documents")
        if visibility not in {"COMPANY_WIDE", "DEPARTMENT"}:
            errors.append(f"{prefix}: visibility must be COMPANY_WIDE or DEPARTMENT")
        if visibility == "COMPANY_WIDE" and department.upper() != "ALL":
            errors.append(f"{prefix}: COMPANY_WIDE documents must use department=ALL")
        if visibility == "DEPARTMENT" and department.upper() == "ALL":
            errors.append(f"{prefix}: DEPARTMENT documents must name one department, not ALL")
        if not version_note:
            errors.append(f"{prefix}: version_note is required")
        elif len(version_note) > 500:
            errors.append(f"{prefix}: version_note exceeds 500 characters")

        try:
            minimum_role_level = int(minimum_role_level_text)
            if not 1 <= minimum_role_level <= 5:
                raise ValueError
        except ValueError:
            errors.append(f"{prefix}: min_role_level must be an integer from 1 to 5")
            minimum_role_level = 0

        documents.append(
            ManifestDocument(
                row_number=row_number,
                file_path=file_path,
                title=title,
                department=department,
                minimum_role_level=minimum_role_level,
                visibility=visibility,
                category=category,
                tags=tags,
                version_note=version_note,
                file_size=file_size,
            )
        )

    listed_files = {document.file_path.name.casefold() for document in documents}
    ignored_files = {manifest_path.name.casefold()} if manifest_path.parent == folder else set()
    folder_files = {
        path.name.casefold()
        for path in folder.iterdir()
        if path.is_file() and path.name.casefold() not in ignored_files
    }
    unlisted_files = sorted(folder_files - listed_files)
    if unlisted_files:
        errors.append(
            "document folder contains file(s) missing from the manifest: "
            + ", ".join(unlisted_files)
        )

    if errors:
        raise PreflightError(errors)
    return documents


def make_reference_index(items: Any, resource_name: str) -> dict[str, dict[str, Any] | None]:
    if not isinstance(items, list):
        raise PreflightError([f"{resource_name} API returned an unexpected response"])

    index: dict[str, dict[str, Any] | None] = {}
    for item in items:
        if not isinstance(item, dict) or item.get("id") is None:
            raise PreflightError([f"{resource_name} API returned an invalid item"])
        for field in ("code", "name"):
            key = normalize_text(item.get(field)).casefold()
            if not key:
                continue
            if key in index and index[key] != item:
                index[key] = None
            else:
                index[key] = item
    return index


def resolve_reference(
    value: str,
    index: dict[str, dict[str, Any] | None],
    resource_name: str,
    row_number: int,
) -> dict[str, Any]:
    match = index.get(value.casefold())
    if match is None:
        reason = "is ambiguous; use its unique code" if value.casefold() in index else "does not exist or is inactive"
        raise PreflightError([f"row {row_number}: {resource_name} {value!r} {reason}"])
    return match


def resolve_upload_documents(
    documents: list[ManifestDocument],
    categories: Any,
    tags: Any,
    departments: Any,
) -> list[UploadDocument]:
    category_index = make_reference_index(categories, "category")
    tag_index = make_reference_index(tags, "tag")
    department_index = make_reference_index(departments, "department")
    errors: list[str] = []
    resolved: list[UploadDocument] = []

    for document in documents:
        try:
            category_id = None
            if document.category:
                category = resolve_reference(
                    document.category, category_index, "category", document.row_number
                )
                category_id = str(category["id"])

            tag_ids = tuple(
                str(resolve_reference(tag, tag_index, "tag", document.row_number)["id"])
                for tag in document.tags
            )

            if document.visibility == "COMPANY_WIDE":
                api_visibility = "COMPANY_WIDE"
                accessible_departments: tuple[int, ...] = ()
            else:
                department = resolve_reference(
                    document.department, department_index, "department", document.row_number
                )
                api_visibility = "SPECIFIC_DEPARTMENTS"
                accessible_departments = (int(department["id"]),)

            resolved.append(
                UploadDocument(
                    source=document,
                    category_id=category_id,
                    tag_ids=tag_ids,
                    api_visibility=api_visibility,
                    accessible_departments=accessible_departments,
                )
            )
        except (PreflightError, TypeError, ValueError) as error:
            if isinstance(error, PreflightError):
                errors.extend(error.errors)
            else:
                errors.append(f"row {document.row_number}: invalid remote metadata: {error}")

    if errors:
        raise PreflightError(errors)
    return resolved


def get_existing_documents(
    base_url: str, token: str, timeout_seconds: float
) -> list[dict[str, Any]]:
    page = 0
    documents: list[dict[str, Any]] = []
    while True:
        result = get_json(
            base_url,
            token,
            "/api/v1/knowledge/documents",
            timeout_seconds,
            {"page": page, "size": 500},
        )
        if not isinstance(result, dict) or not isinstance(result.get("content"), list):
            raise PreflightError(["document list API returned an unexpected response"])
        documents.extend(result["content"])
        if result.get("last") is True:
            return documents
        page += 1
        if page > 10000:
            raise PreflightError(["document list API pagination did not terminate"])


def validate_plan_limits(
    documents: list[ManifestDocument],
    existing_documents: Any,
    subscription: Any,
) -> tuple[int, int]:
    if not isinstance(existing_documents, list):
        raise PreflightError(["existing document usage is not a list"])
    if not isinstance(subscription, dict):
        raise PreflightError(["subscription API returned an unexpected response"])

    errors: list[str] = []
    existing_size = 0
    for document in existing_documents:
        try:
            existing_size += int(document.get("fileSize") or 0)
        except (AttributeError, TypeError, ValueError):
            errors.append("document list API returned an invalid fileSize")
            break

    incoming_size = sum(document.file_size for document in documents)
    max_documents = subscription.get("maxDocuments")
    max_storage_gb = subscription.get("maxStorageGb")

    if max_documents is not None:
        try:
            max_documents_int = int(max_documents)
            projected_documents = len(existing_documents) + len(documents)
            if projected_documents > max_documents_int:
                errors.append(
                    "document plan limit exceeded: "
                    f"{len(existing_documents)} existing + {len(documents)} incoming = "
                    f"{projected_documents}, but the plan allows {max_documents_int}"
                )
        except (TypeError, ValueError):
            errors.append("subscription API returned an invalid maxDocuments")

    if max_storage_gb is not None:
        try:
            max_storage_bytes = int(max_storage_gb) * 1024 * 1024 * 1024
            projected_size = existing_size + incoming_size
            if projected_size > max_storage_bytes:
                errors.append(
                    "storage plan limit exceeded: "
                    f"{format_bytes(existing_size)} existing + {format_bytes(incoming_size)} incoming = "
                    f"{format_bytes(projected_size)}, but the plan allows {format_bytes(max_storage_bytes)}"
                )
        except (TypeError, ValueError):
            errors.append("subscription API returned an invalid maxStorageGb")

    if errors:
        raise PreflightError(errors)
    return existing_size, incoming_size


def run_remote_preflight(
    base_url: str,
    token: str,
    timeout_seconds: float,
    documents: list[ManifestDocument],
) -> tuple[list[UploadDocument], int, int, dict[str, Any]]:
    categories = get_json(base_url, token, "/api/v1/knowledge/categories", timeout_seconds)
    tags = get_json(base_url, token, "/api/v1/knowledge/tags", timeout_seconds)
    departments = get_json(
        base_url,
        token,
        "/api/v1/knowledge/documents/access-scope/departments",
        timeout_seconds,
    )
    subscription = get_json(
        base_url, token, "/api/v1/tenant-subscription/my-subscription", timeout_seconds
    )
    existing_documents = get_existing_documents(base_url, token, timeout_seconds)
    upload_documents = resolve_upload_documents(documents, categories, tags, departments)
    existing_size, incoming_size = validate_plan_limits(
        documents, existing_documents, subscription
    )
    return upload_documents, existing_size, incoming_size, subscription


def append_text_field(body: bytearray, boundary: str, name: str, value: str) -> None:
    body.extend(f"--{boundary}\r\n".encode("ascii"))
    body.extend(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode("ascii"))
    body.extend(value.encode("utf-8"))
    body.extend(b"\r\n")


def build_upload_body(document: UploadDocument) -> tuple[bytes, str]:
    file_path = document.source.file_path
    content_type = SUPPORTED_CONTENT_TYPES[file_path.suffix.lower()]
    boundary = f"----bulk-upload-{uuid.uuid4().hex}"
    body = bytearray()

    append_text_field(body, boundary, "documentTitle", document.source.title)
    append_text_field(body, boundary, "versionNote", document.source.version_note)
    append_text_field(body, boundary, "visibility", document.api_visibility)
    append_text_field(
        body, boundary, "minimumRoleLevel", str(document.source.minimum_role_level)
    )
    if document.category_id:
        append_text_field(body, boundary, "categoryId", document.category_id)
    for tag_id in document.tag_ids:
        append_text_field(body, boundary, "tagIds", tag_id)
    for department_id in document.accessible_departments:
        append_text_field(body, boundary, "accessibleDepartments", str(department_id))

    safe_filename = file_path.name.replace('"', "")
    body.extend(f"--{boundary}\r\n".encode("ascii"))
    body.extend(
        (
            f'Content-Disposition: form-data; name="file"; filename="{safe_filename}"\r\n'
            f"Content-Type: {content_type}\r\n\r\n"
        ).encode("utf-8")
    )
    body.extend(file_path.read_bytes())
    body.extend(b"\r\n")
    body.extend(f"--{boundary}--\r\n".encode("ascii"))
    return bytes(body), boundary


def upload_file(
    base_url: str, token: str, document: UploadDocument, timeout_seconds: float
) -> Any:
    body, boundary = build_upload_body(document)
    request = Request(
        f"{base_url}/api/v1/knowledge/documents/upload",
        data=body,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Accept": "application/json",
        },
        method="POST",
    )
    return send_request(request, timeout_seconds)


def format_bytes(size: int) -> str:
    if size < 1024:
        return f"{size} B"
    if size < 1024 * 1024:
        return f"{size / 1024:.1f} KiB"
    if size < 1024 * 1024 * 1024:
        return f"{size / (1024 * 1024):.1f} MiB"
    return f"{size / (1024 * 1024 * 1024):.2f} GiB"


def format_result(result: Any) -> str:
    if not isinstance(result, dict):
        return str(result)
    return (
        f"id={result.get('id')} "
        f"title={result.get('documentTitle')!r} "
        f"status={result.get('embeddingStatus')}"
    )


def print_preflight_error(error: PreflightError) -> None:
    print("PRECHECK FAILED: nothing was uploaded.", file=sys.stderr)
    for item in error.errors:
        print(f"  - {item}", file=sys.stderr)


def main() -> int:
    args = parse_args()
    folder = args.folder.expanduser().resolve()
    if not folder.is_dir():
        print(f"ERROR: folder does not exist or is not a directory: {folder}", file=sys.stderr)
        return 2

    try:
        manifest_path = find_manifest(folder, args.manifest)
        rows = load_manifest_rows(manifest_path)
        local_documents = validate_local_batch(folder, manifest_path, rows)
    except PreflightError as error:
        print_preflight_error(error)
        return 2

    try:
        token = args.token or login(args.base_url, args.email, args.password, args.timeout_seconds)
    except RuntimeError as error:
        print(f"ERROR: authentication failed: {error}", file=sys.stderr)
        return 1

    try:
        documents, existing_size, incoming_size, subscription = run_remote_preflight(
            args.base_url, token, args.timeout_seconds, local_documents
        )
    except (PreflightError, RuntimeError) as error:
        if isinstance(error, PreflightError):
            print_preflight_error(error)
        else:
            print(f"PRECHECK FAILED: nothing was uploaded.\n  - {error}", file=sys.stderr)
        return 2

    max_storage_gb = subscription.get("maxStorageGb")
    max_documents = subscription.get("maxDocuments")
    print(f"Precheck passed: {len(documents)} manifest document(s)")
    print(
        f"Storage: {format_bytes(existing_size)} existing + "
        f"{format_bytes(incoming_size)} incoming"
        + (f" <= {max_storage_gb} GiB plan limit" if max_storage_gb is not None else "")
    )
    print(
        f"Documents: {len(documents)} incoming"
        + (f"; plan limit={max_documents}" if max_documents is not None else "")
    )

    if args.dry_run:
        print("Dry run complete: nothing was uploaded.")
        return 0

    uploaded = 0
    for index, document in enumerate(documents, start=1):
        prefix = f"[{index}/{len(documents)}] {document.source.file_path.name}"
        try:
            result = upload_file(args.base_url, token, document, args.timeout_seconds)
            uploaded += 1
            print(f"{prefix}: OK {format_result(result)}")
        except (OSError, RuntimeError, ValueError) as error:
            print(f"{prefix}: FAILED {error}", file=sys.stderr)
            print(
                f"Stopped after the first upload failure. Summary: "
                f"total={len(documents)} uploaded={uploaded} failed=1",
                file=sys.stderr,
            )
            return 1

        if index < len(documents) and args.delay_seconds:
            time.sleep(args.delay_seconds)

    print(f"Summary: total={len(documents)} uploaded={uploaded} failed=0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
