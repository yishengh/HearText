# -*- coding: utf-8 -*-
from pathlib import Path
import re

base = Path(r"C:\Users\yishe\AndroidStudioProjects\HearText\app\src\main\res")
extra = {
    "toast_profile_updated": ("Profile updated", "资料已更新", "Profil mis à jour", "Perfil actualizado"),
    "toast_voice_installed": (
        "Installed %1$s — tap Select to listen",
        "已安装 %1$s，可点「选用」听书",
        "Installé %1$s — appuyez sur Sélectionner",
        "Instalado %1$s — toca Seleccionar",
    ),
    "toast_download_failed": ("Download failed", "下载失败", "Échec du téléchargement", "Error al descargar"),
    "toast_need_full_offline": (
        "Download the full offline voice first (model/tokens required)",
        "请先下载完整离线音色（需含模型或可生成 tokens）",
        "Téléchargez d’abord la voix hors ligne complète",
        "Descarga primero la voz sin conexión completa",
    ),
    "toast_selected_offline": (
        "Selected offline voice “%1$s”",
        "已选用离线音色「%1$s」",
        "Voix hors ligne « %1$s » sélectionnée",
        "Voz sin conexión “%1$s” seleccionada",
    ),
    "toast_selected_system": ("Selected system TTS", "已选用系统 TTS", "Synthèse système sélectionnée", "TTS del sistema seleccionado"),
    "toast_account_deleted": ("Account deleted", "账号已删除", "Compte supprimé", "Cuenta eliminada"),
    "toast_pick_offline_voice": (
        "Select an offline voice on the player page first",
        "请先在播放页选择离线音色",
        "Sélectionnez d’abord une voix hors ligne sur la page lecteur",
        "Selecciona primero una voz sin conexión en el reproductor",
    ),
    "error_store_load": ("Failed to load bookstore", "书城加载失败", "Échec du chargement de la boutique", "Error al cargar la tienda"),
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
