# ADR 004 — Escolha de Kafka como tecnologia de mensageria na Fase 2

**Status:** Aceito
**Data:** 2026-06-27

---

## Contexto

A Fase 2 do projeto introduz processamento assíncrono: a criação de um
pagamento passa a publicar um evento, e o processamento (comunicação com
gateway, antifraude, atualização de status) acontece de forma desacoplada
da requisição HTTP.

Para isso, era necessário escolher uma tecnologia de mensageria. As duas
opções dominantes no ecossistema Java são **Apache Kafka** e **RabbitMQ**.

As duas resolvem comunicação assíncrona entre componentes, mas partem de
modelos conceituais diferentes:

- **RabbitMQ** é um *message broker* tradicional baseado em filas (modelo
  AMQP). A mensagem representa um comando ou tarefa, é consumida e removida
  da fila. Possui roteamento sofisticado (exchanges, routing keys,
  bindings).
- **Kafka** é um *log distribuído* append-only. A mensagem representa um
  fato ocorrido (evento), persiste após o consumo, e múltiplos consumidores
  independentes podem lê-la, cada um no seu ritmo. Garante ordem por
  partição e suporta altíssimo volume.

Um fator relevante de mercado também foi considerado: no Brasil, vagas de
nível pleno mencionam RabbitMQ com mais frequência que Kafka, enquanto
Kafka aparece com mais força em empresas de grande porte e vagas de nível
mais sênior.

---

## Decisão

Adotar **Apache Kafka** como tecnologia de mensageria da Fase 2, e
posteriormente desenvolver um projeto satélite menor utilizando
**RabbitMQ**, cobrindo as duas tecnologias no portfólio.

A motivação para começar por Kafka:

1. **Riqueza conceitual.** Kafka exige compreensão de partições, consumer
   groups, offsets, garantias de entrega e idempotência num nível mais
   profundo, fornecendo uma base de aprendizado mais sólida sobre sistemas
   distribuídos.
2. **Aderência ao domínio.** Um pagamento criado é naturalmente um **evento**
   ("PaymentCreated"), não um comando. O modelo de log de eventos do Kafka se
   encaixa melhor na modelagem event-driven da plataforma.
3. **Posicionamento técnico.** Perguntas sobre Kafka (event sourcing,
   garantias de entrega, idempotência) são frequentes em entrevistas de
   nível pleno e sênior.

A cobertura de RabbitMQ em um projeto satélite (por exemplo, um sistema de
notificações por e-mail, que é um caso típico de fila de tarefas) permite
demonstrar domínio das duas ferramentas e, principalmente, **critério de
escolha** entre elas conforme o caso de uso.

---

## Consequências

### Positivas

- Base conceitual sólida em sistemas distribuídos e arquitetura
  event-driven.
- Modelagem alinhada ao domínio (eventos de pagamento como log persistente).
- Implementação das três garantias fundamentais: ordenação (key por
  partição), idempotência (guarda de estado) e tratamento de falhas (retry +
  Dead Letter Queue).
- Diferencial em entrevistas técnicas de nível pleno e sênior.

### Negativas

- Kafka possui maior complexidade operacional que RabbitMQ (configuração de
  partições, retenção, consumer groups).
- Cobertura de RabbitMQ fica pendente de um projeto satélite futuro, ainda
  não desenvolvido.
- Para vagas pleno que exigem especificamente RabbitMQ, a experiência direta
  só estará completa após o projeto satélite.

---

## Alternativas consideradas

### Adotar RabbitMQ na Fase 2

Rejeitada como ponto de partida. Embora tenha maior aderência imediata a
vagas pleno no mercado brasileiro e curva de aprendizado mais suave,
oferece menor profundidade conceitual sobre sistemas distribuídos e se
encaixa menos no modelo event-driven do domínio de pagamentos.

### Implementar apenas uma das tecnologias

Rejeitada. Cobrir somente Kafka deixaria uma lacuna frequente em vagas
pleno; cobrir somente RabbitMQ deixaria de demonstrar o domínio conceitual
mais valorizado. A combinação das duas, com critério de escolha explícito,
é mais forte para o portfólio.

### Utilizar uma abstração agnóstica (Spring Cloud Stream)

Rejeitada para este momento. Abstrair o broker esconderia justamente os
detalhes (partições, offsets, garantias) que constituem o principal valor
de aprendizado da fase.

---

## Referências

- Documentação oficial do Apache Kafka — conceitos de topics, partitions e
  consumer groups.
- Documentação oficial do RabbitMQ — modelo AMQP, exchanges e filas.
- Revisão da Fase 2 (`docs/fase-2-kafka-revisao.md`) — detalhamento das três
  garantias implementadas.