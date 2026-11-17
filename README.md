# Findora Backend — Spring Boot

Spring Boot 3.5 / Java 21 port of the Express + TypeScript + MongoDB backend. Same MongoDB database,
same REST routes (`/api/...`), same JSON shapes, same JWT format, same Socket.IO events — the Next.js
frontend keeps working when its API and Socket.IO URLs are configured for the deployment.

## Run

Configuration is defined in `src/main/resources/application.yml`. Spring Boot no
longer imports a local `.env` file. For local development, the YAML provides
development-only JWT values; set `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`,
and `CLOUDINARY_API_SECRET` in the process environment before uploading.

For deployment, set `SPRING_PROFILES_ACTIVE=production` and provide real values
for `JWT_SECRET`, `JWT_REFRESH_SECRET`, `MONGODB_URI`, `CLOUDINARY_CLOUD_NAME`,
`CLOUDINARY_API_KEY`, and `CLOUDINARY_API_SECRET` through the hosting provider's
environment settings. The production profile requires both JWT secrets and all
three Cloudinary values and fails to start if any are missing.

From this directory, run:

```bash
mvn spring-boot:run
```

The API listens on `http://localhost:5000`, and Socket.IO listens on port `5001`.
Alternatively, build and run the packaged application:

```bash
mvn clean package
java -jar target/findora-backend-1.0.0.jar
```

Requires JDK 21+ and Maven. New report photos and chat attachments are uploaded
to Cloudinary. Existing `/uploads/...` URLs still require the old `uploads/`
folder.

## Deploy with Docker on Render

The Dockerfile builds and tests the app with Java 21, then runs Spring Boot,
Netty Socket.IO, and an Nginx reverse proxy as a non-root user. Nginx exposes
only Render's `PORT` (defaulting to `10000`); the application and Socket.IO
listeners stay on private loopback ports inside the container. Docker excludes
the local `application.yml` and packages the environment-driven
`application-container.yml`, so local credential defaults are not included in
the runtime image.

For a Render Web Service:

1. Select **Docker** as the runtime. If deploying from the monorepo, set the
   Root Directory to `findora-springboot-backend`; for this standalone backend
   repository, leave it at the repository root.
2. Set the Health Check Path to `/api/health`.
3. Add these environment variables in Render (do not commit secrets):
   `SPRING_PROFILES_ACTIVE=production`, `MONGODB_URI`, `JWT_SECRET`,
   `JWT_REFRESH_SECRET`, `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`,
   `CLOUDINARY_API_SECRET`, `CORS_ORIGIN`, and `FRONTEND_URL`.
   `CORS_ORIGIN` must be the exact deployed frontend origin (not `*`).
   Render supplies `PORT` automatically.
4. In the frontend hosting provider, set `NEXT_PUBLIC_API_URL` to the backend
   URL ending in `/api` and `NEXT_PUBLIC_SOCKET_URL` to the same backend's public
   origin (without `/api`). Rebuild the frontend after changing these values.

Local Docker build and run:

```bash
docker build -t findora-backend .
docker run --rm -p 10000:10000 --env-file .env findora-backend
```

`.env` and local `uploads/` files are excluded from the image. Configure
Cloudinary for uploaded files in production; Render's container filesystem is
ephemeral unless you attach a persistent disk.

The container routes REST, the health check, and both Socket.IO transports
through the same public port. Nginx forwards `/socket.io/` to the private
Netty listener, including WebSocket upgrades and HTTP long-polling; all other
paths go to Spring Boot. Do not expose or map the internal `SERVER_PORT` or
`SOCKET_PORT`.

## Request and error logs

The backend writes HTTP request logs to the application console, which is also
where IDE terminals and hosting platforms collect application output. Each
request log includes the method, path, status, duration, and a generated request
ID. The same ID is returned in the `X-Request-ID` response header so a failed
request can be matched to its server-side logs. Responses with 4xx statuses are
logged as warnings; 5xx responses and unexpected exceptions are logged as
errors with stack traces. Request bodies, authorization headers, and query
strings are intentionally excluded to avoid logging passwords, tokens, or
personal data.

Demo data (**wipes** users, reports, matches, notifications, messages, bookmarks):

```bash
java -jar target/findora-backend-1.0.0.jar --findora.seed=true
```

## Socket.IO

The Socket.IO handshake requires the existing JWT in the Socket.IO `auth`
payload. Clients can join only their own user room, admins/police can join the
`admins` room, and a conversation room can be joined only by one of that match's
two owners. Typing, read receipts, location updates, and call signaling are
bound to the authenticated user and authorized conversation. The socket server
uses the first explicit `CORS_ORIGIN`; wildcards are rejected.

For local development without Docker, Spring Boot remains on port `5000` and
Socket.IO on `SOCKET_PORT` (default `5001`). The frontend's local Socket.IO
fallback is `http://localhost:5001`. In production, set
`NEXT_PUBLIC_SOCKET_URL` to the backend's public origin; the proxy forwards
`/socket.io/` through Render's public `PORT`.

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

- "Restrict user" only sets `isVerified=false`; it does not block login.
- No refresh-token endpoint (the frontend doesn't use one either); existing `/uploads/...` files remain
  on local disk, while new report photos and chat attachments are stored in Cloudinary.
