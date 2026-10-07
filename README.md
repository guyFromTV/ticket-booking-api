# Ticket Booking API

A seat-reservation REST API built with **Java 21** and **Spring Boot 4**, written to
solve one problem properly: **a seat must never be sold twice**, no matter how many
people press "book" at the same instant.

Booking happens in two steps. A client *holds* seats, which reserves them for a
limited window, and then *confirms* the hold into a booking. Anything not confirmed
in time is returned to the pool automatically.

---

## Why it is built this way

Overselling is the defining failure of a booking system, and it only appears under
concurrency: the naive "check if free, then mark as taken" sequence passes every
sequential test and still breaks in production, because two requests can both pass
the check before either writes.

Two independent mechanisms prevent that here.

**1. A pessimistic row lock on the contended rows.** The hold service loads seats with
`SELECT ... FOR UPDATE` *before* testing availability, so a competing transaction
waits rather than reading stale state:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select s from Seat s where s.id in :ids order by s.id")
List<Seat> lockAllById(@Param("ids") Collection<Long> ids);
```

The `order by s.id` matters as much as the lock. Without a deterministic lock order,
a request for seats `[1,2]` racing a request for `[2,1]` could each hold one row and
wait for the other — a deadlock. Sorting by id makes every transaction acquire rows
in the same sequence.

**2. An optimistic `@Version` column on `Seat`.** This is the backstop for any path
that does not take a lock. It is not decoration: removing the pessimistic lock and
re-running the concurrency test shows 9 of 50 racing threads failing with
`ObjectOptimisticLockingFailureException` — the version check catching lost updates
the lock would otherwise have prevented.

The two layers do different jobs. The version column guarantees *correctness*; the
row lock turns what would be a pile of retry-me errors into a single clean
`409 Seats unavailable` with the exact seats to re-pick.

**Expiry is checked against the clock, not the sweeper.** A background job releases
timed-out holds, but confirmation independently refuses any hold past its window.
That split means a late, slow, or failed sweeper can never let an expired hold
convert into a booking — the job is cleanup, not correctness.

---

## Running it

Needs a JDK 21+. Nothing else — the database is in-memory and the Maven wrapper
downloads Maven itself.

```bash
./mvnw spring-boot:run
```

The app seeds two demo events on startup.

| What | Where |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| H2 console | http://localhost:8080/h2-console (JDBC `jdbc:h2:mem:ticketing`, user `sa`, no password) |
| Health | http://localhost:8080/actuator/health |

Run the tests with:

```bash
./mvnw test
```

---

## The API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/events` | Create an event and generate its seat inventory from a section layout |
| `GET` | `/api/v1/events` | List events; filter by `venue`, `from`, `to`; paged and sortable |
| `GET` | `/api/v1/events/{id}` | Event detail with a live seat-status breakdown |
| `GET` | `/api/v1/events/{id}/seats` | Seats for an event, filterable by `status` |
| `POST` | `/api/v1/events/{id}/holds` | Hold seats — `409` if any is already taken |
| `GET` | `/api/v1/holds/{ref}` | Inspect a hold |
| `DELETE` | `/api/v1/holds/{ref}` | Release a hold early |
| `POST` | `/api/v1/holds/{ref}/confirm` | Confirm a hold into a booking |
| `GET` | `/api/v1/bookings/{ref}` | Look up a booking |
| `GET` | `/api/v1/bookings?customerEmail=` | A customer's bookings |

### A full booking, end to end

```bash
# Hold two seats
curl -X POST http://localhost:8080/api/v1/events/1/holds \
  -H 'Content-Type: application/json' \
  -d '{"customerEmail":"buyer@example.com","seatIds":[1,2]}'
# -> 201 { "holdReference": "7dfcf3b6-...", "expiresAt": "...", "totalPrice": 150.00 }

# Confirm it
curl -X POST http://localhost:8080/api/v1/holds/7dfcf3b6-.../confirm
# -> 201 { "bookingReference": "BXT24FFS", "totalPrice": 150.00, ... }
```

Asking for seats somebody already holds returns an RFC 7807 problem response that
names them, so a UI can highlight exactly what to change:

```json
{
  "type": "https://api.ticketing.com/problems/seats-unavailable",
  "title": "Seats unavailable",
  "status": 409,
  "detail": "Seats no longer available: Stalls-A-1, Stalls-A-2",
  "unavailableSeats": ["Stalls-A-1", "Stalls-A-2"]
}
```

---

## Design notes

- **Layered on purpose.** Controllers handle HTTP and validation only; all business
  rules and every transaction boundary live in the service layer; entities own their
  own state transitions (`claim`, `release`, `attachToBooking`) rather than being
  mutated from outside.
- **Records for DTOs.** Entities are never serialised directly, so the JSON contract
  cannot drift as the schema changes, and lazy associations can't leak into responses.
- **`BigDecimal` for money, never `double`.** Binary floating point cannot represent
  `0.10` exactly, and rounding drift in prices is unacceptable.
- **The clock is injected.** Services read time from an injected `Clock`, which is why
  the expiry tests move time forward instead of sleeping for ten minutes.
- **Aggregate queries over loaded collections.** The seat-status breakdown uses
  `countByEventIdAndStatus` rather than loading 90 seats to count them, and
  `spring.jpa.open-in-view` is off so no query can sneak out during serialisation.
- **One error contract.** A `@RestControllerAdvice` maps every domain exception to
  RFC 7807 `application/problem+json`; no stack trace or HTML page ever reaches a client.
- **Auditing is centralised.** `createdAt` / `updatedAt` come from a `@MappedSuperclass`
  plus Spring Data auditing, not from code that remembers to set them.

## Tests

25 tests, no `Thread.sleep` anywhere.

| Suite | Covers |
|---|---|
| `ConcurrentHoldTest` | 50 threads racing for one seat (exactly one wins); overlapping sets requested in opposite orders (no deadlock); disjoint sets (all succeed, proving the locking does not over-serialise) |
| `BookingLifecycleTest` | hold → confirm, double-confirm rejection, expiry refused before the sweeper runs, sweeper behaviour, release and re-book, cross-event seats, duplicate ids, status counts |
| `EventControllerTest`, `HoldControllerTest` | web slice with the service mocked: binding, nested Bean Validation, and the problem-detail contract |
| `TicketBookingApiApplicationTests` | context smoke test |

The concurrency tests run real threads against a shared datasource, so each task gets
its own transaction and connection and genuinely contends. They were verified by
removing the lock and watching them fail — a concurrency test that passes either way
is worthless.

## Stack

Java 21 · Spring Boot 4.1 · Spring Web MVC · Spring Data JPA / Hibernate 7 ·
Bean Validation · H2 · springdoc OpenAPI 3 · JUnit 5 · AssertJ · Mockito · Maven ·
Docker · GitHub Actions
