# Document Service

Document upload/download and claim evidence metadata service.

Last documentation sync: 2026-03-08.

## Port
- `8083`

## Database
- Schema: `document_db`
- Main env vars:
  - `DOCUMENT_DB_URL`
  - `DOCUMENT_DB_USERNAME`
  - `DOCUMENT_DB_PASSWORD`

## File storage
- Config key: `file.storage.path`
- Default: `./uploads`
- Multipart limits: 10MB

## API endpoints (`/api/documents`)
- `POST /upload` (multipart)
- `GET /{id}`
- `GET /claim/{claimId}`
- `GET /user/{userId}`
- `GET /claim/{claimId}/type/{documentType}`
- `GET /{id}/download`
- `DELETE /{id}`

## Notes
- Used for policyholder evidence uploads during claim submission.
- Used by staff for evidence review.
- Frontend admin-only documents console route: `/documents`.

## Run
```bash
mvn -pl document-service spring-boot:run
```

## Diagram
- [../WORKFLOW_DIAGRAM.md](../WORKFLOW_DIAGRAM.md)
