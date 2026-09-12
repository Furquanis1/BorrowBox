import React, { useCallback, useEffect, useRef, useState } from 'react'
import Drawer from '../ui/Drawer'
import Button from '../ui/Button'
import Spinner from '../ui/Spinner'
import { useAuth } from '../../contexts/AuthContext'
import { requestService } from '../../services'

const TERMINAL_STATES = new Set(['COMPLETED', 'REJECTED', 'CANCELLED'])
const WRITABLE_STATES = new Set(['APPROVED', 'AWAITING_HANDOVER', 'ACTIVE', 'RETURN_INITIATED', 'RETURN_REPORTED'])

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
  const { user } = useAuth()
  const bottomRef = useRef(null)
  const inputRef = useRef(null)

  useEffect(() => {
    setCurrentTxn(transaction)
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

  if (!transaction) return null

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title={`Conversation \u2014 ${transaction.title || 'Transaction'}`}
      className="request-drawer"
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

      {!terminal && (
        <div className="conversation-context">
          {actionError && (
            <p className="field-error" role="alert">{actionError}</p>
          )}

          {state === 'ACTIVE' && isBorrower && (
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
              <p className="conversation-return-hint">Tell the lender the item has been physically handed back.</p>
              <Button variant="primary" size="sm" loading={actionBusy} onClick={handleReportHandback}>
                <i className="bi bi-box-arrow-up" aria-hidden="true" />
                I&apos;ve handed the item back
              </Button>
            </div>
          )}

          {state === 'RETURN_REPORTED' && isLender && (
            <div className="conversation-return-action">
              <p className="conversation-return-title">The borrower reported the item is back.</p>
              <p className="conversation-return-hint">Confirm you have received it to close the loan.</p>
              <Button variant="primary" size="sm" loading={actionBusy} onClick={handleConfirmReceipt}>
                <i className="bi bi-check-lg" aria-hidden="true" />
                Confirm received
              </Button>
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