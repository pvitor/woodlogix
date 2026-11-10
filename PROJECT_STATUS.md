# WoodLogix — estado do projeto

**Referência:** branch `feature/arquitetura-nova` (módulo Server estrutural criado, ainda não commitado).

**Verificação de testes registrada em:** 10 nov. 2026.

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
| Spring Boot | Versão `3.5.16`; o Desktop mantém seu contexto local sem web e o Server possui persistência SQLite própria e o endpoint inicial de listagem de materiais. |
| Spring Data JPA / Hibernate | Repositórios e persistência; dialeto SQLite via `hibernate-community-dialects`. |
| SQLite | Banco de dados local, configurável. Driver `sqlite-jdbc` `3.42.0.0`. |
| Flyway | Migração inicial versionada do schema SQLite; versão `11.7.2` gerenciada pelo Spring Boot. |
| Apache PDFBox | Geração e leitura de propostas PDF; versão `3.0.5`. |
| Jackson | Serialização dos DTOs de transporte; gerenciado pelo BOM Spring Boot no Server e também no Desktop. |
| MapStruct | Geração em compilação dos mapeadores HTTP no Server e Desktop; versão `1.5.5.Final`, sem geração/reflexão de mapeamento em runtime. |
| JUnit 5, Mockito e Spring Boot Test | Testes unitários e de integração existentes. |
| Lombok | Redução pontual de código repetitivo. |

As versões estão declaradas no `pom.xml` raiz e nos POMs dos módulos.

## 3. Arquitetura

### Implementação atual

- **`woodlogix-core`** contém modelos de domínio, enums e cálculos (`PricingEngine`, `EdgeBandingCalculator`). Não depende de Spring, JavaFX ou JPA.
- **`woodlogix-application`** contém os casos de uso dos catálogos de ferragens, materiais e fitas de borda, do cadastro de clientes, do catálogo de ambientes, do pipeline de vendas e do CRUD de peças de projeto, com portas de persistência e transação, sem dependências de Spring, JPA, Jakarta ou JavaFX em produção.
- **`woodlogix-server`** é um módulo Spring Boot Web dependente de Application e Core, sem dependência de Desktop. Contém DTOs e mapeadores MapStruct, uma cópia independente do grafo JPA completo, adapters, configuração SQLite/Flyway, `TransactionPort` e composição dos casos de uso. Expõe `GET /api/v1/materials` para listar materiais ordenados, `POST /api/v1/materials` para criar e `PUT /api/v1/materials/{id}` para atualizar materiais; as três operações usam `MaterialCatalog` e `MaterialMapper`. O POST retorna `201 Created`; POST e PUT retornam `400 Bad Request` estruturado pelo `ApiErrorResponse` para dados inválidos, e o PUT retorna `404 Not Found` estruturado para material inexistente. Os demais endpoints ainda não foram implementados. O caminho `woodlogix.database.path` é obrigatório e a V1 está duplicada byte a byte para manter o mesmo checksum. Persistência e endpoints foram testados somente com SQLite temporário; não estão conectados ao banco real nem à interface.
- **`woodlogix-desktop`** continua sendo a aplicação JavaFX, com controladores, serviços, entidades JPA, repositórios Spring Data, configuração SQLite/Flyway e a cópia original da V1. Sua persistência permanece ativa para a aplicação; a infraestrutura HTTP inicial ainda não está conectada às fachadas, controllers ou inicialização. A duplicação temporária da persistência no Server existe para testes isolados e não deve ser interpretada como transferência do banco de produção.
- **Contratos de transporte:** os DTOs estão duplicados nos módulos Server e Desktop, sem módulo compartilhado. JSON de referência e testes de serialização/desserialização nos dois módulos verificam equivalência estrutural, enums por nome, campos opcionais nulos, listas vazias e `BigDecimal`. O teste do Desktop também verifica tolerância a campos adicionais. Os mapeadores MapStruct são gerados no build e não acessam serviços, fachadas ou repositórios. `ApiHttpClientTest` verifica requests/responses JSON, erros HTTP, timeout, indisponibilidade e interrupção; a infraestrutura do cliente permanece isolada e sem conexão com a aplicação.
- **Limite de Environment:** `EnvironmentData` representa as operações unitárias `create/update` da fachada, mas os DTOs HTTP existentes só representam criação em lote (`EnvironmentAddRequest`) e substituição/sincronização (`EnvironmentReplaceRequest`). Não foi criado mapeamento entre esses modelos incompatíveis; o fluxo CRUD unitário precisa de decisão/contrato próprio antes de ser transportado por HTTP. Os fluxos em lote usados pela tela são cobertos.
- **Serviços** coordenam operações como CRUD, validações, relações e transações. **Repositórios** estendem interfaces Spring Data JPA; não há DAOs JDBC manuais no fluxo atual.
- **Integração JavaFX/Spring:** `WoodLogixDesktopLauncher` inicia `AppLauncher`; o contexto Spring é aberto no ciclo de vida JavaFX e encerrado em `stop()`. O `FXMLLoader` obtém controladores pelo contexto.
- **Trabalho assíncrono:** operações de interface que consultam ou alteram dados usam `UiTaskRunner`, com `Task` em executor próprio e atualização da interface nos callbacks JavaFX.
- **Persistência local atual:** o Desktop continua usando `DatabaseConfiguration`, que cria o diretório do banco e configura SQLite e chaves estrangeiras. O caminho padrão está em `config.properties` e pode ser substituído por configuração externa, propriedade de sistema ou `WOODLOGIX_DATABASE_PATH`. O Server usa configuração própria e exige um caminho explícito, sem default compartilhado com o Desktop.
- **Schema SQLite:** Desktop e Server possuem cópias byte a byte da `V1__initial_sqlite_schema.sql`, com chaves estrangeiras físicas; ambos configuram Hibernate com `ddl-auto=validate`. A migração de bancos não vazios sem histórico Flyway ainda não foi implementada. O Server só foi testado contra bancos temporários.
- **Interface e idioma:** as rotas atuais são Dashboard, Clientes, Ambientes, Catálogo de insumos, Orçamento e Funil de vendas. Os bundles `messages.properties`, `messages_en_US.properties` e `messages_es_ES.properties` fornecem os textos; o idioma é selecionado por `woodlogix.ui.locale`.

### Direção arquitetural aprovada — parcialmente implementada

A aplicação terá uma única base de código, com casos de uso e regras compartilhados:

- **`woodlogix-core`:** domínio e regras puras, sem dependências de infraestrutura.
- **`woodlogix-application`:** casos de uso, coordenação e portas; depende de Core e não de Spring ou JPA. Casos de uso compostos expressarão atomicidade por uma porta de transação implementada pela infraestrutura.
- **`woodlogix-server`:** depende de Application e Core, não de Desktop. A infraestrutura de persistência SQLite/JPA e a composição dos casos de uso foram reproduzidas para testes isolados; `GET` e `POST /api/v1/materials` são os primeiros endpoints implementados, sem integração com o Desktop.
- **`woodlogix-desktop`:** JavaFX, apresentação e infraestrutura inicial do cliente HTTP (ainda não conectada à aplicação).

No **modo individual**, o fluxo aprovado é JavaFX → HTTP → Spring Boot local → SQLite, sem internet ou sincronização entre instalações. A direção é executar o servidor na mesma JVM, restrito a loopback; sua viabilidade, empacotamento e consumo de recursos ainda precisam ser comprovados.

No **modo compartilhado**, JavaFX acessará via HTTP um Spring Boot central na empresa, conectado a PostgreSQL. O acesso não dependerá obrigatoriamente de nuvem ou internet.

Esses fluxos são **decisões de arquitetura, não funcionalidades disponíveis em sua totalidade**: Application contém os casos de uso; o Server possui DTOs, mapeadores, persistência e os endpoints `GET` e `POST /api/v1/materials`, testados com banco temporário, mas não está conectado ao Desktop. O Desktop mantém a infraestrutura HTTP inicial desconectada e continua sendo o único proprietário do banco usado pela aplicação. Não se deve iniciar os dois módulos escrevendo no mesmo arquivo SQLite. A transferência efetiva exige um corte coordenado dos consumidores JavaFX para HTTP e um plano para bancos existentes sem histórico Flyway. PostgreSQL e o modo compartilhado ainda não estão configurados. Permanece pendente decidir como empacotar/iniciar o Server no modo individual, mantendo a restrição de não haver dependência Maven Desktop → Server; a direção aprovada de mesma JVM ainda não foi validada.

## 4. Funcionalidades

Legenda:

- **Implementado e testado:** há comportamento e testes automatizados correspondentes; isso não implica cobertura completa da interface.
- **Implementado, cobertura insuficiente:** há fluxo funcional, mas não testes automatizados suficientes para afirmar o comportamento ponta a ponta.
- **Parcialmente implementado:** há apenas parte do fluxo ou persistência sem uma funcionalidade de usuário completa.
- **Planejado, não implementado:** reservado a itens que aparecem como planejados em fonte de projeto. Não foi encontrada uma lista formal de roadmap no repositório nem issue de produto aberta; por isso, as sugestões da seção 8 não são compromissos já aprovados.

| Área | Estado real e evidências |
|---|---|
| **Dashboard** | **Implementado, cobertura automatizada insuficiente.** `DashboardController` carrega de forma assíncrona a contagem de clientes, oportunidades fora da etapa `CLOSED`, contagem por etapa e existência do rascunho ativo. Os atalhos navegam para módulos existentes. Não há teste automatizado do controlador ou da interface; dados são consultados de serviços reais, não de fixtures de produção. |
| **Clientes** | **Implementado e testado no serviço/persistência; interface sem cobertura automatizada.** `CustomerCatalog`, `CustomerCatalogPort`, `CustomerChangeNotifier`, `CustomerInput`, `CustomerRow` e `CustomerNotFoundException` residem em Application; `CustomerCatalogAdapter`, `CustomerChangeNotifierAdapter` e `CustomerService` (fachada com Bean Validation) permanecem no Desktop. O catálogo implementa cadastro, edição, exclusão e busca por nome parcial, e-mail ou telefone, sem distinguir maiúsculas/minúsculas; `CustomerInput` valida somente o nome. A exclusão não pré-verifica referências: no SQLite migrado, a FK bloqueia clientes referenciados e somente a violação de FK reconhecida é traduzida pela fachada. `CustomerChangedEvent` é publicado dentro da transação, após o flush; os listeners de `CustomerController`, `KanbanController` e `EnvironmentController` usam `AFTER_COMMIT` (executam só após o commit, e sua falha não desfaz a transação). `PersistenceRepositoryTest` cobre busca, operações e dados inválidos. A tela `CustomerController` coordena formulário, pesquisa e tabela; seus cliques não são cobertos por teste de UI. |
| **Ambientes** | **Implementado e testado no serviço/persistência; interface sem cobertura automatizada.** `EnvironmentCatalog`, `EnvironmentCatalogPort`, `EnvironmentChangeNotifier`, `EnvironmentRow` e `EnvironmentNotFoundException` residem em Application; `EnvironmentCatalogAdapter`, `EnvironmentChangeNotifierAdapter`, `EnvironmentData`, `EnvironmentChangedEvent`, os listeners `AFTER_COMMIT` e `EnvironmentService` (fachada com Bean Validation) permanecem no Desktop. As consultas auxiliares de clientes da fachada delegam a `CustomerCatalog`, sem acesso direto a `CustomerRepository`. O catálogo associa ambientes a clientes, aceita tipos de catálogo e personalizados, normaliza e elimina duplicados e, na sincronização, verifica referências de peças legadas, móveis e projetos antes da remoção (retorno antecipado sem notificação quando nada muda). O evento é publicado dentro da transação, após o flush. A fachada traduz somente violações SQLite de FK reconhecidas restritas à exclusão (inclusive em lote) e registra log com a quantidade de tipos solicitados apenas quando há alteração efetiva. Peças de projeto, móveis e projetos de venda bloqueiam a exclusão (FK física testada nas três tabelas). |
| **Materiais / chapas** | **Implementado e testado no serviço/persistência; listagem, criação e atualização HTTP implementadas sem integração com a interface.** `MaterialCatalog`, `MaterialCatalogPort`, `MaterialInput` e `MaterialRow` residem em Application; `MaterialCatalogAdapter` e o controller HTTP permanecem no Server; `MaterialCatalogAdapter`, `MaterialService`, `MaterialController`, `PricingController` e `PricingWorkpiece` da aplicação desktop permanecem no Desktop. `GET /api/v1/materials` retorna materiais ordenados por nome, cor e espessura; `POST /api/v1/materials` cria e retorna `MaterialResponse` com status 201; `PUT /api/v1/materials/{id}` atualiza pelo caso de uso existente e retorna `MaterialResponse` com status 200, ou `ApiErrorResponse` com status 400 para validação e 404 para ID inexistente. Testes MockMvc usam SQLite temporário e cobrem atualização, preservação de ID, persistência via GET, relacionamento com rascunho, validação, corpo JSON `null` e ID inexistente. A exclusão verifica peças de projeto e rascunho e traduz violações SQLite de FK reconhecidas. |
| **Ferragens** | **Implementado e testado no serviço/persistência; interface sem cobertura automatizada.** Os casos de uso do catálogo residem em Application; `HardwareService` é uma fachada no Desktop e os controllers usam records independentes de JPA. CRUD e restrição de exclusão são cobertos nos testes de persistência. |
| **Orçamentos e cálculos** | **Cálculo implementado e testado em escopo limitado; fluxo de orçamento implementado com cobertura parcial de UI.** `PricingController` monta peças, seleciona projeto e insumos, mantém parâmetros e ferragens, atualiza o resumo e salva/restaura um rascunho ativo. O CRUD de peças de projeto e o caso de uso de salvar/restaurar o rascunho estão em Application (`ProjectWorkpiece` e `EstimateDraft`); `ProjectWorkpieceService` e `EstimateDraftService` permanecem como fachadas Desktop, e os adapters JPA também permanecem no Desktop. `EstimateDraft` valida quantidades, dimensões, parâmetros, margem e a exigência de fita quando há arestas antes de chamar o port; a entidade mantém validação defensiva de limites numéricos. O caso de uso coordena escrita REQUIRED e leitura read-only por `TransactionPort`; o adapter mantém a substituição integral das coleções e `saveAndFlush`. O rascunho persiste IDs de catálogo, não snapshots; ao restaurar, a interface usa os valores atuais dos catálogos. O modelo local `PricingWorkpiece` e a precificação continuam no Desktop. Testes JavaFX cobrem cenários específicos de seleção e restauração, não o fluxo completo. O rascunho é único (`ACTIVE_DRAFT_ID = "active"`); não existe entidade de orçamento final/histórico de versões. |
| **Fitas de borda** | **Implementado e testado em cálculo e persistência, com cobertura limitada de UI.** `EdgeBandingCatalog`, `EdgeBandingCatalogPort`, `EdgeBandingInput`, `EdgeBandingRow` e `EdgeBandingNotFoundException` residem em Application; `EdgeBandingCatalogAdapter`, `EdgeBandingService` (fachada), `MaterialController`, `PricingController` e `PricingWorkpiece` permanecem no Desktop. A exclusão verifica peças de projeto e rascunho e traduz somente violações SQLite de FK. A quantidade linear decorre das arestas selecionadas, dimensões e quantidade da peça; o custo usa metros lineares e preço cadastrado. Existem testes no `PricingEngineTest` para comprimento/custo e testes unitários e de integração do catálogo. Isso não representa um plano de corte ou uma estimativa de sobras de fita. |
| **Pipeline de vendas** | **Implementado e testado no caso de uso/persistência; interação visual sem cobertura automatizada.** `SalesPipeline`, `SalesPipelinePort`, `SalesProjectInput` e `SalesProjectRow` residem em Application; `SalesPipelineAdapter` e `SalesPipelineService` (fachada com Bean Validation) permanecem no Desktop. O caso de uso cria projetos vinculados a clientes e ambientes, lista todas as etapas (`CONTACT`, `MEASUREMENT_SCHEDULED`, `QUOTE_SENT`, `NEGOTIATION`, `CLOSED`) mantendo a ordenação por criação decrescente, busca por ID e movimenta projetos. As consultas auxiliares `findCustomers()` e `findEnvironments()` da fachada delegam, respectivamente, a `CustomerCatalog` e `EnvironmentCatalog`; o serviço não acessa repositórios JPA diretamente. `KanbanController` apresenta cartões e implementa movimentação por arrastar e soltar. Testes cobrem associação, ordenação e rollback, mas não o gesto de arrastar na tela. |
| **Propostas em PDF** | **Implementado e testado com cobertura limitada.** `ProposalPdfService` gera PDF de uma página com dados de cliente/projeto, etapa e valor informado no projeto. Há testes de exportação e conteúdo em inglês e espanhol, além de teste integrado de exportação. O PDF usa o valor comercial do projeto; não calcula nem incorpora o resultado de `PricingEngine` ou o detalhamento do rascunho. |
| **Persistência** | **Implementada e testada em integração no Desktop; infraestrutura duplicada e testada isoladamente no Server.** A aplicação Desktop continua usando seu SQLite/JPA. O Server possui cópia do grafo JPA, Flyway, adapters e composição, validada com bancos temporários; não está conectado à UI nem ao banco de produção. Relacionamentos, FKs, rollback e bloqueios de exclusão são cobertos pelos testes dos dois módulos. A transferência do banco real e o tratamento de bancos sem histórico Flyway continuam pendentes. `FurnitureItemEntity`, `FurniturePartEntity` e seus repositórios representam dados de móveis/partes e há teste direto de persistência, mas não foi encontrado serviço ou tela de cadastro para completar esse fluxo: **parcialmente implementado como funcionalidade de produto**. |

### Cálculo de consumo atualmente implementado

`PricingEngine` soma a área das peças por material, aplica o percentual de desperdício e arredonda o resultado para cima em chapas inteiras por material. O custo de chapas usa esse número multiplicado pelo preço cadastrado. O custo base soma chapas, fitas, ferragens e horas estimadas multiplicadas pela soma das taxas horária e de máquinas. O preço sugerido divide o custo base por `1 - margem/100`; a margem precisa ser não negativa e menor que 100%.

O consumo de chapas é **estimativa por área**, não otimização de corte: o código não posiciona peças em chapas nem verifica encaixe geométrico. Os testes cobrem exemplos desta regra de área, não resultados de nesting/corte.

## 5. Testes

Executado para este registro:

```text
mvn -o -pl woodlogix-server -am '-Dtest=MaterialControllerIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
BUILD SUCCESS
10 testes de integração MockMvc: 0 falhas, 0 erros, 0 ignorados

mvn -o test
BUILD SUCCESS
231 testes: 0 falhas, 0 erros, 0 ignorados

git diff --check
BUILD SUCCESS
Sem erros de whitespace

mvn -o -pl woodlogix-server,woodlogix-desktop -am -DskipTests compile
BUILD SUCCESS
MapStruct gerou e compilou os mapeadores dos dois módulos

mvn -o -pl woodlogix-server,woodlogix-desktop -am '-Dtest=ApiMapperTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
BUILD SUCCESS
10 testes de mapeadores (Server 5, Desktop 5): 0 falhas, 0 erros, 0 ignorados

mvn -o test
BUILD SUCCESS
202 testes (core 7, application 59, server 10, desktop 126): 0 falhas, 0 erros, 0 ignorados

mvn -o -pl woodlogix-server,woodlogix-desktop -am '-Dtest=TransportDtoJsonTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
BUILD SUCCESS
9 testes de contratos JSON (Server 4, Desktop 5): 0 falhas, 0 erros, 0 ignorados

mvn -o test
BUILD SUCCESS
192 testes (core 7, application 59, server 5, desktop 121): 0 falhas, 0 erros, 0 ignorados

mvn -o -pl woodlogix-server -am '-Dtest=WoodLogixServerApplicationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
BUILD SUCCESS
1 teste: contexto Spring Boot iniciado com Tomcat em porta aleatória, sem banco de dados

mvn -o test
BUILD SUCCESS
183 testes (core 7, application 59, server 1, desktop 116): 0 falhas, 0 erros, 0 ignorados

mvn -o -pl woodlogix-server -am -DskipTests package
BUILD FAILURE
Reempacotamento não concluído: dependências do spring-boot-maven-plugin ausentes do cache offline
  (incluindo spring-boot-buildpack-platform:3.5.16 e spring-boot-loader-tools:3.5.16)
Não foi verificado um JAR executável; não alteramos versões nem tentamos baixar dependências

mvn -o -pl woodlogix-desktop -am '-Dtest=EnvironmentAndSalesPipelineServiceTest,DeletionErrorTranslationTest,EnvironmentCatalogIntegrationTest,DeletionAndPipelineCharacterizationTest,PersistenceRepositoryTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
BUILD SUCCESS
57 testes selecionados no Desktop: 0 falhas, 0 erros, 0 ignorados

mvn -o test
BUILD SUCCESS
182 testes (core 7, application 59, desktop 116): 0 falhas, 0 erros, 0 ignorados

mvn -o -pl woodlogix-application,woodlogix-desktop -am '-Dtest=EstimateDraftTest,EnvironmentAndDraftCharacterizationTest,PersistenceRepositoryTest,PricingControllerMaterialTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
BUILD SUCCESS
42 testes selecionados (application 8, desktop 34): 0 falhas, 0 erros, 0 ignorados

mvn -o test
BUILD SUCCESS
178 testes (core 7, application 59, desktop 112): 0 falhas, 0 erros, 0 ignorados
```

Grupos existentes:

- `woodlogix-core/.../PricingEngineTest.java` — 7 testes de área, comprimento/custo de fita, agrupamento e arredondamento de chapas, desperdício, custo base, preço/margem e entradas inválidas.
- `woodlogix-application/.../DeleteHardwareTest.java` — verifica a ordem da exclusão, referências, ID inexistente e propagação de falhas sem Spring ou JPA.
- `woodlogix-application/.../HardwareCatalogTest.java` — verifica criação, validações, atualização, listagem e falhas do catálogo sem Spring ou JPA.
- `woodlogix-application/.../MaterialCatalogTest.java` — verifica validações, catálogo, exclusão, referências e falhas sem Spring ou JPA.
- `woodlogix-application/.../EdgeBandingCatalogTest.java` — verifica validações, CRUD, transações, ordem da exclusão, referências e propagação de falhas sem Spring ou JPA.
- `woodlogix-application/.../CustomerCatalogTest.java` — 6 testes: validação restrita ao nome, CRUD, busca, ordem da exclusão e notificação, propagação de falhas sem Spring ou JPA.
- `woodlogix-desktop/.../CustomerCatalogIntegrationTest.java` — 9 testes: CRUD e busca, validação da fachada, notificações `AFTER_COMMIT` somente após commit (inclusive com transação externa) e ausentes em rollback, FK física real em `project_environments`, `sales_projects` e `furniture_items`, falha que não é FK propagada, rollback após falha de flush com comparação do estado persistido, em SQLite temporário migrado.
- `woodlogix-application/.../EnvironmentCatalogTest.java` — 15 testes: normalização, deduplicação, sincronização e resultado (`environments`, `changed` e `requestedCount`), retornos antecipados, referências, notificação e propagação de falhas, sem Spring ou JPA.
- `woodlogix-application/.../SalesPipelineTest.java` — 7 testes: listagem de todas as etapas, validação de cliente/ambiente e associação, criação, movimentação, IDs inexistentes, validação do input e propagação de falhas, sem Spring ou JPA.
- `woodlogix-application/.../ProjectWorkpieceTest.java` — 6 testes: CRUD, IDs inexistentes, delegação de ordenação/consultas, transações REQUIRED/read-only, rows com material e fita opcional e propagação de falhas, sem Spring ou JPA.
- `woodlogix-application/.../EstimateDraftTest.java` — 8 testes: invariantes de taxas e margem, quantidades, dimensões e fita obrigatória antes do port; transações de escrita e leitura, retorno vazio e propagação de falhas sem Spring ou JPA.
- `woodlogix-desktop/.../EnvironmentCatalogIntegrationTest.java` — 10 testes: CRUD, sincronização e supressão de log em retornos antecipados, validação e não encontrado, `AFTER_COMMIT` após commit/ausente em rollback/com transação externa, FK física em `furniture_parts`, `furniture_items` e `sales_projects`, bloqueio por pré-checagem, rollback após flush e falha que não é FK propagada, em SQLite temporário migrado.
- `woodlogix-desktop/.../MaterialCatalogIntegrationTest.java` — verifica CRUD, ordenação, FK real e rollback em SQLite temporário migrado.
- `woodlogix-desktop/.../EdgeBandingCatalogIntegrationTest.java` — 9 testes: CRUD, ordenação, pré-checagem de referências, FK física real em `project_workpieces` e `project_estimate_workpieces`, rollback após falha de flush, falha que não é FK propagada sem conversão, em SQLite temporário migrado.
- `woodlogix-desktop/.../PricingControllerMaterialTest.java` — verifica cenários JavaFX de materiais e peças e restauração de valores atuais de materiais, fitas e ferragens, com serviços simulados; exige ambiente gráfico JavaFX.
- `woodlogix-desktop/.../HardwareCatalogIntegrationTest.java` — verifica catálogo, precisão dos valores testados e rollback após flush em SQLite temporário migrado.
- `woodlogix-desktop/.../HardwareDeletionIntegrationTest.java` — verifica a exclusão por meio da fachada, FK real, transações e rollback após flush em SQLite temporário migrado.
- `woodlogix-desktop/.../PersistenceRepositoryTest.java` — 22 testes de integração com Spring e SQLite temporário (`@TempDir`), cobrindo persistência de relações, CRUD e restrições de clientes, ambientes, insumos, ferragens, peças de projeto e rascunho; também pesquisa, dados inválidos, movimento de projeto e exportação PDF.
- `woodlogix-desktop/.../ProjectWorkpieceIntegrationTest.java` — 5 testes em SQLite temporário migrado com Flyway: criação/atualização/ordenação e mapeamento de rows, valores atuais dos catálogos, validação e IDs inexistentes, rollback após flush e tradução da falha de integridade na exclusão sem conversão de outras falhas.
- `woodlogix-desktop/.../ProposalPdfServiceTest.java` — 4 testes de conteúdo/exportação PDF e mensagens/currency em inglês e espanhol.
- `woodlogix-server/.../MaterialControllerIntegrationTest.java` — testa com MockMvc, Flyway e SQLite temporário a listagem, criação e atualização: status/content-type, campos, ordenação, precisão decimal, persistência, preservação de ID, relacionamento com rascunho, preço zero, campos inválidos, corpo JSON `null` e ID inexistente com `ApiErrorResponse`; confirma o caminho do banco de teste.
- `woodlogix-server/.../WoodLogixServerApplicationTest.java` — inicia o contexto com `RANDOM_PORT` e confirma que um servidor HTTP embarcado recebeu uma porta, sem configurar ou exigir banco de dados.
- `woodlogix-server/.../TransportDtoJsonTest.java` e `woodlogix-desktop/.../TransportDtoJsonTest.java` — validam serialização/desserialização dos contratos DTO contra fixtures JSON equivalentes, incluindo valores decimais, enums, nulos e coleções; o Desktop verifica também campos extras.
- `woodlogix-server/.../ApiMapperTest.java` e `woodlogix-desktop/.../ApiMapperTest.java` — exercitam as implementações geradas pelo MapStruct sem inicializar Spring nem usar `Mappers.getMapper`; verificam conversões de tipos, `BigDecimal`, nulos, coleções, pipeline completo/ordenado e rascunho ausente.
- `woodlogix-desktop/.../ConfigLoaderTest.java` — 1 teste de configuração externa.
- `woodlogix-desktop/.../DeletionAndPipelineCharacterizationTest.java` — 16 testes de caracterização em SQLite temporário: exclusões com FKs reais, associações do pipeline, ordenação por etapa/data, movimentação sem alterar outros campos e rollback de criação após flush.
- `woodlogix-desktop/.../EnvironmentAndDraftCharacterizationTest.java` — caracteriza sincronização de ambientes e rascunhos; cobre referências ausentes, validações, projeto opcional, fita ausente/obrigatória, substituição de filhos, listas vazias e rollback após flush real com SQLite temporário migrado via Flyway.
- `woodlogix-desktop/.../SqliteMigrationTest.java` — testa aplicação e reabertura da migração, recusa de banco não vazio sem histórico e validação de schema.
- `woodlogix-desktop/.../DeletionErrorTranslationTest.java` — 5 testes: verifica reconhecimento seletivo de violações de FK SQLite, tradução restrita a exclusões pontuais e em lote, e propagação de erros de criação e não-FK.
- `woodlogix-desktop/.../EnvironmentAndSalesPipelineServiceTest.java` — 4 testes: verifica delegação das consultas auxiliares das fachadas aos catálogos de Application, preservação das rows retornadas e tratamento de ID de cliente nulo ou em branco.

**Lacunas de teste:** a cobertura JavaFX é limitada aos cenários de materiais e peças descritos acima; não cobre navegação completa, arrastar e soltar, atualização visual de resumo ou fechamento com alterações pendentes. Os testes do motor não validam aproveitamento real de chapas, ampla precisão monetária, regras de marcenaria de um móvel completo ou arredondamentos em vários cenários de produção. A exportação PDF não tem cobertura de paginação ou de integração com os valores calculados do orçamento.

## 6. Limitações e riscos técnicos

- **Precisão financeira:** entidades e DTOs de persistência usam `BigDecimal`, mas modelos usados por `PricingEngine` usam `double`. `PricingController` converte dimensões e preços persistidos de `BigDecimal` para `double` antes do cálculo. Para valores monetários, essa representação binária pode introduzir diferenças de precisão; os sete testes atuais usam tolerância numérica em exemplos simples e não estabelecem uma política abrangente de precisão/arredondamento.
- **Consumo de chapas:** o cálculo por área não considera geometria, rotação, orientação/veio, espessura do corte, margens ou reaproveitamento de retalhos. O valor não deve ser interpretado como plano de corte ou garantia de quantidade de compra.
- **Escopo do orçamento:** o sistema persiste um rascunho ativo e peças vinculadas a projetos, mas não um histórico de orçamentos emitidos. A proposta PDF exporta o valor comercial salvo no projeto, não o preço sugerido atualizado pelo cálculo do rascunho.
- **Materiais e fitas de borda em peças:** o schema armazena apenas o ID do material e da fita (`edge_banding_id`), não um snapshot histórico de seus valores. As peças exibidas mantêm os valores carregados em memória; após restaurar ou recarregar, são usados os valores atuais do catálogo.
- **Validações:** há validações nos modelos, records, serviços, entidades e na interface, mas elas não são uniformes em todos os caminhos. Os testes cobrem casos concretos; não demonstram uma auditoria completa de todas as combinações/limites.
- **Integridade e evolução do banco:** a V1 do Flyway cria FKs físicas em bancos novos; sua fiscalização depende de `woodlogix.database.foreign-keys=true`, valor padrão configurável. A sincronização de ambientes verifica referências de peças, móveis e projetos; exclusões de clientes e ambientes traduzem violações SQLite de FK reconhecidas. Bancos anteriores sem histórico Flyway não são migrados automaticamente e exigem um plano específico de backup, diagnóstico e migração. Hibernate `validate` não substitui a verificação de FKs físicas.
- **Tratamento de erros:** `UiTaskRunner` registra falhas de operações assíncronas e apresenta mensagem localizada com detalhes técnicos expansíveis. A experiência de erro não tem testes de interface, e operações/exportação não foram verificadas em todas as falhas de sistema de arquivos ou banco.
- **Experiência do usuário:** as telas atuais carregam e a navegação foi verificada, inclusive em janela reduzida, mas a automação não valida acessibilidade, escala de tela, todos os tamanhos, estados de falha nem fluxos completos. A UI e o Kanban não têm testes de interação automatizados.
- **Dados legados de móveis:** entidades e repositórios de móveis/partes permanecem, mas não há fluxo de usuário correspondente identificado; não assumir que cadastro ou projeto completo de móveis esteja disponível.

## 7. Decisões de produto

As decisões de produto e a direção arquitetural aprovada são:

- Manter a experiência **desktop** e a operação individual offline; evoluir para comunicação HTTP com backend local ou central, conforme a seção 3. O backend separado e o modo compartilhado ainda não estão implementados.
- Priorizar necessidades de **pequenas marcenarias**.
- Favorecer simplicidade e facilidade de uso, evitando complexidade arquitetural ou funcionalidades sem necessidade confirmada.
- Priorizar melhorias que reduzam custo, desperdício e tempo administrativo, preservando transparência sobre estimativas e limites dos cálculos.

## 8. Próximas etapas propostas

A direção arquitetural da seção 3 foi aprovada; os itens abaixo são recomendações adicionais baseadas nas lacunas observadas, não compromissos de escopo já aprovados.

| Prioridade | Objetivo | Motivo | Critério de aceitação |
|---|---|---|---|
| **P1** | Definir e testar precisão e arredondamento dos valores calculados. | O motor calcula com `double` enquanto valores persistidos são `BigDecimal`; os testes atuais não cobrem escalas e limites financeiros de forma ampla. | Registrar a política de precisão com exemplos aprovados pelo produto; adicionar testes para conversão, acumulação, margem e apresentação sem alterar silenciosamente valores já persistidos. |
| **P1** | Automatizar os principais fluxos de orçamento e rascunho na UI. | Não há testes JavaFX para edição/exclusão de peças, ferragens, resumo, autosave, restauração e fechamento. | Testes reproduzíveis verificam esses fluxos e o comportamento em falha de salvamento, usando banco temporário, sem depender do banco local. |
| **P1** | Tornar explícita a relação entre orçamento calculado e proposta comercial. | Hoje o rascunho e o valor comercial do projeto são dados distintos; o PDF não exporta o resultado calculado. | Definir com o produto o fluxo esperado; testes confirmam que o documento usa os valores e o estado persistidos esperados, ou documentar explicitamente que são independentes. |
| **P2** | Especificar requisitos para uma eventual otimização de corte antes de implementá-la. | A contagem atual é por área e não modela encaixe; não há regra de produção suficiente no código para inferir um algoritmo correto. | Aprovar premissas operacionais da oficina e casos de referência mensuráveis antes de implementar; sem premissas confirmadas, manter a funcionalidade identificada como estimativa por área. |
| **P2** | Decidir o destino do domínio legado de móveis e partes. | Persistência existe e é testada, mas não há serviço/tela de cadastro correspondente encontrado. | Registrar decisão de produto: manter explicitamente como legado/futuro ou definir escopo e critérios de aceitação para um fluxo completo, sem tratar a entidade como funcionalidade disponível. |

## 9. Histórico

- **PR #1 — “feat: enhance UI components and improve layout for better user experience”** foi integrada à `main` no commit `d639fcd`. A referência atual deste documento está na branch indicada no início.
- A integração alterou a navegação, o Dashboard, Clientes, Ambientes, Catálogo, Kanban e Orçamentação, incluindo FXML, CSS, controladores e mensagens localizadas.
- A existência da PR integrada e os testes de build não significam que todos os fluxos de UI foram validados. A suíte automatizada tem cobertura concentrada em domínio, serviços/persistência e PDF; continuam pendentes os testes de interação descritos acima.
