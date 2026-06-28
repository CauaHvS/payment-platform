# Revisão da Fase 2 — Event-Driven com Kafka

Documento de estudo da plataforma de pagamentos. Cobre as decisões de arquitetura, os conceitos importantes e os principais pontos de atenção da Fase 2, servindo como material de revisão para entrevistas técnicas.

---

# Conceito central

Nesta fase, separamos **recepção** de **processamento**.

O endpoint `POST /payments` passa a ser responsável apenas por registrar a intenção de pagamento: ele persiste o pagamento como `PENDING`, publica um evento e responde imediatamente ao cliente.

O processamento efetivo ocorre de forma assíncrona em um consumer Kafka.

Essa arquitetura reduz o tempo de resposta da API, desacopla responsabilidades e torna o sistema mais resiliente a falhas de componentes downstream, como motores antifraude, gateways de pagamento e serviços de notificação.

Também facilita a escalabilidade horizontal, já que múltiplos consumidores podem processar eventos em paralelo.

---

# compose.yml

O Kafka é executado em **modo KRaft**, eliminando a necessidade do Zookeeper. O mesmo container atua simultaneamente como:

* Broker
* Controller

O ponto que mais costuma gerar dúvidas é a configuração dos **dois listeners**.

## PLAINTEXT (29092)

Usado para comunicação entre containers da rede Docker.

Exemplo:

* Kafka UI
* Outros containers
* Futuramente outros microsserviços

## PLAINTEXT_HOST (9092)

Usado por aplicações executando fora da rede Docker.

Exemplo:

* Aplicação Spring Boot rodando na máquina do desenvolvedor.

A diferença existe porque o Kafka anuncia (`advertised.listeners`) o endereço que os clientes deverão utilizar.

Quem está dentro da rede Docker enxerga o broker pelo nome do container.

Quem está fora precisa enxergá-lo via `localhost`.

**Pergunta comum de entrevista**

> Por que usar dois listeners?

Porque o endereço anunciado pelo broker deve ser diferente para clientes internos da rede Docker e clientes externos.

---

# PaymentCreatedEvent (record)

Eventos representam fatos que já aconteceram.

Como fatos não devem mudar, faz sentido modelá-los como um `record`, que é naturalmente imutável.

Além disso, o evento possui um factory:

```java
PaymentCreatedEvent.from(payment)
```

Esse método centraliza toda a conversão entre o modelo de domínio (`Payment`) e o contrato do evento.

Outra decisão importante é transportar apenas tipos simples:

* UUID
* String
* BigDecimal

Em vez de enviar objetos do domínio (como `Money`), o evento atravessa a fronteira do sistema em formato JSON e deve permanecer independente da implementação interna da aplicação.

---

# KafkaProducerConfig

O Spring Boot consegue criar automaticamente um `KafkaTemplate`, mas neste projeto foi criado um bean explícito e tipado:

```java
KafkaTemplate<String, PaymentCreatedEvent>
```

Isso garante segurança de tipos durante a publicação dos eventos e evita casts desnecessários.

Configuração dos serializers:

* `StringSerializer` para a key.
* `JsonSerializer` para o valor.

Também foi configurado:

```java
JsonSerializer.ADD_TYPE_INFO_HEADERS = false
```

Assim o produtor não envia metadados específicos do Java nos headers da mensagem, deixando o JSON mais limpo e facilitando integração com aplicações escritas em outras linguagens.

---

# KafkaPaymentEventPublisher

Implementa a porta `PaymentEventPublisher`, seguindo os princípios da Arquitetura Hexagonal.

O domínio conhece apenas a interface.

Somente a camada de infraestrutura conhece o Kafka.

A publicação acontece através de:

```java
kafkaTemplate.send(
    TOPIC,
    payment.id().toString(),
    event
);
```

O segundo argumento é a **key** da mensagem.

Neste projeto, utilizamos o `paymentId`.

Isso garante que todos os eventos referentes ao mesmo pagamento sejam enviados para a mesma partição.

Como o Kafka preserva ordem apenas dentro de uma partição, todos os eventos daquele pagamento serão processados na ordem correta.

**Importante**

A ordem **não é garantida entre partições diferentes**.

Eventos com keys distintas podem ser processados em paralelo e não possuem ordem relativa entre si.

**Pergunta comum de entrevista**

> Como garantir ordem no Kafka?

Utilizando sempre a mesma key para entidades relacionadas.

Mesma key → mesma partição → ordem preservada naquela partição.

---

# CreatePaymentService

Após persistir o pagamento:

```java
paymentRepository.save(payment);
```

o serviço publica:

```java
eventPublisher.publishPaymentCreated(payment);
```

Assim o endpoint devolve resposta imediatamente, enquanto o processamento real acontece de forma assíncrona.

## Dual Write

Existe um problema conhecido nesta abordagem.

Se o evento for publicado com sucesso, mas o commit da transação do banco falhar logo depois, teremos um evento referente a um pagamento que nunca foi persistido.

Esse problema é conhecido como **Dual Write**.

A solução adotada por sistemas distribuídos é o **Transactional Outbox Pattern**.

Nesse padrão:

1. A transação grava o pagamento.
2. Na mesma transação grava o evento em uma tabela Outbox.
3. Um processo separado lê essa tabela e publica os eventos no Kafka.

Dessa forma banco e evento permanecem consistentes.

Ainda não implementado neste projeto.

---

# KafkaConsumerConfig

Foi criada uma configuração manual do consumidor.

Principais pontos:

## JsonDeserializer

Configurado diretamente para:

```java
PaymentCreatedEvent.class
```

Como consequência, o produtor não precisa enviar informações de tipo nos headers.

## Trusted Packages

```java
addTrustedPackages(...)
```

É uma medida de segurança.

Permite desserializar apenas classes pertencentes a pacotes confiáveis, evitando ataques de desserialização de classes arbitrárias.

## AUTO_OFFSET_RESET

Configurado como:

```text
earliest
```

Quando um consumer novo entra no grupo e ainda não possui offset salvo, ele começa a leitura desde o início do tópico.

Foi exatamente por isso que, na primeira execução, os pagamentos antigos também foram processados.

## EnableKafka

Como a configuração foi criada manualmente, foi necessário utilizar:

```java
@EnableKafka
```

Sem essa anotação os métodos anotados com `@KafkaListener` não seriam registrados.

---

# PaymentCreatedListener

O listener utiliza:

```java
@KafkaListener(...)
```

para se inscrever no tópico.

Também define:

```text
groupId = payment-processor
```

Esse consumer group permite que múltiplas instâncias da aplicação compartilhem o processamento das partições.

Benefícios:

* escalabilidade horizontal;
* balanceamento automático;
* failover.

Cada partição é atribuída a apenas um consumer dentro do grupo.

Caso existam mais consumidores do que partições, alguns consumidores permanecerão ociosos.

---

# ProcessPaymentService — Idempotência

O primeiro passo do processamento é verificar o estado do pagamento:

```java
if (!payment.isPending()) {
    return;
}
```

Essa guarda torna o processamento idempotente.

A configuração padrão do Kafka/Spring Kafka oferece semântica **at least once**, ou seja, uma mesma mensagem pode ser entregue novamente em situações como:

* falha de ACK;
* reinício do consumer;
* rebalanceamento;
* timeout.

Sem essa verificação haveria risco de:

* exceções;
* trabalho duplicado;
* alteração incorreta do estado.

Com a guarda, um reprocessamento torna-se simplesmente um **no-op**, ou seja, uma operação sem efeito.

Isso funciona porque:

* o estado é persistido no banco;
* todos os eventos do mesmo pagamento chegam na mesma partição;
* esses eventos são processados em sequência.

## Limitação

Essa estratégia funciona muito bem quando existe uma máquina de estados.

Para operações sem estado evidente (como envio de e-mails, geração de notificações ou integração com terceiros), normalmente utiliza-se uma **tabela de idempotência**, armazenando os IDs dos eventos já processados.

---

# Dead Letter Queue (DLQ)

Sem uma DLQ, um evento defeituoso (poison pill) poderia falhar indefinidamente e bloquear o processamento da partição.

Neste projeto foi configurado:

* `DefaultErrorHandler`
* `DeadLetterPublishingRecoverer`

Após:

```text
FixedBackOff(1000, 2)
```

o consumer realiza:

* 2 tentativas de reprocessamento;
* intervalo de 1 segundo entre elas.

Se todas falharem, a mensagem é enviada para:

```text
payment.created-dlt
```

e o processamento da fila principal continua normalmente.

No Spring Kafka 4.x, a convenção é utilizar o sufixo:

```text
-dlt
```

em vez do antigo `.DLT`.

## Relação com a idempotência

Caso uma tentativa tenha alterado parcialmente o estado do pagamento antes da falha, uma nova tentativa encontrará o estado atualizado e não executará novamente a operação.

## Importante

A DLQ **não resolve** o problema da mensagem.

Ela apenas isola o evento problemático para que o restante do sistema continue funcionando.

Os eventos enviados para a DLQ devem ser monitorados e analisados posteriormente.

---

# As quatro garantias implementadas

## 1. Ordering

Mesma key → mesma partição → ordem preservada.

---

## 2. Idempotência

A guarda baseada no estado torna seguro reprocessar eventos.

---

## 3. Failure Handling

Retry + DLQ impedem que mensagens defeituosas bloqueiem o processamento.

---

## 4. Escalabilidade

Consumer Groups permitem adicionar novas instâncias da aplicação para dividir as partições automaticamente.

---

# Vocabulário Kafka

## Topic

Canal nomeado onde eventos são publicados.

---

## Producer

Quem publica eventos.

Neste projeto:

`CreatePaymentService`

---

## Consumer

Quem consome eventos.

Neste projeto:

`PaymentCreatedListener`

---

## Broker

Servidor Kafka responsável por armazenar tópicos, receber mensagens dos produtores e entregá-las aos consumidores.

---

## Partition

Divisão física de um tópico.

Permite paralelismo.

A ordem é garantida apenas dentro de uma mesma partição.

---

## Key

Valor utilizado para determinar em qual partição uma mensagem será armazenada.

Mesma key → mesma partição.

---

## Consumer Group

Grupo de consumidores que divide as partições entre si.

Permite:

* escalabilidade horizontal;
* balanceamento;
* failover.

---

## Offset

Posição da mensagem dentro de uma partição.

Permite continuar o processamento de onde parou ou reprocessar mensagens.

---

# Trade-offs da arquitetura

## Vantagens

* Endpoint responde rapidamente.
* Processamento desacoplado.
* Melhor tolerância a falhas.
* Escalabilidade horizontal.
* Componentes independentes.
* Melhor aproveitamento de recursos.

## Desvantagens

* Consistência passa a ser eventual.
* Debug torna-se mais complexo.
* Necessidade de monitorar Kafka e DLQ.
* Introdução de desafios como dual write e idempotência.
* Maior complexidade operacional.

---

# Perguntas comuns de entrevista

## Por que usar Kafka em vez de chamadas síncronas?

Porque reduz acoplamento entre componentes, melhora a escalabilidade e evita que a indisponibilidade de um serviço bloqueie todo o fluxo.

---

## Como garantir ordem dos eventos?

Utilizando a mesma key para todos os eventos relacionados à mesma entidade.

Mesma key → mesma partição → ordem preservada.

---

## Como evitar processamento duplicado?

Implementando idempotência.

Neste projeto, isso é feito verificando o estado atual do pagamento antes de iniciar o processamento.

---

## Como lidar com falhas?

Utilizando retentativas automáticas e Dead Letter Queue (DLQ).

---

## Como resolver o problema de Dual Write?

Com o Transactional Outbox Pattern.

---

## Por que utilizar `record` para eventos?

Porque eventos representam fatos imutáveis, e `record` fornece uma implementação simples, concisa e naturalmente imutável.

---

# Pontos de melhoria futuros

* Implementar **Transactional Outbox** para eliminar o problema de dual write.
* Criar uma **tabela de idempotência** caso surjam operações sem máquina de estados.
* Avaliar a migração para os novos mecanismos de serialização recomendados pelo Spring Kafka 4.x, já que `JsonSerializer` e `JsonDeserializer` estão marcados para descontinuação futura.
* Criar testes de integração completos utilizando `testcontainers-kafka`.
* Implementar observabilidade (métricas, logs estruturados e tracing) para acompanhar publicação, consumo, retries e DLQ.

---

# Resumo para entrevistas

Ao explicar esta fase, os pontos mais importantes são:

* O endpoint apenas registra a intenção de pagamento.
* O processamento ocorre de forma assíncrona via Kafka.
* O domínio permanece desacoplado da infraestrutura através da Arquitetura Hexagonal.
* A key (`paymentId`) garante ordering.
* A guarda de estado garante idempotência.
* Retry + DLQ garantem tratamento de falhas.
* Consumer Groups permitem escalabilidade horizontal.
* O principal problema arquitetural remanescente é o **Dual Write**, cuja solução é o **Transactional Outbox Pattern**.

Esses conceitos representam os fundamentos de uma arquitetura orientada a eventos preparada para evoluir em direção a cenários de produção.
