"""Regenerate HearText Play feature graphic (1024x500) from fresh screenshots."""
from __future__ import annotations

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ASSETS = Path(r"C:\Users\yishe\.cursor\projects\c-Users-yishe-AndroidStudioProjects-HearText\assets")
OUT = Path(r"C:\Users\yishe\AndroidStudioProjects\HearText\play\promo\feature_graphic_1024x500.png")

SHOTS = {
    "library": ASSETS
    / "c__Users_yishe_AppData_Roaming_Cursor_User_workspaceStorage_empty-window_images_Screenshot_20260725_160139-bee4ad5c-dc91-4b1d-a95b-7686ea048983.png",
    "store": ASSETS
    / "c__Users_yishe_AppData_Roaming_Cursor_User_workspaceStorage_empty-window_images_Screenshot_20260725_160129-5a8cc21a-aaee-4926-999e-5ca345343726.png",
    "reader": ASSETS
    / "c__Users_yishe_AppData_Roaming_Cursor_User_workspaceStorage_empty-window_images_Screenshot_20260725_160208-3a70c6a7-0aaa-484f-9c13-c8375aa66ca7.png",
    "player": ASSETS
    / "c__Users_yishe_AppData_Roaming_Cursor_User_workspaceStorage_empty-window_images_Screenshot_20260725_160318-2d41e0c7-3e17-4cc6-b58e-53557835b4f4.png",
    "typography": ASSETS
    / "c__Users_yishe_AppData_Roaming_Cursor_User_workspaceStorage_empty-window_images_Screenshot_20260725_160152-74fb0f00-d0b1-4541-9d71-d56ca35ebd55.png",
}

PURPLE = (108, 99, 255)
PURPLE_DEEP = (72, 64, 210)
WHITE = (255, 255, 255)


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    paths = [
        r"C:\Windows\Fonts\segoeuib.ttf" if bold else r"C:\Windows\Fonts\segoeui.ttf",
        r"C:\Windows\Fonts\arialbd.ttf" if bold else r"C:\Windows\Fonts\arial.ttf",
    ]
    for p in paths:
        if Path(p).exists():
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def round_shot(path: Path, w: int, h: int, radius: int = 22) -> Image.Image:
    """Scale-to-cover crop, then round corners. Fully opaque RGB inside alpha mask."""
    im = Image.open(path).convert("RGB")
    sw, sh = im.size
    scale = max(w / sw, h / sh)
    nw, nh = int(sw * scale), int(sh * scale)
    im = im.resize((nw, nh), Image.Resampling.LANCZOS)
    left = (nw - w) // 2
    top = max(0, (nh - h) // 5)  # bias upward so headers stay visible
    im = im.crop((left, top, left + w, top + h))

    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], radius=radius, fill=255)
    out.paste(im, (0, 0))
    out.putalpha(mask)
    return out


def main() -> None:
    fw, fh = 1024, 500
    canvas = Image.new("RGB", (fw, fh))
    d = ImageDraw.Draw(canvas)
    for x in range(fw):
        t = x / (fw - 1)
        r = int(PURPLE_DEEP[0] * (1 - t) + PURPLE[0] * t)
        g = int(PURPLE_DEEP[1] * (1 - t) + PURPLE[1] * t)
        b = int(PURPLE_DEEP[2] * (1 - t) + PURPLE[2] * t)
        d.line([(x, 0), (x, fh)], fill=(r, g, b))

    # ---- Left copy (hard-limited to x < 470) ----
    brand_f = font(20, bold=True)
    title_f = font(38, bold=True)
    sub_f = font(18)
    x0 = 48
    d.text((x0, 130), "HearText", font=brand_f, fill=(230, 226, 255))
    d.text((x0, 175), "Read & listen", font=title_f, fill=WHITE)
    d.text((x0, 225), "anywhere", font=title_f, fill=WHITE)
    d.text((x0, 300), "Import · Store · Offline TTS", font=sub_f, fill=(230, 226, 255))

    # ---- Right: two staggered screenshots, fully inside margins ----
    # Back card (reader) slightly left/up, front card (player) slightly right/down
    back = round_shot(SHOTS["reader"], 220, 360, radius=24)
    front = round_shot(SHOTS["player"], 220, 360, radius=24)

    def with_shadow(card: Image.Image) -> Image.Image:
        pad = 18
        layer = Image.new("RGBA", (card.width + pad * 2, card.height + pad * 2), (0, 0, 0, 0))
        sh = Image.new("RGBA", layer.size, (0, 0, 0, 0))
        ImageDraw.Draw(sh).rounded_rectangle(
            [pad - 2, pad + 2, pad + card.width + 2, pad + card.height + 6],
            radius=26,
            fill=(0, 0, 0, 80),
        )
        sh = sh.filter(ImageFilter.GaussianBlur(10))
        layer.alpha_composite(sh)
        layer.alpha_composite(card, (pad, pad))
        return layer

    back_s = with_shadow(back)
    front_s = with_shadow(front)

    base = canvas.convert("RGBA")
    # Keep both cards inside: right margin >= 40, top/bottom >= 40
    back_x, back_y = 520, 50
    front_x, front_y = 700, 70
    # Clamp so nothing goes past edges
    for layer, x, y in ((back_s, back_x, back_y), (front_s, front_x, front_y)):
        x = min(x, fw - layer.width - 8)
        y = min(y, fh - layer.height - 8)
        x = max(8, x)
        y = max(8, y)
        base.alpha_composite(layer, (x, y))

    out = base.convert("RGB")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    out.save(OUT, "PNG", optimize=True)
    print("wrote", OUT, out.size)


if __name__ == "__main__":
    main()
