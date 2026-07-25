# -*- coding: utf-8 -*-
from pathlib import Path
import re

base = Path(r"C:\Users\yishe\AndroidStudioProjects\HearText\app\src\main\res")
extra = {
    "library_subtitle": (
        "%1$d of %2$d books · tap + to import",
        "%1$d / %2$d 本 · 点 + 导入",
        "%1$d sur %2$d livres · appuyez sur + pour importer",
        "%1$d de %2$d libros · toca + para importar",
    ),
    "reader_script_title": (
        "Chinese script",
        "繁简转换",
        "Écriture chinoise",
        "Escritura china",
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
