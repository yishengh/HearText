"""Play Store promos in Nextory-like style:
full-bleed UI, short headline band, no fake phone bezel.
"""
from __future__ import annotations

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ASSETS = Path(r"C:\Users\yishe\.cursor\projects\c-Users-yishe-AndroidStudioProjects-HearText\assets")
OUT = Path(r"C:\Users\yishe\AndroidStudioProjects\HearText\play\promo")
OUT.mkdir(parents=True, exist_ok=True)

SHOTS = {
    "settings_controls": ASSETS
    / "c__Users_yishe_AppData_Roaming_Cursor_User_workspaceStorage_empty-window_images_Screenshot_20260725_154857-674076b6-aef2-422d-a08b-302d521fe898.png",
    "settings_preview": ASSETS
    / "c__Users_yishe_AppData_Roaming_Cursor_User_workspaceStorage_empty-window_images_Screenshot_20260725_154835-1280c2f5-6238-4617-af3e-48332e5049ca.png",
    "reader": ASSETS
    / "c__Users_yishe_AppData_Roaming_Cursor_User_workspaceStorage_empty-window_images_Screenshot_20260725_154736-724380f0-75dd-4b74-ad15-858b9dcdb6a7.png",
    "player": ASSETS
    / "c__Users_yishe_AppData_Roaming_Cursor_User_workspaceStorage_empty-window_images_Screenshot_20260725_154747-676e7d85-5a27-49bd-ba17-edcf765aad7c.png",
    "library": ASSETS
    / "c__Users_yishe_AppData_Roaming_Cursor_User_workspaceStorage_empty-window_images_Screenshot_20260725_154723-6b971085-de8f-40a3-876d-27df5aaaa50f.png",
    "store": ASSETS
    / "c__Users_yishe_AppData_Roaming_Cursor_User_workspaceStorage_empty-window_images_Screenshot_20260725_154653-e987f576-cabb-48cd-a669-94842eefaad0.png",
}

W, H = 1080, 1920
# Header band height (~18% like many store listings)
HEADER_H = 340
PURPLE = (108, 99, 255)
PURPLE_DEEP = (72, 64, 210)
WHITE = (255, 255, 255)
INK = (255, 255, 255)


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    paths = [
        r"C:\Windows\Fonts\segoeuib.ttf" if bold else r"C:\Windows\Fonts\segoeui.ttf",
        r"C:\Windows\Fonts\arialbd.ttf" if bold else r"C:\Windows\Fonts\arial.ttf",
    ]
    for p in paths:
        if Path(p).exists():
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def cover_crop(shot: Image.Image, tw: int, th: int) -> Image.Image:
    """Scale-to-cover then center-crop to tw x th."""
    shot = shot.convert("RGB")
    sw, sh = shot.size
    scale = max(tw / sw, th / sh)
    nw, nh = int(sw * scale), int(sh * scale)
    shot = shot.resize((nw, nh), Image.Resampling.LANCZOS)
    left = (nw - tw) // 2
    top = (nh - th) // 2
    return shot.crop((left, top, left + tw, top + th))


def make_promo(shot_path: Path, title: str, subtitle: str, outfile: str) -> None:
    canvas = Image.new("RGB", (W, H), PURPLE)
    draw = ImageDraw.Draw(canvas)

    # Header gradient
    for y in range(HEADER_H):
        t = y / max(HEADER_H - 1, 1)
        r = int(PURPLE[0] * (1 - t) + PURPLE_DEEP[0] * t)
        g = int(PURPLE[1] * (1 - t) + PURPLE_DEEP[1] * t)
        b = int(PURPLE[2] * (1 - t) + PURPLE_DEEP[2] * t)
        draw.line([(0, y), (W, y)], fill=(r, g, b))

    title_f = font(56, bold=True)
    sub_f = font(28, bold=False)

    # Centered title / subtitle in header
    tw = draw.textlength(title, font=title_f)
    draw.text(((W - tw) / 2, 100), title, font=title_f, fill=WHITE)
    sw = draw.textlength(subtitle, font=sub_f)
    draw.text(((W - sw) / 2, 180), subtitle, font=sub_f, fill=(230, 226, 255))

    # Full-bleed UI under header (no phone frame)
    body_h = H - HEADER_H
    ui = cover_crop(Image.open(shot_path), W, body_h)
    canvas.paste(ui, (0, HEADER_H))

    # Soft divider shadow under header
    shadow = Image.new("RGBA", (W, 28), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    for i in range(28):
        alpha = int(50 * (1 - i / 28))
        sd.line([(0, i), (W, i)], fill=(0, 0, 0, alpha))
    base = canvas.convert("RGBA")
    base.alpha_composite(shadow, (0, HEADER_H))
    canvas = base.convert("RGB")

    out = OUT / outfile
    canvas.save(out, "PNG", optimize=True)
    print("wrote", out)


def make_feature() -> None:
    fw, fh = 1024, 500
    img = Image.new("RGB", (fw, fh), PURPLE)
    d = ImageDraw.Draw(img)
    for x in range(fw):
        t = x / (fw - 1)
        r = int(PURPLE_DEEP[0] * (1 - t) + PURPLE[0] * t)
        g = int(PURPLE_DEEP[1] * (1 - t) + PURPLE[1] * t)
        b = int(PURPLE_DEEP[2] * (1 - t) + PURPLE[2] * t)
        d.line([(x, 0), (x, fh)], fill=(r, g, b))

    brand_f = font(22, bold=True)
    title_f = font(40, bold=True)
    sub_f = font(20)
    left = 56

    d.text((left, 145), "HearText", font=brand_f, fill=(230, 226, 255))
    d.text((left, 195), "Read & listen", font=title_f, fill=WHITE)
    d.text((left, 248), "anywhere", font=title_f, fill=WHITE)
    d.text((left, 335), "Import · Store · Offline TTS", font=sub_f, fill=(230, 226, 255))

    # Rounded screenshot only — fully inside canvas, no white frame, no edge clipping
    card_w, card_h = 280, 380
    shot = cover_crop(Image.open(SHOTS["player"]), card_w, card_h)
    card = Image.new("RGBA", (card_w, card_h), (0, 0, 0, 0))
    mask = Image.new("L", (card_w, card_h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, card_w - 1, card_h - 1], radius=28, fill=255)
    card.paste(shot, (0, 0))
    card.putalpha(mask)

    shadow = Image.new("RGBA", (card_w + 24, card_h + 24), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        [8, 10, card_w + 8, card_h + 10], radius=30, fill=(0, 0, 0, 70)
    )
    from PIL import ImageFilter
    shadow = shadow.filter(ImageFilter.GaussianBlur(10))

    base = img.convert("RGBA")
    card_x = fw - card_w - 64  # 680
    card_y = (fh - card_h) // 2  # 60
    base.alpha_composite(shadow, (card_x - 6, card_y - 2))
    base.alpha_composite(card, (card_x, card_y))
    img = base.convert("RGB")

    out = OUT / "feature_graphic_1024x500.png"
    img.save(out, "PNG", optimize=True)
    print("wrote", out, "card=", card_w, "x", card_h, "at", card_x, card_y)


def main() -> None:
    # Short punchy captions like Nextory / Libby listings
    items = [
        ("library", "Your library", "Import EPUB, TXT & PDF", "01_library.png"),
        ("store", "Discover books", "Browse the catalog & rankings", "02_store.png"),
        ("reader", "Read your way", "Clean pages. Instant progress.", "03_reader.png"),
        ("player", "Listen anywhere", "TTS with speed control", "04_listen.png"),
        ("settings_preview", "Make it yours", "Fonts, themes & Chinese script", "05_typography.png"),
        ("settings_controls", "Comfort first", "Volume keys & page animations", "06_controls.png"),
    ]
    for key, title, sub, name in items:
        make_promo(SHOTS[key], title, sub, name)
    make_feature()
    print("done", OUT)


if __name__ == "__main__":
    main()
