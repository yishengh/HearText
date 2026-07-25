# -*- coding: utf-8 -*-
from pathlib import Path
import re

base = Path(r"C:\Users\yishe\AndroidStudioProjects\HearText\app\src\main\res")

extra = {
    "action_save": ("Save", "保存", "Enregistrer", "Guardar"),
    "action_cancel": ("Cancel", "取消", "Annuler", "Cancelar"),
    "action_delete": ("Delete", "删除", "Supprimer", "Eliminar"),
    "action_close": ("Close", "关闭", "Fermer", "Cerrar"),
    "action_details": ("Details", "详情", "Détails", "Detalles"),
    "action_retry": ("Retry", "重试", "Réessayer", "Reintentar"),
    "common_error": ("Error", "出错了", "Erreur", "Error"),
    "common_not_found": ("Not found", "未找到", "Introuvable", "No encontrado"),
    "common_opening": ("Opening…", "打开中…", "Ouverture…", "Abriendo…"),
    "common_on": ("On", "开", "Oui", "Sí"),
    "common_off": ("Off", "关", "Non", "No"),
    "sign_in": ("Sign in", "登录", "Se connecter", "Iniciar sesión"),
    "sign_out": ("Sign out", "退出登录", "Se déconnecter", "Cerrar sesión"),
    "continue_reading": ("Continue reading", "继续阅读", "Continuer la lecture", "Seguir leyendo"),
    "category_adventure": ("Adventure", "冒险", "Aventure", "Aventura"),
    "category_classics": ("Classics", "经典", "Classiques", "Clásicos"),
    "category_fantasy": ("Fantasy", "奇幻", "Fantasy", "Fantasía"),
    "category_fiction": ("Fiction", "小说", "Fiction", "Ficción"),
    "category_horror": ("Horror", "恐怖", "Horreur", "Terror"),
    "category_humor": ("Humor", "幽默", "Humour", "Humor"),
    "category_mystery": ("Mystery", "悬疑", "Mystère", "Misterio"),
    "category_romance": ("Romance", "浪漫", "Romance", "Romance"),
    "category_scifi": ("Sci-Fi", "科幻", "Science-fiction", "Ciencia ficción"),
    "category_poetry": ("Poetry", "诗歌", "Poésie", "Poesía"),
    "category_drama": ("Drama", "戏剧", "Théâtre", "Drama"),
    "category_history": ("History", "历史", "Histoire", "Historia"),
    "category_philosophy": ("Philosophy", "哲学", "Philosophie", "Filosofía"),
    "category_biography": ("Biography", "传记", "Biographie", "Biografía"),
    "category_children": ("Children", "儿童", "Jeunesse", "Infantil"),
    "category_short_stories": ("Short stories", "短篇", "Nouvelles", "Cuentos"),
    "library_add_book": ("Add book", "添加书籍", "Ajouter un livre", "Añadir libro"),
    "library_empty_title": ("Your shelf is empty", "书架还是空的", "Votre étagère est vide", "Tu estantería está vacía"),
    "library_empty_body": (
        "Import EPUB or TXT files to build your library.",
        "导入 EPUB 或 TXT 文件来建立书架。",
        "Importez des fichiers EPUB ou TXT pour constituer votre bibliothèque.",
        "Importa archivos EPUB o TXT para crear tu biblioteca.",
    ),
    "library_import_book": ("Import a book", "导入书籍", "Importer un livre", "Importar un libro"),
    "library_filter_empty": (
        "No books in “%1$s”",
        "“%1$s”中没有书籍",
        "Aucun livre dans « %1$s »",
        "No hay libros en “%1$s”",
    ),
    "library_book_actions": (
        "Choose an action for this book.",
        "请选择对此书的操作。",
        "Choisissez une action pour ce livre.",
        "Elige una acción para este libro.",
    ),
    "overview_change_cover": ("Change cover", "更换封面", "Changer la couverture", "Cambiar portada"),
    "overview_by_author": ("By %1$s", "作者：%1$s", "Par %1$s", "Por %1$s"),
    "overview_progress": ("Progress", "进度", "Progression", "Progreso"),
    "overview_chapters": ("Chapters", "章节", "Chapitres", "Capítulos"),
    "overview_format": ("Format", "格式", "Format", "Formato"),
    "overview_about": ("About", "简介", "À propos", "Acerca de"),
    "overview_about_pdf": (
        "PDF reading via Readium. Listening is disabled for PDF — use EPUB or TXT for HearText voice.",
        "PDF 通过 Readium 阅读。PDF 不支持听书 — 请使用 EPUB 或 TXT。",
        "Lecture PDF via Readium. L’écoute est désactivée pour les PDF — utilisez EPUB ou TXT.",
        "Lectura PDF con Readium. La escucha no está disponible para PDF: usa EPUB o TXT.",
    ),
    "overview_about_text": (
        "Continue reading or tap Listen in the reader for text-to-speech with your chosen voice.",
        "继续阅读，或在阅读器中点「听书」使用所选音色朗读。",
        "Continuez la lecture ou appuyez sur Écouter dans le lecteur pour la synthèse vocale.",
        "Sigue leyendo o toca Escuchar en el lector para la síntesis de voz.",
    ),
    "catalog_detail_title": ("Book details", "书籍详情", "Détails du livre", "Detalles del libro"),
    "catalog_preview_meta": (
        "Preview %1$d chapters · %2$s KB",
        "试读 %1$d 章 · %2$s KB",
        "Aperçu %1$d chapitres · %2$s Ko",
        "Vista previa %1$d capítulos · %2$s KB",
    ),
    "catalog_no_description": ("No description", "暂无简介", "Aucune description", "Sin descripción"),
    "catalog_preview": ("Preview", "试读", "Aperçu", "Vista previa"),
    "catalog_download": ("Download", "下载上架", "Télécharger", "Descargar"),
    "catalog_preview_suffix": (" · preview", " ·试读", " · aperçu", " · vista previa"),
    "catalog_download_full": ("Download full text", "下载全文", "Télécharger le texte", "Descargar texto completo"),
    "catalog_no_preview": ("No preview chapters", "暂无试读章节", "Aucun chapitre d’aperçu", "No hay capítulos de vista previa"),
    "catalog_prev_chapter": ("Previous chapter", "上一章", "Chapitre précédent", "Capítulo anterior"),
    "catalog_next_chapter": ("Next chapter", "下一章", "Chapitre suivant", "Capítulo siguiente"),
    "profile_edit_name_title": ("Edit display name", "修改昵称", "Modifier le nom", "Editar nombre"),
    "profile_name_label": ("Display name", "昵称", "Nom", "Nombre"),
    "profile_delete_title": ("Delete account?", "确认删除账号？", "Supprimer le compte ?", "¿Eliminar cuenta?"),
    "profile_delete_body": (
        "This will delete local HearText data and your Clerk account. This cannot be undone.",
        "将删除本地 HearText 数据与 Clerk 账号，此操作不可恢复。",
        "Cela supprimera les données locales HearText et votre compte Clerk. Irréversible.",
        "Se eliminarán los datos locales de HearText y tu cuenta de Clerk. No se puede deshacer.",
    ),
    "profile_default_name": ("Reader", "读者", "Lecteur", "Lector"),
    "profile_stat_shelf": ("Shelf", "书架", "Étagère", "Estantería"),
    "profile_stat_time": ("Time", "时长", "Temps", "Tiempo"),
    "profile_stat_reading": ("Reading", "在读", "En cours", "Leyendo"),
    "profile_stat_finished": ("Finished", "读完", "Terminés", "Terminados"),
    "profile_time_zero_min": ("0 min", "0 分钟", "0 min", "0 min"),
    "profile_time_hours_mins": ("%1$d h %2$d min", "%1$d 小时 %2$d 分", "%1$d h %2$d min", "%1$d h %2$d min"),
    "profile_time_mins": ("%1$d min", "%1$d 分钟", "%1$d min", "%1$d min"),
    "listen_voice_title": ("Listening voices", "听书音色", "Voix d’écoute", "Voces de escucha"),
    "listen_voice_blurb": (
        "System TTS is used by default. You can also download offline voice packs.",
        "默认使用系统 TTS；也可下载离线语音包后选用（无需联网朗读）。",
        "La synthèse système est utilisée par défaut. Vous pouvez aussi télécharger des packs hors ligne.",
        "Por defecto se usa el TTS del sistema. También puedes descargar paquetes sin conexión.",
    ),
    "listen_system_in_use": ("System TTS · in use", "系统 TTS · 使用中", "Synthèse système · en cours", "TTS del sistema · en uso"),
    "listen_use_system": ("Use system TTS", "选用系统 TTS", "Utiliser la synthèse système", "Usar TTS del sistema"),
    "listen_offline_packs": ("Offline voice packs", "离线语音包", "Packs vocaux hors ligne", "Paquetes de voz sin conexión"),
    "listen_offline_blurb": (
        "Download, then tap Select to listen offline.",
        "下载后点「选用」即可离线听书。",
        "Téléchargez, puis appuyez sur Sélectionner pour écouter hors ligne.",
        "Descarga y toca Seleccionar para escuchar sin conexión.",
    ),
    "listen_no_packs": ("No featured voice packs", "暂无精选语音包", "Aucun pack en vedette", "No hay paquetes destacados"),
    "listen_sign_in_packs": (
        "Sign in to browse offline voice packs",
        "登录后可浏览离线语音包",
        "Connectez-vous pour parcourir les packs hors ligne",
        "Inicia sesión para ver paquetes sin conexión",
    ),
    "listen_downloading_pct": ("Downloading %1$d%% · %2$s", "下载中 %1$d%% · %2$s", "Téléchargement %1$d%% · %2$s", "Descargando %1$d%% · %2$s"),
    "listen_installing": ("Installing…", "正在解压安装…", "Installation…", "Instalando…"),
    "listen_downloading": ("Downloading…", "下载中…", "Téléchargement…", "Descargando…"),
    "listen_redownload": ("Re-download", "重新下载", "Retélécharger", "Volver a descargar"),
    "listen_download": ("Download", "下载", "Télécharger", "Descargar"),
    "listen_in_use": ("In use", "使用中", "En cours", "En uso"),
    "listen_select": ("Select", "选用", "Sélectionner", "Seleccionar"),
    "storage_total": ("Total used", "占用合计", "Espace utilisé", "Espacio usado"),
    "storage_calculating": ("Calculating…", "计算中…", "Calcul…", "Calculando…"),
    "storage_books": ("Book files", "书籍文件", "Fichiers de livres", "Archivos de libros"),
    "storage_covers": ("Covers", "封面", "Couvertures", "Portadas"),
    "storage_catalog": ("Store cache", "书城缓存", "Cache boutique", "Caché de tienda"),
    "storage_voices": ("Offline voices", "离线语音", "Voix hors ligne", "Voces sin conexión"),
    "storage_fonts": ("Custom fonts", "自定义字体", "Polices perso", "Fuentes personalizadas"),
    "storage_clear_hint": (
        "Clearing won’t remove books or covers on your shelf. Store temp cache can be cleared.",
        "清理不会删除书架上的书籍与封面。可清理书城临时缓存。",
        "Le nettoyage ne supprime pas les livres ni les couvertures. Le cache temporaire de la boutique peut être vidé.",
        "Limpiar no elimina libros ni portadas. Se puede borrar la caché temporal de la tienda.",
    ),
    "storage_cleared": ("Cleared cache %1$s", "已清理缓存 %1$s", "Cache vidé %1$s", "Caché liberada %1$s"),
    "storage_clear_cache": ("Clear cache", "清理缓存", "Vider le cache", "Limpiar caché"),
    "home_title": ("Home", "首页", "Accueil", "Inicio"),
    "home_cloud_on": ("Cloud sync on", "云同步已开启", "Sync cloud activée", "Sincronización en la nube activada"),
    "home_cloud_off": (
        "Offline · sign in for cloud sync",
        "离线 · 登录后可云同步",
        "Hors ligne · connectez-vous pour la sync",
        "Sin conexión · inicia sesión para sincronizar",
    ),
    "home_read_before": ("Read before", "最近读过", "Lu récemment", "Leídos antes"),
    "home_listen_title": ("Listen while reading", "边读边听", "Écouter en lisant", "Escuchar mientras lees"),
    "home_listen_body": (
        "Use System TTS or offline voice packs from Listen settings.",
        "在听书设置中选用系统 TTS 或离线语音包。",
        "Utilisez la synthèse système ou des packs hors ligne dans Écoute.",
        "Usa el TTS del sistema o paquetes sin conexión en Escucha.",
    ),
    "home_stat_books": ("Books", "书籍", "Livres", "Libros"),
    "home_stat_reading": ("Reading", "在读", "En cours", "Leyendo"),
    "home_stat_sync": ("Sync", "同步", "Sync", "Sync"),
    "home_empty_sync": ("Sign in for cloud sync", "登录以云同步", "Connectez-vous pour la sync cloud", "Inicia sesión para sincronizar"),
    "home_continue_book": ("Continue · %1$s", "继续 · %1$s", "Continuer · %1$s", "Continuar · %1$s"),
    "reader_bookmarks_empty": (
        "No bookmarks yet. Tap the top-right to add this page.",
        "暂无书签，点右上角加入当前页。",
        "Aucun signet. Appuyez en haut à droite pour ajouter cette page.",
        "Aún no hay marcadores. Toca arriba a la derecha para añadir esta página.",
    ),
    "reader_chapter_n": ("Chapter %1$d", "第 %1$d 章", "Chapitre %1$d", "Capítulo %1$d"),
    "reader_page_n": ("Page %1$d", "第 %1$d 页", "Page %1$d", "Página %1$d"),
    "reader_offline_short": ("Offline", "离线", "Hors ligne", "Sin conexión"),
    "reader_system_short": ("System", "系统", "Système", "Sistema"),
    "reader_tts_speaking": ("Speaking", "朗读中", "Lecture", "Reproduciendo"),
    "reader_tts_paused": ("Paused", "已暂停", "En pause", "En pausa"),
    "reader_tts_error": ("Error — tap to retry", "出错，点重试", "Erreur — appuyez pour réessayer", "Error — toca para reintentar"),
    "reader_tts_idle": ("Not playing", "未播放", "Arrêté", "Sin reproducir"),
    "reader_listen_page": ("This page", "本页听", "Cette page", "Esta página"),
    "reader_pause": ("Pause", "暂停", "Pause", "Pausa"),
    "reader_play": ("Play", "播放", "Lecture", "Reproducir"),
    "reader_stop": ("Stop", "停止", "Arrêter", "Detener"),
    "reader_search": ("Search", "搜索", "Rechercher", "Buscar"),
    "reader_search_hint": ("Search in book", "在书中搜索", "Rechercher dans le livre", "Buscar en el libro"),
    "reader_search_empty": ("No results", "无结果", "Aucun résultat", "Sin resultados"),
    "reader_search_results": ("%1$d results", "%1$d 条结果", "%1$d résultats", "%1$d resultados"),
    "reader_settings_title": ("Reading settings", "阅读设置", "Réglages de lecture", "Ajustes de lectura"),
    "reader_page_turn": ("Page turn", "翻页", "Tourner la page", "Pasar página"),
    "reader_turn_slide": ("Slide", "滑动", "Glisser", "Deslizar"),
    "reader_turn_fade": ("Fade", "淡入", "Fondu", "Fundido"),
    "reader_turn_curl": ("Curl", "仿真", "Curl", "Rizo"),
    "reader_curl_hint": (
        "Curl simulates paper turning (EPUB/TXT). Use Slide or Fade for PDF.",
        "仿真翻页适用于 EPUB/TXT。PDF 请使用滑动或淡入。",
        "Le curl simule le papier (EPUB/TXT). Utilisez Glisser ou Fondu pour les PDF.",
        "El rizo simula papel (EPUB/TXT). Usa Deslizar o Fundido para PDF.",
    ),
    "reader_font": ("Font", "字体", "Police", "Fuente"),
    "reader_font_publisher": ("Publisher", "原版", "Éditeur", "Editorial"),
    "reader_font_sans": ("Sans", "无衬线", "Sans", "Sans"),
    "reader_font_serif": ("Serif", "衬线", "Serif", "Serif"),
    "reader_font_mono": ("Mono", "等宽", "Mono", "Mono"),
    "reader_font_custom": ("Custom", "自定义", "Perso", "Personalizada"),
    "reader_font_import": ("Import", "导入", "Importer", "Importar"),
    "reader_font_replace": ("Replace custom font…", "更换自定义字体…", "Remplacer la police perso…", "Reemplazar fuente personalizada…"),
    "reader_size": ("Size", "字号", "Taille", "Tamaño"),
    "reader_letter_spacing": ("Letter spacing", "字距", "Interlettre", "Espaciado"),
    "reader_line_height": ("Line height", "行高", "Interligne", "Interlineado"),
    "reader_paragraph_spacing": ("Paragraph spacing", "段距", "Espacement des paragraphes", "Espaciado de párrafos"),
    "reader_theme": ("Theme", "主题", "Thème", "Tema"),
    "reader_theme_light": ("Light", "浅色", "Clair", "Claro"),
    "reader_theme_sepia": ("Sepia", "羊皮纸", "Sépia", "Sepia"),
    "reader_theme_dark": ("Dark", "深色", "Sombre", "Oscuro"),
    "reader_brightness": ("Brightness", "亮度", "Luminosité", "Brillo"),
    "reader_voice_hint": (
        "Voice settings live on the player page. Use Listen in the bottom bar; bookmark from the top right.",
        "音色设置在播放页。底部栏可听书；右上角可加书签。",
        "Les voix sont sur la page lecteur. Écoutez via la barre du bas ; signet en haut à droite.",
        "Las voces están en la página del reproductor. Escucha desde la barra inferior; marcador arriba a la derecha.",
    ),
    "reader_pdf_no_listen": ("Listen is not available for PDF.", "PDF 不支持听书。", "L’écoute n’est pas disponible pour les PDF.", "Escuchar no está disponible para PDF."),
    "player_now_playing": ("Now playing", "正在播放", "En cours", "Reproduciendo"),
    "player_open_reader": ("Open reader", "打开阅读器", "Ouvrir le lecteur", "Abrir lector"),
    "player_speed": ("Speed", "倍速", "Vitesse", "Velocidad"),
    "player_no_offline": ("No offline voice packs installed", "尚未安装离线语音包", "Aucun pack hors ligne installé", "No hay paquetes sin conexión"),
    "auth_connecting": ("Connecting…", "连接中…", "Connexion…", "Conectando…"),
    "auth_clerk_missing": (
        "Clerk is not configured. You can continue offline.",
        "Clerk 未配置。可继续离线使用。",
        "Clerk n’est pas configuré. Vous pouvez continuer hors ligne.",
        "Clerk no está configurado. Puedes continuar sin conexión.",
    ),
    "auth_continue_offline": ("Continue offline", "离线继续", "Continuer hors ligne", "Continuar sin conexión"),
    "auth_create_account": ("Create account", "创建账号", "Créer un compte", "Crear cuenta"),
    "auth_email": ("Email", "邮箱", "E-mail", "Correo"),
    "auth_password": ("Password", "密码", "Mot de passe", "Contraseña"),
    "auth_code": ("Verification code", "验证码", "Code de vérification", "Código de verificación"),
    "auth_verify": ("Verify", "验证", "Vérifier", "Verificar"),
    "auth_resend": ("Resend", "重新发送", "Renvoyer", "Reenviar"),
    "auth_sign_up": ("Sign up", "注册", "S’inscrire", "Registrarse"),
    "auth_have_account": ("Already have an account? Sign in", "已有账号？去登录", "Déjà un compte ? Se connecter", "¿Ya tienes cuenta? Inicia sesión"),
    "auth_need_account": ("Need an account? Sign up", "没有账号？去注册", "Pas de compte ? S’inscrire", "¿No tienes cuenta? Regístrate"),
    "auth_blurb": (
        "Sign in to sync library progress and browse the store.",
        "登录后可同步书架进度并浏览书城。",
        "Connectez-vous pour synchroniser la bibliothèque et parcourir la boutique.",
        "Inicia sesión para sincronizar la biblioteca y explorar la tienda.",
    ),
    "error_import_failed": ("Import failed", "导入失败", "Échec de l’import", "Error al importar"),
    "error_delete_failed": ("Delete failed", "删除失败", "Échec de la suppression", "Error al eliminar"),
    "error_book_not_found": ("Book not found", "未找到书籍", "Livre introuvable", "Libro no encontrado"),
    "error_load_chapters": ("Unable to load chapters", "无法加载章节", "Impossible de charger les chapitres", "No se pudieron cargar los capítulos"),
    "error_open_readium": ("Unable to open with Readium", "无法用 Readium 打开", "Impossible d’ouvrir avec Readium", "No se pudo abrir con Readium"),
    "toast_bookmark_removed": ("Bookmark removed", "已取消书签", "Signet retiré", "Marcador eliminado"),
    "toast_bookmark_added": ("Bookmark added", "已添加书签", "Signet ajouté", "Marcador añadido"),
    "toast_speed": ("Speed %1$s", "倍速 %1$s", "Vitesse %1$s", "Velocidad %1$s"),
    "toast_switched_system": ("Switched to system voice", "已切换系统音色", "Voix système sélectionnée", "Voz del sistema seleccionada"),
    "toast_need_offline_voice": (
        "Download this offline voice in Me first",
        "请先到「我的」下载该离线音色",
        "Téléchargez d’abord cette voix hors ligne dans Moi",
        "Descarga primero esta voz sin conexión en Yo",
    ),
    "toast_switched_offline": ("Switched to offline voice", "已切换离线音色", "Voix hors ligne sélectionnée", "Voz sin conexión seleccionada"),
}


def escape(s: str) -> str:
    return s.replace("\\", "\\\\").replace("'", "\\'").replace("&", "&amp;")


def existing_keys(path: Path):
    text = path.read_text(encoding="utf-8")
    return set(re.findall(r'<string name="([^"]+)"', text))


def append_locale(folder: str, idx: int):
    path = base / folder / "strings.xml"
    keys = existing_keys(path)
    lines = []
    for name, vals in extra.items():
        if name in keys:
            continue
        lines.append(f'    <string name="{name}">{escape(vals[idx])}</string>')
    if not lines:
        print(folder, "nothing to add")
        return
    text = path.read_text(encoding="utf-8")
    insert = "\n".join(lines) + "\n"
    text = text.replace("</resources>", insert + "</resources>")
    path.write_text(text, encoding="utf-8")
    print(folder, "added", len(lines))


append_locale("values", 0)
append_locale("values-zh", 1)
append_locale("values-fr", 2)
append_locale("values-es", 3)
print("done")
