# Eureka Server

Service discovery registry for ClaimSwift microservices.

Last documentation sync: 2026-03-08.

## Port
- `8761`

## Purpose
- Hosts Eureka dashboard.
- Accepts service registration from gateway and domain services.

## Security
- HTTP Basic and form login enabled.
- Default local values:
  - `EUREKA_USERNAME=eureka`
  - `EUREKA_PASSWORD=eurekapass`

## Run
```bash
mvn -pl eureka-server spring-boot:run
```

## Access
- Dashboard: `http://localhost:8761`
- Health: `GET /actuator/health`

## Diagram
- [../WORKFLOW_DIAGRAM.md](../WORKFLOW_DIAGRAM.md)
