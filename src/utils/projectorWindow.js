const PROJECTOR_WINDOW_NAME = 'disp-mes-projector'
const PROJECTOR_SCREEN_KEY = 'disp-mes-projector-screen-v1'

const number = value => Number(value) || 0
const screenKey = screen => [
  screen.label || '',
  number(screen.availLeft ?? screen.left),
  number(screen.availTop ?? screen.top),
  number(screen.availWidth ?? screen.width),
  number(screen.availHeight ?? screen.height),
].join('|')
const displayName = (screen, index) => screen.label || `显示器 ${index + 1}`

export function getSavedProjectorScreen() {
  try {
    const saved = JSON.parse(localStorage.getItem(PROJECTOR_SCREEN_KEY) || 'null')
    return saved?.key ? saved : null
  } catch { return null }
}

export function saveProjectorScreen(screen) {
  if (!screen?.key) return null
  const preference = {
    key: screen.key,
    label: screen.label || '',
    name: screen.name,
    left: number(screen.left),
    top: number(screen.top),
    width: number(screen.width),
    height: number(screen.height),
  }
  localStorage.setItem(PROJECTOR_SCREEN_KEY, JSON.stringify(preference))
  return preference
}

export function clearSavedProjectorScreen() {
  localStorage.removeItem(PROJECTOR_SCREEN_KEY)
}

export async function getWindowManagementPermission() {
  if (typeof window === 'undefined' || !('getScreenDetails' in window)) return 'unsupported'
  try {
    const result = await navigator.permissions?.query?.({ name: 'window-management' })
    return result?.state || 'prompt'
  } catch { return 'prompt' }
}

export async function getProjectorScreens() {
  if (typeof window === 'undefined' || !('getScreenDetails' in window)) return { status: 'unsupported', screens: [] }
  try {
    const details = await window.getScreenDetails()
    return {
      status: 'ready',
      screens: details.screens.map((screen, index) => ({
        key: screenKey(screen),
        label: screen.label || '',
        name: displayName(screen, index),
        left: number(screen.availLeft ?? screen.left),
        top: number(screen.availTop ?? screen.top),
        width: number(screen.availWidth ?? screen.width),
        height: number(screen.availHeight ?? screen.height),
        isCurrent: screen === details.currentScreen,
        isPrimary: !!screen.isPrimary,
      })),
    }
  } catch (error) {
    return { status: error?.name === 'NotAllowedError' ? 'denied' : 'unavailable', screens: [] }
  }
}

export function findSavedProjectorScreen(screens = []) {
  const saved = getSavedProjectorScreen()
  if (!saved) return null
  return screens.find(screen => screen.key === saved.key) || null
}

function popupFeatures(screen) {
  const width = number(screen?.width) || 1280
  const height = number(screen?.height) || 720
  const left = number(screen?.left)
  const top = number(screen?.top)
  return `popup=yes,resizable=yes,scrollbars=no,left=${left},top=${top},width=${width},height=${height}`
}

function placeProjectorWindow(projectorWindow, screen) {
  if (!projectorWindow || !screen) return
  try {
    projectorWindow.moveTo(number(screen.left), number(screen.top))
    projectorWindow.resizeTo(number(screen.width), number(screen.height))
  } catch { /* Browser may reject window movement; popup creation still falls back safely. */ }
}

export function openProjectorWindow(taskId, screen = null) {
  if (typeof window === 'undefined') return { status: 'unavailable', window: null }
  const id = encodeURIComponent(String(taskId || ''))
  const url = `${window.location.origin}${window.location.pathname}#/op/projector?taskId=${id}`
  const targetScreen = screen || getSavedProjectorScreen()
  const projectorWindow = window.open(url, PROJECTOR_WINDOW_NAME, popupFeatures(targetScreen))
  if (!projectorWindow) return { status: 'blocked', window: null }
  placeProjectorWindow(projectorWindow, targetScreen)
  projectorWindow.focus()
  return { status: targetScreen ? 'positioned' : 'fallback', window: projectorWindow }
}
