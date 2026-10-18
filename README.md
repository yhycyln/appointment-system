# Appointment System (starter)

Java 21 + Spring Boot + PostgreSQL scaffold for a multi-employee appointment system.
Appointments are created as `PENDING` and must be approved by an employee/admin before becoming `CONFIRMED`.

## Included
- Maven project and Spring Boot application
- PostgreSQL Docker Compose service
- Flyway initial schema
- Employee and appointment entities/repositories
- Basic management API for employees and appointment approval/rejection
- Availability/slot calculator starter
- Health endpoint via Spring Actuator

## Prerequisites
- JDK 21
- Maven 3.9+
- Docker Desktop or Docker Engine

## Run with Docker (recommended)
1. Configure `.env` values (`POSTGRES_PASSWORD` and the WhatsApp values).
2. Build and start the full stack:

   ```bash
   docker compose up --build -d
   ```

3. Health check:

   ```bash
   curl http://localhost:8080/actuator/health
   ```

4. Follow logs when needed:

   ```bash
   docker compose logs -f app
   ```

## Run locally (without app container)
1. Start PostgreSQL container:

   ```bash
   docker compose up -d postgres
   ```

2. Export datasource environment variables (or set them in your IDE):

   ```bash
   export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/appointments
   export SPRING_DATASOURCE_USERNAME=appointments_app
   export SPRING_DATASOURCE_PASSWORD=password
   ```

3. Start the application:

   ```bash
   mvn spring-boot:run
   ```

4. Health check: `GET http://localhost:8080/actuator/health`

## Starter management endpoints
- `POST /api/v1/admin/employees` — create employee
- `GET /api/v1/admin/employees` — list employees
- `POST /api/v1/admin/employees/{id}/deactivate` — set employee as inactive (`active=false`)
- `POST /api/v1/admin/appointments` — create a pending appointment (starter endpoint)
- `GET /api/v1/admin/appointments?status=PENDING` — list appointments
- `POST /api/v1/admin/appointments/{id}/approve` — approve pending appointment
- `POST /api/v1/admin/appointments/{id}/reject` — reject pending appointment
- `POST /api/v1/admin/appointments/{id}/cancel` — cancel pending or confirmed appointment
- `POST /api/v1/bot/messages` — process WhatsApp bot conversation with button-driven actions (employee -> day -> slot)
- `GET /webhooks/whatsapp` — verify the Meta webhook subscription
- `POST /webhooks/whatsapp` — receive WhatsApp messages and reply through the Cloud API

## WhatsApp Cloud API setup

The WhatsApp webhook uses Meta's Cloud API. Add these values to the ignored local `.env` file or your deployment secret store:

```dotenv
WHATSAPP_ACCESS_TOKEN=your_access_token
WHATSAPP_PHONE_NUMBER_ID=your_phone_number_id
WHATSAPP_VERIFY_TOKEN=your_private_webhook_verify_token
WHATSAPP_APP_SECRET=your_meta_app_secret
WHATSAPP_GRAPH_API_VERSION=v23.0
```

Use the access token and phone number ID from the WhatsApp API Setup page. The verify token is a secret string you choose; enter the same value in Meta's webhook configuration. `WHATSAPP_APP_SECRET` is the App Secret from the Meta app settings and is used to validate `X-Hub-Signature-256` on incoming events. Keep the access token, verify token, and app secret private, and rotate temporary access tokens before production. `WHATSAPP_GRAPH_API_VERSION` can be changed to a Graph API version supported by your Meta app.

In the Meta app, configure the callback URL as `https://<public-host>/webhooks/whatsapp`, use the verify token above, and subscribe the WhatsApp Business Account to the `messages` webhook field. The callback must be reachable from Meta over HTTPS. For local development, expose the app through a trusted HTTPS tunnel and use that temporary public URL as the callback.

Customers start the conversation by messaging the business number. The bot replies with WhatsApp interactive list choices for employees, days, and available times. Lists include cancel/restart and page controls when needed; the name step accepts text and includes a cancel button. After the customer provides their name, the bot creates a `PENDING` appointment that still needs employee/admin approval. Incoming WhatsApp message IDs are recorded to avoid processing webhook retries twice. This implementation replies to inbound messages and does not send outbound template notifications.

## Bot slot policy
- Sunday: no slots
- Monday-Saturday: slots can be generated between 10:00 and 24:00

## Error response format
API errors return a structured payload:

```json
{
  "timestamp": "2026-10-12T21:19:03.570Z",
  "status": 409,
  "error": "Conflict",
  "code": "BUSINESS_RULE_VIOLATION",
  "message": "Only pending appointments can be rejected. Current status: CONFIRMED",
  "path": "/api/v1/admin/appointments/1/reject",
  "details": null
}
```

Example create employee:

```json
{
  "name": "Ahmet Yilmaz"
}
```

Example create appointment:

```json
{
  "employeeId": 1,
  "customerName": "Example Customer",
  "customerPhone": "+905xxxxxxxxx",
  "startAt": "2026-10-12T10:00:00+03:00",
  "endAt": "2026-10-12T10:40:00+03:00"
}
```

Example bot start request:

```json
{
  "channel": "TELEGRAM",
  "externalUserId": "123456789",
  "action": "START"
}
```

Example bot employee selection request:

```json
{
  "channel": "TELEGRAM",
  "externalUserId": "123456789",
  "action": "SELECT_EMPLOYEE",
  "value": "1"
}
```

Example bot day selection request:

```json
{
  "channel": "TELEGRAM",
  "externalUserId": "123456789",
  "action": "SELECT_DAY",
  "value": "2026-10-15"
}
```

## Important limitations of this starter
- The management endpoints are **not authenticated yet**. Do not expose them to the internet before adding authentication/authorization.
- The appointment creation endpoint is a bootstrap/admin endpoint, not the final public booking flow.
- Employee approval is represented as an authenticated admin action for now; assigning approvals to each employee is a next step.
- Telegram and WhatsApp booking flows are wired up; customer/channel account linking and outbound notification messages are not included.
- Availability logic is a starter and must be completed with working hours, multiple breaks, blocked periods, existing appointments, 7-day horizon, and concurrent booking tests.
- Configure strong secrets, TLS, backups, rate limiting, and privacy/retention controls before production use.
