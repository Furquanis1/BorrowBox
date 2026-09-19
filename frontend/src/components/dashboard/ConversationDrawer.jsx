import React, { useCallback, useEffect, useRef, useState } from 'react'
import Drawer from '../ui/Drawer'
import Button from '../ui/Button'
import Spinner from '../ui/Spinner'
import EventTimeline from './EventTimeline'
import { useAuth } from '../../contexts/AuthContext'
import { eventService, requestService } from '../../services'

const TERMINAL_STATES = new Set(['COMPLETED', 'REJECTED', 'CANCELLED', 'HANDOVER_DISPUTED', 'RETURN_DISPUTED'])
const WRITABLE_STATES = new Set(['APPROVED', 'AWAITING_HANDOVER', 'ACTIVE', 'RETURN_INITIATED', 'RETURN_REPORTED'])
const EVIDENCE_STATES = new Set(['RETURN_INITIATED', 'RETURN_REPORTED', 'RETURN_DISPUTED'])
const EVIDENCE_LABELS = {
  BORROWER_PRE_RETURN: 'Before return',
  BORROWER_RETURN_HANDOVER: 'At handover',
}

function formatTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  const hours = d.getHours().toString().padStart(2, '0')
  const mins = d.getMinutes().toString().padStart(2, '0')
  return `${hours}:${mins}`
}

function formatDate(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

export default function ConversationDrawer({ open, onClose, transaction, onDataChanged }) {
  const [messages, setMessages] = useState([])
  const [loading, setLoading] = useState(false)
  const [body, setBody] = useState('')
  const [error, setError] = useState('')
  const [actionError, setActionError] = useState('')
  const [busy, setBusy] = useState(false)
  const [actionBusy, setActionBusy] = useState(false)
  const [currentTxn, setCurrentTxn] = useState(transaction)
  const [extensionDate, setExtensionDate] = useState('')
  const [extensionNote, setExtensionNote] = useState('')
  const [counterDate, setCounterDate] = useState('')
  const [counterNote, setCounterNote] = useState('')
  const [counterOpen, setCounterOpen] = useState(false)
  const [requestOpen, setRequestOpen] = useState(false)
  const [evidence, setEvidence] = useState([])
  const [evidenceLoading, setEvidenceLoading] = useState(false)
  const [uploadingType, setUploadingType] = useState(null)
  const [showTimeline, setShowTimeline] = useState(false)
  const [timeline, setTimeline] = useState([])
  const [timelineLoading, setTimelineLoading] = useState(false)
  const [timelineError, setTimelineError] = useState('')
  const { user } = useAuth()
  const bottomRef = useRef(null)
  const inputRef = useRef(null)
  const preRef = useRef(null)
  const handoverRef = useRef(null)

  useEffect(() => {
    setCurrentTxn(transaction)
    setExtensionDate('')
    setExtensionNote('')
    setCounterDate('')
    setCounterNote('')
    setCounterOpen(false)
    setRequestOpen(false)
    setShowTimeline(false)
    setTimeline([])
    setTimelineError('')
  }, [transaction])

  const active = currentTxn || transaction
  const state = active?.state
  const terminal = TERMINAL_STATES.has(state)
  const writable = WRITABLE_STATES.has(state)
  const isBorrower = active && user?.id === active.borrowerId
  const isLender = active && user?.id === active.lenderId

  const loadMessages = useCallback(() => {
    if (!transaction) return Promise.resolve()
    setLoading(true)
    setError('')
    return requestService
      .getMessages(transaction.id)
      .then((data) => setMessages(data))
      .catch((err) => setError(err?.message || 'Could not load messages'))
      .finally(() => setLoading(false))
  }, [transaction])

  useEffect(() => {
    if (open && transaction) loadMessages()
  }, [open, transaction?.id, loadMessages])

  const loadEvidence = useCallback(() => {
    if (!transaction) return Promise.resolve()
    setEvidenceLoading(true)
    return requestService
      .getEvidence(transaction.id)
      .then(setEvidence)
      .catch(() => setEvidence([]))
      .finally(() => setEvidenceLoading(false))
  }, [transaction])

  useEffect(() => {
    if (open && transaction && EVIDENCE_STATES.has(transaction.state)) loadEvidence()
  }, [open, transaction?.id, transaction?.state, loadEvidence])

  const loadTimeline = useCallback(() => {
    if (!transaction) return Promise.resolve()
    setTimelineLoading(true)
    setTimelineError('')
    return eventService
      .getTimeline(transaction.id)
      .then(setTimeline)
      .catch((err) => setTimelineError(err?.message || 'Could not load the timeline'))
      .finally(() => setTimelineLoading(false))
  }, [transaction])

  useEffect(() => {
    if (open && showTimeline && transaction) loadTimeline()
  }, [open, showTimeline, transaction?.id, currentTxn?.state, loadTimeline])

  useEffect(() => {
    if (!loading && messages.length) {
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
    }
  }, [loading, messages.length])

  useEffect(() => {
    if (open && !terminal) {
      inputRef.current?.focus()
    }
  }, [open, terminal])

  const handleSend = async (event) => {
    event.preventDefault()
    const trimmed = body.trim()
    if (!trimmed) return
    setBusy(true)
    setError('')
    try {
      const sent = await requestService.sendMessage(transaction.id, trimmed)
      setMessages((prev) => [...prev, sent])
      setBody('')
    } catch (err) {
      setError(err?.message || 'Could not send the message')
    } finally {
      setBusy(false)
      inputRef.current?.focus()
    }
  }

  const handleKeyDown = (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      handleSend(event)
    }
  }

  const handleMarkReturned = async () => {
    setActionBusy(true)
    setActionError('')
    try {
      const updated = await requestService.initiateReturn(active.id)
      setCurrentTxn(updated)
      await loadMessages()
      onDataChanged?.()
    } catch (err) {
      setActionError(err?.message || 'Could not report the return')
    } finally {
      setActionBusy(false)
      inputRef.current?.focus()
    }
  }

  const handleConfirmReceipt = async () => {
    setActionBusy(true)
    setActionError('')
    try {
      const updated = await requestService.confirmReturn(active.id)
      setCurrentTxn(updated)
      await loadMessages()
      onDataChanged?.()
    } catch (err) {
      setActionError(err?.message || 'Could not confirm the receipt')
    } finally {
      setActionBusy(false)
      inputRef.current?.focus()
    }
  }

  const handleReportHandback = async () => {
    setActionBusy(true)
    setActionError('')
    try {
      const updated = await requestService.reportHandback(active.id)
      setCurrentTxn(updated)
      await loadMessages()
      onDataChanged?.()
    } catch (err) {
      setActionError(err?.message || 'Could not report the handback')
    } finally {
      setActionBusy(false)
      inputRef.current?.focus()
    }
  }

  const handleConfirmHandoverReceipt = async () => {
    setActionBusy(true)
    setActionError('')
    try {
      const updated = await requestService.confirmReceipt(active.id)
      setCurrentTxn(updated)
      await loadMessages()
      onDataChanged?.()
    } catch (err) {
      setActionError(err?.message || 'Could not confirm the receipt')
    } finally {
      setActionBusy(false)
      inputRef.current?.focus()
    }
  }

  const handleDisputeHandover = async () => {
    setActionBusy(true)
    setActionError('')
    try {
      const updated = await requestService.disputeHandover(active.id)
      setCurrentTxn(updated)
      await loadMessages()
      onDataChanged?.()
    } catch (err) {
      setActionError(err?.message || 'Could not report the problem')
    } finally {
      setActionBusy(false)
      inputRef.current?.focus()
    }
  }

  const hasEvidenceType = (type) => evidence.some((item) => item.type === type)
  const evidenceReady = hasEvidenceType('BORROWER_PRE_RETURN') && hasEvidenceType('BORROWER_RETURN_HANDOVER')

  const handleUploadEvidence = async (type, file) => {
    if (!file) return
    setUploadingType(type)
    setActionError('')
    try {
      await requestService.uploadEvidence(transaction.id, type, file)
      await loadEvidence()
    } catch (err) {
      setActionError(err?.message || 'Could not upload the photo')
    } finally {
      setUploadingType(null)
    }
  }

  const handleDisputeReturn = async () => {
    setActionBusy(true)
    setActionError('')
    try {
      const updated = await requestService.disputeReturn(active.id)
      setCurrentTxn(updated)
      await loadMessages()
      onDataChanged?.()
    } catch (err) {
      setActionError(err?.message || 'Could not report the problem')
    } finally {
      setActionBusy(false)
      inputRef.current?.focus()
    }
  }

  const handleRequestExtension = async () => {
    setActionBusy(true)
    setActionError('')
    try {
      const updated = await requestService.requestExtension(active.id, {
        newDueAt: extensionDate,
        note: extensionNote || null,
      })
      setCurrentTxn(updated)
      await loadMessages()
      onDataChanged?.()
    } catch (err) {
      setActionError(err?.message || 'Could not request the extension')
    } finally {
      setActionBusy(false)
    }
  }

  const handleAcceptExtension = async () => {
    setActionBusy(true)
    setActionError('')
    try {
      const updated = await requestService.acceptExtension(active.id)
      setCurrentTxn(updated)
      setCounterOpen(false)
      setCounterDate('')
      setExtensionDate('')
      setExtensionNote('')
      await loadMessages()
      onDataChanged?.()
    } catch (err) {
      setActionError(err?.message || 'Could not accept the extension')
    } finally {
      setActionBusy(false)
    }
  }

  const handleRejectExtension = async () => {
    setActionBusy(true)
    setActionError('')
    try {
      const updated = await requestService.rejectExtension(active.id)
      setCurrentTxn(updated)
      setCounterOpen(false)
      await loadMessages()
      onDataChanged?.()
    } catch (err) {
      setActionError(err?.message || 'Could not reject the extension')
    } finally {
      setActionBusy(false)
    }
  }

  const handleCounterExtension = async () => {
    setActionBusy(true)
    setActionError('')
    try {
      const updated = await requestService.counterExtension(active.id, {
        newDueAt: counterDate,
        note: counterNote || null,
      })
      setCurrentTxn(updated)
      setCounterOpen(false)
      setCounterDate('')
      setCounterNote('')
      await loadMessages()
      onDataChanged?.()
    } catch (err) {
      setActionError(err?.message || 'Could not send the counter offer')
    } finally {
      setActionBusy(false)
    }
  }

  const handleAcceptExtensionCounter = async () => {
    setActionBusy(true)
    setActionError('')
    try {
      const updated = await requestService.acceptExtensionCounter(active.id)
      setCurrentTxn(updated)
      await loadMessages()
      onDataChanged?.()
    } catch (err) {
      setActionError(err?.message || 'Could not accept the counter offer')
    } finally {
      setActionBusy(false)
    }
  }

  const handleRejectExtensionCounter = async () => {
    setActionBusy(true)
    setActionError('')
    try {
      const updated = await requestService.rejectExtensionCounter(active.id)
      setCurrentTxn(updated)
      await loadMessages()
      onDataChanged?.()
    } catch (err) {
      setActionError(err?.message || 'Could not decline the counter offer')
    } finally {
      setActionBusy(false)
    }
  }

  if (!transaction) return null

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title={`Conversation \u2014 ${transaction.title || 'Transaction'}`}
      className="request-drawer conversation-drawer"
    >
      {loading ? (
        <div className="conversation-status">
          <Spinner />
        </div>
      ) : error && !messages.length ? (
        <p className="field-error" role="alert">{error}</p>
      ) : (
        <div className="conversation-body">
          {!messages.length && (
            <p className="conversation-empty">
              No messages yet. Use this conversation to coordinate pickup with the other party.
            </p>
          )}

          {messages.map((msg) => (
            <div
              key={msg.id}
              className={`conversation-msg conversation-msg--${msg.kind === 'SYSTEM' ? 'system' : 'user'}`}
            >
              {msg.kind === 'SYSTEM' ? (
                <div className="conversation-system-msg">
                  <span className="conversation-system-body">{msg.body}</span>
                  {msg.createdAt && (
                    <span className="conversation-time">{formatTime(msg.createdAt)}</span>
                  )}
                </div>
              ) : (
                <div className={`conversation-bubble${msg.authorId === user?.id ? ' conversation-bubble--own' : ''}`}>
                  {msg.authorId !== user?.id && (
                    <span className="conversation-author">{msg.authorName}</span>
                  )}
                  <p className="conversation-text">{msg.body}</p>
                  <span className="conversation-time">
                    {msg.createdAt && formatDate(msg.createdAt)} {formatTime(msg.createdAt)}
                  </span>
                </div>
              )}
            </div>
          ))}
          <div ref={bottomRef} />
        </div>
      )}

      <div className="conversation-timeline">
        <button
          type="button"
          className="timeline-toggle"
          aria-expanded={showTimeline}
          onClick={() => setShowTimeline((value) => !value)}
        >
          <i
            className={`bi ${showTimeline ? 'bi-chevron-down' : 'bi-chevron-right'}`}
            aria-hidden="true"
          />
          Activity timeline
        </button>

        {showTimeline &&
          (timelineLoading ? (
            <div className="conversation-status">
              <Spinner />
            </div>
          ) : timelineError ? (
            <p className="field-error" role="alert">{timelineError}</p>
          ) : (
            <EventTimeline events={timeline} currentUserId={user?.id} />
          ))}
      </div>

      {state === 'HANDOVER_DISPUTED' && (
        <div className="conversation-context">
          <p className="conversation-return-status">
            Handover disputed. The item has been returned to available inventory.
          </p>
        </div>
      )}

      {state === 'RETURN_DISPUTED' && (
        <div className="conversation-context">
          <p className="conversation-return-status">
            Return disputed. The loan has been frozen for review and can no longer be modified.
          </p>
        </div>
      )}

      {EVIDENCE_STATES.has(state) && (
        <div className="conversation-evidence">
          <p className="conversation-return-title">Photos</p>
          {evidenceLoading ? (
            <div className="conversation-status">
              <Spinner />
            </div>
          ) : evidence.length ? (
            <div className="conversation-evidence-grid">
              {evidence.map((item) => (
                <figure className="conversation-evidence-item" key={item.id}>
                  <img src={item.contentUrl} alt={EVIDENCE_LABELS[item.type] || item.type} />
                  <figcaption>
                    {EVIDENCE_LABELS[item.type] || item.type} · {item.capturerName}
                  </figcaption>
                </figure>
              ))}
            </div>
          ) : (
            <p className="conversation-return-hint">No photos were captured for this return.</p>
          )}
        </div>
      )}

      {!terminal && (
        <div className="conversation-context">
          {actionError && (
            <p className="field-error" role="alert">{actionError}</p>
          )}

          {state === 'ACTIVE' && isBorrower && active.handoverWindowOpen && !active.borrowerConfirmedAt && (
            <div className="conversation-return-action">
              <p className="conversation-return-title">Confirm the item was received?</p>
              <p className="conversation-return-hint">
                Verify the item was handed over to you. You have 30 minutes from handover to confirm or report a problem.
              </p>
              <div className="conversation-window-actions">
                <Button variant="primary" size="sm" loading={actionBusy} onClick={handleConfirmHandoverReceipt}>
                  <i className="bi bi-check-circle" aria-hidden="true" />
                  Confirm receipt
                </Button>
                <Button variant="outline" size="sm" loading={actionBusy} onClick={handleDisputeHandover}>
                  <i className="bi bi-exclamation-triangle" aria-hidden="true" />
                  Report a problem
                </Button>
              </div>
            </div>
          )}

          {state === 'ACTIVE' && isBorrower && active.borrowerConfirmedAt && (
            <p className="conversation-return-status">
              Receipt confirmed. The loan is active; due date and overdue status are tracked automatically.
            </p>
          )}

          {state === 'ACTIVE' && isBorrower && !active.extensionRequestPending && !active.extensionCounterPending && (
            <div className="conversation-return-action">
              <p className="conversation-return-title">Ready to return the item?</p>
              <p className="conversation-return-hint">Start the return process and coordinate the handback here.</p>
              <Button variant="primary" size="sm" loading={actionBusy} onClick={handleMarkReturned}>
                <i className="bi bi-arrow-90deg-left" aria-hidden="true" />
                Start return
              </Button>
            </div>
          )}

          {state === 'RETURN_INITIATED' && isBorrower && (
            <div className="conversation-return-action">
              <p className="conversation-return-title">Return in progress</p>
              <p className="conversation-return-hint">
                Add a photo of the item before returning it and one at the handover, then tell the lender the item has been handed back.
              </p>
              <div className="conversation-window-actions">
                <Button
                  variant="outline"
                  size="sm"
                  loading={uploadingType === 'BORROWER_PRE_RETURN'}
                  disabled={uploadingType !== null}
                  onClick={() => preRef.current?.click()}
                >
                  <i className="bi bi-camera" aria-hidden="true" />
                  {hasEvidenceType('BORROWER_PRE_RETURN') ? 'Before-return photo added' : 'Add before-return photo'}
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  loading={uploadingType === 'BORROWER_RETURN_HANDOVER'}
                  disabled={uploadingType !== null}
                  onClick={() => handoverRef.current?.click()}
                >
                  <i className="bi bi-camera" aria-hidden="true" />
                  {hasEvidenceType('BORROWER_RETURN_HANDOVER') ? 'Handover photo added' : 'Add handover photo'}
                </Button>
              </div>
              <input
                ref={preRef}
                type="file"
                accept="image/*"
                hidden
                onChange={(event) => {
                  handleUploadEvidence('BORROWER_PRE_RETURN', event.target.files[0])
                  event.target.value = ''
                }}
              />
              <input
                ref={handoverRef}
                type="file"
                accept="image/*"
                hidden
                onChange={(event) => {
                  handleUploadEvidence('BORROWER_RETURN_HANDOVER', event.target.files[0])
                  event.target.value = ''
                }}
              />
              {!evidenceReady && (
                <p className="conversation-return-hint">
                  Both photos are required before you can report the handback.
                </p>
              )}
              <Button variant="primary" size="sm" loading={actionBusy} onClick={handleReportHandback} disabled={!evidenceReady || uploadingType !== null}>
                <i className="bi bi-box-arrow-up" aria-hidden="true" />
                I&apos;ve handed the item back
              </Button>
            </div>
          )}

          {state === 'RETURN_REPORTED' && isLender && (
            <div className="conversation-return-action">
              <p className="conversation-return-title">The borrower reported the item is back.</p>
              <p className="conversation-return-hint">Confirm you have received it to close the loan, or report a problem.</p>
              <div className="conversation-window-actions">
                <Button variant="primary" size="sm" loading={actionBusy} onClick={handleConfirmReceipt}>
                  <i className="bi bi-check-lg" aria-hidden="true" />
                  Confirm received
                </Button>
                <Button variant="outline" size="sm" loading={actionBusy} onClick={handleDisputeReturn}>
                  <i className="bi bi-exclamation-triangle" aria-hidden="true" />
                  Not received
                </Button>
              </div>
            </div>
          )}

          {state === 'RETURN_INITIATED' && isLender && (
            <p className="conversation-return-status">
              The borrower is coordinating the return.
            </p>
          )}

          {state === 'RETURN_REPORTED' && isBorrower && (
            <p className="conversation-return-status">
              Handback reported. Awaiting the owner&apos;s receipt confirmation.
            </p>
          )}

          {state === 'ACTIVE' && isBorrower && !active.extensionRequestPending && !active.extensionCounterPending && (
            requestOpen ? (
              <div className="conversation-extension-action">
                <p className="conversation-extension-title">Need more time?</p>
                <p className="conversation-extension-hint">
                  Request a due-date extension. Current due date:{' '}
                  <strong>{formatDate(active.dueAt)} {formatTime(active.dueAt)}</strong>.
                </p>
                <label className="conversation-extension-field">
                  <span>New due date</span>
                  <input
                    type="datetime-local"
                    className="conversation-extension-input"
                    value={extensionDate}
                    onChange={(event) => setExtensionDate(event.target.value)}
                  />
                </label>
                <label className="conversation-extension-field">
                  <span>Note (optional)</span>
                  <input
                    type="text"
                    className="conversation-extension-input"
                    placeholder="Why do you need more time?"
                    value={extensionNote}
                    onChange={(event) => setExtensionNote(event.target.value)}
                    maxLength={255}
                  />
                </label>
                <Button variant="primary" size="sm" loading={actionBusy} onClick={handleRequestExtension} disabled={!extensionDate}>
                  <i className="bi bi-calendar-plus" aria-hidden="true" />
                  Request extension
                </Button>
              </div>
            ) : (
              <div className="conversation-extension-action">
                <Button variant="outline" size="sm" onClick={() => setRequestOpen(true)}>
                  <i className="bi bi-calendar-plus" aria-hidden="true" />
                  Need more time?
                </Button>
              </div>
            )
          )}

          {state === 'ACTIVE' && isBorrower && active.extensionRequestPending && !active.extensionCounterPending && (
            <p className="conversation-extension-status">
              Extension request sent to the owner. Awaiting their decision.
            </p>
          )}

          {state === 'ACTIVE' && isLender && active.extensionRequestPending && (
            <div className="conversation-extension-action">
              <p className="conversation-extension-title">Extension requested</p>
              <p className="conversation-extension-hint">
                {active.extensionNote && (
                  <>
                    <span className="conversation-extension-quote">&ldquo;{active.extensionNote}&rdquo;</span>{' '}
                  </>
                )}
                The borrower asked to move the due date to{' '}
                <strong>{formatDate(active.extensionRequestedDueAt)} {formatTime(active.extensionRequestedDueAt)}</strong>.
              </p>
              <div className="conversation-window-actions">
                <Button variant="primary" size="sm" loading={actionBusy} onClick={handleAcceptExtension}>
                  <i className="bi bi-check-circle" aria-hidden="true" />
                  Accept
                </Button>
                <Button variant="outline" size="sm" loading={actionBusy} onClick={handleRejectExtension}>
                  <i className="bi bi-x-circle" aria-hidden="true" />
                  Reject
                </Button>
                <Button variant="outline" size="sm" loading={actionBusy} onClick={() => setCounterOpen(!counterOpen)}>
                  <i className="bi bi-arrow-repeat" aria-hidden="true" />
                  Counter offer
                </Button>
              </div>
              {counterOpen && (
                <div className="conversation-extension-counter">
                  <label className="conversation-extension-field">
                    <span>Proposed due date</span>
                    <input
                      type="datetime-local"
                      className="conversation-extension-input"
                      value={counterDate}
                      onChange={(event) => setCounterDate(event.target.value)}
                    />
                  </label>
                  <label className="conversation-extension-field">
                    <span>Note (optional)</span>
                    <input
                      type="text"
                      className="conversation-extension-input"
                      placeholder="Your proposed date"
                      value={counterNote}
                      onChange={(event) => setCounterNote(event.target.value)}
                      maxLength={255}
                    />
                  </label>
                  <Button variant="primary" size="sm" loading={actionBusy} onClick={handleCounterExtension} disabled={!counterDate}>
                    <i className="bi bi-send" aria-hidden="true" />
                    Send counter offer
                  </Button>
                </div>
              )}
            </div>
          )}

          {state === 'ACTIVE' && isBorrower && active.extensionCounterPending && (
            <div className="conversation-extension-action">
              <p className="conversation-extension-title">Counter offer received</p>
              <p className="conversation-extension-hint">
                {active.extensionNote && (
                  <>
                    <span className="conversation-extension-quote">&ldquo;{active.extensionNote}&rdquo;</span>{' '}
                  </>
                )}
                The owner proposed{' '}
                <strong>{formatDate(active.extensionOfferedDueAt)} {formatTime(active.extensionOfferedDueAt)}</strong>{' '}
                instead.
              </p>
              <div className="conversation-window-actions">
                <Button variant="primary" size="sm" loading={actionBusy} onClick={handleAcceptExtensionCounter}>
                  <i className="bi bi-check-circle" aria-hidden="true" />
                  Accept counter
                </Button>
                <Button variant="outline" size="sm" loading={actionBusy} onClick={handleRejectExtensionCounter}>
                  <i className="bi bi-x-circle" aria-hidden="true" />
                  Decline
                </Button>
              </div>
            </div>
          )}

          {state === 'ACTIVE' && isLender && active.extensionCounterPending && (
            <p className="conversation-extension-status">
              Counter offer sent. Awaiting the borrower&apos;s decision.
            </p>
          )}
        </div>
      )}

      {writable && (
        <form className="conversation-composer" onSubmit={handleSend} noValidate>
          <input
            ref={inputRef}
            type="text"
            className="conversation-input"
            placeholder="Type a message..."
            value={body}
            onChange={(event) => setBody(event.target.value)}
            onKeyDown={handleKeyDown}
            disabled={busy}
            maxLength={1000}
          />
          <Button type="submit" size="sm" loading={busy} disabled={!body.trim()}>
            Send
          </Button>
        </form>
      )}
    </Drawer>
  )
}