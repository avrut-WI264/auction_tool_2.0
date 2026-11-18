# Production hardening report

Date: 2026-11-15. These changes harden the application and supply deployment tooling. They do not provision a public host, certificates, a production database, or a monitoring service.

## Issues, changes, and reasons

| Issue | Change | Why it was necessary |
| --- | --- | --- |
| Predictable demo administrator accounts | Seeding requires `demo` and is excluded from `prod`. Normal startup no longer drops a constraint or rewrites purses. Production startup rejects insecure cookies, the default database user, missing administrators, demo administrator passwords, and placeholder metrics credentials. An explicit administrator provisioning/reset tool hashes passwords and revokes old sessions. | Prevent known credentials and startup maintenance from becoming production behavior. |
| Public player contact/employee information | Guest player lists, detail responses, queues, team rosters, and live snapshots use allowlisted public fields. Editing uses the protected `players/{id}/private` endpoint; the endpoint also checks the requested city. Public responses no longer contain email, mobile number, employee ID, or inline photos. The existing photo endpoint remains public for displaying player images. | Preserve spectator access without disclosing administrative/contact records. |
| Stored SVG injection | Inline logos are sanitized with DOMPurify, including removal of event handlers, active elements, and external href references. A production reverse-proxy example adds CSP and other response headers. | Removing an XML declaration was not sanitization. |
| Cookie authentication without CSRF protection | Cookie-backed CSRF checks protect mutations, including login/logout and multipart uploads. The browser obtains the token through `/api/auth/csrf` and sends it in the required header. Only an explicit CSRF rejection is automatically retried once. | Cookies are automatically attached by browsers; CORS is not a substitute for CSRF checks. |
| Logout did not invalidate a copied token | JWTs carry a database session identifier. Requests require an active, unexpired session and an enabled account; current roles/city come from the database. Logout revokes that session. Password change revokes every session. | Stolen or previously issued tokens must stop working after revocation/account changes. Existing pre-upgrade tokens require a fresh login. |
| Unbounded login attempts | Shared PostgreSQL counters limit attempts per account and source address. Rejections return HTTP 429 with `Retry-After`. Defaults are 10/account and 100/address per five minutes. | Protect password verification and make limits effective across backend instances. Configure trusted proxies correctly; shared-office NAT may require tuning. |
| Separate application instances missed each other's broadcasts | A shared PostgreSQL event journal distributes committed revisions to every node's local broker. No sticky session or separate message-broker installation is required for this implementation. | A process-local STOMP broker alone cannot distribute changes between servers. |
| A commit could succeed while its notification failed | Journal entries commit atomically with state. Each node advances its cursor only after successful local enqueueing, retrying failures. Clients ignore stale messages, detect revision gaps, and periodically compare the small authoritative revision notice. | Retain committed changes and recover from missed delivery. Delivery can duplicate; this is not exactly-once browser acknowledgement. |
| Other writes could race bidding/settlement | All catalog writes acquire the same city lock and require a paused/completed auction. This includes imports, uploads, roster changes, and generated-logo persistence. Lock/statement waits are bounded; transient database failures return a retryable status. Checked upload exceptions explicitly roll back the transaction. | Keep roster, purse, active player, and event history consistent across every write path. |
| An administrator could reset a stale lot | Start/re-auction/pause/resume require the displayed revision. Running auctions must be paused before switching lots or editing data. Resume preserves the bid; catalog edits invalidate the paused selection. The UI explains this distinction. | Prevent a second administrator or delayed request from silently clearing newer bids. |
| Large per-bid data and queue loading | Bid responses/events are compact. Queue reads use explicit scalar projections; roster eligibility uses aggregate counts/spending rather than loading roster photos. Full snapshots are reserved for initial/state synchronization. Page routes are loaded lazily and hashed assets have long cache lifetimes. | Reduce lock duration, database transfer, browser work, and per-viewer outbound traffic. |
| Cache hashes missed metadata edits | Every catalog/state mutation advances a durable city data revision. The cache checkpoint is captured before data reads. New revision hashes force a complete public catalog refresh; legacy delta support remains for old hash formats. Storage failures do not discard successful network reads. | Renames and contact/metadata changes must not leave old cached data looking current. |
| Uncontrolled production DDL | Flyway V1/V2 migrations support fresh schemas and explicit version-0 baselining of existing app schemas. Hibernate uses `validate`. Production migration execution is a separate operator action by default. | Make database evolution explicit and allow a restricted runtime account without schema ownership. |
| Expired operational records and unbounded queues | Scheduled cleanup removes expired sessions, old login counters, and journal events older than the configured retention (seven days by default). WebSocket message size, queues, buffers, and send time are bounded. Event work has a separate scheduler from heartbeats. | Bound operational growth and avoid a blocked database task preventing heartbeats. |
| Missing operational checks | Health/readiness endpoints are available. Prometheus metrics use a separate bearer token; production management binds a separate port. HTTP/JVM/pool metrics and event counters are available. Container startup uses a non-root user and container-aware JVM memory sizing. | Detect failures and constrain resource use instead of relying on a running process alone. |
| Known dependency vulnerabilities and untested build outputs | Spring Boot was upgraded to 4.1.2 with Jackson 3 and the corresponding test APIs. Vite/React Router and compatible dependencies were updated. CI runs PostgreSQL integration tests, frontend tests/build, and npm audit. A separate verification output directory avoids IDE/compiler interference locally. | Production should use maintained framework dependencies and repeatable checks. |

## Application behavior

- Owners bid only for their assigned team. Guests retain read-only access.
- The first valid command at a displayed revision succeeds; competing stale commands must refresh and retry deliberately.
- **Change Lot** pauses the auction. **Resume** preserves the active player, current bid, leading team, and round.
- Editing catalog data while paused clears the selection; start a new lot afterward. Running/completed auction behavior is checked on the server.
- **Change Password** is available to signed-in administrators and owners and signs out all their sessions.
- Browser reconnection uses exponential backoff. Compact bid notices merge only when consecutive; state changes and gaps load a snapshot. A lightweight revision check runs approximately every 30–35 seconds.
- Each backend tails the event journal independently at a default 200 ms interval. This is suitable for moderate league workloads and has been tested across two instances. Large deployments should benchmark database polling/fan-out and consider a dedicated broker if the measured workload requires it.

## Local data and backup

The changes were applied only to the separate `wbal_db.auction_app` schema. The unrelated `public` schema retains its 14 tables. Before migration, a custom-format backup was taken under the ignored `.local-backups` directory. Restoring it into a disposable database verified the original 6 teams, 21 players, and 6 users. These counts were also checked after migration.

Existing local demo credentials remain usable under the local profile so administrators can change them. They are rejected by production startup. Do not copy the local `.env` into production.

Windows operator commands from the workspace root:

```powershell
# Backup first. The script refuses to overwrite a backup file.
.\scripts\backup.ps1

# First migration of an existing app schema only; inspect/backup it before baselining.
.\scripts\database.ps1 -Operation migrate -BaselineExisting

# Subsequent migrations omit BaselineExisting.
.\scripts\database.ps1 -Operation migrate

# Password is prompted securely, not passed on the command line.
.\scripts\database.ps1 -Operation provision-admin -City Pune -Username pune_admin -AllowReset
```

Use `-EnvFile` to select a migration/operator environment. Runtime credentials should not own the schema. Do not enable baselining against an unknown database: it establishes that the existing schema is the supported V1 layout.

On Linux, load the required variables from a secret manager, compile the tool, and run it without credentials in the argument list:

```sh
cd auction-backend
mvn -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/tool-classpath.txt
java -cp "target/classes:$(cat target/tool-classpath.txt)" com.wissen.auction.ops.DatabaseTool migrate
```

Provisioning additionally requires `BOOTSTRAP_CITY`, `BOOTSTRAP_USERNAME`, and `BOOTSTRAP_PASSWORD` in process environment. Reset requires `BOOTSTRAP_ALLOW_RESET=true`. Explicit existing-schema baseline requires `APP_ALLOW_BASELINE_EXISTING=true`. Do not persist these secrets in the repository.

## Production operator requirements

1. Provision a managed PostgreSQL database with TLS and backups. Run migrations using an operator account. Use `deploy/runtime-role.sql` as a reviewed template to grant the runtime role only application-table/sequence privileges; it does not grant schema creation or writes to migration history. Set its password interactively.
2. Provision administrator credentials, then create owner accounts through the admin UI. Configure unique random JWT and metrics secrets. Every application instance needs the same JWT secret and database.
3. Create the production environment from `.env.production.example`. Use `prod`, an explicit HTTPS frontend origin, a dedicated schema, and restricted runtime credentials. Production disables automatic migration execution unless explicitly enabled for a controlled migration job.
4. Terminate TLS at a configured ingress/load balancer. Forward WebSocket upgrade headers and allow a heartbeat-friendly idle timeout. Only trust the actual proxy addresses. The included Nginx example is an internal HTTP service; it does **not** install a public certificate or configure DNS/HSTS.
5. Keep the management port private. Probe `/actuator/health/readiness`; scrape `/actuator/prometheus` with `Authorization: Bearer <METRICS_TOKEN>`. Alert on HTTP error/latency, pool saturation, process memory, database failures, and event-dispatch errors. Dashboard/alert routing requires the actual monitoring environment.
6. Review ingress request/connection limits for expected office NAT and audience size. Set container/database resources and connection limits from measured load. Docker Compose is a single-backend example; a production load balancer/service-discovery system is responsible for replica lifecycle and readiness.
7. Schedule encrypted, off-site backups and periodic restore drills. `scripts/backup.ps1` supplies the application-schema backup operation, not an external storage/scheduling service. During an actual restore, stop all application nodes first and refresh/reconnect clients after restart to discard pre-restore revisions.
8. Run a staging soak/load test with realistic player photos/data, audience count, bidder count, network latency, deployment restarts, and database outages. The local smoke test is not a capacity guarantee.

Container execution was not tested locally because Docker Desktop's engine was stopped. The Compose configuration was parsed successfully with the example environment. No production service was published or external infrastructure changed.

## Verification artifacts

The final isolated backend verification passed **24 tests**. The frontend passed **18 tests**, a clean `npm ci`, its production build, and `npm audit` with **0 reported vulnerabilities**. Lazy routes reduced the main JavaScript chunk from approximately 627 KB to 223 KB (uncompressed).

- Backend PostgreSQL integration suite covers authentication, real CSRF cookies/headers, revocation, role/account changes, public-data filtering, pause/revision rules, concurrent bids, shared-node delivery, and failure recovery.
- Fresh-schema and explicit legacy-baseline migrations are exercised against PostgreSQL 18.
- Frontend tests cover SVG sanitization, CSRF refresh/retry, compact-message ordering/gaps, session races, catalog races, and cache failure handling.
- `auction-backend/target/load-smoke.json` records the local 100-read / 50-bid smoke test, including measured latency. The bid test uses two test teams and one displayed revision: exactly one accepted bid and 49 stale rejections are expected.
- The packaged application reached readiness `UP`. Prometheus returned HTTP 200 with the metrics token and HTTP 401 without authentication. A separate read-only smoke test connected all 20 guest WebSocket clients and completed 100 state reads with zero errors (local read p95: 371 ms). Results are in `auction-backend/target-verify/packaged-verification.txt`; these local measurements are not a production capacity guarantee.
- `mvn -Dauction.build.directory=target-verify verify` can be used when the IDE builds into `target`. Generated test schemas/databases are disposable and do not use app/public tables.

For a read-only staging smoke test, run `npm run load:smoke` in `auction-frontend`. `BASE_URL`, `CITY`, `VUS`, and `REQUESTS` configure it; defaults are localhost:8080, pune, 20 WebSocket clients, and 100 state reads. It never places bids or edits data. Run it only against an environment you own or are authorized to test.
