# BorrowBox 15-Day Checklist

## Current Status
- [x] Backend foundations are in place
- [x] MySQL is connected and verified
- [x] Item feature is complete and tested
- [x] Category feature is complete and tested
- [x] User feature is complete and tested
- [x] MySQL-backed integration tests are passing for Item, Category, and User
- [x] Group feature is complete and tested
- [x] Borrow workflow stack is wired and validated
- [x] Day 9 business rules and validation are complete
- [x] Day 10 API completion is complete and tested
- [x] Day 11 search and filters are complete and tested
- [x] Day 12 frontend start is complete
- [x] Day 13 frontend completion is complete
- [x] Day 14 integration testing is complete
- [x] Day 15 deployment and final review is complete

## Day 1 - Environment and Foundations
- [x] Install and verify Java, Maven, and MySQL
- [x] Create the Spring Boot backend project
- [x] Set up MySQL database configuration
- [x] Add the health check endpoint

## Day 2 - Base Backend Structure
- [x] Create the Item entity
- [x] Create the Item repository
- [x] Create the Item service
- [x] Create the Item controller
- [x] Add basic Item request validation

## Day 3 - First Domain Expansion
- [x] Add Item GET by ID, update, and delete endpoints
- [x] Add Item error handling for not found and validation cases
- [x] Add Item controller tests

## Day 4 - Secondary Domain Expansion
- [x] Create the Category entity
- [x] Create the Category repository
- [x] Create the Category service
- [x] Create the Category controller
- [x] Add Category controller tests

## Day 5 - Real Database Coverage
- [x] Add a MySQL-backed integration test
- [x] Verify persistence works against the real database
- [x] Add more integration coverage for additional entities

## Day 6 - Core User Domain
- [x] Create the User entity
- [x] Create the User repository
- [x] Create the User service
- [x] Create the User controller
- [x] Add User controller tests
- [x] Add a MySQL-backed User integration test

## Day 7 - Group and Ownership Model
- [x] Create the Group entity
- [x] Connect users to groups
- [x] Define item ownership or membership rules
- [x] Add tests for group-related behavior

## Day 8 - Borrowing Workflow Entities
- [x] Create the BorrowRequest entity
- [x] Create the BorrowRecord entity
- [x] Define the request/approval/borrow/return flow

## Day 9 - Business Rules and Validation
- [x] Add workflow validation rules
- [x] Add overdue and archive behavior
- [x] Add richer service-layer business logic

## Day 10 - API Completion
- [x] Complete CRUD for the remaining entities
- [x] Add GET by ID, update, and delete where missing
- [x] Add consistent API response handling

## Day 11 - Search and Filters
- [x] Add search endpoints (Item and BorrowRecord)
- [x] Add filtering by status, category, owner, and group
- [x] Add pagination and Specification-based dynamic queries
- [x] Add unit tests for search/filter endpoints

## Day 12 - Frontend Start
 - [x] Create the frontend structure (minimal Vite + React demo at `frontend`)
 - [x] Build the main layout and navigation (Item / BorrowRecord search views)
 - [x] Connect the UI to backend endpoints (proxy and CORS configured)

## Day 13 - Frontend Completion
- [x] Add forms for create/update flows
- [x] Add list and detail views
- [x] Polish the user experience

## Day 14 - Integration Testing
- [x] Add more end-to-end tests
- [x] Validate full workflow behavior
- [x] Check MySQL persistence across scenarios

## Day 15 - Deployment and Final Review
- [x] Prepare deployment configuration
- [x] Verify production readiness
- [x] Review the full project and close remaining gaps
