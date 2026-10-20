"""Build the CurtinHonestly launch posters.

Generates one QR code per variant (unique ?ref= slug each), then renders every
HTML source with headless Chrome to:

  export/<name>-A3.pdf      vector, print this one (297 x 420 mm, no bleed)
  export/<name>-A4.pdf      vector, flyer size (210 x 297 mm)
  export/<name>-A3.png      3508 x 4961 px, 300 dpi raster
  export/<name>-A4.png      2480 x 3508 px, 300 dpi raster
  export/<name>-preview.png small preview for checking on screen
  export/<name>-9x16.png    1080 x 1920 px for Instagram stories and Discord

Run from this folder:  python build.py
Options:               python build.py --no-prize   (hides the prize strip)
                       python build.py --base https://www.curtinhonestly.com/sem2

Requires: Python 3 with the `qrcode` and `Pillow` packages, and Google Chrome
or Microsoft Edge installed. No other dependencies.
"""
from __future__ import annotations

import os
import shutil
import subprocess
import sys
from pathlib import Path

import qrcode
import qrcode.image.svg

HERE = Path(__file__).resolve().parent
QR_DIR = HERE / "qr"
OUT_DIR = HERE / "export"

# Where every QR code lands. The ref slug is appended as ?ref=<slug>. Referral
# capture is site-wide (frontend app.ts), so any path works. If the public
# campaign page ships before printing, pass --base .../sem2 and rebuild.
BASE_URL = "https://www.curtinhonestly.com/"

# One slug per printed design so scans attribute per design. Create each slug
# in the admin UI (Campaigns > Create referral link) BEFORE printing, otherwise
# the visit beacon is dropped on the floor.
POSTERS = {
    "poster-rant": {"file": "poster-rant.html", "letter": "A", "title": "Rant or rave (black)"},
    "poster-first": {"file": "poster-first.html", "letter": "B", "title": "Be the first (paper)"},
    "poster-results": {"file": "poster-results.html", "letter": "C", "title": "Results are out (gold)"},
}
SOCIALS = {
    "social-rant": {"file": "social-rant.html", "letter": "A", "title": "Rant or rave, 9:16"},
    "social-first": {"file": "social-first.html", "letter": "B", "title": "Be the first, 9:16"},
    "social-results": {"file": "social-results.html", "letter": "C", "title": "Results are out, 9:16"},
}

CHROME_CANDIDATES = [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/usr/bin/google-chrome",
    "/usr/bin/chromium",
]


def find_chrome() -> str:
    env = os.environ.get("CHROME")
    if env and Path(env).exists():
        return env
    for c in CHROME_CANDIDATES:
        if Path(c).exists():
            return c
    for name in ("chrome", "google-chrome", "chromium", "msedge"):
        p = shutil.which(name)
        if p:
            return p
    sys.exit("Chrome or Edge not found. Set CHROME=<path to chrome.exe> and retry.")


def ref_url(base: str, slug: str) -> str:
    sep = "&" if "?" in base else "?"
    return f"{base}{sep}ref={slug}"


def make_qr(slug: str, url: str) -> None:
    QR_DIR.mkdir(exist_ok=True)
    q = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_H, border=1, box_size=20)
    q.add_data(url)
    q.make(fit=True)
    q.make_image(image_factory=qrcode.image.svg.SvgPathImage).save(QR_DIR / f"{slug}.svg")
    q.make_image(fill_color="#000000", back_color="#FFFFFF").save(QR_DIR / f"{slug}.png")


def file_url(path: Path, query: str) -> str:
    return path.resolve().as_uri() + ("?" + query if query else "")


def run_chrome(chrome: str, args: list[str]) -> None:
    base = [
        chrome,
        "--headless=new",
        "--disable-gpu",
        "--hide-scrollbars",
        "--no-first-run",
        "--disable-extensions",
        "--allow-file-access-from-files",
        "--run-all-compositor-stages-before-draw",
        "--virtual-time-budget=4000",
    ]
    subprocess.run(base + args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def render_poster(chrome: str, name: str, src: Path, prize: bool) -> None:
    pq = "" if prize else "&prize=0"
    OUT_DIR.mkdir(exist_ok=True)
    # Vector PDFs: these are the print masters.
    run_chrome(chrome, ["--no-pdf-header-footer", f"--print-to-pdf={OUT_DIR / (name + '-A3.pdf')}", file_url(src, "size=a3" + pq)])
    run_chrome(chrome, ["--no-pdf-header-footer", f"--print-to-pdf={OUT_DIR / (name + '-A4.pdf')}", file_url(src, "size=a4" + pq)])
    # 300 dpi rasters. A3 = 1123x1587 css px, A4 = 794x1123 css px, both x3.125.
    run_chrome(chrome, ["--force-device-scale-factor=3.125", "--window-size=1123,1587", f"--screenshot={OUT_DIR / (name + '-A3.png')}", file_url(src, "size=a3" + pq)])
    run_chrome(chrome, ["--force-device-scale-factor=3.125", "--window-size=794,1123", f"--screenshot={OUT_DIR / (name + '-A4.png')}", file_url(src, "size=a4" + pq)])
    run_chrome(chrome, ["--force-device-scale-factor=1", "--window-size=794,1123", f"--screenshot={OUT_DIR / (name + '-preview.png')}", file_url(src, "size=a4" + pq)])


def render_social(chrome: str, name: str, src: Path) -> None:
    OUT_DIR.mkdir(exist_ok=True)
    run_chrome(chrome, ["--force-device-scale-factor=1", "--window-size=1080,1920", f"--screenshot={OUT_DIR / (name + '-9x16.png')}", file_url(src, "size=none")])


def write_links(base: str) -> None:
    rows = ["| Slug | Design | Printed on | URL |", "|---|---|---|---|"]
    for slug, meta in {**POSTERS, **SOCIALS}.items():
        rows.append(f"| `{slug}` | {meta['letter']} | {meta['title']} | `{ref_url(base, slug)}` |")
    text = (
        "# QR and referral links\n\n"
        "Generated by `build.py`. Every QR code below encodes the URL in its row.\n"
        "Create each slug as a referral link in the admin UI before printing, then\n"
        "scan each printed proof once and confirm the visit appears in the admin\n"
        "Campaigns page.\n\n" + "\n".join(rows) + "\n"
    )
    (QR_DIR / "links.md").write_text(text, encoding="utf-8")


def main() -> None:
    argv = sys.argv[1:]
    prize = "--no-prize" not in argv
    base = BASE_URL
    if "--base" in argv:
        base = argv[argv.index("--base") + 1]
    chrome = find_chrome()
    print(f"Chrome: {chrome}")
    print(f"Base URL: {base}  prize strip: {'on' if prize else 'off'}")

    for slug in {**POSTERS, **SOCIALS}:
        make_qr(slug, ref_url(base, slug))
        print(f"QR  {slug} -> {ref_url(base, slug)}")
    write_links(base)

    for name, meta in POSTERS.items():
        print(f"render {name}")
        render_poster(chrome, name, HERE / meta["file"], prize)
    for name, meta in SOCIALS.items():
        print(f"render {name}")
        render_social(chrome, name, HERE / meta["file"])
    print(f"done -> {OUT_DIR}")


if __name__ == "__main__":
    main()
