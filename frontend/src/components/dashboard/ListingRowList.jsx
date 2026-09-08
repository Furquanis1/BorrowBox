import React from 'react'

export default function ListingRowList({ listings }) {
  return (
    <ul className="explore-listing-list">
      {listings.map((listing) => (
        <li key={listing.id} className="explore-listing-row">
          <div className="explore-listing-row-main">
            <h3>{listing.title}</h3>
            {listing.description && (
              <p className="explore-listing-description">{listing.description}</p>
            )}
          </div>
          <div className="explore-listing-row-meta">
            <span>{listing.availableUnits} available</span>
            <span>{listing.borrowedUnits} borrowed</span>
            <span>{listing.totalUnits} total</span>
          </div>
        </li>
      ))}
    </ul>
  )
}