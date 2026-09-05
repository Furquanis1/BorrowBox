import React from 'react'

export default function EmptyState({ icon, title, description, action, className = '' }) {
  return (
    <div className={`empty-state ${className}`.trim()}>
      {icon && (
        <span className="empty-state-icon" aria-hidden="true">
          <i className={`bi ${icon}`} />
        </span>
      )}
      {title && <h3 className="empty-state-title">{title}</h3>}
      {description && <p className="empty-state-description">{description}</p>}
      {action && <div className="empty-state-action">{action}</div>}
    </div>
  )
}