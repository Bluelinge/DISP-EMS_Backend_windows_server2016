const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, '')
const TOKEN_KEY = 'disp-ems-access-token'

// HTTP 的局域网地址不是安全上下文，部分 Chromium 版本不会提供
// crypto.randomUUID()。写接口不能因为生成幂等键失败而根本不发请求。
export function newIdempotencyKey() {
  const cryptoApi = globalThis.crypto
  if (typeof cryptoApi?.randomUUID === 'function') return cryptoApi.randomUUID()
  const bytes = new Uint8Array(16)
  if (typeof cryptoApi?.getRandomValues === 'function') cryptoApi.getRandomValues(bytes)
  else for (let index = 0; index < bytes.length; index += 1) bytes[index] = Math.floor(Math.random() * 256)
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = [...bytes].map(value => value.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

// 保持以下各写接口调用形式一致，并把它们全部接到兼容生成器。
const crypto = { randomUUID: newIdempotencyKey }

export class ApiError extends Error {
  constructor(message, { status = 0, code = 1500, data = null } = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.data = data
  }
}

export const apiMode = import.meta.env.VITE_USE_MOCK === 'false'
export const authToken = () => sessionStorage.getItem(TOKEN_KEY) || ''
export function saveAuthToken(token) {
  if (token) sessionStorage.setItem(TOKEN_KEY, token)
  else sessionStorage.removeItem(TOKEN_KEY)
}

export async function endSession() {
  try {
    if (apiMode && authToken()) await request('/auth/logout', { method: 'POST' })
  } finally {
    saveAuthToken('')
  }
}

export async function request(path, { method = 'GET', body, headers = {}, signal } = {}) {
  const token = authToken()
  const isLoginRequest = path === '/auth/login'
  const isForm = body instanceof FormData
  const response = await fetch(API_BASE_URL + path, {
    method,
    signal,
    headers: {
      Accept: 'application/json',
      ...(isForm ? {} : body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token && !isLoginRequest ? { Authorization: 'Bearer ' + token } : {}),
      ...headers,
    },
    body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
  })
  let payload = null
  try { payload = await response.json() } catch {}
  if (!response.ok || !payload || payload.code !== 0) {
    if (response.status === 401) saveAuthToken('')
    const fallbackMessage = response.status === 413
      ? '上传文件超过 2GB 限制，请压缩或拆分后重试'
      : '请求失败，请稍后重试'
    throw new ApiError(payload?.message || fallbackMessage, {
      status: response.status, code: payload?.code, data: payload?.data,
    })
  }
  return payload.data
}

export const api = {
  login(payload) { return request('/auth/login', { method: 'POST', body: payload }) },
  me() { return request('/auth/me') },
  logout() { return request('/auth/logout', { method: 'POST' }) },
  changePassword(payload) { return request('/auth/change-password', { method: 'POST', body: payload, headers: { 'Idempotency-Key': crypto.randomUUID() } }) },
  workstations() { return request('/workstations/options') },
  createWorkstation(payload) { return request('/workstations', { method: 'POST', body: payload, headers: { 'Idempotency-Key': crypto.randomUUID() } }) },
  updateWorkstation(id, payload) { return request('/workstations/' + encodeURIComponent(id), { method: 'PATCH', body: payload, headers: { 'Idempotency-Key': crypto.randomUUID() } }) },
  deleteWorkstation(id, expectedVersion) { return request('/workstations/' + encodeURIComponent(id) + '?expectedVersion=' + encodeURIComponent(expectedVersion), { method: 'DELETE', headers: { 'Idempotency-Key': crypto.randomUUID() } }) },
  uploadFile(file, fileKind, signal) {
    const body = new FormData()
    body.append('file', file)
    body.append('fileKind', String(fileKind))
    return request('/files', { method: 'POST', body, signal })
  },
  categories() { return request('/video-categories/tree') },
  videos(params = {}) {
    const query = new URLSearchParams(Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''))
    return request('/videos' + (query.size ? '?' + query : ''))
  },
  resolveVideo(name) { return request('/videos/resolve?name=' + encodeURIComponent(name)) },
  createVideos(payload) {
    return request('/videos', { method: 'POST', body: payload, headers: { 'Idempotency-Key': crypto.randomUUID() } })
  },
  playback(id) { return request('/videos/' + id + '/playback') },
  deleteVideo(id, expectedVersion) { return request('/videos/' + encodeURIComponent(id) + '?expectedVersion=' + encodeURIComponent(expectedVersion), { method: 'DELETE', headers: { 'Idempotency-Key': crypto.randomUUID() } }) },
  icons() { return request('/projector/icons') },
  createIcon(payload) { return request('/projector/icons', { method: 'POST', body: payload, headers: { 'Idempotency-Key': crypto.randomUUID() } }) },
  replaceIcon(id, payload) { return request('/projector/icons/' + encodeURIComponent(id), { method: 'PUT', body: payload, headers: { 'Idempotency-Key': crypto.randomUUID() } }) },
  deleteIcon(id, expectedVersion) { return request('/projector/icons/' + encodeURIComponent(id) + '?expectedVersion=' + encodeURIComponent(expectedVersion), { method: 'DELETE', headers: { 'Idempotency-Key': crypto.randomUUID() } }) },
  templates() { return request('/projector/templates') },
  createTemplate(payload) { return request('/projector/templates', { method: 'POST', body: payload, headers: { 'Idempotency-Key': crypto.randomUUID() } }) },
  updateTemplate(id, payload) { return request('/projector/templates/' + encodeURIComponent(id), { method: 'PUT', body: payload, headers: { 'Idempotency-Key': crypto.randomUUID() } }) },
  deleteTemplate(id, expectedVersion) { return request('/projector/templates/' + encodeURIComponent(id) + '?expectedVersion=' + encodeURIComponent(expectedVersion), { method: 'DELETE', headers: { 'Idempotency-Key': crypto.randomUUID() } }) },
  users(params = {}) {
    const query = new URLSearchParams(Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''))
    return request('/users' + (query.size ? '?' + query : ''))
  },
  createUser(payload) { return request('/users', { method: 'POST', body: payload, headers: { 'Idempotency-Key': crypto.randomUUID() } }) },
  updateUser(id, payload) { return request('/users/' + id, { method: 'PATCH', body: payload, headers: { 'Idempotency-Key': crypto.randomUUID() } }) },
  resetUserPassword(id, payload) { return request('/users/' + id + '/reset-password', { method: 'POST', body: payload, headers: { 'Idempotency-Key': crypto.randomUUID() } }) },
  deleteUser(id, expectedVersion) { return request('/users/' + id + '?expectedVersion=' + encodeURIComponent(expectedVersion), { method: 'DELETE', headers: { 'Idempotency-Key': crypto.randomUUID() } }) },
  createTask(payload) { return request('/tasks', { method: 'POST', body: payload, headers: { 'Idempotency-Key': crypto.randomUUID() } }) },
  taskBinding(productCode) { return request('/tasks/binding?productCode=' + encodeURIComponent(productCode)) },
  task(id) { return request('/tasks/' + encodeURIComponent(id)) },
  taskModels() { return request('/tasks/models') },
  tasks(params = {}) {
    const query = new URLSearchParams(Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''))
    return request('/tasks' + (query.size ? '?' + query : ''))
  },
  activeTasks() { return request('/tasks/active') },
  saveTaskStep(id, stepNo, values) { return request('/tasks/' + encodeURIComponent(id) + '/steps/' + encodeURIComponent(stepNo), { method: 'PUT', body: { values }, headers: { 'Idempotency-Key': crypto.randomUUID() } }) },
  confirmTaskStation(id, stationId, items) { return request('/tasks/' + encodeURIComponent(id) + '/stations/' + encodeURIComponent(stationId) + '/confirm', { method: 'POST', body: { items }, headers: { 'Idempotency-Key': crypto.randomUUID() } }) },
  updateTaskSteelNo(id, steelNo) { return request('/tasks/' + encodeURIComponent(id) + '/steel-no', { method: 'PUT', body: { steelNo }, headers: { 'Idempotency-Key': crypto.randomUUID() } }) },
  completeTask(id, items = []) { return request('/tasks/' + encodeURIComponent(id) + '/complete', { method: 'POST', body: { items }, headers: { 'Idempotency-Key': crypto.randomUUID() } }) },
  productionStatistics(params = {}) {
    const query = new URLSearchParams(Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''))
    return request('/statistics/production' + (query.size ? '?' + query : ''))
  },
  processes() { return request('/processes') },
  createProcess(payload) { return request('/processes', { method: 'POST', body: payload, headers: { 'Idempotency-Key': crypto.randomUUID() } }) },
  deleteProcess(id, expectedVersion) { return request('/processes/' + encodeURIComponent(id) + '?expectedVersion=' + encodeURIComponent(expectedVersion), { method: 'DELETE', headers: { 'Idempotency-Key': crypto.randomUUID() } }) },
  updateProcessStep(id, stepNo, payload) { return request('/processes/' + encodeURIComponent(id) + '/steps/' + encodeURIComponent(stepNo), { method: 'PATCH', body: payload, headers: { 'Idempotency-Key': crypto.randomUUID() } }) },
  updateProcessProjection(id, stepNo, payload) { return request('/processes/' + encodeURIComponent(id) + '/projections/' + encodeURIComponent(stepNo), { method: 'PATCH', body: payload, headers: { 'Idempotency-Key': crypto.randomUUID() } }) },
}

export async function openTaskEvents(taskId, onEvent, { signal } = {}) {
  const token = authToken()
  const response = await fetch(API_BASE_URL + '/tasks/' + taskId + '/events', {
    headers: { Accept: 'text/event-stream', Authorization: 'Bearer ' + token }, signal,
  })
  if (!response.ok || !response.body) throw new ApiError('无法建立任务事件连接', { status: response.status })
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let pending = ''
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    pending += decoder.decode(value, { stream: true })
    const blocks = pending.split(/\n\n/)
    pending = blocks.pop() || ''
    blocks.forEach(block => {
      const type = block.match(/^event:\s*(.+)$/m)?.[1]
      const data = block.match(/^data:\s*(.+)$/m)?.[1]
      if (type && data) {
        try { onEvent(type, JSON.parse(data)) } catch {}
      }
    })
  }
}
