# ADR 002 — Adoção do Testcontainers e tratamento de vulnerabilidades transitivas

**Status:** Substituído
**Data:** 2026-06-25

> Esta decisão foi substituída pelo ADR 003, que resolveu simultaneamente a incompatibilidade com o Docker Engine 29.x e eliminou a vulnerabilidade transitiva ao adotar o Testcontainers 2.0.5.

---

## Contexto

Para implementar os testes de integração da camada de persistência, foi adotado o **Testcontainers**, biblioteca amplamente utilizada para execução de bancos de dados e demais dependências reais em containers descartáveis durante os testes automatizados.

Inicialmente foi utilizada a versão **1.20.4**, posteriormente atualizada para **1.21.3** através do BOM oficial.

Durante a análise das dependências, ferramentas como IntelliJ/Mend.io identificaram duas vulnerabilidades na dependência transitiva:

```
org.apache.commons:commons-compress:1.24.0
```

As vulnerabilidades reportadas foram:

* **CVE-2024-25710** — possibilidade de loop infinito durante processamento de arquivos comprimidos maliciosos.
* **CVE-2024-26308** — possibilidade de consumo excessivo de memória ao processar arquivos especialmente construídos.

Essa biblioteca é utilizada internamente pelo Testcontainers durante operações relacionadas ao Docker e não fazia parte das dependências diretas da aplicação.

---

## Tentativa de mitigação

Foi realizada a atualização para **Testcontainers 1.21.3**, última versão disponível naquele momento.

Entretanto, a vulnerabilidade permaneceu presente, indicando que a atualização da dependência transitiva ainda não havia ocorrido na linha 1.x.

---

## Decisão

Aceitar temporariamente o risco e manter o uso do **Testcontainers 1.21.3**, considerando que:

* a biblioteca vulnerável era utilizada exclusivamente durante os testes;
* nenhum arquivo comprimido proveniente de usuários era processado pela aplicação;
* a vulnerabilidade não fazia parte do artefato de produção;
* não existia, naquele momento, uma atualização oficial que resolvesse o problema sem riscos de incompatibilidade.

---

## Validação

Os testes de integração permaneceram funcionais utilizando containers reais.

A vulnerabilidade permaneceu apenas como alerta em ferramentas de análise estática, sem impacto prático no ambiente de desenvolvimento.

---

## Consequências

### Positivas

* Possibilidade de executar testes de integração contra PostgreSQL real.
* Estrutura preparada para futura utilização de Kafka, Redis e RabbitMQ.
* Utilização de uma biblioteca consolidada no ecossistema Java.

### Negativas

* Alertas permanentes em scanners de segurança.
* Necessidade de acompanhar futuras versões da biblioteca.
* Possível impacto reputacional caso o projeto fosse publicado contendo vulnerabilidades conhecidas.

---

## Critérios de revisão

Esta decisão deveria ser revisada quando ocorresse uma das seguintes situações:

* atualização oficial do Testcontainers para uma versão sem as vulnerabilidades;
* validação segura da sobrescrita manual da versão de `commons-compress`;
* adoção de scanners automáticos de dependências no pipeline de CI/CD.

**Todos esses critérios foram atendidos posteriormente pelo ADR 003**, que substituiu esta decisão.

---

## Lições aprendidas

1. **Dependências transitivas também fazem parte da arquitetura.**

   Mesmo quando não utilizadas diretamente, podem introduzir riscos de segurança e compatibilidade.

2. **O contexto influencia a severidade de uma vulnerabilidade.**

   Uma CVE presente apenas em dependências de teste possui impacto muito diferente daquela presente em produção.

3. **Aceitação consciente de risco é uma decisão arquitetural válida.**

   Registrar a justificativa torna explícito que o risco foi avaliado, compreendido e monitorado.

4. **Manter dependências atualizadas reduz problemas futuros.**

   A atualização realizada posteriormente para Testcontainers 2.0.5 resolveu simultaneamente a incompatibilidade com o Docker Engine e eliminou a vulnerabilidade transitiva.
