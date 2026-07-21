
![[colours-palette.png]]
## Color Palette
https://www.realtimecolors.com/?colors=1f1f1f-fafafa-c09711-000000-666666&fonts=Inter-Inter

| Role                 | Name         | Hex       | RGB                  | Notes                     |
| -------------------- | ------------ | --------- | -------------------- | ------------------------- |
| Text                 | `text`       | `#1F1F1F` | `rgb(31, 31, 31)`    | Near-black, very readable |
| Background           | `background` | `#FAFAFA` | `rgb(250, 250, 250)` | Off-white, accessible     |
| Primary Accent       | `primary`    | `#C09711` | `rgb(192, 151, 17)`  | Inspired by Curtin Gold   |
| Secondary (Contrast) | `secondary`  | `#000000` | `rgb(0, 0, 0)`       | White                     |
| Accent               | `accent`     | `#666666` | `rgb(102, 102, 102)` | Light grey.               |
| Primary (text-safe)  | `primary-ink`| `#8A6D0C` | `rgb(138, 109, 12)`  | Derived shade of `primary`, darkened for AA contrast. **Not a new brand hue.** |
| Muted-on-dark        | `on-dark-muted` | `#CFCFCF` | `rgb(207, 207, 207)` | Body text on black (`secondary`) surfaces, e.g. hero/footer copy |

> Note: Colors were independently developed and chosen to loosely echo Curtin University's branding without duplication or official affiliation.

### `--primary-ink` — why it exists

`#C09711` (`primary`) measures **2.62–2.74:1** contrast as text on white/`#FAFAFA` —
fails WCAG AA (needs 4.5:1 for normal text, 3:1 for large text). It's fine as a
**fill** (buttons, bars, stars, borders — black-on-gold measures 7.68:1), but it
must never be used as text/link color on a light surface.

`--primary-ink` (`#8A6D0C`) is the same hue, darkened until it passes AA:
**4.91:1 on white, 4.71:1 on `#FAFAFA`**. Use it for any gold-colored text or
link (breadcrumbs, "back" links, unit codes on cards, footer links) instead of
`primary`. It is a *shade* of the existing primary accent, not a new hue.

### `--on-dark-muted` — why it exists

Plain white body copy on the black hero/footer background reads as visually
loud at length; `#CFCFCF` keeps it legible (10:1+ contrast on black) while
softening it, matching how the footer's existing copyright text already
behaves.


--text:
![[colour-text.png]]

--background:
![[colour-background.png]]

-primary:
![[colour-primary.png]]

--secondary:
![[colour-secondary.png]]

--accent
![[colour-accent.png]]


