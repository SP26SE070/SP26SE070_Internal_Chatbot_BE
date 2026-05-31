#!/usr/bin/env python3
"""Bulk upload tenant documents through the deployed backend API."""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import uuid
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


DEFAULT_BACKEND_BASE_URL = (
    "https://sp26se070internalchatbotbe-production.up.railway.app"
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


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Upload every file in a folder as COMPANY_WIDE documents with "
            "minimumRoleLevel=5 so regular employees can retrieve them."
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


def append_text_field(body: bytearray, boundary: str, name: str, value: str) -> None:
    body.extend(f"--{boundary}\r\n".encode("ascii"))
    body.extend(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode("ascii"))
    body.extend(value.encode("utf-8"))
    body.extend(b"\r\n")


def build_upload_body(file_path: Path) -> tuple[bytes, str]:
    content_type = SUPPORTED_CONTENT_TYPES.get(file_path.suffix.lower())
    if content_type is None:
        supported = ", ".join(sorted(SUPPORTED_CONTENT_TYPES))
        raise ValueError(f"unsupported extension {file_path.suffix!r}; expected one of: {supported}")

    boundary = f"----bulk-upload-{uuid.uuid4().hex}"
    body = bytearray()
    append_text_field(body, boundary, "visibility", "COMPANY_WIDE")
    append_text_field(body, boundary, "minimumRoleLevel", "5")

    safe_filename = file_path.name.replace('"', '\\"')
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


def upload_file(base_url: str, token: str, file_path: Path, timeout_seconds: float) -> Any:
    body, boundary = build_upload_body(file_path)
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


def format_result(result: Any) -> str:
    if not isinstance(result, dict):
        return str(result)
    return (
        f"id={result.get('id')} "
        f"title={result.get('documentTitle')!r} "
        f"status={result.get('embeddingStatus')}"
    )


def main() -> int:
    args = parse_args()
    folder = args.folder.expanduser().resolve()
    if not folder.is_dir():
        print(f"ERROR: folder does not exist or is not a directory: {folder}", file=sys.stderr)
        return 2

    files = sorted((path for path in folder.iterdir() if path.is_file()), key=lambda path: path.name.lower())
    if not files:
        print(f"No files found in {folder}")
        return 0

    try:
        token = args.token or login(args.base_url, args.email, args.password, args.timeout_seconds)
    except RuntimeError as error:
        print(f"ERROR: authentication failed: {error}", file=sys.stderr)
        return 1

    uploaded = 0
    failed = 0
    print(f"Uploading {len(files)} file(s) from {folder}")
    print("Access scope: visibility=COMPANY_WIDE, minimumRoleLevel=5")

    for index, file_path in enumerate(files, start=1):
        prefix = f"[{index}/{len(files)}] {file_path.name}"
        try:
            result = upload_file(args.base_url, token, file_path, args.timeout_seconds)
            uploaded += 1
            print(f"{prefix}: OK {format_result(result)}")
        except (OSError, RuntimeError, ValueError) as error:
            failed += 1
            print(f"{prefix}: FAILED {error}", file=sys.stderr)

        if index < len(files) and args.delay_seconds:
            time.sleep(args.delay_seconds)

    print(f"Summary: total={len(files)} uploaded={uploaded} failed={failed}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
