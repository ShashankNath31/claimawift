# ClaimSwift Authentication and Authorization Design

Last documentation sync: 2026-03-08.

## Scope
This document captures the implemented auth design used in this repository.

## Implemented Login Sequence
1. User submits username/email + password to `POST /api/auth/login`.
2. Auth service validates credentials and creates a short-lived OTP challenge.
3. OTP is delivered to the registered email.
4. User submits OTP to `POST /api/auth/login/verify-otp`.
5. Service returns JWT + user profile payload.
6. Frontend stores token and routes user by role.

## Token and Service Access
- JWT is validated at API gateway.
- Gateway forwards identity attributes (`userId`, `username`, `roles`) to downstream services.
- Downstream services enforce role checks with method security (`@PreAuthorize`).

## Role Model
- `ROLE_POLICYHOLDER`: self-service claims and history.
- `ROLE_ADJUSTER`: assigned claim assessment + decision workflow.
- `ROLE_MANAGER`: queue governance, assignment, settlement processing.
- `ROLE_ADMIN`: user administration and platform-level operations.

Hierarchy:
`ROLE_ADMIN > ROLE_MANAGER > ROLE_ADJUSTER > ROLE_POLICYHOLDER`

## Registration and Provisioning
- Public registration (`/api/auth/register`) always creates `ROLE_POLICYHOLDER`.
- Internal users (adjuster/manager/admin) are provisioned through admin APIs.

## Data Fields
- User profile includes `phoneNumber`.
- Email verification state is not part of active frontend/backend auth flow.

## Operational Endpoints
- Public:
  - `POST /api/auth/register`
  - `POST /api/auth/login`
  - `POST /api/auth/login/verify-otp`
  - `POST /api/auth/refresh`
- Authenticated:
  - `POST /api/auth/logout`
  - `GET /api/auth/me`
- Role-scoped:
  - `GET /api/auth/adjusters` (manager/admin)
  - `GET /api/auth/managers` (adjuster/manager/admin)
  - `GET /api/auth/admin/users*` (admin)

## Frontend Touchpoints
- `frontend-angular/src/app/pages/login/*`: OTP challenge + verify UI state.
- `frontend-angular/src/app/core/interceptors/auth.interceptor.ts`: bearer token forwarding.
- `frontend-angular/src/app/core/guards/*`: route protection.

## Related Docs
- [AUTH_SERVICE_ARCHITECTURE.md](AUTH_SERVICE_ARCHITECTURE.md)
- [README.md](README.md)
- [WORKFLOW_MATRIX.md](WORKFLOW_MATRIX.md)
