import { isRangeValueValid } from './parameterRange'

export function isBlankParameterValue(value) {
  return value === null || value === undefined || String(value).trim() === ''
}

export function firstOptionValue(parameter) {
  const options = Array.isArray(parameter?.options)
    ? parameter.options
    : String(parameter?.options || parameter?.referenceValue || '').split(/\r?\n/)
  return options.map(option => String(option).trim()).find(Boolean) || ''
}

export function isParameterValueInvalid(parameter, value) {
  if (isBlankParameterValue(value)) return false

  const type = Number(parameter?.type)
  if (type === 1) return String(value).trim() !== firstOptionValue(parameter)
  if (type === 2) return !isRangeValueValid(value, parameter?.referenceValue)
  return false
}
