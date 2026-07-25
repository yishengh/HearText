# -*- coding: utf-8 -*-
from pathlib import Path
import re

base = Path(r"C:\Users\yishe\AndroidStudioProjects\HearText\app\src\main\res")
extra = {
    "reader_volume_keys": (
        "Volume keys turn pages",
        "音量键翻页",
        "Touches volume pour tourner",
        "Teclas de volumen pasan página",
    ),
    "reader_volume_keys_sub": (
        "Volume up: previous · Volume down: next",
        "音量上键上一页 · 音量下键下一页",
        "Volume + : précédent · Volume − : suivant",
        "Volumen + : anterior · Volumen − : siguiente",
    ),
}


def esc(s: str) -> str:
    return s.replace("'", "\\'").replace("&", "&amp;")


for folder, idx in [("values", 0), ("values-zh", 1), ("values-fr", 2), ("values-es", 3)]:
    path = base / folder / "strings.xml"
    text = path.read_text(encoding="utf-8")
    keys = set(re.findall(r'<string name="([^"]+)"', text))
    lines = []
    for k, v in extra.items():
        if k not in keys:
            lines.append(f'    <string name="{k}">{esc(v[idx])}</string>')
    if lines:
        text = text.replace("</resources>", "\n".join(lines) + "\n</resources>")
        path.write_text(text, encoding="utf-8")
        print(folder, len(lines))
    else:
        print(folder, "skip")
