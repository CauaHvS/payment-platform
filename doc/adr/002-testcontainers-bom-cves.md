# ADR 003: Adoção do Testcontainers e tratamento de CVEs transitivas

## Status
Aceito (2026-06-25). Decisão temporária; revisar conforme indicado.

## Contexto
Para implementar testes de integração da camada de persistência da
Fase 1, foi adotado o Testcontainers — biblioteca padrão de mercado
para subir dependências reais (Postgres, Kafka, Redis) em containers
descartáveis durante a execução de testes automatizados.

A versão inicial adotada foi `testcontainers-bom:1.20.4`. Maven
gerencia as dependências individuais (`junit-jupiter`, `postgresql`)
sem versão explícita, resolvendo via BOM.

Ao analisar o `pom.xml`, o IntelliJ (com Mend.io) reportou duas CVEs
em uma **dependência transitiva** do Testcontainers,
`org.apache.commons:commons-compress:1.24.0`:

- **CVE-2024-25710** (severidade 8.1, alta): Loop infinito ao processar
  arquivos maliciosos. Pode causar denial of service.
- **CVE-2024-26308** (severidade 5.5, média): Alocação de recursos sem
  limites ao processar arquivos. Pode causar OutOfMemory.

A `commons-compress` é usada pelo Testcontainers para extração de
imagens Docker. Não é dependência direta do projeto.

## Tentativa de mitigação

Atualizou-se o BOM para `testcontainers-bom:1.21.3` (versão mais recente
disponível). O alerta de CVE persistiu, indicando que a versão mais
recente do Testcontainers ainda depende da `commons-compress` vulnerável,
ou que a base de dados do scanner aponta uma versão fixa que ainda não
foi atualizada.

## Decisão
Adotar `testcontainers-bom:1.21.3` (versão mais recente disponível),
**aceitando temporariamente** o alerta de CVE. A justificativa:

1. **Vetor de ataque inexistente no contexto atual.** A `commons-compress`
   é usada pelo Testcontainers apenas para extrair imagens Docker
   oficiais (Docker Hub). O projeto não processa arquivos comprimidos
   originados de fontes externas não-confiáveis. O risco prático é
   próximo de zero.
2. **Escopo limitado.** O Testcontainers é usado apenas em testes
   (`<scope>test</scope>`). A vulnerabilidade não entra no artefato
   final em produção.
3. **Bloqueio externo.** A solução definitiva depende de uma release
   do Testcontainers que atualize a `commons-compress`. Não há
   workaround sem forçar uma sobrescrita de versão (que pode quebrar
   outras dependências do Testcontainers que dependem da API antiga).

## Consequências

### Positivas
- Testes de integração possíveis com containers reais.
- Padrão de mercado, fácil de explicar em entrevista.
- BOM facilita adicionar módulos extras (Kafka, Redis) na Fase 2 e
  seguintes.

### Negativas / riscos
- Alerta visual em ferramentas de scan (Mend.io, Snyk, Dependabot).
- Risco real próximo de zero, mas risco reputacional não-zero quando o
  projeto for público no GitHub.

## Critérios de reavaliação

Esta decisão deve ser revisitada quando uma das condições for
satisfeita:

- Testcontainers liberar nova versão atualizando a `commons-compress`
  para versão sem CVEs (ex: 1.26+).
- Configuração do projeto via `dependencyManagement` para **forçar**
  versão segura de `commons-compress` for testada e validada
  (não estiver quebrando outras transitivas).
- Em ambiente produtivo (CI/CD), automatizar análise via Dependabot ou
  similar (planejado para Fase 8).

## Lições aprendidas

1. **Dependências transitivas são invisíveis até causarem problema.**
   Vale a pena inspecionar a árvore de dependências de qualquer
   biblioteca adicionada, especialmente em projetos públicos.

2. **CVE em dev/teste tem severidade diferente de CVE em produção.**
   Não é "Sou imune", mas é "Avalio o vetor de ataque real, não só o
   score CVSS abstrato".

3. **Documentar a aceitação consciente do risco é o padrão
   profissional.** O alerta vai aparecer pra qualquer um que olhar o
   projeto. Em vez de esconder ou ignorar, este ADR explica por que a
   decisão foi tomada e em que circunstâncias deve ser revisada.

4. **Scanner de vulnerabilidade no CI vale ouro.** Ferramentas como
   Dependabot, Snyk e OWASP Dependency-Check pegam isso automaticamente
   em produção. Adicionar essas ferramentas é parte do roadmap do
   projeto (Fase 8).