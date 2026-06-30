# Payment Platform

Plataforma de processamento de pagamentos construída como projeto de estudo
e portfólio, demonstrando uma arquitetura backend moderna: hexagonal,
orientada a eventos, com separação entre API e Worker, cache, segurança JWT,
observabilidade completa e uma suíte de testes de integração com
infraestrutura real.

> **Stack principal:** Java 21 · Spring Boot 4.1 · PostgreSQL · Apache Kafka ·
> Redis · Prometheus · Grafana · Docker

---

## Visão geral

A aplicação expõe uma API REST para criação e consulta de pagamentos. Cada
pagamento criado é publicado como um evento no Kafka e processado de forma
assíncrona por um Worker independente, que simula a comunicação com um
gateway externo e atualiza o estado do pagamento (PENDING → COMPLETED /
FAILED).

O projeto foi desenvolvido em fases incrementais, cada uma documentada por um
Architecture Decision Record (ADR) e uma revisão de estudo, registrando as
decisões de arquitetura e os trade-offs envolvidos.

---

## Arquitetura

O sistema segue a **Arquitetura Hexagonal** (Ports & Adapters), isolando o
domínio das tecnologias de infraestrutura. A API e o Worker rodam como
processos independentes a partir do mesmo artefato, selecionados por Spring
Profiles (ver ADR 005).

```
                          +-------------------+
        HTTP (REST)       |                   |
   client  ───────────►   |     API (web)     |
                          |                   |
                          |  - PaymentController
                          |  - Auth (JWT)
                          |  - persiste no banco
                          |  - publica evento
                          +---------+---------+
                                    |
                                    |  PaymentCreatedEvent
                                    v
                          +-------------------+
                          |      Kafka        |
                          |  topic:           |
                          |  payment.created  |
                          +---------+---------+
                                    |
                                    |  consome
                                    v
                          +-------------------+
                          |   Worker          |
                          |                   |
                          |  - processa pagamento
                          |  - simula gateway
                          |  - atualiza estado
                          |  - DLQ em falha
                          +---------+---------+
                                    |
                  +-----------------+-----------------+
                  v                 v                 v
            +-----------+     +-----------+     +-----------+
            | PostgreSQL|     |   Redis   |     |   Kafka   |
            |  (dados)  |     |  (cache)  |     |   (DLQ)   |
            +-----------+     +-----------+     +-----------+
```

### Camadas (hexagonal)

- **domain** — entidades e regras de negócio puras (Payment, Money, Role,
  máquina de estados), sem dependências de framework.
- **application** — casos de uso (services) e portas (interfaces de entrada e
  saída).
- **adapter/in** — adaptadores de entrada: controllers REST, listeners Kafka,
  segurança.
- **adapter/out** — adaptadores de saída: persistência JPA, publicação Kafka,
  cache Redis.

---

## Stack tecnológica

| Categoria          | Tecnologia                                  |
|--------------------|---------------------------------------------|
| Linguagem          | Java 21                                     |
| Framework          | Spring Boot 4.1                             |
| Persistência       | PostgreSQL 16 + Spring Data JPA + Flyway    |
| Mensageria         | Apache Kafka (KRaft)                        |
| Cache              | Redis 7                                     |
| Segurança          | Spring Security + JWT (jjwt)                |
| Observabilidade    | Micrometer + Prometheus + Grafana           |
| Documentação API   | SpringDoc OpenAPI (Swagger UI)              |
| Testes             | JUnit 5 + Testcontainers + Awaitility       |
| Build              | Maven (wrapper incluído)                    |
| Containerização    | Docker + Docker Compose                     |

---

## Como rodar

### Pré-requisitos

- Docker e Docker Compose
- (Opcional, para desenvolvimento) JDK 21 e a IDE de sua preferência

### Subir a stack completa

Toda a aplicação (API + Worker) e a infraestrutura (PostgreSQL, Kafka, Redis,
Prometheus, Grafana) sobem com um único comando:

```bash
docker compose up -d --build
```

Aguarde alguns segundos para a inicialização. A API estará disponível em
`http://localhost:8080`.

Para acompanhar os logs:

```bash
docker compose logs -f app-web
docker compose logs -f app-worker
```

Para derrubar:

```bash
docker compose down
```

### Rodar apenas a infraestrutura (desenvolvimento local)

Para rodar a aplicação pela IDE e apenas a infraestrutura em containers, suba
os serviços de apoio e inicie a aplicação localmente (ela usa `localhost` por
padrão):

```bash
docker compose up -d postgres kafka redis
```

---

## Endpoints da API

| Método | Rota                | Descrição                       | Acesso          |
|--------|---------------------|---------------------------------|-----------------|
| POST   | `/auth/register`    | Registra usuário, retorna token | Público         |
| POST   | `/auth/login`       | Autentica, retorna token        | Público         |
| POST   | `/payments`         | Cria um pagamento               | Autenticado     |
| GET    | `/payments/{id}`    | Consulta um pagamento           | Autenticado     |
| GET    | `/payments`         | Lista todos os pagamentos       | ADMIN           |

A autenticação usa **JWT Bearer**. Após registrar ou logar, envie o token no
header `Authorization: Bearer <token>`.

### Exemplo rápido

```bash
# Registrar e capturar o token
TOKEN=$(curl -s -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"senha123456"}' | jq -r .token)

# Criar um pagamento
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"payerId":"p1","payeeId":"p2","amount":100.00,"currency":"BRL"}'
```

---

## Interfaces e portas

| Serviço        | URL                                   | Credenciais         |
|----------------|---------------------------------------|---------------------|
| API            | http://localhost:8080                 | —                   |
| Swagger UI     | http://localhost:8080/swagger-ui.html | —                   |
| Prometheus     | http://localhost:9090                 | —                   |
| Grafana        | http://localhost:3000                 | admin / admin       |
| Kafka UI       | http://localhost:8081                 | —                   |
| PostgreSQL     | localhost:5432                        | payments / payments |
| Redis          | localhost:6379                        | —                   |

---

## Observabilidade

A aplicação expõe métricas no formato Prometheus em `/actuator/prometheus`,
incluindo métricas automáticas (JVM, HTTP, Kafka, pool de conexões) e
métricas de negócio customizadas:

- `payments_total` — total de pagamentos criados.
- `payments_processed_total{status}` — pagamentos processados por status
  (COMPLETED / FAILED).

O Grafana traz um dashboard com painéis de negócio, latência HTTP (p95),
memória JVM e pool de conexões, além de um alerta para taxa de falha de
pagamentos. Detalhes no ADR 008.

---

## Testes

A suíte cobre domínio, segurança, persistência, mensageria, cache e
tratamento de falhas. Os testes de integração usam **Testcontainers**,
subindo PostgreSQL, Kafka e Redis reais em containers descartáveis.

```bash
./mvnw test
```

> Requer Docker em execução (para os Testcontainers).

---

## Decisões de arquitetura (ADRs)

As decisões de projeto estão documentadas em `docs/adr/`, e cada fase tem uma
revisão de estudo em `docs/`:

| ADR | Tema                                              |
|-----|---------------------------------------------------|
| 003 | Atualização do Testcontainers (Docker Engine 29)  |
| 004 | Escolha do Kafka como tecnologia de mensageria    |
| 005 | Separação entre API e Worker via Spring Profiles  |
| 007 | Autenticação e autorização com JWT                |
| 008 | Observabilidade com Prometheus e Grafana          |

---

## Roadmap de fases

- [x] Fase 0–1 — Fundação e API REST
- [x] Fase 2 — Mensageria com Kafka
- [x] Fase 3 — Separação API / Worker
- [x] Fase 4 — Cache com Redis
- [x] Fase 5 — Segurança com JWT
- [x] Fase 6 — Observabilidade (Prometheus + Grafana)
- [x] Fase 7 — Suíte de testes de integração
- [x] Fase 8 — Containerização e documentação

---

## Autor

**Cauã Henrique Viana Salgado**
- Repositório: https://github.com/CauaHvS/payment-platform
- LinkedIn: https://www.linkedin.com/in/caua-henrique
- Email: cauahenrique230503@gmail.com
- Celular: (31) 99414-5996