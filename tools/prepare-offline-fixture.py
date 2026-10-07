"""Prepare an official sherpa voice fixture for local-only Android integration tests."""
from pathlib import Path
import hashlib
import json
import tarfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1] / "build/local-validation/voice-fixtures"
URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-low.tar.bz2"
ROOT.mkdir(parents=True, exist_ok=True)
archive = ROOT / "amy.tar.bz2"
if not archive.exists():
    partial = archive.with_suffix(".part")
    urllib.request.urlretrieve(URL, partial)
    partial.replace(archive)
extracted = ROOT / "extracted"
extracted.mkdir(exist_ok=True)
with tarfile.open(archive, "r:bz2") as source:
    members = source.getmembers()
    if len(members) > 20_000 or sum(m.size for m in members) > 512 * 1024 * 1024:
        raise ValueError("Unexpected fixture archive size")
    for member in members:
        target = (extracted / member.name).resolve()
        if not target.is_relative_to(extracted.resolve()) or not (member.isdir() or member.isfile()):
            raise ValueError("Unsafe fixture archive entry")
    source.extractall(extracted, members=members, filter="data")
model_root = extracted / "vits-piper-en_US-amy-low"
server_root = ROOT / "server"
destination = server_root / "v1/offline-voices/local-amy/download"
destination.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED) as output:
    for path in sorted(model_root.rglob("*")):
        if path.is_file():
            output.write(path, path.relative_to(model_root).as_posix())
checksum = hashlib.file_digest(destination.open("rb"), "sha256").hexdigest()
(server_root / "checksum.txt").write_text(checksum, encoding="ascii")
(ROOT / "provenance.json").write_text(json.dumps({
    "source": URL, "source_sha256": hashlib.file_digest(archive.open("rb"), "sha256").hexdigest(),
    "test_zip_sha256": checksum, "test_zip_bytes": destination.stat().st_size,
}, indent=2), encoding="utf-8")
print(f"Local fixture ready: {destination.stat().st_size} bytes; SHA-256 {checksum}")
