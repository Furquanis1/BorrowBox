import React from 'react'

export default function Input({ label, id, error, hint, className = '', ...rest }) {
  return (
    <div className="form-group">
      {label && <label htmlFor={id}>{label}</label>}
      <input
        id={id}
        className={`input ${className}`.trim()}
        aria-invalid={error ? true : undefined}
        {...rest}
      />
      {hint && <p className="form-hint">{hint}</p>}
      {error && (
        <p className="field-error" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}