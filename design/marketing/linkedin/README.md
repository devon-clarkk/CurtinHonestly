# CurtinHonestly LinkedIn company page kit

Everything for the LinkedIn company page lives in this one folder:

```
design/marketing/linkedin/
```

Full path on this machine:
`C:\Users\devon\Desktop\PROJECTS\CURTIN HONESTLY\Curtin-Computing-Review-Platform\design\marketing\linkedin`

Nothing has been posted, uploaded, or deployed. These are files on disk.

Built on the same brand and positioning as `design/marketing/launch-plan.md`, so the
page reads as the same product as the posters, the lecture slide, and the video work.

---

## What this channel is actually for

Worth stating, because the launch plan says the opposite about a different job.
Section 8 of that plan rules LinkedIn out as a **social** channel, on the grounds that
it reaches nobody who will ever write a unit review. That still holds. Students do not
open LinkedIn to review a second year elective.

A company page is a different instrument. It is what comes up when a unit coordinator,
a Guild officer, a club president, a journalist, or a potential collaborator searches
the name after seeing a poster and wants to know whether this is a real project or a
scam. It is credibility infrastructure, not a review-supply channel. Judge it on
whether it answers "is this legitimate, and who is behind it", and do not expect it to
move the review count.

So: build it, fill it in properly, then leave it alone. Do not put it on the weekly
posting schedule.

---

## Assets

All PNG, all opaque, all square-cornered. LinkedIn applies its own corner rounding at
display time, so the files must not be pre-rounded and must not be transparent.

| File | Size | Use |
| --- | --- | --- |
| `exports/logo-300x300-dark.png` | 300 x 300 | **Primary company logo.** Gold shield on solid black. |
| `exports/logo-300x300-light.png` | 300 x 300 | Light variant. Gold shield on solid off-white. Only over dark surroundings. |
| `exports/logo-400x400-dark.png` | 400 x 400 | Profile-safe spare of the primary, for any surface that wants more than 300. |
| `exports/logo-400x400-light.png` | 400 x 400 | Profile-safe spare of the light variant. |
| `exports/logo-300x300-stacked-dark.png` | 300 x 300 | Alternate. Shield plus wordmark. Only where it renders at 80px or larger. |
| `exports/logo-300x300-stacked-light.png` | 300 x 300 | Alternate, light. |
| `exports/cover-1128x191-light.png` | 1128 x 191 | **Primary company cover.** Off-white. |
| `exports/cover-1128x191-dark.png` | 1128 x 191 | Cover, black. Pairs with the light logo tile. |
| `exports/cover-2256x382-light.png` | 2256 x 382 | Exact 2x of the light cover, for high-DPI displays. |
| `exports/cover-2256x382-dark.png` | 2256 x 382 | Exact 2x of the dark cover. |
| `exports/preview-pairing-REFERENCE-ONLY.png` | 1200 x 800 | **Reference mockup. Never upload this file.** Shows the pairing and proves the tile clears the cover content. |
| `copy.md` | text | Tagline, About, specialties, page fields, copy rules. |
| `src/` | HTML, CSS, fonts, shield | Editable source. Regenerate from here. |

Largest file is 59 KB, so LinkedIn's 8 MB upload ceiling is not a factor.

### Which logo to use

**Use `logo-300x300-dark.png`.** Two reasons. The black tile keeps a hard edge against
both the off-white cover and LinkedIn's own light page surface, and it matches how the
site already sets a black header lockup on an off-white page. The light tile on
LinkedIn's light interface has almost no edge at all and reads as a floating shield.

The shield sits at 58% of the tile height. That is deliberate: it leaves clear space
well outside anything LinkedIn's corner rounding can reach, and the silhouette still
reads at a 48px feed avatar. The wordmark is not on the primary tile because
"CurtinHonestly" at a 48px avatar is roughly 1.5px of type, which is a grey smudge. The
stacked variant exists for surfaces that render larger. It is an alternate, not the
default.

---

## Safe zones

### Cover, 1128 x 191

LinkedIn overlaps the company logo tile onto the lower left of the cover, and clips the
outer edges on narrow viewports. Guidance is to treat the bottom-left 250 x 250 px as
unusable. This canvas is only 191 px tall, so in practice that is **the left 250 px at
full height**.

This build reserves **316 px** on the left, for margin of error.

Measured ink bounds of both covers, checked against the exported PNG rather than the
CSS:

```
content occupies    x 316 to 817     y 33 to 159
clear on the left   316 px           (66 px more than the 250 px dead zone needs)
clear on the right  311 px
clear top           33 px
clear bottom        32 px
```

The block sits close to centred inside the usable band and stays a long way off both
edges, which is what survives the mobile crop.
`preview-pairing-REFERENCE-ONLY.png` shows the tile landing in that reserved area with
nothing behind it.

**If you edit the cover:** keep everything inside `x 316 to 1084` and `y 30 to 161`.
Do not put the URL, or any single critical word, in the outer 10% of either side.

### Logo tile, 300 x 300 and 400 x 400

Keep the mark inside the middle 70% of the square. LinkedIn rounds the corners and, on
some surfaces, crops closer to a circle. The current 58% shield sits well inside that.
The background must stay solid and edge to edge. No transparency, no drop shadow, no
rounded corners baked into the file.

---

## Pairing

| Logo tile | Cover | Verdict |
| --- | --- | --- |
| Dark | Light | **Recommended.** Tile keeps a hard edge on the cover and on LinkedIn's page surface. |
| Light | Dark | Works. Use only if the dark cover is chosen. |
| Dark | Dark | Avoid. The tile dissolves into the cover and loses its edge. |
| Light | Light | Avoid. Same problem, and the tile also disappears against LinkedIn's own light interface. |

The rule underneath all four rows: never put like on like. The tile needs a tonal edge
or it stops reading as a mark.

---

## Uploading, when Devon decides to

Manual steps for a person. Nothing here is automated and nothing has been sent.

1. Create the page. Name `CurtinHonestly`, website `https://www.curtinhonestly.com`.
2. Logo: upload `exports/logo-300x300-dark.png`.
3. Cover: upload `exports/cover-1128x191-light.png`. If LinkedIn rejects it as too
   small on a high-DPI display, use `exports/cover-2256x382-light.png` instead. It is
   the same design at exactly double the pixels, so the crop is identical.
4. Tagline and About: paste from `copy.md`. Both are already inside LinkedIn's limits,
   at 114 of 120 characters and 1981 of 2000.
5. Specialties: the 15 in `copy.md` section 3.
6. Check the page on a phone before telling anyone about it. That is where the cover
   crops hardest.

**Note on cover dimensions.** LinkedIn's help centre also lists 4200 x 700 as a
recommended upload size. That is a 6.00:1 aspect against 1128 x 191's 5.906:1, so a
4200 x 700 file gets cropped slightly. The 2x file supplied here holds the exact
requested aspect, which is why it is 2256 x 382 and not 4200 x 700.

---

## Regenerating

The artboards are HTML rendered at exact pixel sizes, the same method as the poster set.

```bash
python3 -m http.server 4175 -d design/marketing/linkedin
```

There is also a `linkedin` entry in `.claude/launch.json` that does this.

```bash
CH="/c/Program Files/Google/Chrome/Application/chrome.exe"
B="http://localhost:4175/src"

# logo tile: size=300|400, bg=dark|light, style=shield|stacked
"$CH" --headless=new --hide-scrollbars --virtual-time-budget=4000 \
  --window-size=300,300 --screenshot=exports/logo-300x300-dark.png \
  "$B/tile.html?size=300&bg=dark"

# cover: bg=light|dark, add --force-device-scale-factor=2 for the 2x file
"$CH" --headless=new --hide-scrollbars --virtual-time-budget=4000 \
  --window-size=1128,191 --screenshot=exports/cover-1128x191-light.png \
  "$B/cover.html?bg=light"
```

`src/shield-gold-hires.png` is a 961 x 1217 transparent gold shield, cut from
`design/branding/Logo/shield.png` (1890 x 1417), which is the highest resolution
source in the repo. **There is no vector version of the logo anywhere in the project.**
If Devon ever has one made, redo the shield from it and re-render everything here.
That is the single biggest quality upgrade available to this folder.

Do not use `design/branding/Logo/final.png` or `frontend/public/assets/images/logo.png`
for any of this. Both carry an opaque black slab baked into the artwork, which is
correct for the site header and wrong everywhere else.
