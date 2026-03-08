# ClaimSwift Service Map

Last documentation sync: 2026-03-08.

## Core Platform Services

| Service | Port | Responsibility |
|---|---:|---|
| `config-server` | 8888 | Centralized configuration |
| `eureka-server` | 8761 | Service discovery registry |
| `api-gateway` | 8080 | Single entrypoint, JWT filter, circuit breaker fallback |

## Domain Services

| Service | Port | Main Area |
|---|---:|---|
| `auth-service` | 8081 | Register, login challenge + OTP verify, admin user management |
| `claim-service` | 8082 | Claim submission, assignment, status lifecycle, policy portfolio, claim bank details |
| `document-service` | 8083 | Evidence upload/download and document metadata |
| `assessment-service` | 8084 | Assessment and adjustment workflows |
| `payment-service` | 8085 | Manager-led settlement processing |
| `notification-service` | 8086 | Notification APIs + WebSocket delivery |
| `reporting-service` | 8087 | Aggregated analytics, PDFs, report event storage |

## Gateway Routes

Configured in `config-server/src/main/resources/config/api-gateway.yml`:

- `/api/auth/**` -> `auth-service`
- `/api/claims/**` -> `claim-service`
- `/api/documents/**` -> `document-service`
- `/api/assessments/**` -> `assessment-service`
- `/api/payments/**` -> `payment-service`
- `/api/notifications/**` -> `notification-service`
- `/api/reports/**` -> `reporting-service`

## Frontend

| Module | Port | Notes |
|---|---:|---|
| `frontend-angular` | 4200 (dev) | Angular 17 app, uses gateway APIs only |

Role-specific screens:
- Policyholder: `dashboard`, `policies`, `claim`, `status`, `history`
- Adjuster: `dashboard`, `claims`, `assessment/:id`, `reports`
- Manager: `dashboard`, `claims`, `payments`, `reports`
- Admin: `dashboard`, `claims`, `documents`, `reports`, `admin/users`

## Datastores

- `auth_db`
- `claim_db`
- `document_db`
- `assessment_db`
- `payment_db`
- `notification_db`
- `report_db`

## Diagram
- [WORKFLOW_DIAGRAM.md](WORKFLOW_DIAGRAM.md)
