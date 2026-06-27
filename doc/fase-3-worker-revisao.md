# Revisão da Fase 3 — Separação API e Worker via Spring Profiles

Documento de estudo da plataforma de pagamentos. Cobre as decisões de
arquitetura, os conceitos importantes e os principais pontos de atenção da
Fase 3, servindo como material de revisão para entrevistas técnicas.

---

# Conceito central

Até a Fase 2, o consumidor Kafka executava dentro da mesma aplicação que
atendia às requisições HTTP. Producer e consumer compartilhavam o mesmo
processo.

A Fase 3 separa essas responsabilidades em dois modos de execução
independentes:

* **API (web):** recebe requisições HTTP, persiste o pagamento e publica
  eventos. Escala conforme a demanda de tráfego.
* **Worker:** consome eventos do Kafka e executa o processamento. Escala
  conforme a demanda de processamento (tamanho da fila).

O objetivo é permitir **escalabilidade independente**. Se o tráfego HTTP
cresce, sobem-se mais instâncias da API. Se a fila de processamento
acumula, sobem-se mais workers. Os dois deixam de competir por CPU e
memória no mesmo processo.

A separação foi implementada com **Spring Profiles** sobre a mesma base de
código, e não com módulos Maven ou repositórios distintos (ver ADR 005 para
o detalhamento dessa decisão).

---

# Os três perfis

## web

Ativa o `PaymentController`. Desativa o consumidor Kafka e sua
configuração. A aplicação atende apenas HTTP.

## worker

Ativa o `PaymentCreatedListener` e o `KafkaConsumerConfig`. Desativa o
controller. A aplicação processa eventos sem expor HTTP.

## default (sem perfil)

Ativa tudo. Usado em desenvolvimento local, mantendo a experiência de ter
producer e consumer no mesmo processo.

---

# Anotação @Profile

A separação é feita anotando os componentes:

```java
@Profile({"web", "default"})     // PaymentController
@Profile({"worker", "default"})  // PaymentCreatedListener, KafkaConsumerConfig
```

A inclusão do perfil `default` em ambos garante que, em desenvolvimento
local (sem perfil ativo), todos os componentes sejam carregados.

## KafkaProducerConfig sem perfil

O `KafkaProducerConfig` permanece **sem** anotação `@Profile`.

O motivo: a API (perfil web) precisa publicar eventos ao criar pagamentos.
Portanto, o producer deve estar disponível em todos os perfis, enquanto o
consumer só faz sentido no worker.

**Pergunta comum de entrevista**

> Por que o producer não tem perfil, mas o consumer tem?

Porque a API precisa publicar eventos (producer ativo sempre), enquanto o
consumo de eventos é responsabilidade exclusiva do worker.

---

# application-worker.yaml

Para o perfil worker foi criado um arquivo de configuração específico:

```yaml
spring:
  main:
    web-application-type: none
```

## Por que web-application-type: none

O worker não expõe endpoints HTTP. Sem essa configuração, o Spring Boot
ainda tentaria iniciar o servidor Tomcat na porta 8080, o que causaria
conflito de porta caso a API já estivesse rodando na mesma máquina.

Com `web-application-type: none`, o worker sobe como processo **headless**,
sem servidor web.

## O que mantém o worker vivo

Um detalhe importante: ao desativar o servidor web, não há mais um servidor
escutando numa porta para manter o processo ativo.

O que mantém o worker vivo é o **loop de polling do consumidor Kafka**. O
container do listener fica continuamente buscando novas mensagens, e isso
impede que a JVM encerre.

Durante a implementação, esse ponto gerou um problema: ao rodar um JAR
desatualizado (buildado antes de o consumer estar ativo no perfil worker),
o processo subia e encerrava imediatamente, pois não havia nem servidor web
nem consumer para mantê-lo vivo. A solução foi rebuildar o artefato com o
código atualizado.

---

# Execução

A mesma imagem/artefato é executada em modos diferentes apenas alterando o
perfil ativo:

```bash
# API (REST, porta 8080)
java -jar app.jar --spring.profiles.active=web

# Worker (consumer Kafka, sem HTTP)
java -jar app.jar --spring.profiles.active=worker

# Desenvolvimento (tudo junto)
java -jar app.jar
```

Rodando API e Worker em processos separados, comunicando-se exclusivamente
via Kafka, obtém-se a arquitetura desacoplada de produção.

---

# Validação do fluxo

O teste que comprova a separação:

1. Com API e Worker rodando, envia-se `POST /payments` (vai para o processo
   web).
2. A API cria o pagamento como `PENDING` e publica o evento, mas **não
   processa** (não tem consumer).
3. O Worker recebe o evento e processa, atualizando o status para
   `COMPLETED`/`FAILED`.
4. `GET /payments/{id}` na API retorna o status já final.

Isso demonstra que os dois processos se comunicam exclusivamente pelo
Kafka, cada um com sua responsabilidade.

---

# Trade-offs da abordagem

## Vantagens

* Separação real de responsabilidades entre API e Worker.
* Escalabilidade independente de cada componente.
* Custo de implementação baixíssimo: apenas anotações e um arquivo de
  configuração.
* Um único build, um único artefato, um único repositório.
* O modo `default` preserva a simplicidade do desenvolvimento local.

## Desvantagens

* API e Worker convivem no mesmo artefato; não há isolamento físico real.
* O artefato carrega dependências de ambos os modos, mesmo quando apenas um
  está ativo.
* Não reflete completamente um cenário de microsserviços com times e
  ciclos de deploy independentes.

---

# Perguntas comuns de entrevista

## Por que separar API e Worker?

Para permitir escalabilidade independente. Tráfego HTTP e carga de
processamento têm padrões de demanda diferentes e não devem competir pelos
mesmos recursos.

## Por que usar Spring Profiles em vez de módulos separados?

Porque entrega a separação de responsabilidades com baixíssimo custo de
implementação, adequado a um projeto de portfólio. Em um cenário real com
times distintos e deploys independentes, a separação em módulos ou serviços
seria preferível.

## Como o worker se mantém em execução sem servidor web?

Pelo loop de polling do consumidor Kafka, que mantém a JVM ativa.

## O que aconteceria se ambos rodassem na mesma porta?

Conflito de porta. Por isso o worker sobe com
`web-application-type: none`, sem servidor web.

---

# Pontos de melhoria futuros

* Em evolução para microsserviços reais, separar em módulos Maven
  (multi-module) com um módulo `common` compartilhando domínio e eventos.
* Containerizar cada perfil em imagens Docker dedicadas para deploy
  independente.
* Configurar réplicas independentes (por exemplo, em Kubernetes) para API e
  Worker, validando a escalabilidade independente na prática.

---

# Resumo para entrevistas

Ao explicar esta fase, os pontos mais importantes são:

* API e Worker foram separados em perfis Spring distintos (`web` e
  `worker`), sobre a mesma base de código.
* O perfil `default` mantém tudo junto para desenvolvimento local.
* O producer permanece sem perfil (a API precisa publicar); o consumer é
  exclusivo do worker.
* O worker roda headless (`web-application-type: none`) e se mantém vivo
  pelo polling do Kafka.
* A comunicação entre os dois ocorre exclusivamente via Kafka.
* A decisão por Spring Profiles em vez de módulos separados foi consciente,
  priorizando simplicidade adequada ao estágio do projeto.