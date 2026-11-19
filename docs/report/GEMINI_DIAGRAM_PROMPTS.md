# BorrowBox — Diagram Prompts for Gemini

This document contains ready-to-paste prompts for **Google Gemini** to generate the
diagrams required for a B.Tech Computer Science **Industry Internship Report**:

1. a **Data Flow Diagram (DFD)**, and
2. a **UML Use-Case Diagram** and a **UML Class Diagram**.

The prompts are grounded only in functionality that was **verified in the BorrowBox
source code** (backend controllers/services/entities, security configuration, database
schema, and frontend routing/services). The project README is out of date relative to
the code, so it was **not** used as the source of truth.

**How to use:** copy the entire fenced prompt for the diagram you want (Section 2 or
Section 3) and paste it into Gemini. Ask Gemini for the Mermaid output even if image
generation is available, because Mermaid can be rendered at <https://mermaid.live> and
exported as PNG/SVG for Microsoft Word.

---

## 1. Repository findings used as the basis for the diagrams

### 1.1 Verified facts (confirmed directly from source code)

**Purpose.** BorrowBox is a full-stack, community-based platform for sharing and
tracking physical assets. Users own physical assets independently of any community,
list an asset into one or more communities, and members negotiate and carry out loans
with a full lifecycle (request → approval → handover → active loan → return), supported
by messaging, evidence photos, waitlists, reputation and community moderation.

**Technology stack (verified).**

- **Frontend:** React 18 single-page application built with Vite, served in production
  by Nginx. `nginx.conf` proxies `/api/` to the backend. All API calls go to the relative
  base `/api` with `credentials: 'include'`.
- **Backend:** Spring Boot 3.5, Java 21, Spring Security, Spring Data JPA/Hibernate,
  Maven. REST controllers → services → Spring Data repositories (entities).
- **Database:** MySQL 8.0 (InnoDB). Schema is version-controlled in
  `backend/src/main/resources/schema.sql` (`ddl-auto=validate`).
- **Authentication:** stateless JWT stored in an **HttpOnly** cookie named `jwt`
  (BCrypt password hashing). Public paths: `/api/auth/**`, `/api/health`, Swagger.
  Everything else requires an authenticated user. The JWT carries only
  `userId`/`email`/`fullName`; **roles are resolved from the database per request**.
- **Evidence storage:** evidence photos are stored on the **server filesystem**
  (`borrowbox.media.dir`, default `./media`) under UUID filenames and served only
  through an authenticated, participant-only endpoint (never statically).
- **No external system integrations** are present: there is **no** email, SMS, push,
  payment, cloud-storage or third-party API integration anywhere in the backend.

**Actors (verified).**

- **Visitor** — unauthenticated person who can register or sign in.
- **User** — any authenticated person. A user can be an owner (lender side) or a
  borrower in a transaction, and may be both in different transactions.
- **Community roles:** `MEMBER` and `MANAGER` (a membership also has status
  `PENDING / ACTIVE / SUSPENDED / LEFT / REJECTED`). The creator of a community
  becomes its `MANAGER`.
- **Transaction roles:** `borrower` and `lender` (the lender is the asset owner side).

**Verified loan-lifecycle actors** (confirmed in `TransactionService.java`):

| Action | Actor |
|---|---|
| Create borrow request | borrower |
| Approve / Reject / Counter-offer | lender |
| Accept counter-offer | borrower |
| Cancel | borrower or lender |
| Stage handover | either participant |
| **Confirm handover** | **lender** (requires ≥1 lender handover evidence) |
| Confirm receipt | borrower |
| Dispute handover | borrower |
| Request extension | borrower |
| Accept / Reject / Counter extension | lender |
| Accept / Reject extension counter | borrower |
| Initiate return | borrower |
| Report handback | borrower |
| **Confirm return** | **lender** |
| **Dispute return** | **lender** |
| Upload / view evidence | borrower or lender (type- and state-dependent) |
| Send / view messages | borrower or lender |

**Verified subsystems.**

- **Authentication & Users** — register, login, logout, current session, user CRUD.
- **Community & Membership** — community creation; admission modes `MANAGER_APPROVAL`
  and `LOCATION_VERIFIED` (Haversine radius check); join/leave; manager review of
  pending memberships; suspend / reinstate / remove members; last-manager protection.
- **Categories** — simple CRUD lookup used by assets.
- **Assets & Inventory** — an owner creates an `Asset` with one or more `AssetUnit`
  rows (all start `AVAILABLE`); units are the shared physical inventory; archive asset
  is blocked if any unit is borrowed.
- **CommunityListing** — selectively expose an owned asset to a community; owner must
  be an active member of that community; list / soft-unlist.
- **Borrowing / Transaction engine** — full state machine:
  `PENDING → APPROVED / REJECTED / COUNTER_OFFERED / CANCELLED`,
  `APPROVED → AWAITING_HANDOVER → ACTIVE`, `ACTIVE → RETURN_INITIATED → RETURN_REPORTED
  → COMPLETED`, plus `HANDOVER_DISPUTED`, `RETURN_DISPUTED` and `EXPIRED`. Reservation
  uses a pessimistic DB lock plus a `UNIQUE(reserved_unit_id)` backstop. A persisted
  per-phase reservation deadline enables an **opportunistic expiry sweep** (no scheduler).
- **Messages & Events** — per-transaction conversation (`USER` and server-generated
  `SYSTEM` messages) and structured semantic events with per-recipient delivery state
  (`UNREAD / READ / DISMISSED`) used for in-app notifications.
- **Evidence & Condition history** — photos captured at handover/return; immutable;
  optional condition note (≤1000 chars) and rating (1–5); the latest observation is
  projected onto the unit and appended to an immutable condition-history chain.
- **Waitlist** — per-asset queue shared across communities; when a unit is released it
  is promoted (creating a new `PENDING` transaction) under a fixed lock order.
- **Community Rules** — manager-managed versioned rules of types
  `MEMBERSHIP_CONTEXT_FIELDS`, `MAX_ACTIVE_MEMBERS`, `ADMISSION_NOTE`,
  `OVERDUE_GRACE_PERIOD`.
- **Reputation & Trust** — an append-only reputation ledger
  (`LOAN_COMPLETED`, `RETURN_DISPUTED`); the trust profile is **derived at read time**
  (nothing precomputed).
- **Flags & Community Health** — manager-only moderation flags
  (`OVERDUE / HANDOVER_DISPUTED / RETURN_DISPUTED / EVIDENCE_ISSUE / MANUAL`, status
  `OPEN / REVIEWED / RESOLVED / DISMISSED`) and a manager dashboard/health roll-up.

**Verified data entities / tables (17).**
`users`, `communities`, `memberships`, `categories`, `community_rules`, `assets`,
`asset_units`, `community_listings`, `transactions`, `transaction_messages`,
`transaction_evidence`, `asset_unit_condition_history`, `waitlist_entries`,
`transaction_events`, `transaction_event_deliveries`, `reputation_events`, `flags`.

**Verified key relationships (cardinalities).**

- User 1—\* Membership; Community 1—\* Membership (unique per user+community).
- User 1—\* Community (as creator); User 1—\* Asset (as owner).
- Asset \*—0..1 Category; Asset 1—1..\* AssetUnit.
- Asset 1—\* CommunityListing; Community 1—\* CommunityListing (unique per asset+community).
- Community 1—\* Transaction; CommunityListing 1—\* Transaction; Asset 1—\* Transaction.
- User 1—\* Transaction (as borrower); User 1—\* Transaction (as lender).
- Transaction \*—0..1 AssetUnit (reserved unit, unique).
- Transaction 1—\* Evidence; Transaction 1—\* TransactionMessage;
  Transaction 1—\* TransactionEvent.
- Asset 1—\* WaitlistEntry; CommunityListing 1—\* WaitlistEntry; User 1—\* WaitlistEntry.
- Community 1—\* Flag; Transaction 1—\* Flag; User 1—\* ReputationEvent.

### 1.2 Uncertain / not verified — Gemini must NOT guess these

- **No scheduled jobs or background workers exist.** Reservation expiry and waitlist
  promotion happen opportunistically on request paths, not on a timer.
- **No email / SMS / push notification system exists.** Notifications are in-app events
  only. The documentation lists email/push as *future* work — it is **not implemented**.
- **AI, "tribunal", blind peer review and condition-matching** appear only in the
  documentation roadmap — **not implemented**. Do not include them.
- **Some enum values are defined but not fully wired.** `AssetUnitStatus`
  `LOST / DAMAGED / NOT_AVAILABLE` and `CommunityRuleType` `MAX_ACTIVE_MEMBERS /
  ADMISSION_NOTE / MEMBERSHIP_CONTEXT_FIELDS` are stored/managed but not fully enforced
  by workflows; only `OVERDUE_GRACE_PERIOD` is read at runtime. Do not present these as
  complete workflows.
- **`CategoryController` and `UserController` expose unrestricted CRUD** with no role
  gate. Do not present a system "Administrator" actor; none exists.
- **`HANDOVER_DISPUTED` is a terminal state** with no resolution flow in the code.
- The README/roadmap describe an older version (V2.2.2); the code implements up to
  V2.5.3. Trust the code, not the README.
- `docs/` notebooks and `PROJECT_STATUS.md` are planning/aspirational documents and may
  describe features that were never implemented.

---

## 2. Complete Gemini prompt for the Data Flow Diagram (DFD)

Copy everything inside the block below into Gemini.

````text
You are generating a Data Flow Diagram (DFD) for a university B.Tech Computer Science
Industry Internship Report. Produce a single LEVEL-1 DFD for a web application called
BorrowBox. Use ONLY the facts in this prompt. Do not invent any external entity,
process, data store, data flow, API, database or business rule that is not listed. If a
detail is missing, omit it or clearly flag it as "to be confirmed" rather than guessing.

=== ABOUT THE SYSTEM (verified facts) ===
BorrowBox is a community-based physical asset-sharing platform. Users own physical
assets independently of any community. Each asset has one or more physical units
(AssetUnit). Owners list an asset into one or more communities. Community members browse
listings and negotiate borrowing. A loan follows the lifecycle: request -> lender
approve/reject/counter-offer -> handover -> active loan -> return confirmation. The
platform also supports per-transaction messaging, evidence photos, a waitlist, a
reputation ledger, community rules, moderation flags and a manager dashboard.

Technology (context only; this is a DFD, not an architecture diagram):
- Frontend: a React single-page application (SPA) served by Nginx.
- Backend: a Spring Boot REST API that contains all business processes.
- Database: a single MySQL 8.0 database.
- Authentication uses a JWT in an HttpOnly cookie; roles are looked up per request.
- There is NO external system integration (no email, SMS, push, payment or cloud
  storage). Evidence photos are stored on the server's own filesystem.

=== SYSTEM BOUNDARY ===
Draw ONE system boundary labelled "BorrowBox". Everything below is inside it.

=== EXTERNAL ENTITIES (rectangles) — use ONLY these ===
E1  User  — an authenticated person acting as asset owner, borrower and/or lender.
              (An unauthenticated visitor only registers or signs in.)
E2  Community Manager — a member who holds the MANAGER role in a community (member
              review, community rules, moderation flags, dashboard/health).

Do NOT draw the web browser, the React SPA, MySQL, or the server filesystem as external
entities — they are inside the BorrowBox boundary.

=== PROCESSES (circles / rounded rectangles, numbered) — use ONLY these ===
1.0  Web User Interface (React SPA) — presents screens to users, collects input, calls
     the backend API and renders responses. It is the presentation process inside the
     boundary (it is NOT an external entity).
2.0  Authentication & User Management
3.0  Community & Membership Management
4.0  Asset, Inventory & Listing Management
5.0  Borrowing & Loan Lifecycle Management
6.0  Messaging, Evidence & Waitlist Management
7.0  Trust, Reputation & Moderation Management

=== DATA STORES (open-ended rectangles) — use ONLY these ===
D1  Users
D2  Communities & Memberships
D3  Categories
D4  Assets & Asset Units
D5  Community Listings
D6  Transactions
D7  Messages & Events
D8  Evidence & Condition History
D9  Waitlist
D10 Reputation Events
D11 Flags & Community Rules

You may merge two closely-related stores into one if it clearly improves readability,
and you may annotate a store with its table name(s) in brackets, but do NOT drop any
store and do NOT add a new one.

=== DATA FLOWS (labelled directional arrows) — use ONLY these ===

Authentication:
- E1 User -> 1.0 : registration details, sign-in credentials, sign-out request
- 1.0 -> 2.0 : register / login / logout / current-session requests
- 2.0 <-> D1 : create user; read user credentials and profile
- 2.0 -> 1.0 : authentication result and user profile

Community & membership:
- E1 User -> 1.0 : community details, join request, leave request
- E2 Community Manager -> 1.0 : membership decisions, member actions, rule settings,
  flag actions, dashboard request
- 1.0 -> 3.0 : create community, join/leave community, list members, review membership,
  manage members, manage community rules
- 3.0 <-> D2 : store/read communities and memberships
- 3.0 -> 1.0 : community and membership data

Assets & listings:
- E1 User -> 1.0 : asset details, listing/unlisting requests
- 1.0 -> 4.0 : add asset, archive asset, list/unlist asset, view inventory,
  browse community listings
- 4.0 <-> D3 : read categories
- 4.0 <-> D4 : store/read assets and asset units
- 4.0 <-> D5 : store/read community listings
- 4.0 -> 1.0 : inventory and listing data

Borrowing & loan lifecycle:
- E1 User (borrower/lender) -> 1.0 : borrow request, approve/reject, counter-offer,
  accept, cancel, handover, receipt, extension, return and dispute actions
- 1.0 -> 5.0 : all borrowing and loan-lifecycle actions
- 5.0 <-> D6 : store/read transactions and their state
- 5.0 <-> D4 : read/update asset-unit availability (AVAILABLE / RESERVED / BORROWED)
- 5.0 -> 6.0 : record system conversation entries and lifecycle events; request
  promotion of the next waitlisted borrower
- 5.0 -> 7.0 : record loan outcome (completed / return disputed) for reputation
- 5.0 -> 1.0 : transaction state and loan data

Messaging, evidence & waitlist:
- E1 User -> 1.0 : messages, evidence photos, waitlist join/leave
- 1.0 -> 6.0 : send message, list conversation, upload/list/view evidence, join/leave
  waitlist
- 6.0 <-> D7 : store/read messages and events
- 6.0 <-> D8 : store/read evidence metadata; write condition observations
- 6.0 <-> D9 : store/read waitlist entries
- 6.0 -> 1.0 : conversation, evidence and waitlist data

Trust, reputation & moderation:
- E1 User -> 1.0 : trust-profile / reputation-ledger request
- E2 Community Manager -> 1.0 : dashboard/health request, flag list/create/update,
  rule management
- 1.0 -> 7.0 : view trust profile, view reputation ledger, view dashboard/health,
  manage flags, manage rules
- 7.0 <-> D10 : store/read reputation events
- 7.0 <-> D6 : read transactions to compute dashboard/health metrics
- 7.0 <-> D11 : store/read flags and community rules
- 7.0 -> 1.0 : trust, health, flag and rule data

Do NOT add any other data flow. In particular, do NOT create direct store-to-store
flows, and do NOT invent analytics, email or notification flows.

=== DIAGRAM STYLE RULES ===
- Level 1 only: show these processes and data stores; do NOT decompose them into a
  Level 2.
- Every arrow must leave a rectangle/circle/store and enter a different one. No
  store-to-store arrows.
- Label every arrow with the data that moves.
- Plain WHITE background; black lines and black text only.
- Clean academic layout, readable font, minimal line crossings, no overlap.
- NO colours, gradients, drop shadows, 3D effects, decorative icons or clip-art.
- Fit neatly on a single A4 page (portrait or landscape) for a Microsoft Word report.

=== OUTPUT ===
1. If you can generate a rendered image, produce the finished Level-1 DFD image.
2. ALWAYS also output the diagram as Mermaid code in a single code block, so it can be
   rendered at mermaid.live and exported. Use a Mermaid `flowchart` (for example
   `flowchart LR`). Represent external entities and processes as rectangles/rounded
   nodes, data stores as datastore-like nodes (or rectangles labelled "D1 Users",
   etc.), and label every edge with the data that moves. If a visual cannot render
   perfectly in Mermaid, prioritise correctness and readability of the code.
3. After the diagram, give a short explanation of 5 to 8 bullets describing what each
   external entity, process and data store represents, and walk through one example
   ("member borrows an item") to show how data moves. This is so I can defend the
   diagram in a viva.
4. At the very end, list any point where the prompt was incomplete or ambiguous, and
   state that it needs confirmation, instead of inventing detail.
````

---

## 3. Complete Gemini prompt for the UML Use-Case and Class Diagrams

Copy everything inside the block below into Gemini.

````text
You are generating UML diagrams for a university B.Tech Computer Science Industry
Internship Report. Produce TWO diagrams for a web application called BorrowBox:
(A) a UML Use-Case Diagram, and (B) a UML Class Diagram. Use ONLY the facts in this
prompt. Do not invent classes, attributes, operations, relationships, multiplicities,
actors or use cases. If something is not specified, omit it or flag it as "to be
confirmed" rather than guessing.

=== ABOUT THE SYSTEM (verified facts) ===
BorrowBox is a community-based physical asset-sharing platform. Users own physical
assets independently of communities; each asset has one or more physical units. Owners
list an asset into communities. A user can be a borrower and a lender (owner) in
different transactions. A loan follows: request -> lender approve/reject/counter-offer
-> handover -> active loan -> return confirmation, with optional extensions, handover
and return disputes, messaging, evidence photos, a waitlist, a reputation ledger,
community rules, moderation flags and a manager dashboard.

Security context: authentication uses a JWT stored in an HttpOnly cookie. Roles are NOT
in the token; the backend looks up the membership role per request. There is no system
administrator role. There is no external system integration (no email/SMS/push/payment).

NOTE ON UML: Mermaid has no native use-case diagram. For Part A, use Mermaid `flowchart`
and draw actors as stick-figure-like nodes, use cases as rounded/oval nodes `(( ... ))`,
the system boundary as a subgraph, and label associations with plain lines. For Part B,
use Mermaid `classDiagram`.

===============================================================
PART A — UML USE-CASE DIAGRAM
===============================================================

=== ACTORS (stick figures) — use ONLY these ===
- Visitor — an unauthenticated person.
- Community Member — a signed-in user who belongs to at least one community (acts as
  owner, borrower and/or lender).
- Community Manager — a member holding the MANAGER role.
- Borrower — a member acting as the requester in a transaction.
- Lender — a member acting as the owner side in a transaction.

Draw "Borrower" and "Lender" as specialised actors of "Community Member" (generalisation
arrows: Borrower -> Community Member, Lender -> Community Member). A single person may
act as both, and this is expected.

=== SYSTEM BOUNDARY ===
One rectangle labelled "BorrowBox" containing all use cases. Actors sit outside it.

=== USE CASES (ovals) — group them under these subsystems; use ONLY these ===

Authentication & Profile:
  Register Account                         (Visitor)
  Sign In                                  (Visitor)
  Sign Out                                 (Member, Manager)
  View Own Profile & Trust Summary         (Member)
  View Reputation Ledger                   (Member)

Community & Membership:
  Create Community                         (Member; creator becomes Manager)
  Join Community                           (Member)
  Leave Community                          (Member)
  View Community Members                   (Member)
  Review Membership Requests               (Manager)  [approve / reject]
  Manage Members                           (Manager)  [suspend / reinstate / remove]
  Configure Community Rules                (Manager)
  View Community Rules                     (Member)

Assets & Listings:
  Add Asset (with one or more units)       (Member, as owner)
  Archive Asset                            (Member, as owner)
  List Asset to a Community                (Member, as owner)
  Unlist Asset                             (Member, as owner)
  Browse Community Listings / Explore      (Member)
  Join Waitlist                            (Borrower)

Borrowing & Loan Lifecycle:
  Request to Borrow                        (Borrower)
  Approve / Reject Borrow Request          (Lender)
  Counter-Offer                            (Lender)
  Accept Counter-Offer                     (Borrower)
  Cancel Transaction                       (Borrower, Lender)
  Stage Handover                           (Borrower, Lender)
  Confirm Handover                         (Lender)
  Confirm Receipt                          (Borrower)
  Dispute Handover                         (Borrower)
  Request Loan Extension                   (Borrower)
  Decide Loan Extension                    (Lender)  [accept / reject / counter]
  Accept / Reject Extension Counter        (Borrower)
  Initiate Return                          (Borrower)
  Report Handback                          (Borrower)
  Confirm Return                           (Lender)
  Dispute Return                           (Lender)
  View My Requests & Lend Requests         (Member)
  View My Loans                            (Member)

Communication & Evidence:
  Send / View Conversation Messages        (Borrower, Lender)
  Upload Evidence                          (Lender or Borrower; who may upload
                                            depends on evidence type and loan state)
  View Evidence                            (Borrower, Lender)
  View Notifications / Events              (Member)

Trust & Moderation:
  View Manager Dashboard & Community Health (Manager)
  Manage Flags                             (Manager)  [create, update status, assign]

You MAY merge clearly paired outcomes into a single use case where it reduces clutter
(for example "Approve / Reject Borrow Request" is already merged). Do NOT invent new
use cases, and do NOT add any not listed above.

=== RELATIONSHIPS — use ONLY these ===
Actor-to-use-case associations: connect each actor to every use case listed beside it.
Generalisation: Borrower -> Community Member; Lender -> Community Member.

Use «include» for EXACTLY these two (both are required by the implementation):
- Confirm Handover  «include»  Upload Evidence
    (the lender must upload at least one handover evidence photo before the handover
     can be confirmed)
- Report Handback   «include»  Upload Evidence
    (the borrower must upload the required return evidence before handback is reported)

Do NOT add any «extend» relationship, and do NOT add any «include» beyond the two above.
Do not invent generalisations or associations.

=== LAYOUT RULES (Part A) ===
- White background, black text and lines only.
- Actors on the left and/or right of the system boundary; use cases neatly grouped by
  subsystem inside the boundary.
- Readable labels, no overlap, minimal crossing lines.
- No colours, gradients, shadows, 3D or decorative icons.

===============================================================
PART B — UML CLASS DIAGRAM
===============================================================

Show ONLY the 14 domain classes below. These are the verified database-backed entities.
They are plain data classes: show their ATTRIBUTES only. Do NOT invent operations; the
business logic lives in a separate service layer that is out of scope for this diagram.

=== CLASSES AND ATTRIBUTES ===
User
  - id : Long
  - fullName : String
  - email : String
  - passwordHash : String
  - status : UserStatus
  - createdAt : DateTime
  - updatedAt : DateTime

Community
  - id : Long
  - name : String
  - description : String
  - type : CommunityType
  - status : CommunityStatus
  - admissionMode : CommunityAdmissionMode
  - locationLatitude : BigDecimal
  - locationLongitude : BigDecimal
  - locationRadiusM : Integer

Membership
  - id : Long
  - role : MembershipRole
  - status : MembershipStatus
  - verificationMethod : MembershipVerificationMethod
  - verifiedAt : DateTime
  - joinedAt : DateTime
  - contextMetadata : JSON

Category
  - id : Long
  - name : String
  - description : String

Asset
  - id : Long
  - title : String
  - description : String
  - status : AssetStatus

AssetUnit
  - id : Long
  - unitIdentifier : String
  - status : AssetUnitStatus
  - condition : String

CommunityListing
  - id : Long
  - listingStatus : ListingStatus
  - listedAt : DateTime

Transaction
  - id : Long
  - state : TransactionStatus
  - purpose : String
  - requestedDurationDays : Integer
  - agreedDurationDays : Integer
  - reservedAt : DateTime
  - reservationExpiresAt : DateTime
  - startedAt : DateTime
  - dueAt : DateTime
  - originalDueAt : DateTime
  - borrowerConfirmedAt : DateTime
  - completedAt : DateTime

TransactionMessage
  - id : Long
  - kind : MessageKind
  - body : String
  - createdAt : DateTime

TransactionEvent
  - id : Long
  - eventType : TransactionEventType
  - payload : JSON
  - createdAt : DateTime

Evidence
  - id : Long
  - type : EvidenceType
  - fileRef : String
  - contentType : String
  - sizeBytes : Long
  - capturedAt : DateTime
  - conditionNote : String
  - conditionRating : Integer

WaitlistEntry
  - id : Long
  - purpose : String
  - requestedDurationDays : Integer
  - status : WaitlistStatus
  - createdAt : DateTime
  - promotedAt : DateTime

Flag
  - id : Long
  - flagType : FlagType
  - status : FlagStatus
  - note : String
  - occurredAt : DateTime

ReputationEvent
  - id : Long
  - eventType : ReputationEventType
  - role : ReputationRole
  - onTime : Boolean
  - successful : Boolean
  - occurredAt : DateTime

=== RELATIONSHIPS (show cardinalities at BOTH ends) — use ONLY these ===
- User "1"          -- "0..*" : Membership          (a user has many memberships)
- Community "1"     -- "0..*" : Membership          (a community has many memberships)
- User "1"          -- "0..*" : Community           (as creator / createdBy)
- User "1"          -- "0..*" : Asset               (as owner)
- Category "0..1"   -- "0..*" : Asset               (an asset optionally has one category)
- Asset "1" <>-- "1..*" : AssetUnit                 (COMPOSITION: units belong to asset)
- Asset "1"         -- "0..*" : CommunityListing    (an asset can be listed in many communities)
- Community "1"     -- "0..*" : CommunityListing
- Community "1"     -- "0..*" : Transaction         (transactions are community-scoped)
- CommunityListing "1" -- "0..*" : Transaction      (a transaction is created from a listing)
- Asset "1"         -- "0..*" : Transaction
- User "1"          -- "0..*" : Transaction  [role: borrower]
- User "1"          -- "0..*" : Transaction  [role: lender]
- Transaction "0..1" -- "0..1" : AssetUnit   [reserved unit; UNIQUE — at most one
     transaction may hold a given unit]
- Transaction "1"   -- "0..*" : TransactionMessage
- User "0..1"       -- "0..*" : TransactionMessage  [author; null = SYSTEM message]
- Transaction "1"   -- "0..*" : TransactionEvent
- User "0..1"       -- "0..*" : TransactionEvent    [actor; may be null]
- Transaction "1"   -- "0..*" : Evidence
- User "1"          -- "0..*" : Evidence            [capturer]
- Asset "1"         -- "0..*" : WaitlistEntry
- CommunityListing "1" -- "0..*" : WaitlistEntry
- User "1"          -- "0..*" : WaitlistEntry       [borrower]
- Community "1"     -- "0..*" : Flag
- Transaction "0..1" -- "0..*" : Flag               [a flag may or may not reference a transaction]
- User "0..1"       -- "0..*" : Flag                [reporter]
- User "1"          -- "0..*" : ReputationEvent
- Community "1"     -- "0..*" : ReputationEvent
- Transaction "1"   -- "0..*" : ReputationEvent

Label the two User–Transaction associations with their roles (borrower / lender).
Use a filled-diamond composition ONLY for Asset–AssetUnit. Use plain associations
everywhere else; there is no inheritance between these 14 classes.

=== EXPLICITLY EXCLUDE (do NOT draw) ===
- The classes TransactionEventDelivery, AssetUnitConditionHistory and CommunityRule.
- All DTOs, controllers, services, repositories, filters, configuration classes and
  enums-as-classes (show enum attribute types as plain text types only, e.g. "UserStatus").
- Getters, setters and any operations not listed.

=== LAYOUT RULES (Part B) ===
- White background, black text and lines only.
- Neat, readable class boxes; minimal crossing lines; no overlap.
- No colours, gradients, shadows, 3D or decorative icons.
- Keep it simple enough for a student to explain during a viva.

===============================================================
OUTPUT FOR BOTH DIAGRAMS
===============================================================
1. If you can generate rendered images, produce the finished Use-Case diagram and the
   finished Class diagram as images.
2. ALWAYS also output each diagram as Mermaid code in its own code block:
   - Part A: a Mermaid `flowchart` (actors, use cases as `(( ... ))`, a system-boundary
     subgraph, labelled associations).
   - Part B: a Mermaid `classDiagram` with the classes, attributes and relationships
     (include multiplicities such as "1", "0..*").
   The Mermaid code must be renderable at mermaid.live so I can export PNG/SVG.
3. After the diagrams, give a SHORT explanation (about 6 to 10 bullets total) covering:
   - what each actor represents,
   - the meaning of the two «include» relationships, and
   - the most important class relationships and multiplicities.
   This is so I can defend the diagrams in a viva.
4. At the very end, list any point where this prompt was incomplete or ambiguous and mark
   it "to be confirmed" instead of inventing detail.
````

---

### Notes on scope and honesty

- Both prompts deliberately exclude unimplemented roadmap features (AI, tribunal,
  email/push notifications, schedulers) and partially-wired enum values.
- The use-case diagram intentionally contains no `«extend»` relationships because none
  are justified by the verified implementation; the only `«include»` relationships are
  the two enforced evidence prerequisites.
- The class diagram intentionally omits three supporting tables and the entire
  service/DTO layer to keep it simple and viva-explainable.
