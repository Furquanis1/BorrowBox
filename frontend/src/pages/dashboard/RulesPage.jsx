import React, { useCallback, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useCommunity } from '../../contexts/CommunityContext'
import { useApp } from '../../contexts/AppContext'
import { useAsync } from '../../hooks/useAsync'
import { ruleService } from '../../services'
import EmptyState from '../../components/ui/EmptyState'
import Spinner from '../../components/ui/Spinner'
import Button from '../../components/ui/Button'

const RULE_TYPES = [
  'MEMBERSHIP_CONTEXT_FIELDS',
  'MAX_ACTIVE_MEMBERS',
  'ADMISSION_NOTE',
  'OVERDUE_GRACE_PERIOD',
]

const DEFAULT_VALUE_PLACEHOLDER = {
  OVERDUE_GRACE_PERIOD: '{ "days": 3 }',
  MAX_ACTIVE_MEMBERS: '{ "max": 50 }',
  ADMISSION_NOTE: '{ "note": "Welcome to the community" }',
  MEMBERSHIP_CONTEXT_FIELDS: '{ "fields": ["program", "year"] }',
}

function ruleSummary(rule) {
  if (!rule.value) return ''
  if (rule.ruleType === 'OVERDUE_GRACE_PERIOD' && rule.value.days != null) {
    return `${rule.value.days} day grace`
  }
  if (rule.ruleType === 'MAX_ACTIVE_MEMBERS' && rule.value.max != null) {
    return `max ${rule.value.max} members`
  }
  if (rule.ruleType === 'ADMISSION_NOTE' && rule.value.note) {
    return String(rule.value.note)
  }
  return JSON.stringify(rule.value)
}

export default function RulesPage() {
  const { communityId } = useParams()
  const { isManager } = useCommunity()
  const { showToast } = useApp()

  const manager = isManager(communityId)
  const [creating, setCreating] = useState(false)
  const [createType, setCreateType] = useState('OVERDUE_GRACE_PERIOD')
  const [createValue, setCreateValue] = useState('')
  const [saving, setSaving] = useState(false)
  const [actingRuleId, setActingRuleId] = useState(null)

  const fetchRules = useCallback(() => {
    if (!communityId) return Promise.resolve([])
    return manager
      ? ruleService.listRules(communityId)
      : ruleService.listActiveRules(communityId)
  }, [communityId, manager])

  const { data: rules, loading, error, reload } = useAsync(fetchRules, [communityId, manager])

  const openCreate = () => {
    setCreateValue(DEFAULT_VALUE_PLACEHOLDER[createType] || '')
    setCreating((value) => !value)
  }

  const changeCreateType = (event) => {
    const next = event.target.value
    setCreateType(next)
    setCreateValue(DEFAULT_VALUE_PLACEHOLDER[next] || '')
  }

  const handleCreate = async (event) => {
    event.preventDefault()
    let parsedValue
    try {
      parsedValue = createValue.trim() ? JSON.parse(createValue) : {}
    } catch (err) {
      showToast('The rule value must be valid JSON.', 'error')
      return
    }
    setSaving(true)
    try {
      await ruleService.createRule(communityId, createType, parsedValue)
      setCreating(false)
      setCreateValue('')
      showToast('Rule created and activated.')
      await reload()
    } catch (err) {
      showToast(err?.message || 'Failed to create the rule', 'error')
    } finally {
      setSaving(false)
    }
  }

  const toggleRule = async (rule) => {
    setActingRuleId(rule.id)
    try {
      if (rule.status === 'ACTIVE') {
        await ruleService.deactivateRule(communityId, rule.id)
        showToast('Rule deactivated.')
      } else {
        await ruleService.activateRule(communityId, rule.id)
        showToast('Rule activated.')
      }
      await reload()
    } catch (err) {
      showToast(err?.message || 'Failed to update the rule', 'error')
    } finally {
      setActingRuleId(null)
    }
  }

  return (
    <div className="rules-page">
      <section aria-labelledby="rules-title">
        <h2 id="rules-title" className="manager-section-title">
          Community rules
        </h2>

        {manager && (
          <div className="flag-toolbar">
            <Button variant={creating ? 'outline' : 'primary'} onClick={openCreate}>
              <i className="bi bi-plus-lg" aria-hidden="true" />
              {creating ? 'Cancel' : 'Create rule'}
            </Button>
          </div>
        )}

        {creating && (
          <form className="flag-create-form" onSubmit={handleCreate} aria-label="Create a rule">
            <div className="form-group">
              <label htmlFor="create-rule-type">Rule type</label>
              <select
                id="create-rule-type"
                className="select"
                value={createType}
                onChange={changeCreateType}
              >
                {RULE_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label htmlFor="create-rule-value">Value (JSON)</label>
              <textarea
                id="create-rule-value"
                className="textarea"
                value={createValue}
                onChange={(event) => setCreateValue(event.target.value)}
                placeholder={DEFAULT_VALUE_PLACEHOLDER[createType]}
              />
              <p className="form-hint">
                The most recently created rule of a type becomes the single active rule of that
                type; the previous one is archived automatically.
              </p>
            </div>
            <Button type="submit" loading={saving}>
              Create rule
            </Button>
          </form>
        )}

        {loading ? (
          <Spinner />
        ) : error ? (
          <EmptyState
            icon="bi-exclamation-triangle"
            title="Could not load rules"
            description={error.message}
            action={
              <Button variant="outline" onClick={reload}>
                <i className="bi bi-arrow-clockwise" aria-hidden="true" />
                Try again
              </Button>
            }
          />
        ) : rules.length === 0 ? (
          <EmptyState
            icon="bi-shield-check"
            title="No active rules"
            description={
              manager
                ? 'Create a rule to set admission, membership, or grace-period policy.'
                : 'Your community managers have not set any rules yet.'
            }
          />
        ) : (
          <ul className="rule-list">
            {rules.map((rule) => (
              <li key={rule.id} className="rule-item">
                <div className="rule-item-main">
                  <span className={`badge ${rule.status === 'ACTIVE' ? 'badge-teal' : 'badge-neutral'}`}>
                    {rule.status}
                  </span>
                  <span className="rule-item-type">{rule.ruleType}</span>
                  <span className="rule-item-summary">{ruleSummary(rule)}</span>
                </div>
                {manager && (
                  <div className="flag-item-actions">
                    <Button
                      variant={rule.status === 'ACTIVE' ? 'outline' : 'primary'}
                      size="sm"
                      disabled={actingRuleId === rule.id}
                      onClick={() => toggleRule(rule)}
                    >
                      {rule.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                    </Button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}