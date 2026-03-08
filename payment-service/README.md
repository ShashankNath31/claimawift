# Payment Service

Simulated payment gateway for claim settlements.

Last documentation sync: 2026-03-08.

## Port
- `8085`

## Database
- Schema: `payment_db`
- Main env vars:
  - `PAYMENT_DB_URL`
  - `PAYMENT_DB_USERNAME`
  - `PAYMENT_DB_PASSWORD`

## Important
- Sandbox simulation mode by default.
- No real provider integration by default config.

## API endpoints (`/api/payments`)
- `POST /` (manager-only settlement create)
- `GET /{id}` (manager-only)
- `GET /claim/{claimId}` (manager-only)
- `GET /internal/all`
- `GET /internal/status/{status}`
- `GET /internal/summary`
- `GET /internal/claim/{claimId}`
- `POST /internal/auto-process` (manager-only)

## Notes
- Frontend settlements tab (`/payments`) is manager-only.
- Internal reporting endpoints remain available for reporting service integration.

## Run
```bash
mvn -pl payment-service spring-boot:run
```

## Diagram
- [../WORKFLOW_DIAGRAM.md](../WORKFLOW_DIAGRAM.md)
