# ADR 005 — Separação entre API e Worker via Spring Profiles

**Status:** Aceito
**Data:** 2026-06-27

---

## Contexto

Na Fase 2, o consumidor Kafka (`PaymentCreatedListener`) executava dentro
da mesma aplicação que atende às requisições HTTP. Producer e consumer
compartilhavam o mesmo processo.

Esse arranjo funciona, mas apresenta uma limitação de escalabilidade: o
processamento assíncrono de pagamentos compete por CPU e memória com o
atendimento das requisições HTTP. Não é possível escalar os dois
independentemente.

Em arquiteturas de produção, é comum separar essas responsabilidades:

- **API (web):** recebe HTTP, persiste e publica eventos. Escala conforme a
  demanda de tráfego.
- **Worker:** consome eventos do Kafka e processa. Escala conforme a demanda
  de processamento (tamanho da fila).

A Fase 3 teve como objetivo implementar essa separação. Duas abordagens
foram consideradas.

---

## Decisão

Adotar a separação entre API e Worker utilizando **Spring Profiles** sobre
a mesma base de código, em vez de dividir o projeto em módulos Maven ou
repositórios independentes.

Três perfis foram definidos:

- **`web`** — ativa o `PaymentController`; desativa o consumidor Kafka e sua
  configuração. Atende apenas HTTP.
- **`worker`** — ativa o `PaymentCreatedListener` e o `KafkaConsumerConfig`;
  desativa o controller. Processa eventos sem expor HTTP.
- **`default`** (sem perfil) — ativa tudo. Usado em desenvolvimento local.

A implementação utilizou a anotação `@Profile` nos componentes:

```java
@Profile({"web", "default"})     // PaymentController
@Profile({"worker", "default"})  // PaymentCreatedListener, KafkaConsumerConfig
```

O `KafkaProducerConfig` permanece sem perfil, pois a API (web) precisa
publicar eventos.

Para o perfil `worker`, foi criado o arquivo `application-worker.yaml` com:

```yaml
spring:
  main:
    web-application-type: none
```

Isso faz o worker subir como processo *headless* (sem servidor Tomcat),
evitando conflito de porta quando API e Worker rodam na mesma máquina. O
processo permanece vivo por meio do loop de polling do consumidor Kafka.

A mesma imagem/artefato é executada em modos diferentes apenas alterando o
perfil ativo:

```text
java -jar app.jar --spring.profiles.active=web
java -jar app.jar --spring.profiles.active=worker
```

---

## Consequências

### Positivas

- Separação real de responsabilidades entre API e Worker, executando como
  processos independentes que se comunicam exclusivamente via Kafka.
- Escalabilidade independente: é possível subir mais instâncias de web ou de
  worker conforme a demanda específica.
- Baixíssimo custo de implementação: nenhuma refatoração estrutural; apenas
  anotações e um arquivo de configuração adicional.
- Um único build, um único artefato, um único repositório — simplicidade
  operacional.
- O modo `default` preserva a experiência de desenvolvimento local com tudo
  no mesmo processo.

### Negativas

- Os códigos de API e Worker convivem no mesmo artefato. Não há isolamento
  físico real entre eles.
- O artefato carrega dependências de ambos os modos, mesmo quando apenas um
  está ativo (a imagem do worker inclui bibliotecas web não utilizadas, e
  vice-versa).
- Não reflete completamente um cenário de microsserviços com times e
  ciclos de deploy independentes.

---

## Alternativas consideradas

### Separar em módulos Maven (multi-module) ou repositórios distintos

Rejeitada para este momento. Seria a abordagem mais próxima de
microsserviços reais, com artefatos independentes para API e Worker e um
módulo `common` compartilhando domínio e eventos. Entretanto, exigiria
refatoração estrutural significativa, build mais complexo e maior risco de
quebrar funcionalidades já estáveis — caracterizando over-engineering para o
estágio atual do projeto (portfólio de aprendizado, sem times separados).

Em um cenário real com times distintos e necessidade de deploys
independentes, essa seria a escolha recomendada.

### Manter API e Worker no mesmo processo (status quo da Fase 2)

Rejeitada. Não permite escalar processamento e atendimento HTTP de forma
independente, que era justamente o objetivo da Fase 3.

---

## Referências

- Documentação do Spring Boot — Profiles e `spring.main.web-application-type`.
- Revisão da Fase 2 (`docs/fase-2-kafka-revisao.md`) — contexto do
  consumidor Kafka.
- ADR 004 — escolha de Kafka como tecnologia de mensageria.