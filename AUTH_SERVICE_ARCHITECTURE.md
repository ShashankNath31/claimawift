# ClaimSwift Authentication and Authorization Service Architecture

Last documentation sync: 2026-03-08.

## Current auth flow
1. `POST /api/auth/register` creates policyholder accounts.
2. `POST /api/auth/login` validates credentials and creates an OTP challenge.
3. `POST /api/auth/login/verify-otp` verifies OTP and issues JWT.
4. `POST /api/auth/refresh` refreshes token.
5. `POST /api/auth/logout` invalidates token.

## Admin controls
Admin-only endpoints:
- `GET /api/auth/admin/users`
- `GET /api/auth/admin/users/{id}`
- `POST /api/auth/admin/users`
- `PUT /api/auth/admin/users/{id}/roles`
- `PATCH /api/auth/admin/users/{id}/status`

Additional staff endpoint:
- `GET /api/auth/managers` for adjuster/manager/admin workflows

## Data model notes
- `phoneNumber` is retained and used by frontend profile/registration.
- Email verification flag is not part of the active workflow.

## Role hierarchy
`ROLE_ADMIN > ROLE_MANAGER > ROLE_ADJUSTER > ROLE_POLICYHOLDER`

## Frontend integration
- Login page handles OTP challenge and verification.
- Admin page: `/admin/users`.
- API clients:
  - `frontend-angular/src/app/core/services/auth.service.ts`
  - `frontend-angular/src/app/core/services/admin-api.service.ts`

## Related docs
- [README.md](README.md)
- [WORKFLOW_MATRIX.md](WORKFLOW_MATRIX.md)
- [WORKFLOW_DIAGRAM.md](WORKFLOW_DIAGRAM.md)
