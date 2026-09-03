/**
 * Re-encodes a rendered card as an 8-bit palette PNG.
 *
 * resvg emits colour type 6: four channels, eight bits each. A share card is
 * flat colour and type, so it uses fewer than a hundred distinct colours and is
 * opaque everywhere. Three quarters of what type 6 stores is therefore a
 * constant alpha channel and three near-duplicate colour channels, and the
 * measured cost of that is real: 44 kB a card against 16 kB for the same
 * pixels written as indices into a palette, or about 48 MB across the set.
 *
 * This is lossless, not a quality trade. A palette holds 256 entries and no
 * card comes close to filling one, so every pixel keeps its exact colour.
 * Anything that somehow needs more colours than that is handed back unchanged
 * rather than quantised, so the fallback loses bytes and never fidelity.
 */
const zlib = require('zlib');

const PNG_SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
const MAX_PALETTE = 256;

const CRC_TABLE = (() => {
  const table = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[n] = c;
  }
  return table;
})();

function crc32(buffer) {
  let crc = -1;
  for (let i = 0; i < buffer.length; i++) {
    crc = CRC_TABLE[(crc ^ buffer[i]) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ -1) >>> 0;
}

function chunk(type, data) {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length, 0);
  const typeAndData = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(typeAndData), 0);
  return Buffer.concat([length, typeAndData, crc]);
}

/**
 * `pixels` is raw RGBA, row-major, as resvg's render() hands it over.
 * Returns a palette PNG, or null when the image cannot be one losslessly.
 */
function encodeIndexedPng(pixels, width, height) {
  const expected = width * height * 4;
  if (pixels.length !== expected) {
    return null;
  }

  // Palette entries are collected as packed RGB and then sorted, so the palette
  // depends only on which colours are present and not on the order the scan
  // happened to meet them.
  const seen = new Set();
  for (let i = 0; i < pixels.length; i += 4) {
    if (pixels[i + 3] !== 255) {
      // A transparent pixel would need a tRNS table. Nothing here produces one,
      // and inventing partial transparency silently is worse than not shrinking.
      return null;
    }
    seen.add((pixels[i] << 16) | (pixels[i + 1] << 8) | pixels[i + 2]);
    if (seen.size > MAX_PALETTE) {
      return null;
    }
  }

  const palette = [...seen].sort((a, b) => a - b);
  const indexByColor = new Map(palette.map((color, index) => [color, index]));

  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 3; // colour type: indexed
  ihdr[10] = 0; // deflate
  ihdr[11] = 0; // adaptive filtering
  ihdr[12] = 0; // no interlace

  const plte = Buffer.alloc(palette.length * 3);
  palette.forEach((color, index) => {
    plte[index * 3] = (color >> 16) & 0xff;
    plte[index * 3 + 1] = (color >> 8) & 0xff;
    plte[index * 3 + 2] = color & 0xff;
  });

  // One filter byte per scanline, then one palette index per pixel. Filter 0
  // (None) is used throughout: index values are labels rather than magnitudes,
  // so the differencing filters have nothing meaningful to subtract, and a
  // fixed choice keeps the bytes reproducible.
  const raw = Buffer.alloc(height * (width + 1));
  let out = 0;
  for (let y = 0; y < height; y++) {
    raw[out++] = 0;
    const rowStart = y * width * 4;
    for (let x = 0; x < width; x++) {
      const i = rowStart + x * 4;
      raw[out++] = indexByColor.get((pixels[i] << 16) | (pixels[i + 1] << 8) | pixels[i + 2]);
    }
  }

  const idat = zlib.deflateSync(raw, { level: 9 });

  return Buffer.concat([
    PNG_SIGNATURE,
    chunk('IHDR', ihdr),
    chunk('PLTE', plte),
    chunk('IDAT', idat),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

module.exports = { encodeIndexedPng, crc32 };
