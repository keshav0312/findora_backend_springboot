# Findora Backend — Spring Boot

Spring Boot 3.5 / Java 21 port of the Express + TypeScript + MongoDB backend. Same MongoDB database,
same REST routes (`/api/...`), same JSON shapes, same JWT format, same Socket.IO events — the Next.js
frontend keeps working with one small change (see "Socket.IO").

## Run

```bash
cp .env.example .env        # fill in JWT_SECRET / JWT_REFRESH_SECRET / MONGODB_URI (use your old values)
mvn spring-boot:run         # API on http://localhost:5000, Socket.IO on :5001
# or: mvn clean package && java -jar target/findora-backend-1.0.0.jar
```

Requires JDK 21+ and Maven. Copy your old `uploads/` folder next to where you run the app so existing
photo URLs (`/uploads/<file>`) keep resolving.

Demo data (**wipes** users, reports, matches, notifications, messages, bookmarks):

```bash
java -jar target/findora-backend-1.0.0.jar --findora.seed=true
```

## Socket.IO (one frontend change)

netty-socketio cannot share Tomcat's port, so Socket.IO runs on `SOCKET_PORT` (default 5001). In the
frontend `lib/socket.ts` change

```ts
const SOCKET_URL = API_URL.replace(/\/api\/?$/, "");
```
to
```ts
const SOCKET_URL = process.env.NEXT_PUBLIC_SOCKET_URL || "http://localhost:5001";
```
and set `NEXT_PUBLIC_SOCKET_URL` for production. (Alternatively put a reverse proxy in front that routes
`/socket.io/` to port 5001.) `CORS_ORIGIN`: only the first origin is used for Socket.IO.

## Mapping from the Node project

| Node | Spring |
|---|---|
| `routes/*`, `controllers/*` | `web/*Controller` |
| `controllers/itemFactory.ts` | `service/ItemService` (+ `ItemKind`) |
| `middleware/auth.middleware.ts` | `security/*` (JWT filter, role rules in `SecurityConfig`) |
| `middleware/upload.middleware.ts` | `service/FileStorageService` |
| `models/*` (Mongoose) | `model/*` (Spring Data MongoDB; same collections) |
| `utils/matching.ts`, `utils/geo.ts` | `matching/MatchingEngine`, `GeoUtils` (output verified identical) |
| `services/*.service.ts` | `service/AiService`, `EmailService`, `WhatsAppService` |
| `server.ts` (socket.io) | `socket/*` |
| `seed.ts` | `config/DataSeeder` |
| express-rate-limit / helmet / compression | `RateLimitFilter` / Spring Security headers / `server.compression` |

## Deliberate differences (bugs/gaps fixed in the port)

- `GET /lost/:id`, `/found/:id`: the owner now actually sees their verification answers (before, the route
  was unauthenticated so nobody ever did).
- `GET /matches` no longer leaks the other party's verification answers.
- `PATCH /lost|found/:id` only edits whitelisted fields (before: `Object.assign(item, req.body)` allowed
  overwriting `owner`, `status`, etc.).
- Chat endpoints require you to be a party of the match, and the recipient must be the other party.
- Marking a match `returned` awards trust points once, not on every call.
- Filters `city`/`color`/`brand` are escaped "contains" searches instead of raw user regexes.
- Uploaded files must have a safe extension (no serving of arbitrary types by MIME claim).
- Unexpected errors return a generic 500 message instead of the raw exception text.
- Admin user list returns both `_id` and `id` (the admin page reads `id`).
- Presence: repeated `user:join` on one socket counts once.
- Email HTML escapes user-supplied values.

## Known gaps (same as the Node version)

- Socket.IO connections are unauthenticated: any client can join any user/`admins` room. Fix by sending the
  JWT in the socket handshake and checking it in `SocketEventHandlers`.
- "Restrict user" only sets `isVerified=false`; it does not block login.
- No refresh-token endpoint (the frontend doesn't use one either); photos are on local disk.
