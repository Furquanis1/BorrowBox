import React from 'react'
import EvidenceItemFigure from './EvidenceItemFigure'

// V2.5.4: the four evidence moments are fixed by the backend EvidenceType enum,
// and each one is always shown so the drawer reads as a four-step record of the
// loan rather than a bag of photos. The role-explicit labels are the ones V2.5.3
// established: the two handover moments used to both render as "At handover".
export const EVIDENCE_SLOTS = [
  { type: 'LENDER_PRE_LENDING', label: 'Lender — Pre-lending' },
  { type: 'LENDER_HANDOVER', label: 'Lender — Handover' },
  { type: 'BORROWER_PRE_RETURN', label: 'Borrower — Before return' },
  { type: 'BORROWER_RETURN_HANDOVER', label: 'Borrower — Return handover' },
]

// A type the backend adds later must still be visible rather than silently
// dropped, so it gets a readable label derived from its own value. The enum
// itself is never changed from the frontend.
function deriveLabel(type) {
  return String(type)
    .split('_')
    .filter(Boolean)
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ')
}

/**
 * Presentational only: it never fetches, never imports a service and never
 * re-sorts. The incoming array is already ordered by the server
 * (capturedAt ASC, id ASC), so grouping is done with a stable filter/append that
 * preserves that order inside every slot.
 */
export default function EvidenceTimeline({ evidence = [] }) {
  const known = new Map()
  for (const slot of EVIDENCE_SLOTS) known.set(slot.type, [])

  const unknown = new Map()
  for (const item of evidence) {
    const bucket = known.has(item.type) ? known : unknown
    if (!bucket.has(item.type)) bucket.set(item.type, [])
    bucket.get(item.type).push(item)
  }

  const slots = EVIDENCE_SLOTS.map((slot) => ({
    type: slot.type,
    label: slot.label,
    items: known.get(slot.type),
  }))

  for (const [type, items] of unknown) {
    slots.push({ type, label: deriveLabel(type), items })
  }

  return (
    <div className="conversation-evidence-slots">
      {slots.map((slot) => (
        <section className="conversation-evidence-slot" key={slot.type} data-evidence-type={slot.type}>
          <p className="conversation-evidence-slot-title">{slot.label}</p>
          <div className="conversation-evidence-slot-body">
            {slot.items.length ? (
              <div className="conversation-evidence-grid">
                {slot.items.map((item) => (
                  <EvidenceItemFigure key={item.id} item={item} label={slot.label} />
                ))}
              </div>
            ) : (
              <p className="conversation-evidence-empty">Not captured</p>
            )}
          </div>
        </section>
      ))}
    </div>
  )
}