"""Render the design/marketing mockups to exports/ and generate missing QR codes.

Same method as README.md (headless Chrome, `?clean`), scripted so one command
regenerates everything, and run over file:// so no local server is needed.

    python export.py              # the October 2026 additions only
    python export.py --all        # also re-render the four August pieces
    python export.py --noprize    # hide the prize line on the results-day pieces

QR codes are generated only when the matching assets/qr-<slug>.png is missing,
with the same settings as README.md (error correction H, box 20, border 2,
#1F1F1F on #FAFAFA) and the same URL form, https://www.curtinhonestly.com/?ref=<slug>.
Pass --regen-qr to overwrite existing codes (you almost never want this; a
reprinted poster with a new code loses its history in the admin).

Requires Python 3 with `qrcode` and `Pillow`, and Chrome or Edge.
"""
from __future__ import annotations

import os
import shutil
import subprocess
import sys
from pathlib import Path

import qrcode
from qrcode.constants import ERROR_CORRECT_H

HERE = Path(__file__).resolve().parent
MOCKUPS = HERE / "mockups"
ASSETS = HERE / "assets"
EXPORTS = HERE / "exports"
BASE = "https://www.curtinhonestly.com/"

# name -> (kind, qr slug). kind: a3 | a5 | slide | story
ORIGINAL = {
    "poster-01-two-descriptions": ("a3", "poster-a3-enrol"),
    "poster-02-zero-reviews": ("a3", "poster-a3-zero"),
    "poster-03-not-affiliated": ("a3", "poster-a3-someone"),
    "slide-lecture-16x9": ("slide", "slide-lecture"),
}
ADDED = {
    "poster-04-results-day": ("a3", "poster-a3-results"),
    "flyer-a5-supply": ("a5", "flyer-a5-stall"),
    "story-zero-reviews": ("story", "story-zero"),
    "story-not-affiliated": ("story", "story-someone"),
    "story-results-day": ("story", "story-results"),
    "story-rant-or-rave": ("story", "story-rant"),
}

# CSS px at 96 dpi for the print sheets; PNGs are rendered at 2x like the originals.
SIZES = {"a3": (1123, 1588), "a5": (559, 794), "slide": (1920, 1080), "story": (1080, 1920)}

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


def ensure_qr(slug: str, regen: bool) -> None:
    out = ASSETS / f"qr-{slug}.png"
    if out.exists() and not regen:
        return
    q = qrcode.QRCode(error_correction=ERROR_CORRECT_H, box_size=20, border=2)
    q.add_data(f"{BASE}?ref={slug}")
    q.make(fit=True)
    q.make_image(fill_color="#1F1F1F", back_color="#FAFAFA").save(out)
    print(f"QR  {out.name} -> {BASE}?ref={slug}")


def chrome(chrome_bin: str, args: list[str]) -> None:
    base = [
        chrome_bin, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run",
        "--disable-extensions", "--allow-file-access-from-files",
        "--run-all-compositor-stages-before-draw", "--virtual-time-budget=5000",
    ]
    subprocess.run(base + args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def render(chrome_bin: str, name: str, kind: str, noprize: bool) -> None:
    src = (MOCKUPS / f"{name}.html").resolve().as_uri() + "?clean" + ("&noprize" if noprize else "")
    w, h = SIZES[kind]
    EXPORTS.mkdir(exist_ok=True)
    if kind in ("a3", "a5"):
        chrome(chrome_bin, ["--no-pdf-header-footer", f"--print-to-pdf={EXPORTS / (name + '.pdf')}", src])
        chrome(chrome_bin, ["--force-device-scale-factor=2", f"--window-size={w},{h}", f"--screenshot={EXPORTS / (name + '.png')}", src])
    else:
        chrome(chrome_bin, ["--force-device-scale-factor=1", f"--window-size={w},{h}", f"--screenshot={EXPORTS / (name + '.png')}", src])
    print(f"render {name} ({kind})")


def main() -> None:
    argv = sys.argv[1:]
    pieces = dict(ADDED)
    if "--all" in argv:
        pieces = {**ORIGINAL, **ADDED}
    regen = "--regen-qr" in argv
    noprize = "--noprize" in argv
    chrome_bin = find_chrome()
    print(f"Chrome: {chrome_bin}")
    for name, (kind, slug) in pieces.items():
        ensure_qr(slug, regen)
    for name, (kind, slug) in pieces.items():
        render(chrome_bin, name, kind, noprize)
    print(f"done -> {EXPORTS}")


if __name__ == "__main__":
    main()
