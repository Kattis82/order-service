# Order Service

## Om tjänsten

Order Service ansvarar för kundernas ordrar och betalningar i webbshoppen. Tjänsten skapar ordrar, startar betalning via
Stripe Checkout, tar emot webhooks från Stripe och skickar orderbekräftelse via RabbitMQ när betalningen är bekräftad.

Flöde:

```
Frontend -> Gateway -> Order Service -> Product Service (lager och priser)
                                     -> Stripe (betalning)
                                     -> RabbitMQ -> Email Service (orderbekräftelse)
Stripe  -> Order Service (webhook, går direkt, inte via Gateway)
```

Orderstatus: `PENDING` (väntar på betalning), `PAID` (betald) och `EXPIRED` (Checkout-sessionen gick ut, lagret
återställs).

## Teknik

- Java 17
- Spring Boot
- Spring Security (OAuth2 Resource Server, JWT från Auth Service)
- Spring Data JPA
- H2 (lokalt, i minnet) och PostgreSQL (produktion)
- Stripe Java SDK
- RabbitMQ (Spring AMQP)
- Maven

## Köra lokalt

### Krav

- Java 17 och Maven (eller `mvnw`/`mvnw.cmd` som följer med projektet)
- Auth Service och Product Service igång
- Tillgång till en RabbitMQ-instans. Email Service behöver vara ansluten via RabbitMQ för att orderbekräftelser ska
  kunna skickas.
- Ett Stripe-testkonto och [Stripe CLI](https://docs.stripe.com/stripe-cli) för att testa webhooks

### Miljövariabler

Lägg aldrig riktiga värden i Git. Sätt dem i IntelliJ-körkonfigurationen, i terminalen eller i Render.

| Variabel                                                                                     | Beskrivning                                                                               |
|----------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| `SERVER_PORT`                                                                                | Port (standard 8086)                                                                      |
| `PRODUCT_SERVICE_URL`                                                                        | Adress till Product Service (standard `http://localhost:8082`)                            |
| `AUTH_SERVER_URL`                                                                            | Adress till Auth Service, används för JWT-nycklar (`/auth/jwks`)                          |
| `INTERNAL_SERVICE_TOKEN`                                                                     | Intern hemlighet för anrop till Product Service, måste vara samma värde i båda tjänsterna |
| `STRIPE_SECRET_KEY`                                                                          | Stripes hemliga testnyckel (`sk_test_...`)                                                |
| `STRIPE_WEBHOOK_SECRET`                                                                      | Signeringshemlighet för webhooken (`whsec_...`)                                           |
| `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `RABBITMQ_VHOST` | Anslutning till RabbitMQ                                                                  |
| `RABBITMQ_QUEUE_NAME`                                                                        | Kö för orderbekräftelser                                                                  |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`                                                       | Endast produktion (profil `prod`): PostgreSQL                                             |

### Starta tjänsten

Tjänsten körs på port **8086**.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

På Windows:

```bash
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

Profilen `local` använder H2 i minnet (töms vid varje omstart). H2-konsolen finns på
`http://localhost:8086/h2-console` (JDBC URL `jdbc:h2:mem:orderdb`).

**Testa betalning lokalt:**

```bash
stripe listen --events checkout.session.completed,checkout.session.expired --forward-to localhost:8086/webhooks/stripe
```

Sätt det `whsec_...` som skrivs ut som `STRIPE_WEBHOOK_SECRET`. Betala med testkortet `4242 4242 4242 4242`. Tvinga fram
en utgången session med `stripe checkout sessions expire cs_test_...`.

## API / Endpoints

Frontend når tjänsten via Gateway. Alla utom webhooken kräver JWT.

- `POST /orders` – skapar en order (201 Created)
- `POST /orders/checkout` – skapar en order med status `PENDING`, minskar lagret och startar Stripe Checkout, returnerar
  order-id, Stripe-session-id och betalningslänk
- `GET /orders` – hämtar den inloggade kundens orderhistorik
- `POST /webhooks/stripe` – tar emot Stripe-händelser (öppen för Stripe, skyddad av signaturverifiering)

## Övrigt

- Webhooken verifierar `Stripe-Signature` mot orörd request-body. Den får därför inte gå via Gateway.
- `markOrderPaid` och `expirePendingOrder` är säkra att köra flera gånger: ordern ändras bara om den fortfarande är
  `PENDING`.
- Lager återställs i Product Service via `X-Internal-Service-Token`. Om återställningen misslyckas förblir ordern
  `PENDING`.
- Orderbekräftelsen skickas först när betalningen bekräftats av Stripe.
- Stripe-nycklar ska ligga utan mellanslag i miljövariablerna.
- Swagger: `http://localhost:8086/swagger-ui/index.html`

## Länkar till övriga repositories

- Auth Service: https://github.com/J0ssan-iths/Auth-serverProject.git
- Product Service: https://github.com/JohannaVii/product-service.git
- Gateway: https://github.com/Kattis82/jwt-gateway.git
- Frontend: https://github.com/JohannaVii/Frontend.git
- Email Service: https://github.com/paveena-naebsuwan/email-service.git

## Driftsatt miljö (Render)

- Auth Service: https://auth-serverproject-1-0-0.onrender.com
- Product Service: https://product-service-1-0-0-16ub.onrender.com
- Gateway: https://gateway-tyjw.onrender.com
- Order Service: https://order-service-1-0-0.onrender.com
- Frontend: https://webshoppen-kjpj.onrender.com
- Email Service: https://email-service-1-0-1.onrender.com