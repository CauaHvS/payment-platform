# ADR 007 — Autenticação e autorização com JWT e Spring Security

**Status:** Aceito
**Data:** 2026-06-27

---

## Contexto

Até a Fase 4, a API era completamente aberta: qualquer cliente que
conhecesse as URLs podia criar e consultar pagamentos. Para uma plataforma
de pagamentos, autenticação e autorização são requisitos fundamentais.

A Fase 5 introduz:

- **Autenticação:** provar a identidade de quem faz a requisição.
- **Autorização:** controlar o que cada usuário pode fazer (roles).

Era necessário escolher o modelo de autenticação e como integrá-lo à
arquitetura existente, incluindo a separação API/Worker estabelecida na
Fase 3.

---

## Decisão

Adotar autenticação **stateless baseada em JWT** (JSON Web Token) com
Spring Security e a biblioteca jjwt.

### Por que stateless (JWT) e não sessão

No modelo stateful (sessão tradicional), o servidor guarda em memória quem
está autenticado. Isso não escala bem horizontalmente (cada instância
precisaria conhecer todas as sessões) e acopla estado ao servidor.

No modelo stateless com JWT, o servidor não guarda nada. O token, assinado
com uma chave secreta, carrega a identidade e as permissões do usuário. A
cada requisição, o servidor apenas **verifica a assinatura** — sem consultar
banco ou memória de sessão. Qualquer instância valida o token sozinha.

Esse modelo combina diretamente com a arquitetura API/Worker e a
escalabilidade horizontal pretendida pelo projeto.

### Componentes implementados

- **Entidade User + tabela `users`** (migration V2): username único, senha
  com hash, role e timestamp.
- **UserPrincipal**: adapta a entidade ao contrato `UserDetails` do Spring
  Security, expondo a authority com prefixo `ROLE_` (convenção exigida por
  `hasRole`).
- **CustomUserDetailsService**: carrega o usuário do banco pelo username.
- **PasswordEncoder (BCrypt)**: hash de senha lento e com salt automático.
- **JwtService**: gera e valida tokens (assinatura HS256, claims de subject
  e role, expiração).
- **JwtAuthenticationFilter**: intercepta cada requisição, lê o header
  `Authorization: Bearer`, valida o token e popula o `SecurityContext`.
- **SecurityFilterChain**: define rotas públicas (`/auth/**`, Swagger,
  Actuator) e protegidas (o restante), desativa CSRF e sessão (stateless).
- **AuthController**: endpoints `/auth/register` e `/auth/login`.

### Roles (USER e ADMIN)

Dois papéis foram definidos. Todo usuário registrado entra como `USER`.
A promoção a `ADMIN` é feita diretamente no banco (não há endpoint de
promoção por ora).

O controle de acesso por papel usa `@PreAuthorize("hasRole('ADMIN')")`,
habilitado com `@EnableMethodSecurity`. Como demonstração, o endpoint
`GET /payments` (listar todos) é restrito a ADMIN, enquanto criação e
consulta individual ficam disponíveis a qualquer usuário autenticado.

Detalhe importante: o role viaja **dentro** do JWT. Promover um usuário no
banco não afeta tokens já emitidos; é necessário um novo login para o token
refletir o novo papel.

### Integração com a separação API/Worker

Toda a configuração de segurança web é anotada com
`@Profile({"web", "default"})`. O worker (perfil `worker`) não expõe HTTP e,
portanto, não carrega filtros, filter chain nem controllers de segurança.
Isso mantém a separação estabelecida na Fase 3 coerente.

### Tratamento de erros de autenticação

Foi adicionado um `AuthenticationEntryPoint` que retorna **401
Unauthorized** com corpo no formato ProblemDetail (consistente com o resto
da API), em vez do 403 padrão para requisições sem credencial. O
`JwtAuthenticationFilter` trata exceções de token inválido/expirado sem
derrubar a requisição, deixando o entry point responder 401.

### Rastreabilidade: createdBy

A identidade do usuário autenticado é capturada e propagada por toda a
cadeia do pagamento:

1. O `PaymentController` extrai o usuário do `Authentication` (preenchido
   pelo filtro JWT a partir do token).
2. O dado entra no `CreatePaymentCommand` e segue até o domínio (`Payment`
   ganha o campo `createdBy`).
3. É persistido no banco (migration V3, coluna `created_by`).
4. Viaja no evento `PaymentCreatedEvent` até o worker, que registra quem
   originou o pagamento.

A captura ocorre no controller (camada de adapter), e não no service, para
manter a camada de aplicação livre de dependências do Spring Security. O
dado trafega como um campo simples no command, respeitando a Arquitetura
Hexagonal.

---

## Consequências

### Positivas

- API protegida: autenticação obrigatória nas rotas sensíveis.
- Modelo stateless, escalável e coerente com a arquitetura API/Worker.
- Senhas armazenadas com hash BCrypt (nunca em texto puro).
- Controle de acesso por papel pronto e demonstrado.
- Erros de autenticação consistentes com o padrão ProblemDetail.
- Rastreabilidade de ponta a ponta de quem criou cada pagamento.

### Negativas

- JWT stateless não permite revogação imediata de um token antes da
  expiração (um token roubado vale até expirar). Mitigações como blacklist
  ou refresh tokens curtos ficam para evolução futura.
- O secret do JWT está no `application.yaml` para desenvolvimento; em
  produção deve vir de variável de ambiente ou secret manager.
- A promoção a ADMIN é manual no banco; falta um fluxo administrativo.

---

## Alternativas consideradas

### Autenticação por sessão (stateful)

Rejeitada. Não escala horizontalmente sem armazenamento de sessão
compartilhado e acopla estado ao servidor, contrariando a arquitetura
stateless/distribuída do projeto.

### OAuth2 / OpenID Connect com provedor externo

Rejeitada para este momento. Seria mais robusto para um cenário real
(delegar autenticação a um provedor como Keycloak ou Auth0), mas adiciona
complexidade de infraestrutura desproporcional ao objetivo de portfólio,
além de reduzir a demonstração explícita dos mecanismos de segurança.

### Capturar o usuário no service via SecurityContextHolder

Rejeitada. Acoplaria a camada de aplicação ao Spring Security
(infraestrutura). A captura foi feita no controller, repassando o dado via
command, mantendo a camada de aplicação limpa.

---

## Referências

- Documentação do Spring Security — arquitetura de filtros e
  `SecurityFilterChain`.
- jjwt (io.jsonwebtoken) — geração e validação de JWT.
- RFC 7519 — JSON Web Token.
- ADR 003 (separação API/Worker via profiles) — base para restringir a
  segurança ao perfil web.