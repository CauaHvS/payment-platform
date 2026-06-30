# Revisão da Fase 6 — Observabilidade

Documento de estudo da Fase 6 da plataforma de pagamentos. Cobre os
conceitos, as decisões e os pontos de atenção da stack de observabilidade
(Prometheus + Grafana + métricas de negócio + alertas), servindo como
material de revisão para entrevistas técnicas.

---

## Conceito central

Observabilidade é a capacidade de entender o que acontece **dentro** de um
sistema a partir do que ele expõe para fora. Três pilares:

1. **Métricas** — números agregados ao longo do tempo (taxa de requisições,
   latência, uso de memória, pagamentos por status). É o foco desta fase.
2. **Logs** — registros de eventos discretos (já existente via SLF4J).
3. **Traces** — o caminho de uma requisição pelo sistema distribuído (fica
   para evolução futura).

A Fase 6 implementa o pilar de métricas com Prometheus (coleta e armazena) e
Grafana (visualiza e alerta).

---

## O fluxo, ponta a ponta

```
Aplicação (Micrometer)
  → expõe /actuator/prometheus
    → Prometheus coleta (scrape) a cada N segundos e armazena
      → Grafana consulta o Prometheus
        → dashboards e alertas
```

Cada peça tem um papel:

- **Micrometer** é a fachada de métricas. Assim como o SLF4J abstrai o
  backend de logs, o Micrometer abstrai o backend de métricas. O código
  registra métricas no Micrometer sem saber se elas vão para o Prometheus,
  Datadog ou outro.
- **Spring Boot Actuator** expõe os endpoints de gestão, incluindo
  `/actuator/prometheus`.
- **micrometer-registry-prometheus** é a ponte: traduz as métricas do
  Micrometer para o formato de texto do Prometheus.
- **Prometheus** faz *scrape* (puxa) as métricas periodicamente e as guarda
  como séries temporais. Modelo *pull*: o Prometheus busca, a app não
  empurra.
- **Grafana** lê do Prometheus e desenha dashboards, além de avaliar regras
  de alerta.

---

## Métricas automáticas (de graça)

Só por ter o Actuator + Micrometer + a ponte do Prometheus, a aplicação já
expõe um arsenal de métricas sem escrever nada:

- **JVM**: memória (heap/non-heap), threads, garbage collection, classes.
- **HTTP**: `http_server_requests_seconds` — contagem e latência por rota,
  método e status.
- **HikariCP**: pool de conexões (ativas, idle, timeouts).
- **Kafka**: tempo do listener e do template de publicação.
- **Spring Security**: tempo dos filtros e decisões de autorização.
- **Sistema**: CPU, disco.

---

## Métricas de negócio (customizadas)

As métricas automáticas mostram a saúde técnica. As de negócio respondem
perguntas do produto. Foram criadas duas, com duas abordagens diferentes —
e a diferença é um bom ponto de entrevista.

### payments_total — com @Counted (AOP)

A anotação `@Counted` do Micrometer, via AOP, conta automaticamente quantas
vezes um método é chamado. Anotando o `execute()` do `CreatePaymentService`,
cada criação de pagamento incrementa o counter.

Pré-requisito: registrar o bean `CountedAspect` (numa `MetricsConfig`), que
ativa o aspecto. Sem ele, a anotação é ignorada silenciosamente.

Vantagem: elegante, quase sem código. Limitação: conta invocações, mas não
enxerga o **resultado** interno do método.

### payments_processed_total — counter manual com tag

Para separar `COMPLETED` de `FAILED`, o `@Counted` não serve: ele não
diferencia o resultado decidido dentro do método. A solução é um counter
manual com a **tag** `status`, registrado no construtor do
`ProcessPaymentService`:

```java
this.completedCounter = Counter.builder("payments.processed")
        .tag("status", "COMPLETED")
        .register(meterRegistry);
this.failedCounter = Counter.builder("payments.processed")
        .tag("status", "FAILED")
        .register(meterRegistry);
```

E incrementa o counter certo conforme o resultado. Pré-criar os counters no
construtor (em vez de buscá-los a cada chamada) é a prática recomendada:
evita lookup repetido e garante que ambas as séries existam desde o início.

Conceito de tags: um mesmo counter pode ser fatiado por dimensão. Assim,
`payments_processed_total{status="COMPLETED"}` e
`payments_processed_total{status="FAILED"}` são séries distintas do mesmo
counter.

### Counter vs Gauge vs Timer

- **Counter**: valor que só sobe (total de eventos). Ex.: pagamentos
  criados.
- **Gauge**: valor que sobe e desce (estado atual). Ex.: conexões ativas.
- **Timer**: mede duração e frequência. Ex.: latência de requisições.

---

## PromQL essencial

A linguagem de consulta do Prometheus. Alguns padrões usados no dashboard:

- **Valor instantâneo**: `payments_processed_total` — a série bruta.
- **Taxa de variação**: `rate(payments_total[1m])` — quanto o counter cresce
  por segundo, na janela de 1 minuto. Multiplicar por 60 dá "por minuto".
- **Percentil de latência**:
  `histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))` —
  o p95 (95% das requisições respondem abaixo desse tempo). Requer
  histogramas habilitados (ver abaixo).
- **Média**:
  `rate(..._sum[5m]) / rate(..._count[5m])` — alternativa ao percentil
  quando não há histograma.

### Por que o p95 precisava de config extra

Por padrão, o Spring não publica os *buckets* de histograma (geram muitas
séries). Sem buckets, o `histogram_quantile` não tem o que ler. Foi
necessário habilitar:

```yaml
management:
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
```

Isso passa a publicar `http_server_requests_seconds_bucket`, que o p95
consome.

---

## Dashboard

O dashboard reúne painéis de quatro naturezas, o que é exatamente como um
painel de produção real se organiza:

- **Negócio**: pagamentos por status, total criado, taxa de criação.
- **Aplicação**: latência HTTP (p95).
- **Runtime**: memória heap da JVM.
- **Infraestrutura**: conexões ativas do pool do banco.

Detalhe de rede importante: dentro do Docker Compose, o Grafana acessa o
Prometheus pelo **nome do serviço** (`http://prometheus:9090`), não por
`localhost` (que, dentro do container, seria o próprio Grafana). E o
Prometheus acessa a aplicação no host via `host.docker.internal`.

---

## Alertas

Um alerta transforma observabilidade passiva (olhar o dashboard) em ativa (o
sistema avisa). Tem três partes:

1. **Query + condição**: o que medir e o limiar. Ex.: taxa de falha acima de
   40%.
2. **Avaliação**: de quanto em quanto tempo checar (ex.: 1 min) e o *pending
   period* — por quanto tempo a condição precisa se manter antes de disparar.
3. **Notificação**: para onde mandar (email, Slack). Em dev, o alerta muda
   de estado na UI mesmo sem canal externo configurado.

O alerta criado dispara quando:

```promql
sum(payments_processed_total{status="FAILED"}) / sum(payments_processed_total) * 100
```

passa de 40. O *pending period* de 1 minuto evita alarme falso: um pico
momentâneo de falha não dispara; só dispara se o problema persistir.

Estados do alerta: **Normal** (condição não violada) → **Pending** (violada,
aguardando o pending period) → **Firing** (disparado).

---

## Perguntas de entrevista

**Quais são os pilares da observabilidade?**
Métricas, logs e traces.

**Prometheus é push ou pull?**
Pull: o Prometheus puxa (faz scrape) as métricas dos alvos periodicamente.

**O que é o Micrometer?**
A fachada de métricas do ecossistema Spring; abstrai o backend (Prometheus,
Datadog, etc.), como o SLF4J faz para logs.

**Diferença entre Counter, Gauge e Timer?**
Counter só sobe (total de eventos); Gauge sobe e desce (estado atual); Timer
mede duração e frequência.

**Para que servem as tags/labels numa métrica?**
Fatiar a mesma métrica por dimensão (ex.: status COMPLETED vs FAILED),
gerando séries distintas.

**Quando usar @Counted e quando usar counter manual?**
`@Counted` para contar invocações simples de um método. Counter manual
quando é preciso uma tag baseada no resultado interno (que o `@Counted` não
enxerga).

**O que é o p95 de latência e por que importa?**
O tempo abaixo do qual 95% das requisições respondem. Importa porque a média
esconde outliers; o p95 reflete a experiência da cauda lenta.

**O que é o pending period de um alerta?**
O tempo que a condição precisa se manter violada antes de o alerta disparar,
evitando alarme falso por picos momentâneos.

---

## Trade-offs e melhorias futuras

- **Só métricas**: faltam logs centralizados e tracing distribuído
  (OpenTelemetry + Jaeger seria a evolução natural).
- **Notificação real**: configurar um contact point (email/Slack) para o
  alerta entregar de fato, não só mudar de estado.
- **Cardinalidade**: histogramas geram muitas séries; em produção, monitorar
  o custo.
- **Mais alertas**: app fora do ar (target down), latência alta, memória
  perto do limite, fila de processamento crescendo.
- **Dashboards versionados**: exportar o JSON do dashboard para o repositório
  (provisionamento como código), em vez de configurá-lo manualmente.

---

## Resumo

A Fase 6 deu visibilidade à plataforma. Com Prometheus + Grafana integrados
via Micrometer e Actuator, a aplicação passou a expor métricas técnicas
(JVM, HTTP, Kafka, pool) e de negócio (pagamentos criados e processados por
status). Um dashboard reúne as quatro camadas — negócio, aplicação, runtime
e infraestrutura — e um alerta dispara quando a taxa de falha de pagamentos
ultrapassa o limite aceitável. A decisão mais instrutiva da fase foi usar
`@Counted` onde ele cabia (contagem simples) e counter manual com tag onde
era necessário fatiar por resultado — demonstrando entendimento das duas
ferramentas e de quando cada uma se aplica.