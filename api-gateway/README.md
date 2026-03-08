# API Gateway

Spring Cloud Gateway entrypoint for ClaimSwift APIs.

Last documentation sync: 2026-03-08.

## Port
- `8080`

## Purpose
- Route external API traffic to backend services.
- Apply JWT gateway filter for protected routes.
- Apply circuit-breaker fallback routes.

## Routed API prefixes
- `/api/auth/**` -> `auth-service`
- `/api/claims/**` -> `claim-service`
- `/api/documents/**` -> `document-service`
- `/api/assessments/**` -> `assessment-service`
- `/api/payments/**` -> `payment-service`
- `/api/notifications/**` -> `notification-service`
- `/api/reports/**` -> `reporting-service`

## Public allowlist
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/login/verify-otp`
- `POST /api/auth/refresh`

## Fallback endpoints
- `GET /fallback/auth`
- `GET /fallback/claims`
- `GET /fallback/documents`
- `GET /fallback/assessments`
- `GET /fallback/payments`
- `GET /fallback/notifications`
- `GET /fallback/reports`

## Run
```bash
mvn -pl api-gateway spring-boot:run
```

## Health
- `GET /actuator/health`

## Diagram
- [../WORKFLOW_DIAGRAM.md](../WORKFLOW_DIAGRAM.md)
