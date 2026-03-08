# ClaimSwift System Documentation

Last documentation sync: 2026-03-08.

## 1. System Overview

ClaimSwift is a distributed claims-processing platform built with Spring Boot microservices and an Angular frontend.

Primary goals:
- role-based secure operations
- end-to-end claim lifecycle
- service isolation with centralized routing/config/discovery
- reportability and auditable workflow events

## 2. Architecture and Workflow Diagram

- Detailed workflow/architecture diagrams: [WORKFLOW_DIAGRAM.md](WORKFLOW_DIAGRAM.md)

## 3. Service Responsibilities

| Service | Port | Responsibility | Database |
|---|---:|---|---|
| `config-server` | 8888 | Centralized externalized configuration | N/A |
| `eureka-server` | 8761 | Service discovery registry | N/A |
| `api-gateway` | 8080 | JWT-protected routing and circuit-breaker fallback | N/A |
| `auth-service` | 8081 | Register, login challenge, OTP verification, admin user management | `auth_db` |
| `claim-service` | 8082 | Claim submission, assignment, status transitions, policy portfolio, claim bank details | `claim_db` |
| `document-service` | 8083 | Evidence upload/download and metadata | `document_db` |
| `assessment-service` | 8084 | Assessment workflow and decision processing | `assessment_db` |
| `payment-service` | 8085 | Manager-only settlement processing | `payment_db` |
| `notification-service` | 8086 | Notification delivery and websocket updates | `notification_db` |
| `reporting-service` | 8087 | Claims/payments/adjuster analytics + PDF exports + event storage | `report_db` |

## 4. Security and Access Model

- Gateway is the single client HTTP entrypoint.
- Role hierarchy:
  - `ROLE_ADMIN > ROLE_MANAGER > ROLE_ADJUSTER > ROLE_POLICYHOLDER`
- Public auth endpoints:
  - `POST /api/auth/register`
  - `POST /api/auth/login`
  - `POST /api/auth/login/verify-otp`
  - `POST /api/auth/refresh`
- Frontend role screens:
  - Policyholder: `/policies`, `/claim`, `/status`, `/history`
  - Adjuster: `/claims`, `/assessment/:id`, `/reports`
  - Manager: `/claims`, `/payments`, `/reports`
  - Admin: `/claims`, `/documents`, `/reports`, `/admin/users`

## 5. Reporting and Analytics Notes

- Reports API supports date range filtering in report generation.
- Reporting service persists claim/status report events in `report_events`.
- Adjusters see claim analytics only.
- Manager/Admin can access payments and adjuster performance analytics plus PDF export.

## 6. Deployment Prerequisites

- Java 17
- Maven 3.9+
- MySQL 8+
- Node.js 18+

## 7. Startup Order

1. `config-server`
2. `eureka-server`
3. `auth-service`
4. `claim-service`
5. `document-service`
6. `assessment-service`
7. `payment-service`
8. `notification-service`
9. `reporting-service`
10. `api-gateway`
11. `frontend-angular`

## 8. Related Docs

- [README.md](README.md)
- [SERVICES.md](SERVICES.md)
- [RUNBOOK.md](RUNBOOK.md)
- [WORKFLOW_MATRIX.md](WORKFLOW_MATRIX.md)
- [API_CALLS_EXPLAINED.md](API_CALLS_EXPLAINED.md)
- [TESTING.md](TESTING.md)
