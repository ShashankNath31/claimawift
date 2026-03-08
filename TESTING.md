# ClaimSwift Testing Guide

Last documentation sync: 2026-03-08.

## 1) Unit + Integration tests (Maven)

Run from project root:

```cmd
A:\claimswift-master\apache-maven-3.9.13\bin\mvn.cmd clean test
```

Pass criteria:
- `BUILD SUCCESS`

## 2) API smoke testing

Prerequisite: all services are running.

```cmd
powershell -ExecutionPolicy Bypass -File run-logs\api-smoke-test.ps1
```

Output:
- `run-logs\api-smoke-report-<timestamp>.json`
- `run-logs\postman-env-<timestamp>.json`

Pass criteria:
- report `failed` is `0`

## 3) End-to-end workflow test

```cmd
powershell -ExecutionPolicy Bypass -File run-logs\workflow-lifecycle-gateway.ps1
```

Pass criteria:
- report `success` is `true`
- all critical workflow steps pass

## 4) Validation points
- 2FA login flow:
  - `POST /api/auth/login`
  - `POST /api/auth/login/verify-otp`
- Manager assignment workflow:
  - `PATCH /api/claims/{id}/assign`
  - `PATCH /api/claims/{id}/unassign`
- Adjuster assessment workflow:
  - `/assessment/:id`
  - `/api/assessments/*`
- Manager settlement workflow:
  - `POST /api/payments`
  - `GET /api/payments/claim/{claimId}`
- Notification inbox:
  - `GET /api/notifications`
  - `PUT /api/notifications/{id}/read`
- Reporting behavior:
  - date filters applied in summaries
  - report event endpoints persist events

## 5) Recommended CI order
1. `mvn clean test`
2. start full stack
3. `api-smoke-test.ps1`
4. `workflow-lifecycle-gateway.ps1`

## 6) Diagram
- [WORKFLOW_DIAGRAM.md](WORKFLOW_DIAGRAM.md)
