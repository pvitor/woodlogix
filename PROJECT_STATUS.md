# WoodLogix — estado do projeto

**Referência:** branch `main`, commit `d639fcd` (PR #1 integrada).  
**Verificação de testes registrada em:** 6 nov. 2026.

Este documento descreve o que foi encontrado no código, na persistência e nos testes. A existência de uma tela, entidade ou repositório, por si só, não significa que um fluxo de produto esteja completo. As instruções de contribuição e arquitetura permanecem em `.github/`; este arquivo registra o estado funcional, lacunas e evidências.

## 1. Visão geral

WoodLogix é uma aplicação desktop local para apoiar a gestão de pequenas marcenarias. O público-alvo são profissionais que precisam organizar clientes, ambientes, projetos comerciais, insumos e estimativas de custo sem depender de um servidor remoto.

O problema que pretende resolver é a dispersão de informações operacionais e comerciais e a dificuldade de obter uma estimativa de materiais e custos a partir das peças, ferragens e parâmetros informados. O escopo implementado é menor que um ERP: não há, por exemplo, módulos de estoque, compras ou faturamento.

## 2. Tecnologias

| Tecnologia | Uso observado |
|---|---|
| Java 17 | Versão de compilação configurada no Maven e usada na execução JavaFX verificada. |
| Maven | Build multi-módulo; artefatos versionados como `1.1.0-SNAPSHOT`. |
| JavaFX / FXML / CSS | Aplicação desktop e suas telas; JavaFX `17.0.2`. |
| Spring Boot | Contexto local embutido, versão `3.5.16`, sem aplicação web (`spring.main.web-application-type=none`). |
| Spring Data JPA / Hibernate | Repositórios e persistência; dialeto SQLite via `hibernate-community-dialects`. |
| SQLite | Banco de dados local, configurável. Driver `sqlite-jdbc` `3.42.0.0`. |
| Apache PDFBox | Geração e leitura de propostas PDF; versão `3.0.5`. |
| JUnit 5, Mockito e Spring Boot Test | Testes unitários e de integração existentes. |
| Lombok | Redução pontual de código repetitivo. |

As versões estão declaradas no `pom.xml` raiz e nos POMs dos módulos.

## 3. Arquitetura

- **`woodlogix-core`** contém modelos de domínio, enums e cálculos (`PricingEngine`, `EdgeBandingCalculator`). Não depende de Spring, JavaFX ou JPA.
- **`woodlogix-desktop`** contém a aplicação JavaFX, controladores e FXML, serviços de casos de uso, entidades JPA, repositórios Spring Data, configuração SQLite e integração PDF.
- **Serviços** coordenam operações como CRUD, validações, relações e transações. **Repositórios** estendem interfaces Spring Data JPA; não há DAOs JDBC manuais no fluxo atual.
- **Integração JavaFX/Spring:** `WoodLogixDesktopLauncher` inicia `AppLauncher`; o contexto Spring é aberto no ciclo de vida JavaFX e encerrado em `stop()`. O `FXMLLoader` obtém controladores pelo contexto.
- **Trabalho assíncrono:** operações de interface que consultam ou alteram dados usam `UiTaskRunner`, com `Task` em executor próprio e atualização da interface nos callbacks JavaFX.
- **Persistência local:** `DatabaseConfiguration` cria o diretório do banco e configura a conexão SQLite e a opção de chaves estrangeiras. O caminho padrão está em `config.properties` e pode ser substituído por configuração externa, propriedade de sistema ou `WOODLOGIX_DATABASE_PATH`.
- **Interface e idioma:** as rotas atuais são Dashboard, Clientes, Ambientes, Catálogo de insumos, Orçamento e Funil de vendas. Os bundles `messages.properties`, `messages_en_US.properties` e `messages_es_ES.properties` fornecem os textos; o idioma é selecionado por `woodlogix.ui.locale`.

## 4. Funcionalidades

Legenda:

- **Implementado e testado:** há comportamento e testes automatizados correspondentes; isso não implica cobertura completa da interface.
- **Implementado, cobertura insuficiente:** há fluxo funcional, mas não testes automatizados suficientes para afirmar o comportamento ponta a ponta.
- **Parcialmente implementado:** há apenas parte do fluxo ou persistência sem uma funcionalidade de usuário completa.
- **Planejado, não implementado:** reservado a itens que aparecem como planejados em fonte de projeto. Não foi encontrada uma lista formal de roadmap no repositório nem issue de produto aberta; por isso, as sugestões da seção 8 não são compromissos já aprovados.

| Área | Estado real e evidências |
|---|---|
| **Dashboard** | **Implementado, cobertura automatizada insuficiente.** `DashboardController` carrega de forma assíncrona a contagem de clientes, oportunidades fora da etapa `CLOSED`, contagem por etapa e existência do rascunho ativo. Os atalhos navegam para módulos existentes. Não há teste automatizado do controlador ou da interface; dados são consultados de serviços reais, não de fixtures de produção. |
| **Clientes** | **Implementado e testado no serviço/persistência; interface sem cobertura automatizada.** `CustomerService` implementa cadastro, edição, exclusão e busca por nome parcial, e-mail ou telefone, sem distinguir maiúsculas/minúsculas. `PersistenceRepositoryTest` cobre busca, operações e dados inválidos. A tela `CustomerController` coordena formulário, pesquisa e tabela; seus cliques não são cobertos por teste de UI. |
| **Ambientes** | **Implementado e testado no serviço/persistência; interface sem cobertura automatizada.** `EnvironmentService` associa ambientes a clientes, aceita tipos de catálogo e tipos personalizados, elimina duplicados e impede remover ambientes com registros vinculados. Testes cobrem esses casos e a integridade de relações. |
| **Materiais / chapas** | **Implementado e testado no serviço/persistência; interface sem cobertura automatizada.** `MaterialService` implementa CRUD de chapas com nome, cor, espessura, dimensões e preço por chapa. Há validação e bloqueio de exclusão quando a chapa está referenciada por peça ou rascunho; `PersistenceRepositoryTest` cobre CRUD e referências. |
| **Ferragens** | **Implementado e testado no serviço/persistência; interface sem cobertura automatizada.** `HardwareService` implementa CRUD de itens com unidade e preço unitário e protege itens usados pelo rascunho. CRUD e restrição de exclusão aparecem nos testes de persistência. |
| **Orçamentos e cálculos** | **Cálculo implementado e testado em escopo limitado; fluxo de orçamento implementado com cobertura insuficiente de UI.** `PricingController` monta peças, seleciona projeto e insumos, mantém parâmetros e ferragens, atualiza o resumo e salva/restaura um rascunho ativo. As operações de peças de projetos e rascunho usam serviços separados. `PricingEngineTest` contém sete testes de domínio para áreas, consumo por área, desperdício, custo, margem e entradas inválidas. Não há testes automatizados que exercitem o fluxo JavaFX de edição, exclusão, resumo ou fechamento. O rascunho é único (`ACTIVE_DRAFT_ID = "active"`); não existe entidade de orçamento final/histórico de versões. |
| **Fitas de borda** | **Implementado e testado em cálculo e persistência, com cobertura limitada.** A quantidade linear decorre das arestas selecionadas, dimensões e quantidade da peça; o custo usa metros lineares e preço cadastrado. Existem testes no `PricingEngineTest` para comprimento/custo e testes de CRUD/referência no `PersistenceRepositoryTest`. Isso não representa um plano de corte ou uma estimativa de sobras de fita. |
| **Pipeline de vendas** | **Implementado e testado no serviço/persistência; interação visual sem cobertura automatizada.** `SalesPipelineService` cria projetos vinculados a clientes e ambientes, lista projetos por etapas (`CONTACT`, `MEASUREMENT_SCHEDULED`, `QUOTE_SENT`, `NEGOTIATION`, `CLOSED`) e movimenta projetos entre etapas. `KanbanController` apresenta cartões e implementa movimentação por arrastar e soltar. Testes cobrem criação/movimentação no serviço, não o gesto de arrastar na tela. |
| **Propostas em PDF** | **Implementado e testado com cobertura limitada.** `ProposalPdfService` gera PDF de uma página com dados de cliente/projeto, etapa e valor informado no projeto. Há testes de exportação e conteúdo em inglês e espanhol, além de teste integrado de exportação. O PDF usa o valor comercial do projeto; não calcula nem incorpora o resultado de `PricingEngine` ou o detalhamento do rascunho. |
| **Persistência** | **Implementada e testada em integração.** SQLite/JPA armazena clientes, ambientes, insumos, projetos, peças de projetos e o rascunho. Relacionamentos e bloqueios de exclusão são cobertos em testes. `FurnitureItemEntity`, `FurniturePartEntity` e seus repositórios representam dados de móveis/partes e há teste direto de persistência, mas não foi encontrado serviço ou tela de cadastro para completar esse fluxo: **parcialmente implementado como funcionalidade de produto**. |

### Cálculo de consumo atualmente implementado

`PricingEngine` soma a área das peças por material, aplica o percentual de desperdício e arredonda o resultado para cima em chapas inteiras por material. O custo de chapas usa esse número multiplicado pelo preço cadastrado. O custo base soma chapas, fitas, ferragens e horas estimadas multiplicadas pela soma das taxas horária e de máquinas. O preço sugerido divide o custo base por `1 - margem/100`; a margem precisa ser não negativa e menor que 100%.

O consumo de chapas é **estimativa por área**, não otimização de corte: o código não posiciona peças em chapas nem verifica encaixe geométrico. Os testes cobrem exemplos desta regra de área, não resultados de nesting/corte.

## 5. Testes

Executado para este registro:

```text
mvn -o -pl woodlogix-desktop -am test
BUILD SUCCESS
34 testes: 0 falhas, 0 erros, 0 ignorados
```

Grupos existentes:

- `woodlogix-core/.../PricingEngineTest.java` — 7 testes de área, comprimento/custo de fita, agrupamento e arredondamento de chapas, desperdício, custo base, preço/margem e entradas inválidas.
- `woodlogix-desktop/.../PersistenceRepositoryTest.java` — 22 testes de integração com Spring e SQLite temporário (`@TempDir`), cobrindo persistência de relações, CRUD e restrições de clientes, ambientes, insumos, ferragens, peças de projeto e rascunho; também pesquisa, dados inválidos, movimento de projeto e exportação PDF.
- `woodlogix-desktop/.../ProposalPdfServiceTest.java` — 4 testes de conteúdo/exportação PDF e mensagens/currency em inglês e espanhol.
- `woodlogix-desktop/.../ConfigLoaderTest.java` — 1 teste de configuração externa.

**Lacunas de teste:** não há testes automatizados JavaFX/FXML para navegação, interação de formulário, arrastar e soltar, atualização visual de resumo, salvamento automático, restauração ou fechamento com alterações pendentes. Os testes do motor não validam aproveitamento real de chapas, ampla precisão monetária, regras de marcenaria de um móvel completo ou arredondamentos em vários cenários de produção. A exportação PDF não tem cobertura de paginação ou de integração com os valores calculados do orçamento.

## 6. Limitações e riscos técnicos

- **Precisão financeira:** entidades e DTOs de persistência usam `BigDecimal`, mas modelos usados por `PricingEngine` usam `double`. `PricingController` converte dimensões e preços persistidos de `BigDecimal` para `double` antes do cálculo. Para valores monetários, essa representação binária pode introduzir diferenças de precisão; os sete testes atuais usam tolerância numérica em exemplos simples e não estabelecem uma política abrangente de precisão/arredondamento.
- **Consumo de chapas:** o cálculo por área não considera geometria, rotação, orientação/veio, espessura do corte, margens ou reaproveitamento de retalhos. O valor não deve ser interpretado como plano de corte ou garantia de quantidade de compra.
- **Escopo do orçamento:** o sistema persiste um rascunho ativo e peças vinculadas a projetos, mas não um histórico de orçamentos emitidos. A proposta PDF exporta o valor comercial salvo no projeto, não o preço sugerido atualizado pelo cálculo do rascunho.
- **Validações:** há validações nos modelos, records, serviços, entidades e na interface, mas elas não são uniformes em todos os caminhos. Os testes cobrem casos concretos; não demonstram uma auditoria completa de todas as combinações/limites.
- **Integridade e evolução do banco:** SQLite é local e a configuração ativa chaves estrangeiras. Hibernate está configurado com `ddl-auto=update`; não foram encontrados scripts/versionamento de migrações. Mudanças futuras de esquema devem ser avaliadas com cuidado para preservar bancos existentes.
- **Tratamento de erros:** `UiTaskRunner` registra falhas de operações assíncronas e apresenta mensagem localizada com detalhes técnicos expansíveis. A experiência de erro não tem testes de interface, e operações/exportação não foram verificadas em todas as falhas de sistema de arquivos ou banco.
- **Experiência do usuário:** as telas atuais carregam e a navegação foi verificada, inclusive em janela reduzida, mas a automação não valida acessibilidade, escala de tela, todos os tamanhos, estados de falha nem fluxos completos. A UI e o Kanban não têm testes de interação automatizados.
- **Dados legados de móveis:** entidades e repositórios de móveis/partes permanecem, mas não há fluxo de usuário correspondente identificado; não assumir que cadastro ou projeto completo de móveis esteja disponível.

## 7. Decisões de produto

As decisões registradas nas instruções do projeto e refletidas na arquitetura são:

- Manter o produto como **aplicação desktop**, com serviços locais e persistência SQLite; não assumir backend ou serviço web.
- Priorizar necessidades de **pequenas marcenarias**.
- Favorecer simplicidade e facilidade de uso, evitando complexidade arquitetural ou funcionalidades sem necessidade confirmada.
- Priorizar melhorias que reduzam custo, desperdício e tempo administrativo, preservando transparência sobre estimativas e limites dos cálculos.

## 8. Próximas etapas propostas

Não foi encontrado roadmap oficial no repositório e a consulta de issues não retornou itens. A lista abaixo é uma recomendação baseada nas lacunas observadas, não uma afirmação de que esses itens já foram aprovados.

| Prioridade | Objetivo | Motivo | Critério de aceitação |
|---|---|---|---|
| **P1** | Definir e testar precisão e arredondamento dos valores calculados. | O motor calcula com `double` enquanto valores persistidos são `BigDecimal`; os testes atuais não cobrem escalas e limites financeiros de forma ampla. | Registrar a política de precisão com exemplos aprovados pelo produto; adicionar testes para conversão, acumulação, margem e apresentação sem alterar silenciosamente valores já persistidos. |
| **P1** | Automatizar os principais fluxos de orçamento e rascunho na UI. | Não há testes JavaFX para edição/exclusão de peças, ferragens, resumo, autosave, restauração e fechamento. | Testes reproduzíveis verificam esses fluxos e o comportamento em falha de salvamento, usando banco temporário, sem depender do banco local. |
| **P1** | Tornar explícita a relação entre orçamento calculado e proposta comercial. | Hoje o rascunho e o valor comercial do projeto são dados distintos; o PDF não exporta o resultado calculado. | Definir com o produto o fluxo esperado; testes confirmam que o documento usa os valores e o estado persistidos esperados, ou documentar explicitamente que são independentes. |
| **P2** | Especificar requisitos para uma eventual otimização de corte antes de implementá-la. | A contagem atual é por área e não modela encaixe; não há regra de produção suficiente no código para inferir um algoritmo correto. | Aprovar premissas operacionais da oficina e casos de referência mensuráveis antes de implementar; sem premissas confirmadas, manter a funcionalidade identificada como estimativa por área. |
| **P2** | Decidir o destino do domínio legado de móveis e partes. | Persistência existe e é testada, mas não há serviço/tela de cadastro correspondente encontrado. | Registrar decisão de produto: manter explicitamente como legado/futuro ou definir escopo e critérios de aceitação para um fluxo completo, sem tratar a entidade como funcionalidade disponível. |

## 9. Histórico

- **PR #1 — “feat: enhance UI components and improve layout for better user experience”** foi integrada à `main`. O commit atual é `d639fcd` (`feat: enhance UI components and improve layout for better user experience (#1)`).
- A integração alterou a navegação, o Dashboard, Clientes, Ambientes, Catálogo, Kanban e Orçamentação, incluindo FXML, CSS, controladores e mensagens localizadas.
- A existência da PR integrada e os testes de build não significam que todos os fluxos de UI foram validados. A suíte automatizada tem cobertura concentrada em domínio, serviços/persistência e PDF; continuam pendentes os testes de interação descritos acima.
