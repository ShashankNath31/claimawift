# Reporting Service

Reporting and analytics service for claims and payments.

Last documentation sync: 2026-03-08.

## Port
- `8087`

## Database
- Schema: `report_db`
- Main env vars:
  - `REPORTING_DB_URL`
  - `REPORTING_DB_USERNAME`
  - `REPORTING_DB_PASSWORD`

## API endpoints (`/api/reports`)
- `GET /claims/summary` (adjuster/manager/admin)
- `GET /claims/summary/pdf` (manager/admin)
- `GET /payments` (manager/admin)
- `GET /payments/pdf` (manager/admin)
- `GET /adjusters/performance` (manager/admin)
- `GET /adjusters/performance/pdf` (manager/admin)
- `POST /claim-event`
- `POST /status-change`

## Current behavior notes
- Report date filters are applied in backend calculations.
- Claim/status report events are persisted in `report_events`.
- Service aggregates data from claim-service and payment-service internal APIs.

## Frontend visibility
- Adjuster: claim summary analytics only.
- Manager/Admin: full analytics + PDF exports.

## Run
```bash
mvn -pl reporting-service spring-boot:run
```

## Diagram
- [../WORKFLOW_DIAGRAM.md](../WORKFLOW_DIAGRAM.md)
