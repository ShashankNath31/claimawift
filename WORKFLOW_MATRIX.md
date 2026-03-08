# Workflow Matrix

Last documentation sync: 2026-03-08.

| Actor | Screen/Flow | API | Allowed Transition/Outcome |
|---|---|---|---|
| Public User | Register | `POST /api/auth/register` | New account -> `ROLE_POLICYHOLDER` |
| Public User | Login challenge | `POST /api/auth/login` | Valid credentials -> OTP challenge issued |
| Public User | OTP verify | `POST /api/auth/login/verify-otp` | Valid OTP -> JWT session |
| Any Authenticated User | Logout | `POST /api/auth/logout` | Token invalidated |
| Policyholder | My Policies | `GET /api/claims/policies/my` | View coverage and active/expired status |
| Policyholder | New Claim | `POST /api/claims` | `SUBMITTED` |
| Policyholder | Upload Evidence | `POST /api/documents/upload` | Evidence linked to claim |
| Policyholder | Claim Status + timeline | `GET /api/claims/my-claims` | Read own claims only |
| Policyholder | Claim History | `GET /api/claims/history` | Read historical claims |
| Policyholder | Bank details for settlement | `PUT /api/claims/{id}/bank-details` | Save beneficiary/account/IFSC/bank |
| Adjuster | Assigned claims dashboard | `GET /api/claims/assigned` | Priority-sorted queue |
| Adjuster | Assessment workspace | `/assessment/:id`, `/api/assessments/*` | Evaluate evidence and submit decision |
| Adjuster | Claim actions | `PUT/PATCH /api/claims/{id}/status` or approve/disapprove/adjust APIs | Move claim by workflow rules |
| Manager | Claims queue | `GET /api/claims` | View and supervise queue |
| Manager | Assign / unassign adjuster | `PATCH /api/claims/{id}/assign` / `unassign` | Manage adjuster availability and ownership |
| Manager | Settlement create | `POST /api/payments` | Process simulated payout |
| Manager | Settlement read | `GET /api/payments/{id}`, `GET /api/payments/claim/{claimId}` | View settlement records |
| Adjuster | Reports JSON | `GET /api/reports/claims/summary` | Claim analytics only |
| Manager/Admin | Reports JSON | `GET /api/reports/claims/summary`, `GET /api/reports/payments`, `GET /api/reports/adjusters/performance` | Full analytics |
| Manager/Admin | Reports PDF | `/api/reports/*/pdf` | Download report PDFs |
| Admin | Documents console | `/documents`, `/api/documents/*` | Administrative document management |
| Admin | List Users | `GET /api/auth/admin/users` | Full user directory |
| Admin | User Detail | `GET /api/auth/admin/users/{id}` | Single user profile |
| Admin | Create Internal User | `POST /api/auth/admin/users` | Create `ROLE_ADJUSTER`/`ROLE_MANAGER`/`ROLE_ADMIN` |
| Admin | Update User Roles | `PUT /api/auth/admin/users/{id}/roles` | Role assignment update |
| Admin | Update User Status | `PATCH /api/auth/admin/users/{id}/status` | `ACTIVE`/`INACTIVE`/`SUSPENDED` |

## Claim Status Lifecycle

- `SUBMITTED` -> `UNDER_REVIEW`, `CANCELLED`
- `UNDER_REVIEW` -> `APPROVED`, `REJECTED`, `ADJUSTED`, `CANCELLED`
- `ADJUSTED` -> `APPROVED`, `REJECTED`, `CANCELLED`
- `APPROVED` -> `PAID`, `PAYMENT_FAILED`
- `PAYMENT_FAILED` -> `PAID`, `CANCELLED`
- `REJECTED`, `PAID`, `CANCELLED` are terminal

## Diagram
- [WORKFLOW_DIAGRAM.md](WORKFLOW_DIAGRAM.md)
