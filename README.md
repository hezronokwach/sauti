# Sauti HMS

[![CI](https://github.com/hezronokwach/sauti/actions/workflows/ci.yml/badge.svg?branch=staging)](https://github.com/hezronokwach/sauti/actions/workflows/ci.yml)

A voice-first hospital management system for Kenyan clinics: reception, triage, consultation with live Swahili and English transcription, prescriptions with allergy checks, and cash or M-Pesa payments.

## Repository layout

| Folder | Contents |
| --- | --- |
| `backend/` | Spring Boot 4.1 application (Java 21, Maven), with `compose.yaml` for local services |
| `frontend/` | React + TypeScript app |
| `eval/` | Speech evaluation |
| `docs/` | Implementation plan and decision log |

## Running the backend locally

Requires JDK 21 and Docker.

```bash
cd backend
./mvnw spring-boot:run
```

Spring Boot starts the containers in `compose.yaml` automatically. Tests use Testcontainers:

```bash
./mvnw verify
```

## Code quality checks

`./mvnw verify` (locally and in CI) also fails when:

- code is not formatted with palantir-java-format (Spotless); fix it with `./mvnw spotless:apply`
- the compiler reports a warning (`-Xlint:all -Werror`)
- Error Prone finds a likely bug; the message names the pattern and a suggested fix

It also writes a test coverage report to `backend/target/site/jacoco/index.html`; CI uploads it as the `backend-coverage` artifact.

## Configuration

Every environment variable is listed by name in `.env.example`. Never commit real values.
