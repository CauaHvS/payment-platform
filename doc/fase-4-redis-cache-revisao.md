# Revisão da Fase 4 — Cache de Leitura com Redis

Documento de estudo da plataforma de pagamentos. Cobre as decisões de
arquitetura, os conceitos importantes e os principais pontos de atenção da
Fase 4, servindo como material de revisão para entrevistas técnicas.

---

# Conceito central

O endpoint `GET /payments/{id}` lia diretamente do PostgreSQL a cada
chamada. Em cenários reais, clientes consultam repetidamente o mesmo
pagamento (por exemplo, fazendo polling para saber quando o processamento
assíncrono concluiu). Cada consulta gerava uma ida ao banco, mesmo quando o
dado não havia mudado.

A Fase 4 introduz uma camada de **cache de leitura** com **Redis**, um
armazenamento chave-valor em memória com latência na casa de
microssegundos. Após a primeira leitura, o resultado fica no Redis, e as
próximas consultas ao mesmo pagamento são respondidas sem tocar no
PostgreSQL.

Por ser um cache **distribuído** (processo próprio, fora da aplicação),
múltiplas instâncias da API compartilham o mesmo cache.

---

# compose.yml

Foi adicionado o serviço Redis:

```yaml
redis:
    image: redis:7-alpine
    container_name: payment-platform-redis
    ports:
        - "6379:6379"
    volumes:
        - redis-data:/data
```

Porta padrão 6379, com volume para persistência opcional dos dados.

---

# Dependência e habilitação

A dependência `spring-boot-starter-data-redis` adiciona o suporte. O cache
é habilitado com a anotação `@EnableCaching` (colocada na classe de
configuração do Redis).

---

# RedisCacheConfig

Configuração central do cache. Três decisões importantes foram tomadas
aqui.

## 1. Serializer tipado em vez de genérico

A serialização do valor utiliza um serializer **tipado** para
`PaymentResponse`, e não o serializer genérico.

O serializer genérico exige que a informação de tipo seja embutida no JSON
(default typing). A tentativa de habilitar isso gerou conflito de formato na
leitura:

```text
expected JsonToken.START_ARRAY ... WRAPPER_ARRAY type information
```

O serializer tipado conhece a classe de antemão (`PaymentResponse.class`),
dispensando informação de tipo no JSON. Resultado: JSON limpo e
desserialização garantida. Para um cache de tipo único, é a abordagem mais
simples e robusta.

## 2. TTL e null values

```java
.entryTtl(Duration.ofMinutes(10))
.disableCachingNullValues()
```

* TTL de 10 minutos: toda entrada expira após esse período, como camada
  extra de proteção contra dados obsoletos.
* `disableCachingNullValues()`: não cacheia ausência de resultado (não faz
  sentido cachear um "não encontrado").

## 3. CacheManager explícito

Foi necessário declarar um bean `RedisCacheManager` explicitamente:

```java
@Bean
public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
    return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(cacheConfiguration())
            .build();
}
```

Quando se define uma `RedisCacheConfiguration` manual, o Spring Boot não
cria automaticamente o `CacheManager`. Sem esse bean, a aplicação falhava na
inicialização com "required a bean of type CacheManager that could not be
found".

---

# GetPaymentService

Service de leitura dedicado, criado para aplicar o cache corretamente.

```java
@Cacheable(value = "payments", key = "#id",
           unless = "#result == null || !#result.isFinal()")
public PaymentResponse findById(UUID id) {
    return paymentRepository.findById(id)
            .map(PaymentResponse::fromDomain)
            .orElse(null);
}
```

## Por que um service separado (self-invocation)

O cache do Spring funciona via **proxy**. Se um método anotado com
`@Cacheable` for chamado de dentro da própria classe, o proxy não é
acionado e o cache não funciona. Por isso, o `@Cacheable` foi colocado num
service dedicado, chamado de fora (pelo controller), e não diretamente no
controller.

## Por que cachear o DTO, não a entidade

O cache armazena `PaymentResponse` (record), e não a entidade `Payment`.

A entidade `Payment` tem construtor privado e é criada apenas via factories
(`create`, `reconstruct`), para proteger suas invariantes. Isso impede que o
Jackson a reconstrua durante a desserialização — a tentativa resultava em:

```text
ClassCastException: LinkedHashMap cannot be cast to Payment
```

O JSON voltava como um `LinkedHashMap` genérico, pois o Jackson não sabia
remontar a entidade. O `PaymentResponse`, por ser um record imutável de
transporte, serializa e desserializa trivialmente.

Prática adotada: **cachear o objeto que cruza a borda da aplicação (DTO),
não o modelo de domínio interno.**

## Por que cachear apenas estados finais

A condição `unless` impede que pagamentos não-finais sejam cacheados:

```java
unless = "#result == null || !#result.isFinal()"
```

Apenas pagamentos `COMPLETED` ou `FAILED` entram no cache. Isso
**elimina o problema de invalidação de cache na raiz**: o que é armazenado
nunca mais muda, portanto nunca fica desatualizado. Dispensa o uso de
`@CacheEvict` sincronizado com o worker.

O método `isFinal()` foi adicionado ao `PaymentResponse`:

```java
public boolean isFinal() {
    return status == PaymentStatus.COMPLETED || status == PaymentStatus.FAILED;
}
```

O trade-off: consultas a pagamentos em estado não-final sempre vão ao
banco. Na prática, o pagamento permanece em estado não-final por
milissegundos; o volume real de consultas recai sobre pagamentos já
finalizados, que é justamente o que se cacheia.

---

# Estrutura da chave no Redis

Com `value = "payments"` e `key = "#id"`, a chave gerada no Redis tem o
formato:

```text
payments::<uuid>
```

O prefixo `payments` vem do nome do cache, e o restante é o UUID do
pagamento.

---

# Trade-offs da abordagem

## Vantagens

* Redução da carga de leitura sobre o PostgreSQL.
* Cache distribuído compartilhado entre instâncias da API.
* Estratégia de estados finais elimina a complexidade de invalidação.
* JSON armazenado é limpo e legível (serializer tipado).

## Desvantagens

* Consultas a pagamentos em estado não-final não se beneficiam do cache.
* Mais um componente de infraestrutura para operar e monitorar.
* A serialização exigiu configuração cuidadosa devido às mudanças do
  Jackson 3 no Spring Boot 4.

---

# Perguntas comuns de entrevista

## Por que cachear o DTO e não a entidade de domínio?

Porque a entidade tem construtor privado e factories, o que impede a
desserialização pelo Jackson. Além disso, cachear o DTO evita acoplar o
modelo de domínio à camada de cache.

## Como lidar com invalidação de cache?

Neste projeto, cacheando apenas estados finais — que nunca mudam. Assim o
cache nunca fica desatualizado, dispensando invalidação ativa. Alternativa
seria `@CacheEvict` ao atualizar o estado.

## O que é self-invocation no cache do Spring?

Quando um método com `@Cacheable` é chamado de dentro da própria classe, o
proxy não intercepta e o cache não funciona. Por isso o método cacheado fica
num bean separado, chamado externamente.

## Por que usar serializer tipado em vez de genérico?

Porque o genérico exige type info embutido no JSON (frágil e propenso a
conflito de formato). O tipado conhece a classe de antemão, gerando JSON
limpo. Ideal para cache de tipo único.

## Qual a diferença entre Redis e um cache em memória local?

O cache local pertence a cada instância (não compartilhado). O Redis é um
processo externo compartilhado por todas as instâncias, mantendo
consistência num ambiente distribuído.

---

# Pontos de melhoria futuros

* Cachear também listagens ou outras consultas frequentes, se surgirem.
* Avaliar políticas de eviction do Redis (LRU, LFU) conforme o volume.
* Adicionar métricas de cache (hit rate, miss rate) na fase de
  observabilidade.
* Considerar `@CacheEvict` caso passe a ser necessário cachear estados
  não-finais.

---

# Resumo para entrevistas

Ao explicar esta fase, os pontos mais importantes são:

* O cache Redis reduz a carga de leitura sobre o PostgreSQL em consultas
  repetidas.
* Cacheia-se o DTO (`PaymentResponse`), não a entidade de domínio.
* Cacheia-se apenas estados finais, o que elimina o problema de invalidação.
* O `@Cacheable` fica num service dedicado para evitar self-invocation.
* A serialização usa um serializer tipado, gerando JSON limpo.
* A chave no Redis tem o formato `payments::<uuid>`.