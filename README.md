# Mini Payment Platform

### Event-Driven Payment Processing Platform

> Plataforma de processamento de pagamentos construída em fases
> incrementais, com foco em evolução arquitetural e conceitos utilizados em
> sistemas distribuídos modernos.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen)
![License](https://img.shields.io/badge/license-MIT-blue)

---

## Visão geral

A aplicação recebe pagamentos via API REST, persiste de forma síncrona e
processa de forma assíncrona através de eventos Kafka. A arquitetura segue o
padrão **Ports & Adapters (Hexagonal)**, mantendo o domínio isolado de
frameworks e infraestrutura.

O processamento é desacoplado da recepção: o `POST /payments` apenas
persiste o pagamento como `PENDING` e publica um evento, respondendo
rapidamente. Um worker dedicado consome o evento e executa o processamento
real, atualizando o status de forma independente.

---

## Tecnologias

| Categoria | Tecnologia |
|------------|------------|
| Linguagem | Java 21 |
| Framework | Spring Boot 4.1.0 |
| Banco de Dados | PostgreSQL 16 |
| Mensageria | Apache Kafka (modo KRaft) |
| Cache | Redis |
| Segurança | Spring Security + JWT |
| Observabilidade | Prometheus + Grafana |
| Migrations | Flyway |
| Documentação | springdoc-openapi (Swagger UI) |
| Testes | JUnit 5, Mockito, Testcontainers |
| Infraestrutura | Docker & Docker Compose |

---

## Arquitetura

```text
                          ┌──────────────────┐
        POST /payments    │   payment-api    │
        ─────────────────▶│   (perfil web)   │
                          │   REST + Producer│
                          └─────────┬────────┘
                                    │ persiste (PENDING)
                                    ▼
                          ┌──────────────────┐
        GET /payments/{id}│    PostgreSQL    │
        ◀─────────────────│                  │
              ▲           └─────────┬────────┘
              │ cache de            │ publica evento
              │ leitura             ▼
        ┌─────┴──────┐    ┌──────────────────┐
        │   Redis    │    │      Kafka       │
        └────────────┘    │  payment.created │
                          └─────────┬────────┘
                                    │ consome
                                    ▼
                          ┌──────────────────┐
                          │  payment-worker  │
                          │  (perfil worker) │
                          │ Consumer +       │
                          │ processamento    │
                          └─────────┬────────┘
                                    │ atualiza status
                                    ▼
                          ┌──────────────────┐
                          │    PostgreSQL    │
                          │ COMPLETED/FAILED │
                          └──────────────────┘
```

API e Worker executam a mesma base de código em perfis Spring distintos
(`web` e `worker`), comunicando-se exclusivamente via Kafka. O Redis atua
como cache de leitura no `GET /payments/{id}`, reduzindo a carga sobre o
PostgreSQL em consultas repetidas.

---

## Decisões de arquitetura

As principais decisões estão documentadas como ADRs em `docs/adr/`:

| ADR | Decisão |
|-----|---------|
| 001 | Integração OpenAPI com springdoc 3.0.0 (compatível com Spring Boot 4) |
| 002 | CVEs transitivas do Testcontainers (substituída pela 003) |
| 003 | Atualização do Testcontainers 2.0.5 (compatibilidade com Docker Engine 29) |
| 004 | Escolha de Kafka como tecnologia de mensageria |
| 005 | Separação API/Worker via Spring Profiles |

Material de estudo aprofundado de cada fase está em `docs/`
(`fase-1-rest-hexagonal-revisao.md`, `fase-2-kafka-revisao.md`).

---

## Roadmap

- [x] **Fase 0** — Fundação (setup, Docker, estrutura hexagonal)
- [x] **Fase 1** — API REST (domínio, persistência, REST, ProblemDetail, OpenAPI, Testcontainers)
- [x] **Fase 2** — Kafka (producer, consumer, idempotência, Dead Letter Queue)
- [x] **Fase 3** — Worker (separação API/Worker via Spring Profiles)
- [ ] **Fase 4** — Redis (cache de leitura)
- [ ] **Fase 5** — JWT (Spring Security)
- [ ] **Fase 6** — Observabilidade (Prometheus + Grafana)
- [ ] **Fase 7** — Testes avançados
- [ ] **Fase 8** — Docker Compose & Documentação final

---

## Como executar

### Pré-requisitos

- Java 21
- Docker & Docker Compose

### 1. Subir a infraestrutura

```bash
docker compose up -d
```

Sobe PostgreSQL (porta 5432), Kafka (porta 9092) e Kafka UI (porta 8081).

### 2. Executar a aplicação

**Modo desenvolvimento (tudo no mesmo processo):**

```bash
./mvnw spring-boot:run
```

**Modo separado (API e Worker como processos independentes):**

```bash
# Build do artefato
./mvnw clean package -DskipTests

# Terminal 1 - API (REST, porta 8080)
java -jar target/payment-platform-0.0.1-SNAPSHOT.jar --spring.profiles.active=web

# Terminal 2 - Worker (consumer Kafka, sem HTTP)
java -jar target/payment-platform-0.0.1-SNAPSHOT.jar --spring.profiles.active=worker
```

### 3. Acessos

| Recurso | URL |
|---------|-----|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Kafka UI | http://localhost:8081 |
| Actuator | http://localhost:8080/actuator |

---

## Uso da API

### Criar pagamento

```bash
POST /payments
Content-Type: application/json

{
  "payerId": "payer-001",
  "payeeId": "payee-002",
  "amount": 150.75,
  "currency": "BRL"
}
```

Resposta: `201 Created` com header `Location` e o pagamento em estado
`PENDING`. O processamento ocorre de forma assíncrona logo em seguida.

### Consultar pagamento

```bash
GET /payments/{id}
```

Resposta: `200 OK` com o pagamento (já em estado `COMPLETED` ou `FAILED`
após o processamento) ou `404 Not Found`.

---

## Estrutura do projeto

```text
src/main/java/com/cauahvs/payments/
├── domain/                      # Núcleo: regras de negócio puras
│   ├── Payment.java             # Entidade com máquina de estados
│   ├── Money.java               # Value Object (record)
│   ├── Currency.java            # Enum
│   └── PaymentStatus.java       # Enum
├── application/
│   ├── port/in/                 # Portas de entrada (use cases)
│   ├── port/out/                # Portas de saída (repository, publisher)
│   └── service/                 # Implementação dos use cases
├── adapter/
│   ├── in/web/                  # REST controllers, DTOs
│   ├── in/messaging/            # Kafka listener (consumer)
│   └── out/
│       ├── persistence/         # JPA entity, repository, adapter
│       └── messaging/           # Eventos, publisher Kafka
└── config/                      # Configurações Kafka (producer/consumer)
```

---

## Conceitos demonstrados

- Arquitetura Hexagonal (Ports & Adapters)
- Domain-Driven Design (Value Objects, Entities, Always-Valid Model)
- Arquitetura orientada a eventos com Kafka
- Idempotência no processamento de eventos
- Dead Letter Queue para tratamento de falhas
- Separação API/Worker para escala independente
- Tratamento de erros com ProblemDetail (RFC 9457)
- Migrations versionadas com Flyway
- Testes de integração com Testcontainers
- Architecture Decision Records (ADRs)

---

## Autor

**Cauã Henrique Viana Salgado**
Backend Developer

- GitHub: [@CauaHvS](https://github.com/CauaHvS)