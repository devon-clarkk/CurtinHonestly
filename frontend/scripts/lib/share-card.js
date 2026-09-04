/**
 * The share card design, as SVG.
 *
 * These are the images Discord, WhatsApp, Slack and X draw when someone pastes
 * a link, so they are the site's face far more often than the site is. They
 * take their palette, their type and their one ornament from the real site:
 * theme.css supplies the colours, public/assets/fonts supplies the faces, and
 * the short gold bar is .handbook-title::before scaled up.
 *
 * Two constraints shape every decision here. A card is usually seen at about a
 * third of its rendered size in a feed, so the unit code is set large enough to
 * survive that and everything else is ranked beneath it. And a card must never
 * suggest a rating that does not exist: 1,729 of the 1,761 units have no
 * reviews, so the no-rating card is the design and the rated one is the
 * variant, not the other way round.
 */

const WIDTH = 1200;
const HEIGHT = 630;
const MARGIN = 72;
const CONTENT_WIDTH = WIDTH - MARGIN * 2;

// theme.css. Gold is used only as fill: as text on this ground it fails WCAG AA,
// which is why --primary-ink exists there and is used here for gold lettering.
const COLORS = {
  ground: '#fafafa',
  ink: '#000000',
  bodyInk: '#1f1f1f',
  muted: '#666666',
  gold: '#c09711',
  goldInk: '#8a6d0c',
  hairline: '#e8e8e8',
};

// resvg renders a variable font at its default instance and offers no way to
// move the weight axis, so the two variable faces are only ever used at the
// weight they default to. Nimbus Sans ships real static Regular and Bold files,
// so every weight contrast on a card comes from those.
const DISPLAY_FONT = 'Nimbus Sans L';
const NAME_FONT = 'Roboto Condensed';

const FONT_FILES = {
  displayBold: 'NimbusSanL-Bol.otf',
  displayRegular: 'NimbusSanL-Reg.otf',
  name: 'RobotoCondensed-VariableFont_wght.ttf',
};

// A fixed skeleton, shared by all 1,769 cards. Anything that varies in length
// is fitted into its slot rather than being allowed to push the slot around,
// so the whole set reads as one system.
const LAYOUT = {
  barY: 74,
  barWidth: 76,
  barHeight: 12,
  codeBaseline: 236,
  codeSize: 148,
  nameFirstBaseline: 322,
  nameLineHeight: 64,
  nameMaxLines: 2,
  nameSizes: [54, 48, 44],
  statusBaseline: 468,
  statusSize: 40,
  statusSubBaseline: 512,
  statusSubSize: 30,
  hairlineY: 540,
  footerBaseline: 590,
  footerSize: 28,
};

function escapeXml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

/** Trims a coordinate to whole tenths so the SVG text is stable to the byte. */
function round(value) {
  return Math.round(value * 10) / 10;
}

function text({ x, y, size, family, weight = 400, fill, content, anchor }) {
  const anchorAttr = anchor ? ` text-anchor="${anchor}"` : '';
  return (
    `<text x="${round(x)}" y="${round(y)}" font-family="${family}" font-weight="${weight}" ` +
    `font-size="${size}" fill="${fill}"${anchorAttr}>${escapeXml(content)}</text>`
  );
}

/**
 * Breaks `content` into at most `maxLines` lines that each fit `maxWidth`,
 * stepping the font size down before it gives up and clips.
 *
 * Returning the size as well as the lines is the point: the caller cannot lay
 * the block out until it knows which size actually fitted.
 */
function fitLines(content, metrics, sizes, maxLines, maxWidth) {
  const words = String(content).split(/\s+/).filter(Boolean);

  for (const size of sizes) {
    const lines = [];
    let current = '';

    for (const word of words) {
      const candidate = current ? `${current} ${word}` : word;
      if (current && metrics.measure(candidate, size) > maxWidth) {
        lines.push(current);
        current = word;
      } else {
        current = candidate;
      }
    }
    if (current) {
      lines.push(current);
    }

    if (lines.length <= maxLines && lines.every((line) => metrics.measure(line, size) <= maxWidth)) {
      return { lines, size };
    }
  }

  // Nothing in the live catalogue reaches this: the longest name is 90
  // characters and two lines at the smallest size hold about 120. It exists so
  // a longer name added later loses its tail rather than the card's layout.
  const size = sizes[sizes.length - 1];
  const lines = [];
  let current = '';
  for (const word of words) {
    const candidate = current ? `${current} ${word}` : word;
    if (current && metrics.measure(candidate, size) > maxWidth) {
      lines.push(current);
      current = word;
      if (lines.length === maxLines) {
        break;
      }
    } else {
      current = candidate;
    }
  }
  if (lines.length < maxLines && current) {
    lines.push(current);
  }
  const last = lines.length - 1;
  if (last >= 0) {
    let truncated = lines[last];
    while (truncated && metrics.measure(`${truncated}…`, size) > maxWidth) {
      truncated = truncated.slice(0, -1).trimEnd();
    }
    lines[last] = `${truncated}…`;
  }
  return { lines, size };
}

/** The largest size from `sizes` at which `content` fits on one line. */
function fitSingleLine(content, metrics, sizes, maxWidth) {
  for (const size of sizes) {
    if (metrics.measure(content, size) <= maxWidth) {
      return size;
    }
  }
  return sizes[sizes.length - 1];
}

/**
 * A single line of copy, stepped down until it fits the card.
 *
 * Every string on a card that is not unit data is still worth fitting rather
 * than trusting: the copy is edited by hand, and a line that overruns the
 * canvas is invisible in the SVG and obvious in the PNG.
 */
function fittedLine({ x, y, size, family, weight = 400, fill, content, metrics, maxWidth }) {
  const ladder = [size, size - 4, size - 8, size - 12].filter((value) => value > 0);
  return text({
    x,
    y,
    size: fitSingleLine(content, metrics, ladder, maxWidth),
    family,
    weight,
    fill,
    content,
  });
}

/** A five-pointed star, points rounded so the path is byte-stable. */
function starPath(centerX, centerY, radius) {
  const points = [];
  for (let i = 0; i < 10; i++) {
    const isOuter = i % 2 === 0;
    const r = isOuter ? radius : radius * 0.4;
    // Start at the top point and walk clockwise.
    const angle = -Math.PI / 2 + (i * Math.PI) / 5;
    points.push(`${round(centerX + r * Math.cos(angle))},${round(centerY + r * Math.sin(angle))}`);
  }
  return `M${points.join('L')}Z`;
}

/**
 * Five stars filled to `rating` out of five.
 *
 * The fill is a clip rather than five separately drawn part-stars, so a 3.5
 * shows as an exact half and the geometry stays identical whatever the rating.
 */
function starRow({ x, y, rating, id }) {
  const radius = 21;
  const step = 52;
  const empty = [];
  const filled = [];

  for (let i = 0; i < 5; i++) {
    const path = starPath(x + radius + i * step, y, radius);
    empty.push(`<path d="${path}" fill="${COLORS.hairline}"/>`);
    filled.push(`<path d="${path}" fill="${COLORS.gold}"/>`);
  }

  const totalWidth = step * 4 + radius * 2;
  const fillWidth = round((Math.max(0, Math.min(5, rating)) / 5) * totalWidth);

  return {
    width: totalWidth,
    markup:
      `<defs><clipPath id="${id}">` +
      `<rect x="${round(x)}" y="${round(y - radius - 2)}" width="${fillWidth}" height="${radius * 2 + 4}"/>` +
      `</clipPath></defs>` +
      empty.join('') +
      `<g clip-path="url(#${id})">${filled.join('')}</g>`,
  };
}

function plural(count, singular) {
  return count === 1 ? `1 ${singular}` : `${count} ${singular}s`;
}

/**
 * Thousands separated by hand rather than through toLocaleString, whose output
 * depends on the ICU data the running Node was built with. The cards have to be
 * byte-identical from one machine to the next, so nothing that renders into
 * them may depend on the environment.
 */
function formatCount(value) {
  return String(Math.round(Number(value) || 0)).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

/** The frame every card shares: ground, gold bar, hairline and footer. */
function frame({ footerLeft, metrics }) {
  const parts = [
    `<rect width="${WIDTH}" height="${HEIGHT}" fill="${COLORS.ground}"/>`,
    `<rect x="${MARGIN}" y="${LAYOUT.barY}" width="${LAYOUT.barWidth}" height="${LAYOUT.barHeight}" fill="${COLORS.gold}"/>`,
    `<rect x="${MARGIN}" y="${LAYOUT.hairlineY}" width="${CONTENT_WIDTH}" height="1" fill="${COLORS.hairline}"/>`,
    text({
      x: WIDTH - MARGIN,
      y: LAYOUT.footerBaseline,
      size: LAYOUT.footerSize,
      family: DISPLAY_FONT,
      weight: 700,
      fill: COLORS.bodyInk,
      content: 'curtinhonestly.com',
      anchor: 'end',
    }),
  ];

  if (footerLeft) {
    // The footer holds two strings on one line, so the left one is trimmed to
    // whatever the right one leaves rather than running underneath it.
    const rightWidth = metrics.displayBold.measure('curtinhonestly.com', LAYOUT.footerSize);
    const available = CONTENT_WIDTH - rightWidth - 40;
    let label = footerLeft;
    while (label && metrics.displayRegular.measure(label, LAYOUT.footerSize) > available) {
      label = label.slice(0, -1).trimEnd();
    }
    if (label) {
      parts.push(
        text({
          x: MARGIN,
          y: LAYOUT.footerBaseline,
          size: LAYOUT.footerSize,
          family: DISPLAY_FONT,
          fill: COLORS.muted,
          content: label,
        })
      );
    }
  }

  return parts.join('\n  ');
}

function svgDocument(body) {
  return (
    `<svg xmlns="http://www.w3.org/2000/svg" width="${WIDTH}" height="${HEIGHT}" ` +
    `viewBox="0 0 ${WIDTH} ${HEIGHT}">\n  ${body}\n</svg>\n`
  );
}

/**
 * A unit card: the code at a size that survives a feed thumbnail, the name
 * under it, and the unit's review position stated plainly.
 */
function unitCardSvg(unit, metrics) {
  const parts = [frame({ footerLeft: unit.faculty, metrics })];

  parts.push(
    text({
      x: MARGIN,
      y: LAYOUT.codeBaseline,
      size: LAYOUT.codeSize,
      family: DISPLAY_FONT,
      weight: 700,
      fill: COLORS.ink,
      content: unit.code,
    })
  );

  const { lines, size } = fitLines(
    unit.name,
    metrics.name,
    LAYOUT.nameSizes,
    LAYOUT.nameMaxLines,
    CONTENT_WIDTH
  );

  lines.forEach((line, index) => {
    parts.push(
      text({
        x: MARGIN,
        y: LAYOUT.nameFirstBaseline + index * LAYOUT.nameLineHeight,
        size,
        family: NAME_FONT,
        fill: COLORS.bodyInk,
        content: line,
      })
    );
  });

  const reviews = Number(unit.reviews) || 0;

  if (reviews > 0) {
    // The site states the average to one decimal everywhere else, so the card
    // does too rather than inventing a second rounding.
    const rating = Number(unit.rating) || 0;
    const stars = starRow({
      x: MARGIN,
      y: LAYOUT.statusBaseline - 14,
      rating,
      id: 'rating-fill',
    });
    parts.push(stars.markup);
    parts.push(
      fittedLine({
        x: MARGIN + stars.width + 32,
        y: LAYOUT.statusBaseline,
        size: LAYOUT.statusSize,
        family: DISPLAY_FONT,
        weight: 700,
        fill: COLORS.ink,
        content: `${rating.toFixed(1)} out of 5`,
        metrics: metrics.displayBold,
        maxWidth: CONTENT_WIDTH - stars.width - 32,
      })
    );
    parts.push(
      fittedLine({
        x: MARGIN,
        y: LAYOUT.statusSubBaseline,
        size: LAYOUT.statusSubSize,
        family: DISPLAY_FONT,
        fill: COLORS.muted,
        content: `from ${plural(reviews, 'student review')}`,
        metrics: metrics.displayRegular,
        maxWidth: CONTENT_WIDTH,
      })
    );
  } else {
    // No stars, no score, nothing shaped like a rating. The card says what is
    // there, which is an invitation, and says what is not underneath it.
    parts.push(
      fittedLine({
        x: MARGIN,
        y: LAYOUT.statusBaseline,
        size: LAYOUT.statusSize,
        family: DISPLAY_FONT,
        weight: 700,
        fill: COLORS.bodyInk,
        content: 'Be the first to review this unit',
        metrics: metrics.displayBold,
        maxWidth: CONTENT_WIDTH,
      })
    );
    parts.push(
      fittedLine({
        x: MARGIN,
        y: LAYOUT.statusSubBaseline,
        size: LAYOUT.statusSubSize,
        family: DISPLAY_FONT,
        fill: COLORS.muted,
        content: 'No student reviews yet',
        metrics: metrics.displayRegular,
        maxWidth: CONTENT_WIDTH,
      })
    );
  }

  return svgDocument(parts.join('\n  '));
}

/**
 * A faculty hub card: the faculty, and how much of the catalogue it holds.
 *
 * The footer carries only the wordmark. A unit card uses the left of that line
 * to name the faculty, which is the one thing its code does not say; a hub card
 * has already said it, in the largest type on the card.
 */
function facultyCardSvg(hub, metrics) {
  const parts = [frame({ footerLeft: null, metrics })];

  const size = fitSingleLine(hub.name, metrics.name, [104, 92, 80, 72], CONTENT_WIDTH);
  parts.push(
    text({
      x: MARGIN,
      y: LAYOUT.codeBaseline,
      size,
      family: NAME_FONT,
      fill: COLORS.ink,
      content: hub.name,
    })
  );

  parts.push(
    fittedLine({
      x: MARGIN,
      y: LAYOUT.nameFirstBaseline,
      size: LAYOUT.nameSizes[0],
      family: NAME_FONT,
      fill: COLORS.bodyInk,
      content: `${formatCount(hub.unitCount)} units at Curtin University`,
      metrics: metrics.name,
      maxWidth: CONTENT_WIDTH,
    })
  );

  parts.push(
    fittedLine({
      x: MARGIN,
      y: LAYOUT.statusBaseline,
      size: LAYOUT.statusSize,
      family: DISPLAY_FONT,
      weight: 700,
      fill: COLORS.bodyInk,
      content: 'Student ratings and honest reviews',
      metrics: metrics.displayBold,
      maxWidth: CONTENT_WIDTH,
    })
  );

  parts.push(
    fittedLine({
      x: MARGIN,
      y: LAYOUT.statusSubBaseline,
      size: LAYOUT.statusSubSize,
      family: DISPLAY_FONT,
      fill: COLORS.muted,
      content: 'Browse every unit in the faculty',
      metrics: metrics.displayRegular,
      maxWidth: CONTENT_WIDTH,
    })
  );

  return svgDocument(parts.join('\n  '));
}

/** Home, about and contact. Three pages, so the copy is written per card. */
function infoCardSvg(page, metrics) {
  const parts = [frame({ footerLeft: null, metrics })];

  const headingSizes = [104, 92, 80, 72, 64];
  const { lines, size } = fitLines(page.heading, metrics.name, headingSizes, 2, CONTENT_WIDTH);

  lines.forEach((line, index) => {
    parts.push(
      text({
        x: MARGIN,
        y: LAYOUT.codeBaseline + index * (size + 12),
        size,
        family: NAME_FONT,
        fill: COLORS.ink,
        content: line,
      })
    );
  });

  parts.push(
    fittedLine({
      x: MARGIN,
      y: LAYOUT.statusBaseline,
      size: LAYOUT.statusSize,
      family: DISPLAY_FONT,
      weight: 700,
      fill: COLORS.bodyInk,
      content: page.lead,
      metrics: metrics.displayBold,
      maxWidth: CONTENT_WIDTH,
    })
  );

  parts.push(
    fittedLine({
      x: MARGIN,
      y: LAYOUT.statusSubBaseline,
      size: LAYOUT.statusSubSize,
      family: DISPLAY_FONT,
      fill: COLORS.muted,
      content: page.support,
      metrics: metrics.displayRegular,
      maxWidth: CONTENT_WIDTH,
    })
  );

  return svgDocument(parts.join('\n  '));
}

module.exports = {
  WIDTH,
  HEIGHT,
  COLORS,
  FONT_FILES,
  DISPLAY_FONT,
  NAME_FONT,
  escapeXml,
  formatCount,
  fitLines,
  unitCardSvg,
  facultyCardSvg,
  infoCardSvg,
};
