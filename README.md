# Sauti HMS

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

## Configuration

Every environment variable is listed by name in `.env.example`. Never commit real values.
