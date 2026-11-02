"""Google Play feature graphic (1024 x 500) and 512 x 512 icon for Thirteen, drawn like the app's cards and icon.
Run from this folder: python3 images.py. Needs Pillow and the Noto Sans CJK and DejaVu fonts."""
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = sys.argv[1] if len(sys.argv) > 1 else "."
S = 2  # drawn at twice the size, then scaled down for smooth edges
CJK_BLACK = "/usr/share/fonts/opentype/noto/NotoSansCJK-Black.ttc"
CJK_BOLD = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"
CJK_REG = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
SUIT_FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
TC = 3  # index of Noto Sans CJK TC in the .ttc
GREEN, GREEN_DARK = (31, 111, 84), (16, 66, 50)
RED, BLACK = (198, 40, 40), (27, 27, 27)


def font(path, size, index=TC):
    return ImageFont.truetype(path, size * S, index=index)


def card(rank, suit, w, h):
    """One card as in the app: white, rounded, a grey border, the rank above the suit."""
    im = Image.new("RGBA", (w * S, h * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    r = int(w * 0.14 * S)
    d.rounded_rectangle([0, 0, w * S - 1, h * S - 1], r, fill="white", outline=(158, 158, 158), width=S)
    color = RED if suit in "♥♦" else BLACK
    rf = ImageFont.truetype(CJK_BOLD, int(h * 0.36) * S, index=TC)
    sf = ImageFont.truetype(SUIT_FONT, int(h * 0.36) * S)
    d.text((w * S / 2, h * S * 0.30), rank, font=rf, fill=color, anchor="mm")
    d.text((w * S / 2, h * S * 0.68), suit, font=sf, fill=color, anchor="mm")
    return im


def shadowed(base, im, x, y, angle=0):
    if angle:
        im = im.rotate(angle, resample=Image.BICUBIC, expand=True)
    sh = Image.new("RGBA", im.size, (0, 0, 0, 0))
    sh.putalpha(im.getchannel("A").point(lambda a: a * 0.35))
    sh = sh.filter(ImageFilter.GaussianBlur(5 * S))
    base.alpha_composite(sh, (x * S + 3 * S, y * S + 5 * S))
    base.alpha_composite(im, (x * S, y * S))


def background(w, h):
    im = Image.new("RGBA", (w * S, h * S))
    d = ImageDraw.Draw(im)
    for i in range(w * S):  # a soft diagonal-ish gradient, lighter at the left
        t = i / (w * S)
        c = tuple(int(GREEN[k] * (1 - t) + GREEN_DARK[k] * t) for k in range(3))
        d.line([(i, 0), (i, h * S)], fill=c)
    return im


# A valid hand: front a pair of kings, middle a straight, back a flush.
ROWS = [
    [("K", "♠"), ("K", "♥"), ("3", "♦")],
    [("8", "♠"), ("9", "♦"), ("10", "♣"), ("J", "♠"), ("Q", "♦")],
    [("2", "♥"), ("6", "♥"), ("9", "♥"), ("J", "♥"), ("A", "♥")],
]


def feature(lang):
    W, H = 1024, 500
    im = background(W, H)
    d = ImageDraw.Draw(im)
    cw, ch, gap = 74, 104, 8
    x0 = 552
    for r, row in enumerate(ROWS):
        y = 52 + r * (ch + 26)
        x = x0 + (5 - len(row)) * (cw + gap) / 2
        for rank, suit in row:
            shadowed(im, card(rank, suit, cw, ch), int(x), y)
            x += cw + gap
    if lang == "zh":
        d.text((64 * S, 150 * S), "十三支", font=font(CJK_BLACK, 104), fill="white", anchor="ls")
        d.text((68 * S, 230 * S), "AI 擺牌與分析", font=font(CJK_BOLD, 38), fill="white", anchor="ls")
        d.text((68 * S, 285 * S), "網路或面對面對戰", font=font(CJK_BOLD, 38), fill="white", anchor="ls")
        d.text((68 * S, 420 * S), "四人十三支 · 離線練習 · 開放原始碼", font=font(CJK_REG, 22), fill=(220, 236, 228), anchor="ls")
    else:
        d.text((60 * S, 150 * S), "Thirteen", font=font(CJK_BLACK, 92, 0), fill="white", anchor="ls")
        d.text((64 * S, 226 * S), "Chinese poker with an AI", font=font(CJK_BOLD, 34, 0), fill="white", anchor="ls")
        d.text((64 * S, 278 * S), "Analyze, play friends", font=font(CJK_BOLD, 34, 0), fill="white", anchor="ls")
        d.text((64 * S, 420 * S), "Four players · offline practice · open source", font=font(CJK_REG, 21, 0), fill=(220, 236, 228), anchor="ls")
    return im.resize((W, H), Image.LANCZOS).convert("RGB")



def icon():
    """The launcher icon (drawable/ic_launcher.xml), full bleed: Google Play rounds the corners itself."""
    N = 512
    k = N / 48 * S
    im = Image.new("RGBA", (N * S, N * S), GREEN + (255,))
    d = ImageDraw.Draw(im)
    d.polygon([(33 * k, 14 * k), (37 * k, 15 * k), (38.4 * k, 17.4 * k), (33.4 * k, 36 * k), (32 * k, 37.4 * k)], fill=(165, 197, 187, 255))
    d.rounded_rectangle([10 * k, 10 * k, 32 * k, 38 * k], 3 * k, fill="white")
    d.polygon([(21 * k, 17 * k), (26 * k, 24 * k), (21 * k, 31 * k), (16 * k, 24 * k)], fill=RED)
    return im.resize((N, N), Image.LANCZOS).convert("RGB")


feature("zh").save(f"{OUT}/feature-zh.png")
feature("en").save(f"{OUT}/feature-en.png")
icon().save(f"{OUT}/icon-512.png")
