# Config Server

Centralized Spring Cloud Config Server for ClaimSwift.

Last documentation sync: 2026-03-08.

## Port
- `8888`

## Purpose
- Serves shared and service-specific configuration.
- Provides native config from `classpath:/config`.
- Registers with Eureka.

## Security
- HTTP Basic auth on config endpoints.
- Default local values:
  - `CONFIG_SERVER_USERNAME=configuser`
  - `CONFIG_SERVER_PASSWORD=configpass`

## Key files
- `src/main/resources/application.yml`
- `src/main/resources/config/application.yml`
- `src/main/resources/config/*.yml`

## Run
```bash
mvn -pl config-server spring-boot:run
```

## Health
- `GET /actuator/health`

## Diagram
- [../WORKFLOW_DIAGRAM.md](../WORKFLOW_DIAGRAM.md)
