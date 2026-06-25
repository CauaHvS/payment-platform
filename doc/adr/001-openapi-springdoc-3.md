# ADR 002: Integração do OpenAPI com springdoc 3.0.0

## Status
Aceito (2026-06-25). Substitui o ADR 001.

## Contexto
A integração inicial do springdoc-openapi falhou em duas tentativas
diferentes contra Spring Boot 4.1 / Spring Framework 7:

- **springdoc-openapi-starter-webmvc-ui v2.6.0**: falhou em runtime com
  `NoSuchMethodError: ControllerAdviceBean.<init>(Object)`. Esse
  construtor foi removido no Spring Framework 7.
- **springdoc-openapi-starter-webmvc-ui v2.8.17**: falhou no startup com
  `ClassNotFoundException: WebMvcProperties`. Essa classe foi movida no
  Spring Boot 4.

Com base nessas duas falhas, o ADR 001 documentou a decisão de adiar
totalmente a integração do OpenAPI, atribuindo a incompatibilidade ao
fato de o springdoc não ter ainda lançado uma versão compatível com
Spring Boot 4.

Posteriormente, consultando a documentação oficial do springdoc, foi
descoberta a matriz de compatibilidade:

| Spring Boot | springdoc-openapi |
|-------------|-------------------|
| 4.x.x       | 3.x.x             |
| 3.5.x       | 2.8.x             |
| 3.4.x       | 2.7.x - 2.8.x     |
| 3.3.x       | 2.6.x             |

A linha 3.x do springdoc é a versão compatível com Spring Boot 4, mas
ainda não estava amplamente documentada em fóruns públicos
(Stack Overflow, blogs) na época da primeira tentativa. A informação
foi obtida diretamente do springdoc.org.

## Decisão
Reverter a decisão do ADR 001. Adotar **springdoc-openapi 3.0.0** via
BOM, com a dependência principal `springdoc-openapi-starter-webmvc-ui`
sem versão explícita (resolvida pelo BOM):

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

A escolha do padrão BOM em vez de versão explícita visa facilitar
adição futura de módulos do springdoc (Scalar UI, módulo Actuator) sem
fragmentação de versões.

## Consequências

### Positivas
- API expõe documentação interativa em `/swagger-ui.html`.
- Especificação OpenAPI em formato JSON disponível em `/v3/api-docs`.
- Geração automática de schemas a partir de DTOs com Bean Validation.
- Diferencial visual em demos e entrevistas.

### Negativas / riscos
- springdoc 3.0.0 é versão recente e pode ter bugs ainda não
  reportados.
- Quando o springdoc lançar versões 3.x mais recentes, vale reavaliar
  para correções e melhorias.

## Lições aprendidas

1. **Documentação oficial primeiro.** Stack Overflow e blogs estão
   frequentemente desatualizados em ecossistemas que evoluem rápido.
   Quando há dúvida sobre compatibilidade, consultar a documentação
   oficial do projeto resolve mais rápido que tentar versões no escuro.

2. **Decisões arquiteturais não são permanentes.** O ADR 001 documentou
   uma decisão tomada com informação incompleta. Quando informação nova
   apareceu, a decisão foi revisada com novo ADR. Esse processo
   (`Superseded`) é parte saudável da evolução do projeto.

3. **Investigar antes de aceitar verdadeiro.** A primeira recomendação
   foi adiar o OpenAPI. Investigar a documentação oficial mudou o
   resultado. Esse instinto investigativo é mais valioso que aceitar
   conselhos sem questionamento.

## Alternativas reconsideradas

- **Adiar definitivamente (ADR 001):** rejeitado. Custo de manutenção
  manual da documentação é alto e ela inevitavelmente diverge do código.
- **Downgrade para Spring Boot 3.4:** rejeitado. Mantém-se o Spring
  Boot 4 como fundação moderna do projeto.
- **Migrar para alternativa (microprofile-openapi etc):** não avaliado
  por enquanto. springdoc é o padrão de mercado em projetos Spring.