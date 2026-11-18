# Team owner bidding

The app supports `admin`, `owner`, and `guest` roles. Guests retain public read-only access.

1. Sign in as an administrator and open **Teams List → Team Owner Accounts**.
2. Select a team and create the owner's username and password. Passwords are BCrypt hashed. There is no public owner registration.
3. The owner chooses **Team Owner** on the city access page and signs in. They land on the live auction board.
4. The administrator starts a lot. Owners bid in increments of 500 or 1000 for their assigned team. Admins can bid on behalf of a selected team.
5. Only admins can undo, skip, change lots, or finalize sales. A sale with an existing bid must go to the highest bidder at the current price.

Owner/team/city bindings are enforced on the server. The server rejects stale revisions, unavailable players, repeated bids by the leading team, insufficient purse, full rosters, and bids that cannot satisfy roster quotas.

## Live updates

REST commands persist state in PostgreSQL. One `live_auctions` row per city is locked while applying each command, so competing bids at the same revision cannot both succeed. Bid history is scoped to the active round.

After commit, every backend reads the shared event journal and publishes compact notices at `/topic/auction/{city}` through `/ws-auction`. The socket accepts read subscriptions only; client SEND frames cannot publish auction events. Each new subscription receives a targeted snapshot after registration. The React client reconnects, resynchronizes on returning to the tab, and ignores older revisions. Guest views receive the same committed state without gaining write access.

The built-in STOMP broker is local to each node. PostgreSQL event-log polling distributes committed changes to every node; clients fetch a full snapshot for state changes or revision gaps. See the production guide for delivery semantics and deployment requirements.

## Local database

The workspace `.env` points to `wbal_db?currentSchema=auction_app`. This schema is separate from the pre-existing `public` tables, whose text IDs and columns belong to a different application. Flyway manages this schema and Hibernate validates it. Existing app data is preserved. Demo data is created only under the explicit `demo` profile, never under `prod`; normal startup does not seed accounts or rewrite purses.

VS Code's Java launch configuration loads the workspace `.env`. When starting through Maven or a packaged JAR, load those environment variables into the process first. `COOKIE_SECURE=false` is set for local HTTP login; use secure cookies for HTTPS deployments.

## Verification

Run `npm test` and `npm run build` in `auction-frontend`. See [the architecture guide](../ARCHITECTURE.md) for module responsibilities and design decisions.

The PostgreSQL integration suite is opt-in. Set `TEST_POSTGRES_URL` to a JDBC database URL, plus `TEST_POSTGRES_USER` and `TEST_POSTGRES_PASSWORD`, then run `mvn test`. The account must be able to create schemas. Tests create and delete a generated `auction_test_<uuid>` schema and never use the app's tables.

The suite covers real cookie login, owner provisioning, guest and owner permissions, team/city binding, bid eligibility, concurrent bids, stale revisions, undo, sale settlement, WebSocket delivery, and reconnect snapshots.

## Collection iteration

- **Fail-fast mutation:** `Team.removePlayerFromRoster` uses `Iterator.remove()` when the admin releases or deletes a player. It removes entries through the managed list's iterator, without replacing Hibernate's collection or structurally editing the list behind an active iterator. The previous `removeIf` calls were already valid; this centralizes the removal policy. Ordinary fail-fast iterators detect unintended structural edits on a best-effort basis. Their exceptions are not a concurrency control mechanism.
- **Do not suppress traversal failures:** `Team.getPurseRemaining` no longer catches every exception and silently returns the stored purse. An unexpected traversal/data failure must abort the operation rather than allow a bid using a potentially stale balance. The existing fallback for an uninitialized roster remains.
- **Snapshot traversal (often called fail-safe style):** `LiveAuctionSnapshot` copies queue and team lists with `List.copyOf`; `TeamDTO` factories similarly copy roster lists before calculating totals. Published list structures cannot change when the producer later edits its working lists, so delayed WebSocket serialization sees consistent membership for that revision. These are structural snapshots, not deep copies of mutable DTO fields. Create them while the source lists are transaction-confined, and do not modify DTO fields after publishing.
- **Stable auction position:** `LiveAuctionService` captures player IDs before selling or passing a player, rather than keeping managed entities for position lookup. The subsequent queue is queried again after the action.

No `CopyOnWriteArrayList` is introduced: the app has no application-owned shared subscriber list to justify copying its backing array on every write. Spring manages WebSocket subscriptions. Ordinary transaction-local collections remain appropriate for roster and bid processing; PostgreSQL row locks and revision checks still protect competing requests.

See [the production hardening report](../PRODUCTION_HARDENING.md) for the completed fixes and operator steps.
