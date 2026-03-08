# ClaimSwift Postman Testing Guide

Last documentation sync: 2026-03-08.

## Files
- Collection: `run-logs/ClaimSwift-Local-Smoke.postman_collection.json`
- Environment: `run-logs/postman-env-<timestamp>.json`
- Generator script: `run-logs/generate-postman-collection.ps1`

## Recommended endpoint coverage
- Auth: register, login challenge, OTP verify, refresh, logout, admin user management
- Claims: submission, assignment, status updates, history/search, bank details
- Documents: upload/download/list
- Assessment: decision/adjustment flow
- Payments: manager settlement flow + internal reporting APIs
- Notifications: inbox/read/send
- Reporting: summaries, PDFs, event ingestion

## Import in Postman
1. Import collection file.
2. Import environment file.
3. Select the environment.

## Regenerate artifacts

```powershell
powershell -ExecutionPolicy Bypass -File run-logs\api-smoke-test.ps1
powershell -ExecutionPolicy Bypass -File run-logs\generate-postman-collection.ps1
```

## Important notes
- Login flow has OTP verification after `/api/auth/login`.
- `/api/payments` endpoints are manager-only.
- Frontend and external clients should use gateway routes (`http://localhost:8080/api/*`).

## Diagram
- [WORKFLOW_DIAGRAM.md](WORKFLOW_DIAGRAM.md)
