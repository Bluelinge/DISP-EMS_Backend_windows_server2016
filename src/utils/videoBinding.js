const VIDEO_EXTENSION = /\.(?:mp4|webm|ogg|ogv|mov|avi|mkv|m4v)$/i

export const normalizeVideoName = value => String(value ?? '')
  .normalize('NFKC')
  .trim()
  .replace(/^.*[\\/]/, '')
  .replace(VIDEO_EXTENSION, '')
  .replace(/\s+/g, ' ')
  .toLocaleLowerCase()

export function resolveVideoBinding(videoName, videos = [], deletedIds = []) {
  const requestedName = String(videoName ?? '').trim()
  if (!requestedName || requestedName.toUpperCase() === 'NONE') {
    return { status: 'none', video: null, requestedName }
  }

  const activeVideos = videos.filter(video =>
    video?.name && !deletedIds.includes(video.id),
  )
  const exactMatches = activeVideos.filter(video => video.name.trim() === requestedName)
  if (exactMatches.length === 1) {
    return { status: 'matched', video: exactMatches[0], requestedName, matchedBy: 'exact' }
  }

  const normalizedName = normalizeVideoName(requestedName)
  const normalizedMatches = activeVideos.filter(video =>
    normalizeVideoName(video.name) === normalizedName,
  )
  if (normalizedMatches.length === 1) {
    return { status: 'matched', video: normalizedMatches[0], requestedName, matchedBy: 'normalized' }
  }
  if (normalizedMatches.length > 1 || exactMatches.length > 1) {
    return { status: 'ambiguous', video: null, requestedName, candidates: normalizedMatches.length ? normalizedMatches : exactMatches }
  }
  return { status: 'missing', video: null, requestedName }
}
