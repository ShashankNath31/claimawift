# Assessment Service

Claim assessment and adjustment decision service.

Last documentation sync: 2026-03-08.

## Port
- `8084`

## Database
- Schema: `assessment_db`
- Main env vars:
  - `ASSESSMENT_DB_URL`
  - `ASSESSMENT_DB_USERNAME`
  - `ASSESSMENT_DB_PASSWORD`

## API endpoints (`/api/assessments`)
- `POST /`
- `POST /decision`
- `POST /adjustment`
- `GET /{id}`
- `GET /claim/{claimId}`
- `GET /my-assessments`
- `GET /{assessmentId}/adjustments`
- `POST /request`
- `POST /notify-complete`
- `GET /health`

## Frontend integration
- Adjuster assessment workspace route: `/assessment/:id`
- UI client service: `frontend-angular/src/app/core/services/assessment-api.service.ts`

## Notes
- Endpoints are adjuster/manager/admin protected.
- Used by adjusters for approve/disapprove/adjust decisions after evidence review.

## Run
```bash
mvn -pl assessment-service spring-boot:run
```

## Diagram
- [../WORKFLOW_DIAGRAM.md](../WORKFLOW_DIAGRAM.md)
