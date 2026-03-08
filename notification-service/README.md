# Notification Service

Real-time notification service with REST APIs and WebSocket/STOMP delivery.

Last documentation sync: 2026-03-08.

## Port
- `8086`

## Database
- Schema: `notification_db`
- Main env vars:
  - `NOTIFICATION_DB_URL`
  - `NOTIFICATION_DB_USERNAME`
  - `NOTIFICATION_DB_PASSWORD`

## WebSocket
- STOMP SockJS endpoint: `/ws/notifications`
- Broker destinations: `/topic`, `/queue`
- App destination prefix: `/app`
- User destination prefix: `/user`

## API endpoints (`/api/notifications`)
- `GET /`
- `GET /unread`
- `GET /unread/count`
- `PUT /{id}/read`
- `PUT /read-all`
- `POST /send`
- `DELETE /{id}`
- `POST /test`
- `POST /internal/claim-status`
- `POST /internal/payment-processed`

## Frontend integration
- Top navigation notification bell fetches unread count and inbox.
- Per-notification deep links route users to relevant pages.

## Run
```bash
mvn -pl notification-service spring-boot:run
```

## Diagram
- [../WORKFLOW_DIAGRAM.md](../WORKFLOW_DIAGRAM.md)
