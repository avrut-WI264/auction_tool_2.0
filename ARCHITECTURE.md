# Auction application structure

The live-auction, authentication, and frontend catalog flows use feature-based modules with explicit application, domain, and transport responsibilities. Existing REST paths, response field names, roles, database tables, and the public `AppContext` contract are retained.

## Backend responsibilities

```text
com.wissen.auction
├── auction
│   ├── AuctionController                 HTTP mapping and request validation
│   ├── LiveAuctionService                Transactional use-case orchestration
│   ├── LiveAuction                       State transitions and revision guards
│   ├── LiveAuctionRepository             PostgreSQL session persistence/locking
│   ├── LiveAuctionSnapshot               Detached outbound list snapshots
│   ├── api/                              Request records for each command
│   ├── domain/                           Validated city, lot, and event types
│   ├── application/
│   │   ├── AuctionQueue                  Filtered queue reads
│   │   ├── AuctionSnapshotFactory        Snapshot mapping
│   │   ├── AuctionSnapshotReader         Read-only application port
│   │   ├── AuctionSettlementService      Roster and purse writes
│   │   └── AuctionHistoryService         Bid-history reads
│   └── realtime/
│       ├── AuctionSocketConfig           Endpoint, broker, heartbeat wiring
│       ├── AuctionSubscriptionInterceptor Subscription validation/readiness
│       ├── AuctionSubscriptionSnapshotSender Targeted initial state adapter
│       └── AuctionEventLog               Transactional durable event recording
?       ??? AuctionEventDispatcher        Per-node committed-event delivery
?       ??? AuctionNotice                 Compact bid and synchronization events
├── auth
│   ├── AuthController / TeamOwnerController HTTP adapters
│   ├── AuthService                       Credential verification and sessions
│   ├── TeamOwnerService                  Owner provisioning transaction
│   ├── CityAccessService                 Account, role, and city authorization
│   ├── AuthenticatedAccount               Credential-free authorization data
│   ├── UserProfile / AuthenticatedSession Public/internal response separation
│   └── AuthCookieFactory                 Cookie settings shared by login/logout
└── team
    └── TeamPurchasePolicy                Shared purse, capacity, and quota rules
```

The older `AuctionService`, `AuctionStateDTO`, and `BidResponseDTO` were unused by the active API and contained conflicting bidding/state logic. They were removed after their remaining settlement and history responsibilities were extracted.

An auction command authorizes the actor, locks the city's session, checks the displayed revision, locks the active player, applies the policy and state transition, and creates an outbound snapshot in one transaction. `AuctionSettlementService` requires an existing transaction, preventing a sale from committing independently of the auction state. An event-log row is written in the same transaction. Each backend independently tails committed revisions and feeds its local socket broker; publication failures retain the cursor for retry. Clients reconcile gaps through the authoritative snapshot API.

`LiveAuction` exposes named transitions rather than public setters. `AuctionCity` and `AuctionLot` reject invalid values at construction. Bid and sale eligibility use the same `TeamPurchasePolicy`, avoiding rule drift between accepting a bid and settling it.

The socket adapter depends on the read-only `AuctionNoticeReader`, not the command service. Initial socket messages and periodic checks read only the small session row; full snapshots use separate projection queries. All dependencies are explicit constructor arguments. The outbound channel is injected lazily because Spring creates it during broker initialization; application services are never discovered through `ApplicationContext.getBean`.

Authentication returns a typed `UserProfile` to HTTP clients. The JWT remains in the HttpOnly cookie and the internal `AuthenticatedSession`. Its session identifier is checked against a shared, revocable database session, and current account roles are loaded from the database on authenticated requests. Cookie writes require a CSRF token, and login limits are database-backed across nodes. Invalid credentials now correctly return HTTP 401. Owner controllers delegate account creation to the service rather than managing repositories and password hashing directly.

## Frontend responsibilities

```text
src/
├── context/AppContext.jsx                Composition and compatibility facade
├── features/auth/
│   ├── authApi.js                        Authentication/owner HTTP adapter
│   └── useAuthSession.js                 Verified identity and session lifecycle
├── features/auction/
│   ├── auctionApi.js                     Auction HTTP adapter
│   ├── auctionModel.js                   Pure selectors and merge functions
│   ├── useAuctionActions.js              Command lifecycle and conflict recovery
│   └── useLiveAuction.js                 STOMP connection and revision ordering
└── features/catalog/
    ├── catalogApi.js                     Player/team HTTP and logo adapter
    ├── catalogModel.js                   Pure delta/roster transformations
    ├── catalogRepository.js              Cache-aside repository
    ├── useCityCatalog.js                 Data lifecycle and stale-response guards
    └── useCatalogActions.js              Server-confirmed data mutations
```

Components keep consuming the existing context fields. The provider composes hooks; it no longer contains HTTP implementations, cache merge policy, or CRUD implementations. Server-verified identity is separate from localStorage route preferences.

The catalog repository accepts HTTP and storage adapters through its constructor, allowing tests to exercise cache behavior without a browser or database. A version checkpoint is recorded before downloading data so an update during a read cannot be skipped by the next delta fetch. Failed downloads retain cached data, partial refreshes invalidate the combined cache version, and older in-flight loads cannot overwrite newer live team updates.

Team deletion updates local state after server confirmation. A rejection therefore preserves the original roster and sale prices without fragile rollback logic. Responses for a previously selected city are ignored by the state hooks.

## Design decisions

- **Single responsibility:** authorization, purchase eligibility, transactions, mapping, transport, and presentation state have separate owners.
- **Policy pattern:** purchase eligibility is shared by bidding and settlement. Changing a roster rule has one implementation point.
- **Repository pattern:** Spring Data owns backend persistence; the frontend catalog repository owns cache-aside reads through replaceable adapters.
- **Factory pattern:** snapshot and cookie factories centralize construction invariants and transport settings.
- **Observer/event pattern:** application events decouple transaction completion from WebSocket delivery.
- **Interface segregation and dependency inversion:** the socket consumes a read-only application port, while repository adapters are injected explicitly. No service locator or generic base-service hierarchy is introduced.
- **Composition:** named use cases and hooks compose focused collaborators. Interfaces are used at real boundaries, rather than creating an interface for every class.

These are incremental boundaries around the active flows. Other existing feature code, including Excel parsing and page styling, remains in its current modules.

## Validation

- `npm test` in `auction-frontend`: pure model, repository, and React hook regression tests.
- `npm run build` in `auction-frontend`: production bundling.
- `mvn test` in `auction-backend`: snapshot and domain-policy tests. Set `TEST_POSTGRES_URL`, `TEST_POSTGRES_USER`, and `TEST_POSTGRES_PASSWORD` to include the real PostgreSQL integration suite, which uses a generated disposable schema.

The integration suite covers cookie authentication, owner provisioning, permissions, competing bids, stale revisions, eligibility, undo, sale settlement, explicit control endpoints, managed roster removals, WebSocket updates, and reconnect snapshots. React hook tests cover duplicate bids, conflict recovery, late authentication responses, city switching, and live updates racing cache hydration.

The existing fail-fast roster removal and immutable list-snapshot behavior are preserved. PostgreSQL locks and revision checks remain responsible for concurrent requests; iterator behavior does not provide transaction safety. Each STOMP broker serves its own node; the shared PostgreSQL event log distributes changes across instances. This provides replay/retry for enqueueing and revision-based client reconciliation, not exactly-once delivery to browsers. All nodes must use the same primary database and JWT secret.

Production controls, migration/backup commands, deployment requirements, and the issue-by-issue resolution report are documented in [PRODUCTION_HARDENING.md](PRODUCTION_HARDENING.md).
