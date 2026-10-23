const DECIMAL_SOURCE = '[+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)'
const DECIMAL_PATTERN = new RegExp(`^${DECIMAL_SOURCE}$`)
const FORMAT_ERROR = '请输入四段逗号格式，例如 (>30),(=30),(=70),(<70)，空边界填写 ()'

const trim = value => String(value ?? '').trim()

function decimalText(value) {
  const text = trim(value)
  if (!DECIMAL_PATTERN.test(text)) return null
  const negative = text.startsWith('-')
  const [integerPart, fractionPart = ''] = text.replace(/^[+-]/, '').split('.')
  const integer = integerPart.replace(/^0+/, '') || '0'
  const fraction = fractionPart.replace(/0+$/, '')
  return `${negative && (integer !== '0' || fraction) ? '-' : ''}${integer}${fraction ? `.${fraction}` : ''}`
}

function compareDecimals(left, right) {
  const leftNegative = left.startsWith('-')
  const rightNegative = right.startsWith('-')
  if (leftNegative !== rightNegative) return leftNegative ? -1 : 1
  const [leftInteger, leftFraction = ''] = left.replace(/^-/, '').split('.')
  const [rightInteger, rightFraction = ''] = right.replace(/^-/, '').split('.')
  const fractionLength = Math.max(leftFraction.length, rightFraction.length)
  const leftDigits = leftInteger + leftFraction.padEnd(fractionLength, '0')
  const rightDigits = rightInteger + rightFraction.padEnd(fractionLength, '0')
  const comparison = leftInteger.length !== rightInteger.length
    ? Math.sign(leftInteger.length - rightInteger.length)
    : leftDigits === rightDigits ? 0 : leftDigits < rightDigits ? -1 : 1
  return leftNegative ? -comparison : comparison
}

function checkedRange(lower, upper, legacy = false) {
  if (!lower && !upper) return { error: '至少填写一个下限 (>x) 或上限 (<y)' }
  if (lower && upper) {
    const comparison = compareDecimals(lower.text, upper.text)
    if (comparison > 0) return { error: '下限不能大于上限' }
    if (comparison === 0 && (!lower.inclusive || !upper.inclusive)) {
      return { error: '上下限相等时必须同时包含两端，否则区间为空' }
    }
  }
  return {
    range: {
      lower,
      upper,
      legacy,
      normalized: [
        lower ? `(>${lower.text})` : '()',
        lower?.inclusive ? `(=${lower.text})` : '()',
        upper?.inclusive ? `(=${upper.text})` : '()',
        upper ? `(<${upper.text})` : '()',
      ].join(','),
    },
  }
}

function inspectReference(value) {
  const text = trim(value)
  if (!text) return { error: `范围值不能为空；${FORMAT_ERROR}` }
  if (/[\r\n]/.test(text)) {
    const lines = text.split(/\r?\n/).map(decimalText)
    if (lines.length !== 2 || lines.some(line => line === null)) {
      return { error: `旧格式须为两行数值（第一行下限、第二行上限）；${FORMAT_ERROR}` }
    }
    return checkedRange({ text: lines[0], inclusive: true }, { text: lines[1], inclusive: true }, true)
  }
  const parts = text.replace(/\s/g, '').split(',')
  if (parts.length !== 4) return { error: FORMAT_ERROR }
  const operators = ['>', '=', '=', '<']
  const bounds = []
  for (const [index, part] of parts.entries()) {
    if (part === '()') {
      bounds.push(null)
      continue
    }
    const match = new RegExp(`^\\(${operators[index]}(${DECIMAL_SOURCE})\\)$`).exec(part)
    if (!match) return { error: `第 ${index + 1} 段应填写 (${operators[index]}数值) 或 ()，数值仅支持整数、负数和小数` }
    bounds.push(decimalText(match[1]))
  }
  const [lowerExclusive, lowerInclusive, upperInclusive, upperExclusive] = bounds
  if (lowerInclusive !== null && lowerExclusive === null) return { error: '下限包含段 (=x) 必须配合同值的下限排除段 (>x)' }
  if (lowerInclusive !== null && lowerInclusive !== lowerExclusive) return { error: '下限两段数值必须相同' }
  if (upperInclusive !== null && upperExclusive === null) return { error: '上限包含段 (=y) 必须配合同值的上限排除段 (<y)' }
  if (upperInclusive !== null && upperInclusive !== upperExclusive) return { error: '上限两段数值必须相同' }
  return checkedRange(
    lowerExclusive === null ? null : { text: lowerExclusive, inclusive: lowerInclusive !== null },
    upperExclusive === null ? null : { text: upperExclusive, inclusive: upperInclusive !== null },
  )
}

export function rangeReferenceError(value) {
  return inspectReference(value).error || ''
}

export function parseRangeReference(value) {
  return inspectReference(value).range || null
}

export function normalizeRangeReference(value) {
  return parseRangeReference(value)?.normalized || null
}

export function formatRangeReference(value) {
  const parsed = typeof value === 'string' ? parseRangeReference(value) : value
  if (!parsed) return `范围：${trim(value) || '—'}`
  const lower = parsed.lower ? `${parsed.lower.inclusive ? '≥' : '>'}${parsed.lower.text}` : null
  const upper = parsed.upper ? `${parsed.upper.inclusive ? '≤' : '<'}${parsed.upper.text}` : null
  return `范围：${[lower, upper].filter(Boolean).join(' 且 ')}`
}

export function isRangeValueValid(actual, value) {
  const parsed = typeof value === 'string' ? parseRangeReference(value) : value
  const text = decimalText(actual)
  if (!parsed || text === null) return false
  if (parsed.lower) {
    const comparison = compareDecimals(text, parsed.lower.text)
    if (comparison < 0 || (comparison === 0 && !parsed.lower.inclusive)) return false
  }
  if (parsed.upper) {
    const comparison = compareDecimals(text, parsed.upper.text)
    if (comparison > 0 || (comparison === 0 && !parsed.upper.inclusive)) return false
  }
  return true
}
