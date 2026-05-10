# AI Customer Support Chatbot

Spring Boot customer support chatbot for e-commerce order complaints. It uses WebSocket/STOMP for chat, Redis for live conversation state, and JPA for permanent tickets, ticket items, chat messages, and orders.

## Structure

```text
src/main/java/com/personal/ai_chatbot
  config        WebSocket, Redis, and seed-data configuration
  controller    STOMP chat entry point
  dto           WebSocket payloads, AI signal, and Redis session DTO
  entity        JPA entities for tickets, messages, orders, and items
  enums         Conversation state, ticket status, issue type, sender
  repository    Spring Data repositories
  service       Service interfaces
  service/impl  Enterprise-style service implementations
```

## WebSocket Contract

Endpoint:

```text
/ws
```

Client sends:

```text
/app/chat.sendMessage
```

Customer subscribes:

```text
/user/queue/chat
```

Admin sends replies:

```text
/app/admin.reply
```

Admin subscribes to live ticket updates:

```text
/topic/admin/tickets
```

Request:

```json
{
  "userId": "user-1",
  "message": "I have a refund issue"
}
```

Admin reply request:

```json
{
  "ticketId": 1,
  "message": "A support agent is checking this now."
}
```

STOMP connections must include the same bearer token used by the REST APIs:

```text
Authorization: Bearer <accessToken>
```

## Login And Orders

Users are assumed to already exist in the e-commerce platform. There is no registration API.

Login:

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "demo.customer",
  "password": "password123"
}
```

Seeded demo customers:

```text
username: demo.customer
password: password123
userId: user-1

username: priya.sharma
password: password123
userId: user-2
```

The login response includes a JWT access token:

```json
{
  "userId": "user-1",
  "fullName": "Demo Customer",
  "email": "demo.customer@example.com",
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresInSeconds": 7200
}
```

Use the token for protected APIs:

```http
Authorization: Bearer <accessToken>
```

List logged-in user's orders:

```http
GET /api/users/user-1/orders
Authorization: Bearer <accessToken>
```

Get order details with items:

```http
GET /api/users/user-1/orders/ORD-1001
Authorization: Bearer <accessToken>
```

Logout:

```http
POST /api/auth/logout
Authorization: Bearer <accessToken>
```

## Admin Support APIs

Seeded demo admin:

```text
adminId: admin-1
password: admin123
```

Login:

```http
POST /api/admin/auth/login
Content-Type: application/json

{
  "adminId": "admin-1",
  "password": "admin123"
}
```

Use the returned admin token:

```http
Authorization: Bearer <adminAccessToken>
```

Admin ticket operations:

```http
GET /api/admin/tickets/escalated
GET /api/admin/tickets/{ticketId}
POST /api/admin/tickets/{ticketId}/reply
POST /api/admin/tickets/{ticketId}/resolve
```

## Sample Flow

Seed order:

```text
userId: user-1
orderId: ORD-1001
items: Shoes Rs.2000, Shirt Rs.1000, Watch Rs.5000
```

Conversation:

```text
User: I have a refund issue
Bot: Please provide your order ID.

User: ORD-1001
Bot: Your order contains...

User: 1,2
Bot: Could you specify the issue? Choose damaged, not delivered, or wrong item.

User: damaged
Bot: Your refund of Rs.3000 has been initiated.
```

High-value refunds above `5000`, repeated retries, explicit human/agent/manager requests, or AI escalation confidence `>= 0.75` move the ticket to `ESCALATED`.

## Configuration

The default profile runs with H2 so the project starts easily. For PostgreSQL, pass these environment variables:

```text
DB_URL=jdbc:postgresql://localhost:5432/ai_chatbot
DB_USERNAME=postgres
DB_PASSWORD=postgres
DB_DRIVER=org.postgresql.Driver
JPA_DDL_AUTO=update
```

Redis defaults:

```text
REDIS_HOST=localhost
REDIS_PORT=6379
SESSION_TTL_MINUTES=30
```

OpenRouter integration is optional. It is used for intent detection, order ID extraction, issue normalization, and escalation signals. AI output is advisory only; backend services still make final deterministic decisions for refund amount and ticket status.

```text
OPENROUTER_API_KEY=your_openrouter_key
OPENROUTER_MODEL=openrouter/free
OPENROUTER_URL=https://openrouter.ai/api/v1/chat/completions
```

## Run

```bash
./mvnw spring-boot:run
```

On Windows:

```bash
mvnw.cmd spring-boot:run
```

## Notes

If OpenRouter is unavailable, the app falls back to deterministic rule-based logic so chat flow still works.
