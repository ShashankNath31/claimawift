# ClaimSwift API Calls Explained

Last documentation sync: 2026-03-08.

## Base URLs
- API Gateway: `http://localhost:8080`
- Auth: `http://localhost:8081`
- Claim: `http://localhost:8082`
- Document: `http://localhost:8083`
- Assessment: `http://localhost:8084`
- Payment: `http://localhost:8085`
- Notification: `http://localhost:8086`
- Reporting: `http://localhost:8087`

Use gateway URLs for frontend/client traffic.

## Roles
- `ROLE_POLICYHOLDER`
- `ROLE_ADJUSTER`
- `ROLE_MANAGER`
- `ROLE_ADMIN`

Role hierarchy in services:
`ROLE_ADMIN > ROLE_MANAGER > ROLE_ADJUSTER > ROLE_POLICYHOLDER`

## Auth Service (`/api/auth`)

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| POST | `/register` | Public | Register policyholder account |
| POST | `/login` | Public | Create login OTP challenge |
| POST | `/login/verify-otp` | Public | Verify OTP and issue JWT |
| POST | `/refresh` | Public/Bearer | Refresh JWT token |
| POST | `/logout` | Bearer | Logout / invalidate token |
| GET | `/me` | Authenticated | Current user profile |
| GET | `/adjusters` | Manager/Admin | List adjusters |
| GET | `/managers` | Adjuster/Manager/Admin | List managers |
| GET | `/admin/users` | Admin | List all users |
| GET | `/admin/users/{id}` | Admin | Get user by id |
| POST | `/admin/users` | Admin | Create internal user |
| PUT | `/admin/users/{id}/roles` | Admin | Update roles |
| PATCH | `/admin/users/{id}/status` | Admin | Update account status |
| GET | `/health` | Public | Health check |

## Claim Service (`/api/claims`)

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| POST | `/` | Policyholder/Admin | Submit claim |
| GET | `/{id}` | Policyholder/Adjuster/Manager/Admin | Claim details |
| GET | `/number/{claimNumber}` | Policyholder/Adjuster/Manager/Admin | Claim by number |
| GET | `/my-claims` | Policyholder | Own claims |
| GET | `/history` | Policyholder | Claim history |
| GET | `/my-claims/search` | Policyholder | Search own claims |
| GET | `/policies/my` | Policyholder | Policy portfolio |
| PUT | `/{id}/bank-details` | Policyholder (only) | Save settlement bank details |
| GET | `/{id}/bank-details` | Policyholder/Manager/Admin | Read bank details |
| GET | `/{id}/audit` | Adjuster/Manager/Admin | Claim audit trail |
| GET | `/` | Adjuster/Manager/Admin | Claims list |
| PATCH | `/{id}/assign` | Manager/Admin | Assign adjuster |
| PATCH | `/{id}/unassign` | Manager/Admin | Remove adjuster |
| PUT/PATCH | `/{id}/status` | Adjuster/Manager/Admin | Workflow status update |
| PATCH | `/{id}/approve` | Adjuster/Manager/Admin | Approve claim |
| PATCH | `/{id}/disapprove` | Adjuster/Manager/Admin | Reject claim |
| PATCH | `/{id}/adjust` | Adjuster/Manager/Admin | Adjust claim |
| GET | `/assigned` | Adjuster/Manager/Admin | Assigned claims |
| GET | `/statistics` | Adjuster/Manager/Admin | Summary metrics |
| GET | `/search` | Adjuster/Manager/Admin | Search claims |
| GET | `/summary` | Adjuster/Manager/Admin | Date summary |
| GET | `/internal/*` | Adjuster/Manager/Admin | Internal reporting APIs |
| GET | `/health` | Public | Health check |

## Document Service (`/api/documents`)

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| POST | `/upload` | Policyholder/Adjuster/Manager/Admin | Upload evidence |
| GET | `/{id}` | Policyholder/Adjuster/Manager/Admin | Document metadata |
| GET | `/claim/{claimId}` | Policyholder/Adjuster/Manager/Admin | Documents by claim |
| GET | `/user/{userId}` | Policyholder/Adjuster/Manager/Admin | Documents by uploader |
| GET | `/claim/{claimId}/type/{documentType}` | Policyholder/Adjuster/Manager/Admin | Type-filtered docs |
| GET | `/{id}/download` | Policyholder/Adjuster/Manager/Admin | Download document |
| DELETE | `/{id}` | Policyholder/Adjuster/Manager/Admin | Delete document |

## Assessment Service (`/api/assessments`)

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| POST | `/` | Adjuster/Manager/Admin | Create assessment |
| POST | `/decision` | Adjuster/Manager/Admin | Decision output |
| POST | `/adjustment` | Adjuster/Manager/Admin | Adjustment details |
| GET | `/{id}` | Adjuster/Manager/Admin | Assessment details |
| GET | `/claim/{claimId}` | Adjuster/Manager/Admin | Assessment by claim |
| GET | `/my-assessments` | Adjuster/Manager/Admin | Assessor workloads |
| GET | `/{assessmentId}/adjustments` | Adjuster/Manager/Admin | Adjustment history |
| POST | `/request` | Adjuster/Manager/Admin | Request assessment |
| POST | `/notify-complete` | Adjuster/Manager/Admin | Completion callback |

## Payment Service (`/api/payments`)

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| POST | `/` | Manager-only | Create settlement |
| GET | `/{id}` | Manager-only | Payment detail |
| GET | `/claim/{claimId}` | Manager-only | Claim payments |
| GET | `/internal/*` | Adjuster/Manager/Admin | Internal reporting APIs |
| POST | `/internal/auto-process` | Manager-only | Workflow auto-process payout |

## Notification Service (`/api/notifications`)

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| GET | `/` | Policyholder/Adjuster/Manager/Admin | Notification inbox |
| GET | `/unread` | Policyholder/Adjuster/Manager/Admin | Unread notifications |
| GET | `/unread/count` | Policyholder/Adjuster/Manager/Admin | Unread count |
| PUT | `/{id}/read` | Policyholder/Adjuster/Manager/Admin | Mark read |
| PUT | `/read-all` | Policyholder/Adjuster/Manager/Admin | Mark all read |
| POST | `/send` | Adjuster/Manager/Admin | Send notification |
| DELETE | `/{id}` | Policyholder/Adjuster/Manager/Admin | Delete notification |
| POST | `/test` | Policyholder/Adjuster/Manager/Admin | Test notification |

## Reporting Service (`/api/reports`)

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| GET | `/claims/summary` | Adjuster/Manager/Admin | Claims analytics JSON |
| GET | `/payments` | Manager/Admin | Payment analytics JSON |
| GET | `/adjusters/performance` | Manager/Admin | Adjuster performance JSON |
| GET | `/claims/summary/pdf` | Manager/Admin | Claims PDF export |
| GET | `/payments/pdf` | Manager/Admin | Payments PDF export |
| GET | `/adjusters/performance/pdf` | Manager/Admin | Adjuster PDF export |
| POST | `/claim-event` | Authenticated | Persist claim event |
| POST | `/status-change` | Authenticated | Persist status-change event |

Reporting notes:
- Date parameters `startDate` and `endDate` (`YYYY-MM-DD`) are applied in report filtering.
- Reporting service persists incoming report events in `report_events`.

## Gateway fallback routes
- `/fallback/auth`
- `/fallback/claims`
- `/fallback/documents`
- `/fallback/assessments`
- `/fallback/payments`
- `/fallback/notifications`
- `/fallback/reports`

## Workflow Diagram
- [WORKFLOW_DIAGRAM.md](WORKFLOW_DIAGRAM.md)
