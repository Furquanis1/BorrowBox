import React from 'react'
import Input from '../ui/Input'

export const CONTEXT_FIELD_OPTIONS = [
  { value: 'program', label: 'Program' },
  { value: 'year', label: 'Year' },
  { value: 'section', label: 'Section' },
  { value: 'block', label: 'Block' },
  { value: 'floor', label: 'Floor' },
  { value: 'room', label: 'Room' },
  { value: 'college', label: 'College' },
  { value: 'department', label: 'Department' },
  { value: 'team', label: 'Team' },
  { value: 'designation', label: 'Designation' },
  { value: 'tower', label: 'Tower' },
  { value: 'flat', label: 'Flat' },
  { value: 'resident_type', label: 'Resident type' },
]

export const RULE_TYPES = [
  'MEMBERSHIP_CONTEXT_FIELDS',
  'MAX_ACTIVE_MEMBERS',
  'ADMISSION_NOTE',
  'OVERDUE_GRACE_PERIOD',
]

const EMPTY_VALUE = {
  OVERDUE_GRACE_PERIOD: { days: '' },
  MAX_ACTIVE_MEMBERS: { max: '' },
  ADMISSION_NOTE: { note: '' },
  MEMBERSHIP_CONTEXT_FIELDS: { fields: [] },
}

function initialValue(ruleType) {
  return { ...EMPTY_VALUE[ruleType] }
}

export function ruleSummary(rule) {
  if (!rule.value) return ''
  if (rule.ruleType === 'OVERDUE_GRACE_PERIOD' && rule.value.days != null) {
    return `${rule.value.days} day grace`
  }
  if (rule.ruleType === 'MAX_ACTIVE_MEMBERS' && rule.value.max != null) {
    return `max ${rule.value.max} members`
  }
  if (rule.ruleType === 'ADMISSION_NOTE') {
    const note = rule.value.note ?? rule.value.text
    if (note) return String(note)
  }
  if (rule.ruleType === 'MEMBERSHIP_CONTEXT_FIELDS') {
    const fields = Array.isArray(rule.value.fields)
      ? rule.value.fields
      : Array.isArray(rule.value.required)
        ? rule.value.required
        : []
    if (fields.length) {
      return fields
        .map((field) => {
          const option = CONTEXT_FIELD_OPTIONS.find((opt) => opt.value === field)
          return option ? option.label : field
        })
        .join(', ')
    }
  }
  return JSON.stringify(rule.value)
}

function labelsFor(ruleType) {
  switch (ruleType) {
    case 'ADMISSION_NOTE':
      return {
        label: 'Admission note',
        hint: 'A short message that accompanies this community\u2019s admission policy.',
      }
    case 'MAX_ACTIVE_MEMBERS':
      return {
        label: 'Max active members',
        hint: 'The community\u2019s intended cap on active members. It is recorded now and not enforced yet.',
      }
    case 'OVERDUE_GRACE_PERIOD':
      return {
        label: 'Grace period (days)',
        hint: 'Days a loan may run past its due date before it is considered overdue.',
      }
    case 'MEMBERSHIP_CONTEXT_FIELDS':
      return {
        label: 'Membership context fields',
        hint: 'The member attributes this community wants recorded with each membership.',
      }
    default:
      return {}
  }
}

function validate(ruleType, value) {
  switch (ruleType) {
    case 'ADMISSION_NOTE':
      if (!String(value.note ?? '').trim()) return 'Admission note is required.'
      break
    case 'MAX_ACTIVE_MEMBERS':
      if (value.max === '' || value.max == null) return 'Max active members is required.'
      if (!Number.isInteger(Number(value.max)) || Number(value.max) < 1) {
        return 'Max active members must be a whole number of at least 1.'
      }
      break
    case 'OVERDUE_GRACE_PERIOD':
      if (value.days === '' || value.days == null) return 'Grace period is required.'
      if (!Number.isInteger(Number(value.days)) || Number(value.days) < 0 || Number(value.days) > 30) {
        return 'Grace period must be a whole number of days between 0 and 30.'
      }
      break
    case 'MEMBERSHIP_CONTEXT_FIELDS':
      if (!Array.isArray(value.fields) || value.fields.length === 0) {
        return 'Select at least one membership context field.'
      }
      break
    default:
  }
  return ''
}

function payload(ruleType, value) {
  switch (ruleType) {
    case 'ADMISSION_NOTE':
      return { note: String(value.note ?? '').trim() }
    case 'MAX_ACTIVE_MEMBERS':
      return { max: Number(value.max) }
    case 'OVERDUE_GRACE_PERIOD':
      return { days: Number(value.days) }
    case 'MEMBERSHIP_CONTEXT_FIELDS':
      return { fields: Array.isArray(value.fields) ? [...value.fields] : [] }
    default:
      return {}
  }
}

export default function RuleValueEditor({ ruleType, value, onChange }) {
  const labels = labelsFor(ruleType)

  const setNote = (event) => onChange({ ...value, note: event.target.value })

  const setNumber = (key) => (event) => onChange({ ...value, [key]: event.target.value })

  const toggleField = (field) => {
    const fields = Array.isArray(value.fields) ? value.fields : []
    const next = fields.includes(field) ? fields.filter((f) => f !== field) : [...fields, field]
    return () => onChange({ ...value, fields: next })
  }

  if (ruleType === 'MEMBERSHIP_CONTEXT_FIELDS') {
    return (
      <div className="form-group">
        <label>{labels.label}</label>
        <div className="context-field-options">
          {CONTEXT_FIELD_OPTIONS.map((option) => {
            const checked = Array.isArray(value.fields) && value.fields.includes(option.value)
            return (
              <label key={option.value} className="context-field-option">
                <input
                  type="checkbox"
                  id={`create-context-field-${option.value}`}
                  checked={checked}
                  onChange={toggleField(option.value)}
                />
                <span>{option.label}</span>
              </label>
            )
          })}
        </div>
        <p className="form-hint">{labels.hint}</p>
      </div>
    )
  }

  if (ruleType === 'ADMISSION_NOTE') {
    return (
      <div className="form-group">
        <label htmlFor="create-admission-note">{labels.label}</label>
        <textarea
          id="create-admission-note"
          className="textarea"
          value={value.note ?? ''}
          onChange={setNote}
          placeholder="e.g. Be respectful when borrowing."
        />
        <p className="form-hint">{labels.hint}</p>
      </div>
    )
  }

  const isMax = ruleType === 'MAX_ACTIVE_MEMBERS'
  const key = isMax ? 'max' : 'days'
  return (
    <Input
      id={isMax ? 'create-max-members' : 'create-grace-days'}
      label={labels.label}
      type="number"
      min={isMax ? 1 : 0}
      max={isMax ? undefined : 30}
      step={1}
      value={value[key] ?? ''}
      onChange={setNumber(key)}
      hint={labels.hint}
    />
  )
}

export { initialValue as defaultRuleValue, payload as ruleValuePayload, validate as validateRuleValue }