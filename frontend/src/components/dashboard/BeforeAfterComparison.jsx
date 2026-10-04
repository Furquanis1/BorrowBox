import React from 'react'
import EvidenceItemFigure from './EvidenceItemFigure'

// V2.5.5: a before/after comparison of the lender's pre-lending photo against
// the borrower's pre-return photo. The two sides stack rather than sit side by
// side, because at the ~340px drawer width a two-column split would halve the
// thumbnails. The mapping below is a deliberate product decision rather than an
// inference: BEFORE is the lender's pre-lending moment, AFTER is the borrower's
// pre-return moment.
//
// Presentational only: no fetching, no service import, no state, no effects. It
// renders what it is handed and draws no conclusion about the item.
//
// Any other EvidenceType is ignored here on purpose. This view has exactly two
// defined positions, and inventing a mapping for a future type would be a guess
// about what it means. EvidenceTimeline is what guarantees a newly added type
// stays visible, so nothing is lost.
//
// `moment` is deliberately a single field feeding both the visible subtitle and
// the label handed to EvidenceItemFigure, so the side heading and the photo
// caption can never drift apart.
const COMPARISON_SIDES = [
  { key: 'before', type: 'LENDER_PRE_LENDING', title: 'Before', moment: 'Lender — Pre-lending' },
  { key: 'after', type: 'BORROWER_PRE_RETURN', title: 'After', moment: 'Borrower — Before return' },
]

/**
 * The incoming array is already ordered by the server (capturedAt ASC, id ASC),
 * so each side is built with a plain filter: that allocates a new array, never
 * mutates the prop, preserves the incoming order, and keeps every record of a
 * repeated type. Nothing here sorts, ranks, or picks a "primary" photo.
 */
export default function BeforeAfterComparison({ evidence = [] }) {
  const sides = COMPARISON_SIDES.map((side) => ({
    ...side,
    items: evidence.filter((item) => item.type === side.type),
  }))

  return (
    <div className="conversation-comparison">
      {sides.map((side) => (
        <section
          className="conversation-comparison-side"
          key={side.key}
          data-comparison-side={side.key}
        >
          {/* The side title is a real heading, so the Before/After distinction
              survives without CSS or ARIA. Its two children stack into the two
              intended lines under the existing flex-column rule. */}
          <h3 className="conversation-comparison-side-title">
            <strong>{side.title}</strong>
            <span>{side.moment}</span>
          </h3>

          {side.items.length > 0 ? (
            <div className="conversation-evidence-grid">
              {side.items.map((item) => (
                <EvidenceItemFigure key={item.id} item={item} label={side.moment} />
              ))}
            </div>
          ) : (
            <p className="conversation-evidence-empty">Not captured</p>
          )}
        </section>
      ))}

      {/* Stated once for the whole comparison. The participant-entered condition
          note and rating still render per photo inside EvidenceItemFigure,
          because those are recorded inputs rather than anything inferred here. */}
      <p className="conversation-comparison-note">Photos are shown as captured. They are not an automatic condition assessment.</p>
    </div>
  )
}
