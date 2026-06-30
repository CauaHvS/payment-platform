# ADR 008 — Observabilidade com Prometheus, Grafana e métricas de negócio

**Status:** Aceito
**Data:** 2026-06-30

---

## Contexto

Até a Fase 5, a plataforma estava funcional e testada, mas era uma caixa
preta em execução: não havia como saber, em tempo real, quantos pagamentos
estavam sendo processados, qual a taxa de falha, qual a latência das
requisições ou como estava o consumo de recursos. Para uma plataforma de
pagamentos, essa visibilidade é tão importante quanto a funcionalidade.

A Fase 6 teve como objetivo implementar observabilidade, com foco no pilar
de **métricas** (os outros pilares — logs e traces — ficam para evolução
futura). Era necessário escolher como expor, coletar, armazenar, visualizar
e alertar sobre métricas técnicas e de negócio.

---

## Decisão

Adotar a stack **Prometheus + Grafana**, padrão de mercado para métricas em
sistemas cloud-native, integrada via **Micrometer** e **Spring Boot
Actuator**.

### Arquitetura do fluxo

```
Aplicação (Micrometer)
  → expõe /actuator/prometheus
    → Prometheus coleta (scrape) periodicamente e armazena
      → Grafana consulta o Prometheus
        → dashboards e alertas
```

### Componentes

- **Micrometer**: fachada de métricas (como o SLF4J é para logs). Já vinha
  transitivamente com o Actuator. Instrumenta automaticamente JVM, HTTP,
  Kafka, pool de conexões, Spring Security.
- **micrometer-registry-prometheus**: a ponte que traduz as métricas do
  Micrometer para o formato de texto que o Prometheus consome, exposto em
  `/actuator/prometheus`.
- **Prometheus**: container que coleta as métricas a cada 5s (configurável)
  e as armazena como séries temporais. Alcança a aplicação no host via
  `host.docker.internal`.
- **Grafana**: container de visualização, conectado ao Prometheus como data
  source, com dashboards e regras de alerta.

### Métricas de negócio customizadas

Além das métricas automáticas, foram criadas duas métricas de negócio:

- **`payments_total`** (counter): incrementado a cada pagamento criado.
  Implementado com a anotação `@Counted` (via AOP), que conta as invocações
  do método `execute()` do `CreatePaymentService`.
- **`payments_processed_total`** (counter com tag `status`): incrementado a
  cada processamento, separando `COMPLETED` de `FAILED`. Implementado com
  counters manuais do Micrometer, registrados no construtor do
  `ProcessPaymentService`.

A escolha de duas abordagens diferentes foi deliberada: o `@Counted` é
elegante e suficiente para contar invocações simples (criação), mas não
consegue diferenciar resultados internos de um método (COMPLETED vs FAILED).
Para fatiar por status, o counter manual com tag é o caminho correto.

Habilitar o `@Counted` exigiu registrar o bean `CountedAspect` (numa
`MetricsConfig`), que ativa o aspecto AOP.

### Dashboard

Um dashboard no Grafana reúne painéis de quatro naturezas:

- **Negócio**: pagamentos processados por status, total criado, taxa de
  criação.
- **Aplicação**: latência das requisições HTTP (percentil 95).
- **Runtime**: uso de memória heap da JVM.
- **Infraestrutura**: conexões ativas do pool HikariCP.

Para o p95 de latência, foi necessário habilitar os histogramas de
distribuição (`management.metrics.distribution.percentiles-histogram.http.server.requests`),
que geram os buckets que a função `histogram_quantile` do PromQL consome.

### Alerta

Uma regra de alerta dispara quando a **taxa de falha de pagamentos**
ultrapassa 40%:

```promql
sum(payments_processed_total{status="FAILED"}) / sum(payments_processed_total) * 100
```

A regra avalia a cada 1 minuto, com *pending period* de 1 minuto (a condição
precisa se manter por esse período antes de disparar, evitando alarme falso
por picos momentâneos).

---

## Consequências

### Positivas

- Visibilidade em tempo real da saúde técnica e de negócio da plataforma.
- Métricas de negócio (taxa de falha, volume) que contam a história do
  produto, não só da infraestrutura.
- Stack padrão de mercado (Prometheus + Grafana), valiosa como demonstração
  de competência cloud-native.
- Alerta proativo: o sistema avisa quando algo sai do esperado, em vez de
  depender de alguém olhando o dashboard.
- Baixo acoplamento: a instrumentação via Micrometer não amarra o código a
  um backend específico de métricas.

### Negativas

- Apenas o pilar de métricas foi coberto. Logs estruturados centralizados e
  tracing distribuído (ex.: OpenTelemetry + Jaeger) ficam para evolução.
- O alerta não entrega notificação externa (email/Slack) neste momento —
  apenas muda de estado na UI do Grafana. Configurar um contact point real
  exige infraestrutura adicional (SMTP/webhook).
- Histogramas de latência geram muitas séries temporais; em produção, o
  custo de cardinalidade precisa ser avaliado.
- O `host.docker.internal` funciona para a app rodando no host; num cenário
  totalmente containerizado, o target do Prometheus seria o nome do serviço.

---

## Alternativas consideradas

### ELK / EFK Stack (Elasticsearch, Logstash/Fluentd, Kibana)

Rejeitada para este momento. É mais voltada a logs do que a métricas;
Prometheus + Grafana é o padrão para o pilar de métricas e mais leve para o
objetivo da fase.

### Datadog / New Relic (APM gerenciado)

Rejeitada. São soluções SaaS robustas, mas pagas e que abstraem os
mecanismos — o objetivo de portfólio é demonstrar o entendimento explícito
da stack de observabilidade open source.

### Apenas métricas automáticas, sem métricas de negócio

Rejeitada. As métricas automáticas (JVM, HTTP) mostram a saúde técnica, mas
não respondem perguntas de negócio (quantos pagamentos falharam?). As
métricas de negócio são o diferencial da fase.

---

## Referências

- Documentação do Micrometer — conceitos de Counter, Gauge, Timer e tags.
- Spring Boot Actuator — endpoint Prometheus e métricas.
- Prometheus — configuração de scrape e PromQL.
- Grafana — data sources, dashboards e alerting.
- ADR 005 (separação API/Worker via profiles) — contexto da arquitetura
  instrumentada.