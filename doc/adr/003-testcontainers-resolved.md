# ADR 003 — Atualização do Testcontainers para compatibilidade com Docker Engine 29.x

**Status:** Aceito  
**Data:** 2026-06-26

> Esta decisão substitui o ADR 002, resolvendo definitivamente a incompatibilidade entre o Testcontainers e o Docker Engine 29.x, além de eliminar a vulnerabilidade transitiva anteriormente aceita.

---

## Contexto

Durante a execução dos testes de integração da camada de persistência (`PaymentRepositoryAdapterIT`), o **Testcontainers 1.21.3** falhava ao detectar um ambiente Docker válido no **Docker Desktop (Windows 11)** executando **Docker Engine 29.5.3**.

A execução encerrava com a seguinte mensagem:

```text
Could not find a valid Docker environment. Please check configuration.

Attempted configurations were:

EnvironmentAndSystemPropertyClientProviderStrategy:
failed with exception BadRequestException (Status 400)

NpipeSocketClientProviderStrategy:
failed with exception BadRequestException (Status 400)
```

Foram realizadas diversas tentativas de correção:

- exposição do daemon Docker via TCP (`tcp://localhost:2375`);
- configuração explícita do pipe do engine Linux (`npipe:////./pipe/dockerDesktopLinuxEngine`);
- ajustes em `.testcontainers.properties`.

Nenhuma dessas abordagens solucionou o problema.

Posteriormente, uma discussão oficial no Docker Community Forums esclareceu a causa da incompatibilidade.

---

## Causa raiz

A partir do **Docker Engine 29.x**, a versão mínima aceita da Docker Remote API passou a ser **1.44**.

Entretanto, o **Testcontainers 1.21.3** utiliza, de forma transitiva, o **docker-java 3.4.2**, cujo cliente negocia uma versão de API inferior durante o handshake inicial com o daemon Docker.

Como consequência, o Docker rejeita a conexão retornando **HTTP 400 (Bad Request)**, impedindo que o Testcontainers identifique um ambiente Docker válido.

---

## Decisão

Atualizar para **Testcontainers 2.0.5**, versão que utiliza **docker-java 3.7.1**, compatível com as versões atuais da Docker Remote API.

Durante a migração também foi necessário adaptar os *artifactIds* dos módulos individuais, que passaram a utilizar o prefixo `testcontainers-`.

### Antes (1.x)

```xml
<artifactId>junit-jupiter</artifactId>
<artifactId>postgresql</artifactId>
```

### Depois (2.x)

```xml
<artifactId>testcontainers-junit-jupiter</artifactId>
<artifactId>testcontainers-postgresql</artifactId>
```

A adoção da versão 2.x também atualiza automaticamente diversas dependências transitivas, incluindo `commons-compress`, eliminando a vulnerabilidade documentada no ADR 002.

---

## Validação

Após a atualização, o Testcontainers passou a detectar corretamente o Docker Desktop.

Trecho do log:

```text
Found Docker environment with local Npipe socket (npipe:////./pipe/docker_engine)

Connected to docker:

Server Version: 29.5.3
API Version: 1.54
```

Os testes de integração de `PaymentRepositoryAdapterIT` passaram com sucesso, validando:

- inicialização automática de um container PostgreSQL;
- execução das migrações do Flyway;
- persistência e recuperação de dados (`save → findById`);
- isolamento completo do banco de dados durante os testes.

---

## Consequências

### Positivas

- Compatibilidade com Docker Engine 29.x e versões posteriores.
- Restabelecimento dos testes de integração utilizando PostgreSQL real em containers descartáveis.
- Eliminação da vulnerabilidade transitiva (`commons-compress`) documentada no ADR 002.
- Redução da necessidade de configurações específicas de ambiente (TCP, pipes alternativos e ajustes em `.testcontainers.properties`).
- Estrutura preparada para utilização futura de módulos como Kafka e RabbitMQ na linha 2.x do Testcontainers.

### Negativas

- A linha **2.x** do Testcontainers é recente e possui menor maturidade que a série 1.x.
- Todos os novos módulos do ecossistema passam a utilizar os novos *artifactIds*, exigindo atenção durante futuras expansões.

---

## Lições aprendidas

1. **Consultar a documentação e os fóruns oficiais reduz o tempo de investigação.**

   A causa raiz foi identificada no Docker Community Forums com uma explicação técnica detalhada da incompatibilidade.

2. **Problemas de infraestrutura podem estar em dependências transitivas.**

   Embora o erro fosse apresentado pelo Testcontainers, sua origem estava na versão do `docker-java` utilizada internamente.

3. **Atualizações de versão principal exigem leitura do guia de migração.**

   A mudança dos *artifactIds* e outras adaptações só ficaram claras após consultar o BOM e a documentação oficial.

4. **O processo de investigação também gera conhecimento.**

   As tentativas utilizando TCP, pipes alternativos e configurações do daemon eliminaram hipóteses incorretas e direcionaram a investigação para a verdadeira causa do problema.

---

## Alternativas consideradas

### Permanecer na linha 1.x do Testcontainers

Rejeitada. A incompatibilidade com Docker Engine 29.x impede a execução dos testes de integração.

### Sobrescrever manualmente a versão do `docker-java`

Rejeitada. A solução aumentaria o risco de incompatibilidades entre dependências transitivas.

### Utilizar configurações alternativas do Docker (TCP, pipes, daemon)

Rejeitada. Todas as tentativas falharam porque a causa do problema estava na negociação da versão da Docker Remote API.

---

## Referências

- Docker Community Forums — *Testcontainers stopped working after updating Docker Desktop to v4.56.0*
- Migration Guide do Testcontainers 2.x
- Maven Central — BOM do Testcontainers 2.0.5