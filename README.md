# ClaimSwift

ClaimSwift is a Spring Boot microservices platform with an Angular 17 frontend for insurance claim lifecycle management.

Last documentation sync: 2026-03-08.

## Stack
- Java 17, Spring Boot 3, Spring Cloud
- API Gateway: Spring Cloud Gateway (`http://localhost:8080`)
- Service discovery: Eureka (`http://localhost:8761`)
- Central config: Config Server (`http://localhost:8888`)
- Frontend: Angular 17 (`http://localhost:4200`)
- Database: MySQL 8 (schema per service)

## Service Ports
- `auth-service`: `8081`
- `claim-service`: `8082`
- `document-service`: `8083`
- `assessment-service`: `8084`
- `payment-service`: `8085`
- `notification-service`: `8086`
- `reporting-service`: `8087`

## API Base Paths (Gateway)
- `/api/auth/*`
- `/api/claims/*`
- `/api/documents/*`
- `/api/assessments/*`
- `/api/payments/*`
- `/api/notifications/*`
- `/api/reports/*`

Use `http://localhost:8080/api/*` from clients.

## Security Model
- Public auth endpoints:
  - `POST /api/auth/register`
  - `POST /api/auth/login`
  - `POST /api/auth/login/verify-otp`
  - `POST /api/auth/refresh`
- Registration creates only `ROLE_POLICYHOLDER`.
- Admin-only user management:
  - `GET /api/auth/admin/users`
  - `GET /api/auth/admin/users/{id}`
  - `POST /api/auth/admin/users`
  - `PUT /api/auth/admin/users/{id}/roles`
  - `PATCH /api/auth/admin/users/{id}/status`
- Role hierarchy in services:
  - `ROLE_ADMIN > ROLE_MANAGER > ROLE_ADJUSTER > ROLE_POLICYHOLDER`

## Frontend Role Access
- Public: `/`, `/login`, `/register`
- Policyholder: `/dashboard`, `/policies`, `/claim`, `/status`, `/history`
- Adjuster: `/dashboard`, `/claims`, `/assessment/:id`, `/reports` (claim analytics only)
- Manager: `/dashboard`, `/claims`, `/payments`, `/reports`
- Admin: `/dashboard`, `/claims`, `/documents`, `/reports`, `/admin/users`
- All authenticated users: top-right profile menu + notification bell

## Notes on Current Behavior
- Login is 2-step OTP: credential challenge then OTP verification.
- Claim assessment screen is integrated with `assessment-service` (`/assessment/:id`).
- Settlements are manager-only in UI and API (`/payments`).
- Documents page is admin-only.
- Policyholders can submit claim bank details after approval; manager settlement can resolve by claim reference.

## Local Startup
1. Start backend services in order: Config, Eureka, domain services, Gateway.
2. Start frontend:

```bash
cd frontend-angular
npm install
npm start
```

## Diagram and Operational Docs
- [WORKFLOW_DIAGRAM.md](WORKFLOW_DIAGRAM.md)
- [RUNBOOK.md](RUNBOOK.md)
- [WORKFLOW_MATRIX.md](WORKFLOW_MATRIX.md)
- [API_CALLS_EXPLAINED.md](API_CALLS_EXPLAINED.md)
- [SYSTEM_DOCUMENTATION.md](SYSTEM_DOCUMENTATION.md)
