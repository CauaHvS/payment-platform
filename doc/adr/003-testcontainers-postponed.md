# ADR 003: Postergar testes de integração com Testcontainers

## Status
Aceito (2026-06-25). Decisão temporária; ver critérios de reavaliação.

## Contexto
Foi adotado o Testcontainers (versão 1.21.3, BOM) para implementar testes
de integração da camada de persistência da Fase 1 — subir um Postgres
real em container Docker descartável e validar o fluxo
`Adapter → JPA → Postgres → Adapter` ponta a ponta.

A configuração seguiu o padrão moderno do Spring Boot 3.1+:

- `@TestConfiguration` com `@Bean @ServiceConnection PostgreSQLContainer<?>`
- Teste anotado com `@SpringBootTest @Import(TestcontainersConfiguration.class)`
- Imagem fixada em `postgres:16-alpine` (mesma do `compose.yml`)

A infraestrutura do projeto está saudável:

- Docker Desktop 29.5.3, engine rodando (`docker info` retorna info completa,
  Server Version 29.5.3, 1 container ativo, 24 imagens).
- Outras integrações Docker funcionam normalmente (a aplicação principal
  conecta no Postgres via `docker compose up` sem problema).

## Problema observado

Ao executar qualquer teste anotado com Testcontainers, o cliente
`docker-java` (versão 3.4.2, transitiva do Testcontainers 1.21.3) falha
ao estabelecer comunicação com o engine, com erro:
A resposta do Docker Desktop nas duas estratégias é um JSON vazio com o
label `com.docker.desktop.address=npipe://\\.\pipe\docker_cli`. Esse é
o **pipe frontend** do Docker Desktop, não o engine real
(`dockerDesktopLinuxEngine`). O Docker CLI conhece esse redirecionamento;
o `docker-java` 3.4.2 não.

## Tentativas de mitigação (todas falharam)

1. **Atualizar Testcontainers para 1.22.0**: versão inexistente no Maven
   Central na data desta decisão.
2. **Habilitar TCP no Docker Desktop** (`Expose daemon on tcp://localhost:2375
   without TLS`) e configurar `.testcontainers.properties` com
   `docker.host=tcp://localhost:2375`: o endpoint TCP retorna a mesma
   resposta inválida (Docker Desktop encaminha pra mesma camada frontend).
3. **Apontar direto para o pipe interno do engine Linux**:
   `docker.host=npipe:////./pipe/dockerDesktopLinuxEngine` no
   `.testcontainers.properties`: mesma falha; o `docker-java` 3.4.2 não
   consegue dialogar com o npipe nessa versão do Docker Engine.

## Decisão

Postergar a implementação dos testes de integração com Testcontainers.
A Fase 1 é encerrada sem essa cobertura.

## Consequências

### Negativas
- Cobertura de integração da camada de persistência depende de validação
  manual via Postman (`POST /payments` + `GET /payments/{id}` + inspeção
  da tabela `payments`).
- Não há proteção automatizada contra regressões no mapeamento JPA
  (`@Enumerated`, precisão de `BigDecimal`, `TIMESTAMPTZ`).

### Positivas
- Tempo poupado: o problema é externo (interação Docker Desktop x
  docker-java) e não tem solução pelo lado do código. Insistir consumiria
  tempo que pode ser melhor aplicado na Fase 2 (Kafka).
- A estrutura do Testcontainers permanece pronta no projeto
  (`TestcontainersConfiguration.java`, `PaymentRepositoryAdapterIT.java`,
  dependências no `pom.xml`). Quando o impedimento for resolvido, os
  testes voltam a rodar sem mudança de código.

### Mitigações
- Os 18 testes unitários do domínio cobrem a lógica de negócio: validações
  de `Money`, máquina de estados de `Payment`, criação e transições.
- A validação manual via Postman + inspeção do Postgres documentada na
  Fase 1 confirmou que o fluxo de persistência funciona corretamente
  (campos, tipos, precisão, timestamps).
- O Swagger UI permite revalidação manual rápida a qualquer momento.

## Critérios de reavaliação

Esta decisão deve ser revisitada quando uma das condições for satisfeita:

- **Testcontainers libera uma versão com `docker-java` atualizado** (versão
  3.5+ ou superior, com suporte ao protocolo do Docker Engine 29.x).
  Acompanhar release notes de `org.testcontainers:testcontainers-bom`
  no Maven Central.
- **Migração do ambiente de desenvolvimento para Linux ou macOS**, onde
  o `docker-java` se comunica via socket Unix tradicional (mais estável
  que o npipe do Windows). Especialmente relevante quando o projeto
  passar por CI (GitHub Actions roda em Linux por padrão).
- **Substituição do Docker Desktop por alternativa** (Podman, Rancher
  Desktop, Colima) que use protocolo de comunicação compatível com
  `docker-java`.

## Lições aprendidas

1. **Tecnologia de fronteira tem custos.** Spring Boot 4 + Docker Engine
   29 + Testcontainers 1.21.3 é uma combinação recente. Bibliotecas
   transitivas (como `docker-java`) ainda não foram atualizadas pra
   esse ecossistema.

2. **Saber quando parar é parte do trabalho.** Após múltiplas tentativas
   de mitigação sem progresso, insistir teria custo desproporcional
   ao benefício. Documentar a decisão e seguir é melhor que travar.

3. **Diferenciar bug do código de bug do ecosystem.** O código está
   correto (segue padrão Spring Boot 3.1+ documentado oficialmente).
   O impedimento é externo. Essa distinção importa em revisão de código:
   o arquivo `TestcontainersConfiguration.java` permanece no projeto
   como referência correta, não como código quebrado.

4. **CI Linux mitiga problemas Windows.** Quando este projeto rodar em
   GitHub Actions (Fase 8), os testes de Testcontainers provavelmente
   vão funcionar de primeira, porque o runner Linux usa Unix socket
   diretamente.

## Alternativas consideradas

- **Usar H2 in-memory para "testes de integração"**: rejeitado.
  Simular Postgres com H2 mente sobre o comportamento real (tipos,
  precisão, syntax). Testes verdes em H2 podem corresponder a código
  quebrado em Postgres. Antipadrão moderno.
- **Subir Postgres dedicado para testes**: rejeitado. Cria sujeira
  (estado entre rodadas), depende de processo externo, contraria o
  ideal de "teste reproduzível e isolado".
- **Mock do repositório nos testes**: rejeitado para esta camada.
  Mockar `PaymentRepository` no teste do adapter perderia o ponto:
  estamos testando exatamente o adapter, não o que ele faz mock pra
  fora.