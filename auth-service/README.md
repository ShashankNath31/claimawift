# Auth Service

Authentication and authorization service (JWT + OTP login).

Last documentation sync: 2026-03-08.

## Port
- `8081`

## Database
- Schema: `auth_db`
- Main env vars:
  - `AUTH_DB_URL`
  - `AUTH_DB_USERNAME`
  - `AUTH_DB_PASSWORD`

## API endpoints (`/api/auth`)
- `POST /register`
- `POST /login`
- `POST /login/verify-otp`
- `POST /logout`
- `POST /refresh`
- `GET /me`
- `GET /adjusters` (manager/admin)
- `GET /managers` (adjuster/manager/admin)
- `GET /admin/users` (admin)
- `GET /admin/users/{id}` (admin)
- `POST /admin/users` (admin)
- `PUT /admin/users/{id}/roles` (admin)
- `PATCH /admin/users/{id}/status` (admin)
- `GET /health`

## Notes
- Public registration creates policyholder users.
- Internal privileged users are managed through admin endpoints.
- `phoneNumber` is retained; email verification flag is not part of active flow.

## Run
```bash
mvn -pl auth-service spring-boot:run
```

## Diagram
- [../WORKFLOW_DIAGRAM.md](../WORKFLOW_DIAGRAM.md)
