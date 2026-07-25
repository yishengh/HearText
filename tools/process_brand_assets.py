from PIL import Image
from pathlib import Path

src_icon = Path(r"C:\Users\yishe\.cursor\projects\c-Users-yishe-AndroidStudioProjects-HearText\assets\heartext_icon_1024.png")
src_splash = Path(r"C:\Users\yishe\.cursor\projects\c-Users-yishe-AndroidStudioProjects-HearText\assets\heartext_splash_portrait.png")
res = Path(r"C:\Users\yishe\AndroidStudioProjects\HearText\app\src\main\res")

icon = Image.open(src_icon).convert("RGBA")
fg_size = 1024
fg = Image.new("RGBA", (fg_size, fg_size), (0, 0, 0, 0))
inner = 760
scaled = icon.resize((inner, inner), Image.Resampling.LANCZOS)
offset = (fg_size - inner) // 2
fg.paste(scaled, (offset, offset), scaled)
drawable = res / "drawable"
drawable.mkdir(exist_ok=True)
fg.save(drawable / "ic_launcher_foreground.png", optimize=True)

play = Path(r"C:\Users\yishe\AndroidStudioProjects\HearText\play")
play.mkdir(exist_ok=True)
icon.resize((512, 512), Image.Resampling.LANCZOS).save(play / "icon_512.png", optimize=True)
icon.save(play / "icon_1024.png", optimize=True)

splash = Image.open(src_splash).convert("RGB")
w, h = splash.size
target_w = 1080
target_h = int(h * (target_w / w))
splash = splash.resize((target_w, target_h), Image.Resampling.LANCZOS)
nodpi = res / "drawable-nodpi"
nodpi.mkdir(exist_ok=True)
splash.save(nodpi / "splash_background.jpg", quality=88, optimize=True)

sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}
for folder, size in sizes.items():
    out_dir = res / folder
    out_dir.mkdir(exist_ok=True)
    resized = icon.resize((size, size), Image.Resampling.LANCZOS)
    resized.save(out_dir / "ic_launcher.webp", "WEBP", quality=90)
    resized.save(out_dir / "ic_launcher_round.webp", "WEBP", quality=90)

print("icon", icon.size)
print("splash", splash.size)
print("done")
