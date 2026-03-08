# ClaimSwift Runbook

Last documentation sync: 2026-03-08.

## Prerequisites
- Java 17
- Maven 3.9+
- Node.js 18+ and npm 9+
- MySQL 8+

## 1. Backend startup order
1. `config-server` (`8888`)
2. `eureka-server` (`8761`)
3. `auth-service` (`8081`)
4. `claim-service` (`8082`)
5. `document-service` (`8083`)
6. `assessment-service` (`8084`)
7. `payment-service` (`8085`)
8. `notification-service` (`8086`)
9. `reporting-service` (`8087`)
10. `api-gateway` (`8080`)

## 2. Start commands (CMD)

```cmd
A:\claimswift-master\apache-maven-3.9.13\bin\mvn.cmd -pl config-server spring-boot:run
A:\claimswift-master\apache-maven-3.9.13\bin\mvn.cmd -pl eureka-server spring-boot:run
A:\claimswift-master\apache-maven-3.9.13\bin\mvn.cmd -pl auth-service spring-boot:run
A:\claimswift-master\apache-maven-3.9.13\bin\mvn.cmd -pl claim-service spring-boot:run
A:\claimswift-master\apache-maven-3.9.13\bin\mvn.cmd -pl document-service spring-boot:run
A:\claimswift-master\apache-maven-3.9.13\bin\mvn.cmd -pl assessment-service spring-boot:run
A:\claimswift-master\apache-maven-3.9.13\bin\mvn.cmd -pl payment-service spring-boot:run
A:\claimswift-master\apache-maven-3.9.13\bin\mvn.cmd -pl notification-service spring-boot:run
A:\claimswift-master\apache-maven-3.9.13\bin\mvn.cmd -pl reporting-service spring-boot:run
A:\claimswift-master\apache-maven-3.9.13\bin\mvn.cmd -pl api-gateway spring-boot:run
```

## 3. Frontend startup

```cmd
cd frontend-angular
npm install
npm start
```

## 4. Security quick checks
- Login requires OTP verification:
  - `POST /api/auth/login`
  - `POST /api/auth/login/verify-otp`
- Admin user-management routes:
  - `GET /api/auth/admin/users`
  - `GET /api/auth/admin/users/{id}`
  - `POST /api/auth/admin/users`
  - `PUT /api/auth/admin/users/{id}/roles`
  - `PATCH /api/auth/admin/users/{id}/status`

## 5. Health checks
- `http://localhost:8888/actuator/health`
- `http://localhost:8761/actuator/health`
- `http://localhost:8080/actuator/health`
- `http://localhost:8081/actuator/health` ... `http://localhost:8087/actuator/health`

## 6. Role smoke checks
- Policyholder can access `/policies`, `/claim`, `/status`, `/history`.
- Adjuster can access `/claims`, `/assessment/:id`, `/reports`.
- Manager can access `/claims`, `/payments`, `/reports`.
- Admin can access `/claims`, `/documents`, `/reports`, `/admin/users`.

## 7. Diagram
- [WORKFLOW_DIAGRAM.md](WORKFLOW_DIAGRAM.md)
