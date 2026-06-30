# Revisão da Fase 5 — Segurança com JWT e Suíte de Testes

Documento de estudo da plataforma de pagamentos. Cobre as decisões de
arquitetura, os conceitos importantes e os principais pontos de atenção da
Fase 5 (autenticação/autorização com JWT) e da campanha de testes que
acompanhou a fase, servindo como material de revisão para entrevistas
técnicas.

---

# Parte 1 — Segurança com JWT

## Conceito central

Até a Fase 4, a API era aberta: qualquer cliente que conhecesse as URLs
podia criar e consultar pagamentos. A Fase 5 introduz duas camadas
distintas:

- **Autenticação** — provar quem está fazendo a requisição.
- **Autorização** — controlar o que cada um pode fazer (roles).

O modelo escolhido foi **JWT stateless**. O servidor não guarda sessão: o
token, assinado com uma chave secreta, carrega a identidade e as permissões.
A cada requisição, o servidor apenas verifica a assinatura, sem consultar
banco ou memória de sessão. Qualquer instância valida o token sozinha, o que
combina com a escalabilidade horizontal e a separação API/Worker.

---

## Por que stateless e não sessão

No modelo stateful (sessão), o servidor guarda em memória quem está logado.
Isso não escala bem horizontalmente: cada instância precisaria conhecer
todas as sessões, ou seria necessário um store de sessão compartilhado. No
stateless, o token é autocontido — a informação de identidade viaja com ele,
assinada. Não há estado no servidor para sincronizar.

O custo desse modelo aparece na revogação: um token válido vale até expirar.
Não dá para invalidá-lo imediatamente do lado do servidor sem reintroduzir
estado (blacklist) ou usar refresh tokens de vida curta.

---

## Componentes, arquivo por arquivo

### Migration V2 — tabela `users`

Cria a tabela com username único, senha com hash, role e timestamp de
criação. A senha nunca é armazenada em texto puro.

### domain/Role.java

Enum com `USER` e `ADMIN`. Mantido no domínio porque o papel é um conceito
de negócio, não de infraestrutura.

### UserJpaEntity e UserJpaRepository

A entidade de persistência do usuário e o repositório com `findByUsername`,
usado tanto no login quanto no carregamento do `UserDetails`.

### UserPrincipal

Adapta a entidade ao contrato `UserDetails` do Spring Security. Ponto de
atenção: a authority é exposta com o prefixo `ROLE_` (ex.: `ROLE_ADMIN`),
porque o `hasRole('ADMIN')` do Spring adiciona esse prefixo automaticamente
ao comparar. Sem o prefixo, a verificação de papel falha silenciosamente.

### CustomUserDetailsService

Implementa `UserDetailsService`, carregando o usuário do banco pelo
username. É o ponto onde o Spring Security conecta sua base de usuários.

### JwtService

Coração da fase. Usa a biblioteca jjwt (0.12.x). Três operações:

- `generateToken` — cria o token com subject (username), claim de role e
  expiração, assinado com HS256.
- `extractUsername` — lê o subject do token.
- `isTokenValid` — confere se o token bate com o usuário e não expirou.

A API da jjwt 0.12.x usa o builder fluente: `subject()`, `signWith()`,
`verifyWith()`, `parseSignedClaims()`. O secret e a expiração vêm do
`application.yaml` via `@Value`.

### JwtAuthenticationFilter

Um `OncePerRequestFilter` que intercepta cada requisição, lê o header
`Authorization: Bearer <token>`, valida e, se válido, popula o
`SecurityContext` com a autenticação. Trata exceções de token inválido sem
derrubar a requisição — deixa o entry point responder o 401.

### SecurityConfig

Define o `SecurityFilterChain`: desativa CSRF (API stateless não usa
cookies de sessão), define a política de sessão como STATELESS, libera
rotas públicas (`/auth/**`, Swagger, Actuator) e exige autenticação no
resto. Registra o filtro JWT antes do filtro padrão de autenticação e o
entry point de 401. Anotado com `@Profile({"web","default"})`, então o
worker não carrega nada disso. Habilita `@EnableMethodSecurity` para o
`@PreAuthorize`.

### AuthController

Endpoints `/auth/register` (cria usuário como USER e já devolve o token) e
`/auth/login` (autentica via `AuthenticationManager` e devolve o token).

---

## Roles

Todo usuário registrado entra como `USER`. A promoção a `ADMIN` é feita
manualmente no banco (não há endpoint de promoção). O controle de acesso
por papel usa `@PreAuthorize("hasRole('ADMIN')")` — como demonstração, o
`GET /payments` (listar todos) é restrito a ADMIN.

Ponto de atenção crítico para entrevista: **o role viaja dentro do token**.
Promover um usuário no banco não afeta tokens já emitidos. É necessário um
novo login para o token refletir o novo papel.

---

## Tratamento de erros de autenticação

Um `AuthenticationEntryPoint` retorna 401 com corpo no formato ProblemDetail
(consistente com o resto da API) quando falta credencial, em vez do
comportamento padrão. Já a falha de **autorização** (usuário autenticado mas
sem o papel) é tratada separadamente — ver a seção de testes, onde esse
ponto rendeu um bug real.

---

## Rastreabilidade: createdBy

A identidade do usuário autenticado é capturada e propagada por toda a
cadeia do pagamento:

1. O `PaymentController` extrai o usuário do `Authentication` (preenchido
   pelo filtro JWT).
2. O dado entra no `CreatePaymentCommand` e segue até o domínio (`Payment`
   ganha `createdBy`).
3. É persistido (migration V3, coluna `created_by`).
4. Viaja no `PaymentCreatedEvent` até o worker, que registra quem originou
   o pagamento.

Decisão de arquitetura: a captura ocorre no **controller** (adapter), não no
service, para manter a camada de aplicação livre de dependências do Spring
Security. O dado trafega como campo simples no command, respeitando a
Arquitetura Hexagonal.

---

# Parte 2 — Suíte de testes

A Fase 5 foi acompanhada de uma campanha de testes que cobriu domínio,
segurança, persistência, mensageria, cache e tratamento de falhas. Esta é a
parte mais valiosa para entrevistas, porque demonstra capacidade de testar
sistemas distribuídos de verdade, com infraestrutura real.

## Estrutura: unitários vs integração

- **Unitários** (sufixo `Test`): rápidos, sem infraestrutura. Testam o
  domínio e o `JwtService` isoladamente.
- **Integração** (sufixo `IT`): sobem containers reais via Testcontainers.
  Testam o sistema de ponta a ponta.

A distinção de sufixo (`Test` vs `IT`) é convenção consolidada e ajuda a
configurar execução separada (unitários no build rápido, integração num
estágio dedicado).

## Testes unitários

- **PaymentTest** — cobre a máquina de estados do domínio (PENDING →
  COMPLETED/FAILED) e as invariantes.
- **MoneyTest** — value object de dinheiro.
- **JwtServiceTest** — gera e extrai, valida para o mesmo usuário, rejeita
  token de outro usuário, rejeita token expirado, rejeita token adulterado.
  Usa `ReflectionTestUtils.setField` para injetar secret e expiração sem
  precisar do contexto Spring.

## Base de integração: AbstractIntegrationTest

Todos os testes de integração herdam de uma classe base que sobe os
containers **uma única vez** (padrão singleton). Pontos de decisão:

- Os containers (`POSTGRES`, `KAFKA`, `REDIS`) são `static` e iniciados num
  bloco `static {}`. Por serem estáticos, são compartilhados entre todas as
  classes que herdam. O Ryuk (do Testcontainers) limpa tudo no fim da JVM.
- A configuração de conexão é registrada via `@DynamicPropertySource`.

Por que singleton: rodar cada classe de teste subindo seus próprios
containers, em paralelo, sobrecarregava o Docker e causava timeout do
Postgres (HikariPool sem conexão disponível). Com containers compartilhados,
a suíte inteira usa um Postgres, um Kafka e um Redis.

### Detalhe crítico do Spring Boot 4: @ServiceConnection vs @DynamicPropertySource

O `@ServiceConnection` (auto-configuração de conexão a partir do container)
funciona para o Postgres, mas **não** para o Kafka no Spring Boot 4
(lança `ConnectionDetailsNotFoundException`). A solução foi registrar
`spring.kafka.bootstrap-servers` manualmente via `@DynamicPropertySource`,
lendo `KAFKA.getBootstrapServers()`. O Redis também é configurado por
`@DynamicPropertySource` (host e porta mapeada), usando um `GenericContainer`
genérico, já que a linha 2.x do Testcontainers não tem módulo dedicado de
Redis.

## Testes de integração

### PaymentRepositoryAdapterIT

Persistência real no Postgres: salva um pagamento e recupera, validando
todos os campos e que as migrations do Flyway rodaram.

### PaymentFlowIT

Fluxo Kafka ponta a ponta: cria o pagamento (PENDING), e via Awaitility
(`await().atMost(10s)`) confirma que o processamento assíncrono o leva a um
estado final (COMPLETED/FAILED). Prova que producer, broker, consumer e
persistência funcionam juntos.

### PaymentIdempotencyIT

O teste mais valioso para demonstrar entendimento de mensageria. Prova a
idempotência: cria o pagamento, espera processar, captura o estado final, e
**republica o mesmo evento**. Usa `await().during(3s)` para confirmar que,
durante esse intervalo, o status e o `updatedAt` **não mudam** — o
reprocessamento foi no-op. A guarda `if (!payment.isPending()) return;`
detectou o estado final e pulou. O `during()` é a chave: verifica que a
condição se mantém verdadeira (nada muda), em vez de virar verdadeira.

Conceito de entrevista: Kafka garante entrega **at least once**; uma mesma
mensagem pode ser entregue mais de uma vez. Idempotência é o que protege o
sistema disso.

### PaymentCacheIT

Cache Redis: cria um pagamento, espera virar final (o cache só guarda
estados finais, por causa do `unless` no `@Cacheable`), confirma que o
cache está vazio para a chave, faz o primeiro `findById` (que dispara o
`@Cacheable`), e confirma que a entrada agora existe no cache. Usa a
abstração `CacheManager` do Spring, que por baixo usa o Redis real do
container.

### PaymentSecurityIT

Segurança HTTP de verdade: sobe a aplicação numa porta real
(`webEnvironment = RANDOM_PORT`) e faz requisições com `RestClient`. Cobre:

- POST sem token → 401 (o entry point em ação).
- POST com token de USER → 201, com `createdBy` preenchido a partir do
  token (valida a rastreabilidade via HTTP).
- GET (rota ADMIN) com token de USER → 403.

Bug real encontrado: inicialmente o GET de USER retornava **500** em vez de
403. Causa: o `GlobalExceptionHandler` capturava a `AuthorizationDeniedException`
     no handler genérico e a transformava em 500. Correção: adicionar um
     `@ExceptionHandler` específico para `AuthorizationDeniedException`,
     retornando 403. Esse achado é um ótimo exemplo de teste que revela um
     defeito que passaria despercebido.

### PaymentDlqIT

Dead Letter Queue: publica uma mensagem inválida (poison pill) no tópico.
O consumer falha ao processar, o `DefaultErrorHandler` aplica as
retentativas (`FixedBackOff(1000L, 2L)` — 2 tentativas) e, esgotadas, o
`DeadLetterPublishingRecoverer` envia a mensagem para a DLQ
(`payment.created-dlt`). O teste escuta a DLQ com um consumer próprio e
confirma, via Awaitility, que a mensagem aterrissou lá.

Conceito de entrevista: a DLQ evita que uma mensagem problemática trave o
processamento das demais (poison pill). Em vez de reprocessar para sempre,
após N tentativas a mensagem é isolada na DLQ para análise posterior.

---

# Perguntas de entrevista

**Por que JWT em vez de sessão?**
Stateless: o servidor não guarda sessão, valida o token pela assinatura.
Escala horizontalmente sem store de sessão compartilhado.

**Qual a desvantagem do JWT stateless?**
Não dá para revogar um token antes de expirar sem reintroduzir estado
(blacklist) ou usar refresh tokens curtos.

**Onde o role fica e qual a implicação?**
Dentro do token. Promover no banco não afeta tokens já emitidos; precisa de
novo login.

**Por que o secret não deve ficar no application.yaml em produção?**
Qualquer um com acesso ao repositório ou ao artefato veria o secret e
poderia forjar tokens. Em produção, variável de ambiente ou secret manager.

**O que é entrega at least once e como você protege contra duplicação?**
Kafka pode entregar a mesma mensagem mais de uma vez. A proteção é a
idempotência: a guarda que ignora o evento se o pagamento já não está mais
pendente.

**O que é uma poison pill e como a DLQ ajuda?**
Uma mensagem que o consumer nunca consegue processar. Sem DLQ, ela
bloquearia ou seria reprocessada infinitamente. A DLQ isola a mensagem após
N tentativas para análise, liberando o fluxo.

**Por que containers singleton nos testes?**
Subir containers por classe, em paralelo, sobrecarrega o Docker e causa
timeout. Compartilhados, a suíte usa uma instância de cada infra.

**Como você testa idempotência sem flakiness?**
Com `await().during(...)`, que verifica que o estado se mantém inalterado
durante um intervalo, em vez de esperar algo mudar.

---

# Trade-offs e melhorias futuras

- **Revogação de token**: hoje não há. Evolução: refresh tokens de vida
  curta + blacklist de tokens revogados.
- **Secret em produção**: mover para variável de ambiente / secret manager.
- **Promoção a ADMIN**: hoje manual no banco. Falta um fluxo administrativo.
- **Cobertura de testes**: a suíte cobre os caminhos principais; faltam
  casos de borda (token malformado em vários formatos, concorrência no
  cache, etc.).
- **@JsonIgnore no isFinal()**: o campo derivado `final` vaza no JSON do
  PaymentResponse (cosmético).

---

# Resumo

A Fase 5 fechou a segurança da plataforma com JWT stateless, roles e
rastreabilidade de quem originou cada pagamento, tudo respeitando a
Arquitetura Hexagonal (a camada de aplicação não conhece o Spring Security).
A campanha de testes que acompanhou a fase elevou o projeto a um patamar de
maturidade: domínio, segurança, persistência, mensageria, cache e
tratamento de falhas, com infraestrutura real via Testcontainers. Dois
defeitos reais foram revelados pelos testes (o 403 mascarado como 500 e o
cache caindo em fallback de memória), reforçando o valor de testar o sistema
de ponta a ponta em vez de apenas validar o caminho feliz.