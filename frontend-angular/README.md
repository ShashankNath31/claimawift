# Frontend Angular

Angular 17 client for ClaimSwift.

Last documentation sync: 2026-03-08.

## Development server
```bash
npm start
```
Open `http://localhost:4200`.

## Build
```bash
npm run build
```
Build output: `dist/frontend-angular`.

## Key pages by role
- Public: `/`, `/login`, `/register`
- Policyholder: `/dashboard`, `/policies`, `/claim`, `/status`, `/history`
- Adjuster: `/dashboard`, `/claims`, `/assessment/:id`, `/reports`
- Manager: `/dashboard`, `/claims`, `/payments`, `/reports`
- Admin: `/dashboard`, `/claims`, `/documents`, `/reports`, `/admin/users`

## UX highlights
- OTP login is 2-step on `/login` (send OTP then verify OTP).
- Notification bell is available in top navigation for authenticated users.
- Profile menu is available from top-right username chip.
- Policyholder status page includes settlement bank details capture after approval.

## API usage
- All frontend API calls use gateway: `http://localhost:8080/api/*`.

## Diagram
- [../WORKFLOW_DIAGRAM.md](../WORKFLOW_DIAGRAM.md)
