const EVIDENCE_PHOTO_BASE64 =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='

const evidencePhotoBytes = () =>
  Uint8Array.from(atob(EVIDENCE_PHOTO_BASE64), (char) => char.charCodeAt(0))

const evidencePhotoBlob = () => new Blob([evidencePhotoBytes()], { type: 'image/png' })

module.exports = { evidencePhotoBytes, evidencePhotoBlob }
