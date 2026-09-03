/**
 * Advance widths straight out of a TrueType or OpenType file.
 *
 * SVG has no line breaking: <text> draws one line and lets it run off the
 * canvas. Every line break on a share card therefore has to be decided before
 * the SVG is written, and deciding it needs to know how wide a string will be.
 * The longest unit name in the catalogue is 90 characters, so an estimate based
 * on average character width is not good enough: it is wrong by whole words
 * exactly where the layout has least room to absorb it.
 *
 * This reads the three tables that answer the question exactly. `cmap` maps a
 * character to a glyph, `hmtx` gives that glyph its advance, and `head` gives
 * the units-per-em those advances are expressed in. Kerning is deliberately not
 * applied: resvg does apply it, and kerning almost always pulls glyphs closer,
 * so measuring without it overestimates slightly and wraps a shade early. That
 * is the safe direction to be wrong in.
 */
const fs = require('fs');

function readTableDirectory(buffer) {
  const tag = buffer.readUInt32BE(0);
  // 0x00010000 is TrueType outlines, 'OTTO' is CFF. Both carry the horizontal
  // metrics this file needs, so both are read the same way.
  if (tag !== 0x00010000 && tag !== 0x4f54544f && tag !== 0x74727565) {
    throw new Error(`Unrecognised font signature 0x${tag.toString(16)}`);
  }

  const numTables = buffer.readUInt16BE(4);
  const tables = new Map();

  for (let i = 0; i < numTables; i++) {
    const record = 12 + i * 16;
    tables.set(buffer.toString('ascii', record, record + 4), {
      offset: buffer.readUInt32BE(record + 8),
      length: buffer.readUInt32BE(record + 12),
    });
  }

  return tables;
}

/** Format 4: the segmented BMP mapping every modern font ships. */
function parseCmapFormat4(buffer, offset, map) {
  const segCountX2 = buffer.readUInt16BE(offset + 6);
  const segCount = segCountX2 / 2;
  const endCodes = offset + 14;
  const startCodes = endCodes + segCountX2 + 2;
  const idDeltas = startCodes + segCountX2;
  const idRangeOffsets = idDeltas + segCountX2;

  for (let seg = 0; seg < segCount; seg++) {
    const end = buffer.readUInt16BE(endCodes + seg * 2);
    const start = buffer.readUInt16BE(startCodes + seg * 2);
    if (start > end) {
      continue;
    }
    const delta = buffer.readInt16BE(idDeltas + seg * 2);
    const rangeOffset = buffer.readUInt16BE(idRangeOffsets + seg * 2);

    for (let code = start; code <= end && code !== 0xffff; code++) {
      let glyph;
      if (rangeOffset === 0) {
        glyph = (code + delta) & 0xffff;
      } else {
        const glyphIndexAddress = idRangeOffsets + seg * 2 + rangeOffset + (code - start) * 2;
        if (glyphIndexAddress + 1 >= buffer.length) {
          continue;
        }
        glyph = buffer.readUInt16BE(glyphIndexAddress);
        if (glyph !== 0) {
          glyph = (glyph + delta) & 0xffff;
        }
      }
      if (glyph !== 0 && !map.has(code)) {
        map.set(code, glyph);
      }
    }
  }
}

/** Format 12: the grouped mapping fonts use to reach beyond the BMP. */
function parseCmapFormat12(buffer, offset, map) {
  const numGroups = buffer.readUInt32BE(offset + 12);
  for (let group = 0; group < numGroups; group++) {
    const record = offset + 16 + group * 12;
    const start = buffer.readUInt32BE(record);
    const end = buffer.readUInt32BE(record + 4);
    const startGlyph = buffer.readUInt32BE(record + 8);
    for (let code = start; code <= end; code++) {
      if (!map.has(code)) {
        map.set(code, startGlyph + (code - start));
      }
    }
  }
}

function parseCmap(buffer, table) {
  const map = new Map();
  const base = table.offset;
  const numSubtables = buffer.readUInt16BE(base + 2);

  // Unicode subtables only, and format 12 before format 4 so the wider mapping
  // wins where a font ships both.
  const candidates = [];
  for (let i = 0; i < numSubtables; i++) {
    const record = base + 4 + i * 8;
    const platformId = buffer.readUInt16BE(record);
    const encodingId = buffer.readUInt16BE(record + 2);
    const subtableOffset = base + buffer.readUInt32BE(record + 4);
    const isUnicode =
      platformId === 0 || (platformId === 3 && (encodingId === 1 || encodingId === 10));
    if (isUnicode) {
      candidates.push({ offset: subtableOffset, format: buffer.readUInt16BE(subtableOffset) });
    }
  }

  candidates.sort((a, b) => (b.format === 12 ? 1 : 0) - (a.format === 12 ? 1 : 0));

  for (const candidate of candidates) {
    if (candidate.format === 4) {
      parseCmapFormat4(buffer, candidate.offset, map);
    } else if (candidate.format === 12) {
      parseCmapFormat12(buffer, candidate.offset, map);
    }
  }

  return map;
}

/**
 * Loads one font and returns something that can measure a string at a size.
 * The tables are parsed once per font, not once per card.
 */
function loadFontMetrics(filePath) {
  const buffer = fs.readFileSync(filePath);
  const tables = readTableDirectory(buffer);

  for (const required of ['head', 'hhea', 'hmtx', 'cmap']) {
    if (!tables.has(required)) {
      throw new Error(`${filePath} has no ${required} table.`);
    }
  }

  const unitsPerEm = buffer.readUInt16BE(tables.get('head').offset + 18);
  const numberOfHMetrics = buffer.readUInt16BE(tables.get('hhea').offset + 34);
  const hmtxOffset = tables.get('hmtx').offset;
  const cmap = parseCmap(buffer, tables.get('cmap'));

  // Past numberOfHMetrics the table stops repeating the advance: every
  // remaining glyph shares the last one, which is how monospaced tails are
  // stored compactly.
  const lastAdvance = buffer.readUInt16BE(hmtxOffset + (numberOfHMetrics - 1) * 4);

  function advanceForGlyph(glyph) {
    if (glyph < numberOfHMetrics) {
      return buffer.readUInt16BE(hmtxOffset + glyph * 4);
    }
    return lastAdvance;
  }

  const advanceCache = new Map();

  function advanceForCodePoint(codePoint) {
    const cached = advanceCache.get(codePoint);
    if (cached !== undefined) {
      return cached;
    }
    // A character the font has no glyph for measures as the space it will
    // occupy through the fallback stack rather than as nothing.
    const glyph = cmap.get(codePoint) ?? cmap.get(0x20) ?? 0;
    const advance = advanceForGlyph(glyph);
    advanceCache.set(codePoint, advance);
    return advance;
  }

  return {
    unitsPerEm,
    hasCodePoint(codePoint) {
      return cmap.has(codePoint);
    },
    /** Width of `text` drawn at `fontSize`, in the same units as fontSize. */
    measure(text, fontSize) {
      let units = 0;
      for (const character of String(text)) {
        units += advanceForCodePoint(character.codePointAt(0));
      }
      return (units * fontSize) / unitsPerEm;
    },
  };
}

module.exports = { loadFontMetrics };
