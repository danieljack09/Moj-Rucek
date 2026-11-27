'use strict';

/**
 * What kind of picture a file really is, decided from its first bytes (never from the name or the declared type).
 * Only JPEG, PNG and WebP are accepted: they are what phones and browsers produce, and none of them can carry script
 * (SVG, which can, is refused on purpose). Returns { mime, ext } or null.
 */
function detectImage(buf) {
  if (!Buffer.isBuffer(buf) || buf.length < 12) return null;
  if (buf[0] === 0xff && buf[1] === 0xd8 && buf[2] === 0xff) return { mime: 'image/jpeg', ext: 'jpg' };
  if (buf.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) return { mime: 'image/png', ext: 'png' };
  if (buf.subarray(0, 4).toString('latin1') === 'RIFF' && buf.subarray(8, 12).toString('latin1') === 'WEBP') return { mime: 'image/webp', ext: 'webp' };
  return null;
}

const MIME_BY_EXT = { jpg: 'image/jpeg', png: 'image/png', webp: 'image/webp' };

module.exports = { detectImage, MIME_BY_EXT };
