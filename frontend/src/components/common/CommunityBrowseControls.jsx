import React from 'react'

export default function CommunityBrowseControls({
  query,
  onQueryChange,
  category,
  onCategoryChange,
  categories,
}) {
  return (
    <div className="tb-browse-controls">
      <label className="tb-search">
        <i className="bi bi-search tb-search-icon" aria-hidden="true" />
        <input
          type="search"
          className="tb-search-input"
          value={query}
          onChange={(event) => onQueryChange(event.target.value)}
          placeholder="Search items"
          aria-label="Search items"
        />
      </label>

      <div className="tb-filter-list" role="group" aria-label="Filter by category">
        {categories.map((option) => (
          <button
            key={option}
            type="button"
            className={`tb-filter-pill${category === option ? ' active' : ''}`}
            aria-pressed={category === option}
            onClick={() => onCategoryChange(option)}
          >
            {option}
          </button>
        ))}
      </div>
    </div>
  )
}
