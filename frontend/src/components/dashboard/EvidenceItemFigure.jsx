import React from 'react'

// V2.5.5: this figure was inlined in EvidenceTimeline and is now also the item
// markup for BeforeAfterComparison, so the drawer has exactly one
// evidence-card implementation instead of two that can drift apart.
//
// Presentational only: no fetching, no service import, no state of its own. The
// role label arrives as a prop because it belongs to the slot or comparison
// side the item is rendered under, not to the item. The timestamp formatting is
// the V2.5.3 behaviour, moved here unchanged.

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

export default function EvidenceItemFigure({ item, label }) {
  return (
    <figure className="conversation-evidence-item">
      <img src={item.contentUrl} alt={label} />
      <figcaption>
        {label} · {item.capturerName}
        {item.conditionNote && ` · ${item.conditionNote}`}
        {item.conditionRating != null && ` · ${item.conditionRating}/5`}
        {item.capturedAt && (
          <>
            {' · '}
            <span className="conversation-evidence-time">
              {formatDate(item.capturedAt)} {formatTime(item.capturedAt)}
            </span>
          </>
        )}
      </figcaption>
    </figure>
  )
}
