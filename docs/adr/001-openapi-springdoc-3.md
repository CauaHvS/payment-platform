# ADR 001 — Integração do OpenAPI com springdoc 3.0.0

**Status:** Aceito
**Data:** 2026-06-25

> Esta decisão substitui o ADR 001 original, que havia documentado o adiamento da integração do OpenAPI por incompatibilidade com o Spring Boot 4.

---

## Contexto

Durante a configuração da documentação automática da API, foram realizadas duas tentativas de integração do **springdoc-openapi** com **Spring Boot 4.1 / Spring Framework 7**, ambas sem sucesso.

### Primeira tentativa

Utilizando **springdoc-openapi-starter-webmvc-ui 2.6.0**, a aplicação falhava durante a execução com:

```text
NoSuchMethodError:
ControllerAdviceBean.<init>(Object)
```

O construtor utilizado pela biblioteca havia sido removido no Spring Framework 7.

### Segunda tentativa

Após atualizar para **springdoc-openapi-starter-webmvc-ui 2.8.17**, a aplicação passou a falhar durante o startup:

```text
ClassNotFoundException:
WebMvcProperties
```

A classe havia sido movida internamente pelo Spring Boot 4.

Com base nessas duas falhas, concluiu-se inicialmente que ainda não existia uma versão compatível do springdoc para Spring Boot 4, motivando o adiamento da integração.

Posteriormente, consultando a documentação oficial do projeto, foi encontrada a matriz oficial de compatibilidade:

| Spring Boot | springdoc-openapi |
| ----------- | ----------------- |
| 4.x.x       | 3.x.x             |
| 3.5.x       | 2.8.x             |
| 3.4.x       | 2.7.x – 2.8.x     |
| 3.3.x       | 2.6.x             |

Ficou evidente que a incompatibilidade ocorria porque estava sendo utilizada uma linha incorreta da biblioteca.

---

## Decisão

Reverter a decisão documentada anteriormente e adotar **springdoc-openapi 3.0.0**, versão oficialmente compatível com **Spring Boot 4**.

A configuração passa a utilizar o BOM do springdoc, permitindo que as versões dos módulos sejam centralizadas.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-bom</artifactId>
            <version>3.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    </dependency>
</dependencies>
```

A utilização do BOM elimina a necessidade de declarar versões individuais e facilita a futura inclusão de módulos adicionais, como Scalar UI e Actuator.

---

## Validação

Após a atualização:

* a aplicação iniciou normalmente com Spring Boot 4;
* a interface Swagger ficou disponível em `/swagger-ui.html`;
* a especificação OpenAPI passou a ser gerada automaticamente em `/v3/api-docs`;
* DTOs anotados com Bean Validation passaram a gerar schemas automaticamente.

---

## Consequências

### Positivas

* Documentação interativa disponível automaticamente.
* Especificação OpenAPI gerada a partir do código-fonte.
* Redução do esforço de manutenção da documentação.
* Melhor experiência para demonstrações técnicas e entrevistas.
* Estrutura preparada para expansão futura utilizando outros módulos do springdoc.

### Negativas

* O springdoc 3.x ainda é uma linha recente e pode apresentar instabilidades iniciais.
* Atualizações futuras da série 3.x devem ser acompanhadas para obtenção de correções e melhorias.

---

## Lições aprendidas

1. **Priorizar a documentação oficial.**

   Blogs e fóruns frequentemente ficam defasados em ecossistemas que evoluem rapidamente. A matriz oficial de compatibilidade resolveu o problema imediatamente.

2. **Nem toda incompatibilidade significa ausência de suporte.**

   Em muitos casos o problema está apenas na utilização de uma versão incorreta da biblioteca.

3. **ADRs representam decisões, não verdades permanentes.**

   A decisão inicial foi tomada com as informações disponíveis naquele momento e posteriormente revisada quando novas evidências surgiram.

4. **Utilizar BOMs simplifica a evolução do projeto.**

   Centralizar o gerenciamento de versões reduz inconsistências e facilita futuras expansões.

---

## Alternativas consideradas

### Manter o adiamento da integração (ADR anterior)

Rejeitada. A manutenção manual da documentação possui alto custo e tende a divergir da implementação.

### Fazer downgrade para Spring Boot 3

Rejeitada. O projeto mantém o Spring Boot 4 como base tecnológica.

### Migrar para outra implementação OpenAPI

Não avaliada neste momento, pois o springdoc continua sendo a solução predominante no ecossistema Spring.
