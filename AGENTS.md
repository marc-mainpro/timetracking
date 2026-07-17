# AGENTS.md

## Project objective

Build a secure multitenant time-tracking SaaS using Spring Boot,
Angular and PostgreSQL.

## Architecture

- Domain must not depend on Spring.
- Controllers must not contain business logic.
- Controllers must not access repositories directly.
- Infrastructure implements ports.
- Keep the MVP as a modular monolith.

## Multitenancy

- Never trust tenant IDs from clients.
- Resolve tenant from the authenticated principal.
- Every query must be tenant-scoped.
- Add cross-tenant tests.

## Events

- Domain events represent past facts.
- Domain events must not depend on Spring.
- Separate domain and integration events.
- Version integration contracts.
- Do not use events for aggregate invariants.

## Outbox

- Persist Outbox messages in the business transaction.
- Assume at-least-once delivery.
- Consumers must be idempotent.
- Add retry and atomicity tests.
- Do not add a broker without an ADR.

## Security

- Never log secrets.
- Validate all inputs.
- Authorize by role and tenant.
- Add security tests.

## Testing

- Add tests for every business rule.
- Use Testcontainers for PostgreSQL integration tests.
- Do not reduce configured coverage.

## Documentation

- Update OpenAPI when endpoints change.
- Update ADRs when decisions change.
- Update the event catalog when contracts change.
- Keep documentation synchronized.

## AI-generated code

- Review all generated code.
- Never commit generated code without tests.
- Verify dependencies and APIs.
- Prefer small, auditable changes.
