# Revisão da Fase 1 — REST API com Arquitetura Hexagonal

Documento de estudo da plataforma de pagamentos. Cobre as decisões de
arquitetura, os conceitos importantes e os principais pontos de atenção da
Fase 1, servindo como material de revisão para entrevistas técnicas.

---

## Conceito central

A primeira fase do projeto teve como objetivo construir uma API REST
completa utilizando **Arquitetura Hexagonal (Ports & Adapters)**.

O princípio fundamental dessa arquitetura é manter o **domínio isolado** de
qualquer tecnologia externa. O domínio conhece apenas regras de negócio.
Banco de dados, HTTP, Spring Boot, JPA e demais frameworks ficam nas bordas
da aplicação e se comunicam com o domínio através de **portas (interfaces)**
e **adapters (implementações)**.

A principal regra da arquitetura é:

> **As dependências sempre apontam para dentro.**

Ou seja:

- o domínio não conhece infraestrutura;
- a aplicação conhece o domínio;
- a infraestrutura conhece a aplicação e o domínio.

Essa separação facilita testes, manutenção e substituição de tecnologias
sem alterar as regras de negócio.

---

## Camada de Domínio

A camada de domínio concentra exclusivamente as regras de negócio. Ela não
possui dependência de Spring, JPA, banco de dados ou qualquer framework. Por
esse motivo, seus testes são extremamente rápidos e podem ser executados
apenas com JUnit.

### Money (Value Object)

`Money` representa um valor monetário. Foi modelado como um **Value Object**
utilizando `record`. A escolha faz sentido porque dinheiro não possui
identidade própria, é imutável, e dois objetos com mesmo valor representam
exatamente a mesma informação.

**BigDecimal.** Valores monetários utilizam `BigDecimal`. Nunca devem
utilizar `double` ou `float`, pois esses tipos usam ponto flutuante binário
e acumulam erros de precisão. Exemplo clássico: `0.1 + 0.2 != 0.3`. Um dos
assuntos mais frequentes em entrevistas Java.

**Always-Valid Model.** O construtor compacto realiza todas as validações
necessárias, garantindo que um objeto `Money` nunca exista em estado
inválido. Validações típicas: valor obrigatório, moeda obrigatória, escala
compatível com a moeda, valores negativos proibidos (se for regra do
domínio).

**BigDecimal.equals().** Esse método considera também a escala. Assim,
`100.00 != 100` mesmo representando o mesmo valor numérico. Em testes
utiliza-se `compareTo()` ou, no AssertJ, `isEqualByComparingTo()`, que
ignoram a diferença de escala.

### Currency (Enum)

Inicialmente a moeda era representada por `String`. Posteriormente foi
substituída por um `enum`. Essa alteração elimina um problema conhecido como
**Stringly Typed Code**, onde valores válidos são representados apenas por
texto. Exemplo de erro possível com String: `"REIAS"` — o compilador não
detecta. Com enum, apenas moedas previamente definidas (BRL, USD, EUR) podem
ser utilizadas. Cada moeda também carrega sua própria configuração (casas
decimais via `minorUnitScale`, possíveis regras futuras de arredondamento),
concentrando conhecimento do domínio dentro da própria enumeração.

### PaymentStatus (Enum)

Representa a máquina de estados do pagamento: `PENDING`, `PROCESSING`,
`COMPLETED`, `FAILED`. Utilizar enum torna as transições explícitas e
elimina valores inválidos.

### Payment (Entity)

Ao contrário de `Money`, `Payment` foi modelado como uma **Entity**. A
principal diferença é que entidades possuem identidade: dois pagamentos
podem ter exatamente os mesmos dados, mas continuam sendo objetos diferentes
porque possuem IDs distintos. Além disso, seu estado evolui durante o ciclo
de vida.

- Campos mutáveis: status, updatedAt.
- Campos imutáveis: id, payerId, payeeId, money, createdAt.

Por esse motivo, utilizar um `record` não faria sentido.

**Criação e reconstrução.** O construtor da entidade é privado. A criação
acontece através de métodos factory:

- `create()` — cria um novo pagamento: gera os timestamps, inicializa o
  status como `PENDING`, e garante que `createdAt` e `updatedAt` sejam iguais
  na criação. Capturar `Instant.now()` apenas uma vez evita pequenas
  diferenças entre os campos.
- `reconstruct()` — utilizado quando o pagamento é carregado do banco. O
  objeto precisa ser remontado exatamente com o estado persistido. Essa
  responsabilidade pertence ao adapter de persistência.

**Máquina de estados.** O pagamento controla suas próprias transições, com
`startProcessing()`, `complete()` e `fail()`. Cada método valida se a
transição é permitida:

```text
PENDING → PROCESSING → COMPLETED
PENDING → PROCESSING → FAILED
```

Transições inválidas lançam `IllegalStateException` (ex: completar um
pagamento já concluído, completar um que falhou, iniciar processamento duas
vezes). Essa validação protege as invariantes do domínio.

**Tell, Don't Ask.** Em vez de consultar o estado e setar de fora:

```java
if (payment.getStatus() == PROCESSING) {
    payment.setStatus(COMPLETED);
}
```

fazemos:

```java
payment.complete();
```

A própria entidade decide se a operação é válida. Isso reduz acoplamento e
evita duplicação de regras de negócio espalhadas pelo sistema.

**Predicados.** A entidade fornece `isPending()`, `isProcessing()`,
`isCompleted()`, `isFailed()`, tornando o código mais legível e evitando
comparações repetidas com enums.

---

## Camada de Aplicação

A camada de aplicação orquestra os casos de uso. Ela não implementa regras
de negócio complexas. Sua responsabilidade é receber dados, converter
objetos, coordenar chamadas ao domínio, e utilizar portas de entrada e
saída.

### Ports (Interfaces)

A Arquitetura Hexagonal divide as interfaces em dois grupos.

**Portas de entrada.** Representam os casos de uso da aplicação. Neste
projeto: `CreatePaymentUseCase`. Quem utiliza essa interface não precisa
conhecer sua implementação. O método recebe um `CreatePaymentCommand`, que
segue o padrão **Command**, encapsulando todos os dados necessários para
executar o caso de uso. Utiliza apenas tipos simples (UUID, String,
BigDecimal), evitando expor objetos do domínio para as camadas externas.

**Portas de saída.** Representam dependências externas necessárias para
executar o caso de uso. Neste projeto: `PaymentRepository`, que define
`save()` e `findById()`. Pertence à camada de aplicação e não possui nenhuma
anotação de JPA ou Spring Data. Assim, a aplicação conhece apenas o
contrato, nunca a tecnologia utilizada para persistência.

### CreatePaymentService

Implementa o caso de uso `CreatePaymentUseCase` e controla a transação via
`@Transactional`. Fluxo: recebe o `CreatePaymentCommand`, converte tipos
primitivos para objetos do domínio, cria um `Money`, converte a moeda com
`Currency.valueOf()`, cria um `Payment`, salva via porta `PaymentRepository`
e retorna o pagamento. O serviço atua como orquestrador — toda regra de
negócio continua concentrada no domínio.

### Dependency Inversion

O serviço depende da abstração `PaymentRepository`, não da implementação
concreta. Quem fornece a implementação é o Spring via injeção de
dependências. Esse é o **Dependency Inversion Principle (DIP)**. Graças a
ele, o domínio permanece desacoplado, a implementação pode mudar (JPA, JDBC,
MongoDB etc.), e testes podem utilizar mocks ou fakes facilmente.

---

## Camada de Adapters

Na Arquitetura Hexagonal, os adapters ficam nas bordas da aplicação. Sua
responsabilidade é adaptar tecnologias externas para que o domínio permaneça
independente. Nesta fase existem dois grandes grupos: persistência
(JPA/PostgreSQL) e web (REST).

### Adapter de Persistência (Escola B)

O projeto utiliza o que muitos chamam de **Escola B da Arquitetura
Hexagonal**: a entidade de domínio e a entidade JPA são objetos diferentes.
O domínio continua completamente limpo, sem nenhuma anotação de
infraestrutura. Essa decisão evita acoplamento entre regras de negócio e
tecnologia de persistência. Caso o banco seja substituído no futuro, o
domínio permanece inalterado.

**PaymentJpaEntity.** Representa exclusivamente o formato de armazenamento.
Possui anotações como `@Entity`, `@Table`, `@Column`, `@Id`. Ao contrário da
entidade de domínio, **não** é um `record`, porque o JPA necessita de
construtor sem argumentos, objetos mutáveis e gerenciamento de ciclo de vida
pelo EntityManager.

**Mapeamento dos Enums.** Detalhe extremamente importante:

```java
@Enumerated(EnumType.STRING)
```

Faz o banco armazenar `PENDING`, `PROCESSING`, etc., em vez de `0`, `1`, `2`.
Utilizar `EnumType.ORDINAL` é má prática: caso um novo valor seja inserido ou
a ordem do enum seja alterada, todos os registros antigos passam a
representar estados incorretos.

> **Pergunta comum de entrevista:** Por que utilizar `EnumType.STRING`?
> Porque o banco armazena o nome do enum em vez do índice numérico, evitando
> corrupção de dados caso o enum seja reorganizado.

**PaymentJpaRepository.** A interface estende
`JpaRepository<PaymentJpaEntity, UUID>`. Nenhuma implementação é escrita
manualmente — o Spring Data cria uma em tempo de execução via proxies
dinâmicos. Isso elimina código repetitivo para `save`, `findById`, `delete`,
`existsById`, e consultas podem ser criadas apenas pelo nome do método.

**PaymentRepositoryAdapter.** Implementa a porta `PaymentRepository`. É a
ponte entre o domínio e o banco, realizando as conversões via `toEntity()` e
`toDomain()`:

```text
Domínio → PaymentJpaEntity → Banco
Banco → PaymentJpaEntity → Domínio
```

Essa separação evita que detalhes do JPA contaminem o domínio.

**Benefícios e custo.** A separação mantém o domínio independente de
frameworks, testável sem banco, e facilita migração de tecnologia. Como
desvantagem, há uma pequena duplicação de classes e código de conversão.
Trade-off que costuma valer a pena em aplicações de médio e grande porte.

### Adapter Web (REST)

A API REST converte requisições HTTP em chamadas aos casos de uso. O domínio
nunca conhece HTTP, JSON, Controller ou ResponseEntity.

**CreatePaymentRequest.** DTO de entrada (`record`). Usa Bean Validation
(`@NotBlank`, `@NotNull`, `@Positive`) para validar a requisição antes de
chegar ao domínio. Mantém a validação sintática fora do domínio.

**Por que usar DTO?** Para desacoplar a API do domínio. A API pode evoluir
sem alterar regras de negócio. Se hoje recebe `{"amount": 100, "currency":
"BRL"}` e amanhã `{"value": 100, "currencyCode": "BRL"}`, apenas o DTO muda;
o domínio permanece igual.

**PaymentResponse.** DTO de saída com factory `fromDomain(payment)`. Achata
o Value Object `Money`, retornando `amount` e `currency` separados — mais
simples para clientes REST e sem expor detalhes internos do domínio.

**PaymentController.** Ponto de entrada HTTP, com responsabilidade pequena.
Fluxo do POST: recebe a requisição, valida o DTO, cria o Command, chama o
caso de uso, converte o resultado para DTO, devolve a resposta. Não contém
regra de negócio.

**POST /payments.** Retorna `201 Created` com header `Location` apontando a
URL do recurso recém-criado (`/payments/{id}`) — boa prática HTTP para
criação de recursos.

**GET /payments/{id}.** Retorna `200 OK` quando encontrado e `404 Not
Found` quando inexistente. A consulta usa diretamente o repositório,
abordagem conhecida como **CQRS-light**: comandos passam pelos casos de uso,
consultas simples acessam diretamente a camada de leitura. Reduz
complexidade sem abandonar a separação de responsabilidades.

---

## Tratamento de Erros

A aplicação centraliza o tratamento de exceções com `@RestControllerAdvice`,
evitando duplicação em cada controller. Todos os erros são convertidos para
respostas HTTP padronizadas.

### ProblemDetail (RFC 9457)

O Spring Boot usa a classe `ProblemDetail` para representar erros HTTP,
seguindo a RFC 9457. Uma resposta típica contém `type`, `title`, `status`,
`detail`:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Currency is invalid"
}
```

### GlobalExceptionHandler

Handlers específicos por categoria:

- **MethodArgumentNotValidException** → `400 Bad Request`. Erros de Bean
  Validation, normalmente com os erros separados por campo.
- **IllegalArgumentException** → `400 Bad Request`. Entradas inválidas (moeda
  inexistente, valor inválido).
- **IllegalStateException** → `409 Conflict`. Violação de regra de negócio
  (concluir um pagamento já finalizado, iniciar processamento duas vezes). O
  409 indica que o estado atual do recurso impede a operação.
- **Exception** → `500 Internal Server Error`. Handler genérico para erros
  inesperados. Responde ao cliente e registra em log. Importante não expor
  detalhes internos da aplicação na resposta.

---

## OpenAPI / Swagger

A documentação é gerada automaticamente com `springdoc-openapi`, que analisa
controllers, DTOs, Bean Validation e anotações do Spring, gerando
documentação HTML e a especificação OpenAPI em JSON.

- `/swagger-ui.html` — interface gráfica interativa.
- `/v3/api-docs` — especificação OpenAPI, usada por ferramentas como Postman.

A maior vantagem é que a documentação permanece sincronizada com o código.

---

## Banco de Dados

### Flyway

O gerenciamento do banco é feito pelo Flyway. Toda alteração de schema
acontece via migrations versionadas. Primeira migration:
`V1__create_payments_table.sql`. Na inicialização, o Flyway verifica quais
migrations ainda não foram executadas e aplica apenas as pendentes,
garantindo que todos os ambientes usem exatamente o mesmo schema.

### Hibernate ddl-auto

O projeto usa `ddl-auto=validate`. Essa configuração **não cria tabelas** —
apenas verifica se o schema existente é compatível com as entidades. Quem
cria e evolui o banco é exclusivamente o Flyway. Em produção evita-se
`ddl-auto=update`, pois alterações automáticas dificultam auditoria, revisão
e controle de versão do banco.

---

## Testes

### Testes Unitários

Executam apenas regras de negócio. São rápidos, sem Spring, sem banco, sem
infraestrutura. Exemplos: Money, Payment, máquina de estados. Validam o
comportamento do domínio de forma isolada.

### Testes de Integração

Utilizam um PostgreSQL real via Testcontainers. Permitem validar mapeamentos
JPA, conversão dos enums, persistência, leitura, execução das migrations e a
integração completa com o banco. Toda a infraestrutura participa da
execução, aumentando a confiança de que o sistema funcionará em produção.

### Benefícios da estratégia

A combinação oferece equilíbrio: testes unitários garantem rapidez durante o
desenvolvimento; testes de integração validam que todos os componentes
funcionam juntos. Modelo recomendado para aplicações que usam JPA e banco
relacional.

---

## Trade-offs da Arquitetura

Nenhuma decisão arquitetural é composta apenas por vantagens. A Arquitetura
Hexagonal aumenta a qualidade do software, mas introduz maior complexidade
estrutural. Compreender esses trade-offs demonstra maturidade técnica.

### Vantagens

- **Domínio independente de frameworks** — evolui a infraestrutura sem
  alterar o coração da aplicação.
- **Baixo acoplamento** — dependências apontam para abstrações (ports);
  depende-se de contratos, não de implementações.
- **Alta testabilidade** — testes unitários rápidos, sem banco ou contexto
  Spring.
- **Flexibilidade tecnológica** — trocar PostgreSQL por MongoDB, JPA por
  JDBC, REST por gRPC, Kafka por RabbitMQ impacta apenas a camada de
  adapters.
- **Evolução gradual** — novas formas de entrada/saída (REST, Kafka, CLI,
  Scheduler) reutilizam os mesmos casos de uso.

### Desvantagens

- **Maior quantidade de classes** — uma operação simples exige DTO, Command,
  Use Case, Service, Port, Adapter, Entity, Repository. Projetos pequenos
  podem parecer complexos demais.
- **Código de conversão** — separar domínio e infraestrutura exige
  mapeamentos entre objetos.
- **Curva de aprendizado** — conceitos como Ports, Adapters, Inversão de
  Dependência e Casos de Uso podem estranhar quem vem de camadas
  tradicionais.
- **Mais decisões arquiteturais** — a equipe precisa definir claramente onde
  cada responsabilidade pertence.

---

## Glossário

- **Arquitetura Hexagonal (Ports & Adapters):** domínio isolado no centro;
  comunicação com tecnologias externas via interfaces (ports) implementadas
  por adapters.
- **Domain Model:** representação das regras de negócio; núcleo da aplicação;
  não conhece banco, HTTP ou frameworks.
- **Entity:** objeto com identidade própria e ciclo de vida; estado pode
  mudar (ex: Payment).
- **Value Object:** objeto imutável, sem identidade, comparado pelo conteúdo
  (ex: Money).
- **Record:** recurso do Java para objetos imutáveis concisos; ideal para
  DTOs, Commands, Eventos, Value Objects; não usado para entidades com estado
  mutável.
- **Always-Valid Model:** objeto nunca existe em estado inválido; validações
  no momento da criação.
- **Tell, Don't Ask:** enviar mensagens ao objeto em vez de consultar seu
  estado para decidir ações externamente.
- **Dependency Inversion:** depender de abstrações em vez de implementações
  concretas.
- **DTO:** objeto que transporta dados entre camadas; sem regra de negócio.
- **Command:** objeto com todos os dados necessários para executar um caso de
  uso (ex: CreatePaymentCommand).
- **Use Case:** representa uma ação que o sistema oferece (ex:
  CreatePaymentUseCase).
- **Adapter:** conecta uma tecnologia externa ao domínio (ex: Controller,
  Repository Adapter).
- **Port:** interface que define o contrato de comunicação entre domínio e
  infraestrutura.
- **Bean Validation:** validações declarativas nos DTOs (@NotNull,
  @NotBlank, @Positive).
- **ProblemDetail:** padrão RFC 9457 para erros HTTP (type, title, status,
  detail).
- **CQRS-light:** comandos pelos casos de uso, consultas simples direto do
  repositório.

---

## Perguntas comuns de entrevista

**O que é Arquitetura Hexagonal?** Modelo que mantém o domínio independente
de tecnologias externas; integrações acontecem via portas (interfaces)
implementadas por adapters.

**Por que separar a entidade JPA da entidade de domínio?** Porque a de
domínio representa regras de negócio e a JPA representa persistência;
misturar aumenta o acoplamento entre domínio e infraestrutura.

**Por que usar Value Objects?** Representam conceitos do domínio sem
identidade; são imutáveis, encapsulam validações e reduzem inconsistências.

**Quando usar `record`?** Quando o objeto é imutável, sem identidade e
representa apenas dados (DTOs, Commands, Eventos, Value Objects).

**Quando usar classe tradicional?** Quando o objeto tem identidade, estado
mutável e ciclo de vida (ex: Payment).

**Por que `BigDecimal` para dinheiro?** Evita erros de precisão de ponto
flutuante.

**Por que `EnumType.STRING`?** Evita corrupção de dados caso novos valores
sejam adicionados ou a ordem do enum mude.

**O que é Dependency Inversion?** Depender de abstrações em vez de
implementações concretas; reduz acoplamento e facilita testes.

**Por que usar DTOs?** Para desacoplar a API do domínio; mudanças no contrato
HTTP não precisam alterar regras de negócio.

**Diferença entre testes unitários e de integração?** Unitários executam
apenas regras de negócio, rápidos, sem infraestrutura. Integração validam
interação entre componentes, com banco real, verificando mapeamentos,
migrations e persistência.

**Por que Flyway?** Alterações no banco passam a ser versionadas, revisadas
e reproduzíveis em qualquer ambiente.

**Por que `ddl-auto=validate`?** Em produção o Hibernate não deve alterar
automaticamente o banco; essa responsabilidade é do Flyway.

---

## Pontos de melhoria futuros

- **Idempotência:** suporte ao header `Idempotency-Key`, evitando pagamentos
  duplicados quando clientes repetem requisições por timeout ou falha de
  rede.
- **Paginação:** nas futuras consultas de listagem, reduzindo consumo de
  memória e melhorando desempenho.
- **Versionamento da API:** versionar endpoints (ex: `/v1/payments`),
  facilitando evolução do contrato sem quebrar clientes.
- **Segurança das mensagens de erro:** retornar mensagens amigáveis ao
  cliente e registrar detalhes apenas nos logs.
- **Observabilidade:** logs estruturados, métricas e tracing distribuído,
  tornando o diagnóstico em produção muito mais simples.

---

## Resumo para entrevistas

- A aplicação foi construída com Arquitetura Hexagonal (Ports & Adapters).
- O domínio permanece completamente independente de frameworks.
- A comunicação acontece via portas (interfaces) e adapters (implementações).
- `Money` foi modelado como Value Object usando `record`.
- `Payment` é uma entidade com identidade e máquina de estados.
- O domínio segue **Always-Valid Model**, **Tell, Don't Ask** e **Dependency
  Inversion**.
- A persistência usa entidade JPA separada da de domínio, reduzindo
  acoplamento.
- A API REST usa DTOs para isolar o contrato HTTP das regras de negócio.
- O tratamento de erros segue **ProblemDetail (RFC 9457)**.
- O banco é controlado pelo Flyway; o Hibernate apenas valida o schema.
- A estratégia de testes combina unitários rápidos com integração usando
  PostgreSQL real via Testcontainers.

Esses conceitos formam a base arquitetural da plataforma e preparam a
aplicação para as próximas fases, que introduzem processamento assíncrono
com Kafka, resiliência, idempotência e padrões voltados a sistemas
distribuídos.