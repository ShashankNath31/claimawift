# Claim Service

Claim lifecycle management service.

Last documentation sync: 2026-03-08.

## Port
- `8082`

## Database
- Schema: `claim_db`
- Main env vars:
  - `CLAIM_DB_URL`
  - `CLAIM_DB_USERNAME`
  - `CLAIM_DB_PASSWORD`

## API endpoints (`/api/claims`)
- `POST /`
- `GET /{id}`
- `GET /number/{claimNumber}`
- `GET /my-claims`
- `GET /history`
- `GET /my-claims/search`
- `GET /policies/my`
- `PUT /{id}/bank-details`
- `GET /{id}/bank-details`
- `GET /{id}/audit`
- `GET /`
- `PUT /{id}`
- `PUT /{id}/status`
- `PATCH /{id}/status`
- `PATCH /{id}/approve`
- `PATCH /{id}/disapprove`
- `PATCH /{id}/adjust`
- `PATCH /{id}/assign`
- `PATCH /{id}/unassign`
- `GET /status/{status}`
- `GET /pending`
- `GET /assigned`
- `GET /statistics`
- `GET /search`
- `GET /adjuster/{adjusterId}`
- `GET /summary`
- `GET /internal/all`
- `GET /internal/status/{status}`
- `GET /internal/adjuster/{adjusterId}`
- `GET /internal/summary`
- `GET /internal/{id}`
- `DELETE /{id}`
- `GET /health`

## Notes
- Enforces claim workflow transitions.
- Supports manager assignment/unassignment workflows.
- Stores policyholder settlement bank details.
- Emits workflow notifications and reporting events.

## Run
```bash
mvn -pl claim-service spring-boot:run
```

## Diagram
- [../WORKFLOW_DIAGRAM.md](../WORKFLOW_DIAGRAM.md)
