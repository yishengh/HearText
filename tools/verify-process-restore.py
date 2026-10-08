"""Verify a visible text reader survives background process death in the isolated APK.
Install the validation APK and open an EPUB/TXT page first. No production package is supported.
Only hashes of visible text are written to the evidence file.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.yishenghuang.heartext.validation"
COMPONENT = PACKAGE + "/com.yishenghuang.heartext.MainActivity"
REMOTE_XML = "/sdcard/heartext-process-restore.xml"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    args = parser.parse_args()
    if not args.serial.startswith("emulator-"):
        parser.error("Use an explicitly selected emulator, not a personal device.")
    sdk = Path(os.environ.get("ANDROID_SDK_ROOT", str(Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk")))
    adb = sdk / "platform-tools/adb.exe"

    def call(*parts, check=True):
        return subprocess.run([str(adb), "-s", args.serial, *parts], capture_output=True,
                              check=check, timeout=30).stdout.decode("utf-8", errors="replace").strip()

    def pid():
        return call("shell", "pidof", PACKAGE, check=False)

    def visible_text():
        dumped = call("shell", "uiautomator", "dump", REMOTE_XML, check=False)
        if "dumped" not in dumped:
            return None
        xml = call("exec-out", "cat", REMOTE_XML)
        nodes = [node for node in ET.fromstring(xml).iter("node")
                 if node.get("package") == PACKAGE and node.get("class") == "android.widget.TextView"
                 and node.get("bounds", "").startswith("[0,0]") and len(node.get("text", "")) > 150]
        return nodes[0].get("text") if len(nodes) == 1 else None

    before = visible_text()
    old_pid = pid()
    if not before or not old_pid:
        raise RuntimeError("Open a text book page in the validation APK before running this check.")
    call("shell", "input", "keyevent", "KEYCODE_HOME")
    time.sleep(2)
    call("shell", "am", "kill", PACKAGE)
    deadline = time.monotonic() + 5
    while pid() and time.monotonic() < deadline:
        time.sleep(.1)
    if pid():
        raise RuntimeError("Process remains alive; stop validation playback before retrying.")
    call("shell", "am", "start", "-a", "android.intent.action.MAIN", "-c",
         "android.intent.category.LAUNCHER", "-n", COMPONENT)
    deadline = time.monotonic() + 30
    after = None
    while time.monotonic() < deadline:
        after = visible_text()
        if after == before:
            break
        time.sleep(.3)
    new_pid = pid()
    if after != before or not new_pid or new_pid == old_pid:
        raise RuntimeError("Restored visible text or process identity did not match expectations.")
    evidence = {"package": PACKAGE, "serial": args.serial, "old_pid": old_pid, "new_pid": new_pid,
                "old_process_confirmed_absent": True, "same_visible_text": True,
                "text_characters": len(before), "text_sha256": hashlib.sha256(before.encode()).hexdigest()}
    path = Path(__file__).resolve().parent.parent / "build/local-validation/process-restore-script.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(evidence, indent=2), encoding="utf-8")
    call("shell", "rm", REMOTE_XML)
    print(json.dumps(evidence, indent=2))


if __name__ == "__main__":
    main()
