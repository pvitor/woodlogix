# Propostas comerciais e versões

## 1. Objetivo e escopo

Este documento registra a arquitetura conceitual aprovada para a evolução do ciclo de vida de orçamentos e propostas comerciais do WoodLogix. Seu objetivo é orientar futuras decisões de domínio, persistência, cálculo e geração de documentos sem apresentar funcionalidades planejadas como se já estivessem implementadas.

O documento diferencia:

- **Estado atual observado:** comportamento identificado no código existente.
- **Diretriz aprovada:** decisão de negócio que deve orientar a evolução.
- **Pendente de decisão:** regra que não deve ser presumida até ser definida.

Esta é uma referência arquitetural e de produto; não especifica uma implementação completa nem altera o funcionamento atual.

## 2. Estado atual observado

O WoodLogix já possui um projeto comercial no funil de vendas, um rascunho ativo de orçamento e exportação de um PDF associado a um projeto. Esses conceitos ainda não formam um fluxo de propostas versionadas.

- `SalesProjectEntity` representa o projeto comercial, com cliente, ambiente opcional, nome, valor total, `SalesStage` e data de criação. `SalesPipelineService` cria projetos, lista-os por etapa e altera o estágio comercial.
- `EstimateDraftEntity` representa um único rascunho ativo, identificado pelo valor fixo `"active"`. Mantém parâmetros gerais e coleções de peças e ferragens; `EstimateDraftService` salva e recupera esse rascunho.
- As linhas do rascunho preservam dimensões, quantidades e referências aos cadastros de materiais, fitas e ferragens. Em particular, `EstimateDraftHardwareEntity` mantém referência à ferragem e quantidade, não o preço aplicado. Assim, o rascunho não é um snapshot comercial independente dos catálogos atuais.
- `PricingEngine`, em `woodlogix-core`, calcula custos estimados de chapas, fitas, ferragens e mão de obra/máquina e calcula um preço sugerido a partir da margem. Seus cálculos monetários atualmente usam `double`; a persistência de alguns preços usa `BigDecimal`.
- `ProposalPdfService.export` recebe um ID de projeto e gera um PDF usando dados correntes do projeto, incluindo seu valor total e estágio. Não recebe uma versão de proposta nem reconstrói um snapshot do orçamento.
- `SalesStage` descreve o andamento do projeto no funil. Não representa o estado de aceite de uma proposta.

Esses componentes podem ser reutilizados como contexto comercial, origem de edição, referência às regras de cálculo e infraestrutura de exportação. Entretanto, não constituem histórico imutável de propostas emitidas.

## 3. Conceitos e estrutura comercial

A estrutura conceitual aprovada é:

`SalesProject → Proposal → ProposalVersion`

- **SalesProject:** projeto comercial já representado atualmente por `SalesProjectEntity`. Agrupa o relacionamento com cliente/ambiente e o andamento no funil.
- **Proposal:** oferta ou alternativa comercial independente dentro de um projeto. Um mesmo projeto pode ter múltiplas propostas independentes, por exemplo, alternativas distintas de materiais ou escopo.
- **ProposalVersion:** registro de uma emissão específica de uma proposta. Cada proposta mantém seu próprio histórico de versões; uma nova emissão cria nova versão, sem sobrescrever a anterior.

As propostas devem usar identificadores comerciais no formato `PROP-AAAA-NNNN`, sem reutilização. As versões devem usar a numeração `V01`, com numeração crescente e sem reutilização dentro da proposta.

Propostas diferentes do mesmo projeto podem ter moedas diferentes. A moeda principal de cada proposta é única e deverá estar preservada em suas versões emitidas. A possibilidade de mudar a moeda entre versões da mesma proposta ainda é uma decisão pendente.

Cada `Proposal` possui um único rascunho editável corrente. O rascunho não é uma versão emitida e não substitui o histórico imutável de `ProposalVersion`.

O conceito de `Proposal` é necessário porque o projeto pode agrupar mais de uma oferta independente. O conceito de `ProposalVersion` é necessário porque revisões emitidas precisam ser identificáveis e preservadas. Essa separação não determina uma hierarquia extensa de classes ou entidades: o modelo deve continuar limitado às responsabilidades de projeto, proposta e versão.

## 4. Ciclo de vida do orçamento de trabalho

O orçamento de trabalho permanece editável e é distinto de qualquer proposta emitida.

1. A marcenaria edita o orçamento de trabalho e consulta os cadastros e regras disponíveis.
2. A emissão cria uma nova `ProposalVersion` definitiva, com o snapshot dos dados e valores usados.
3. Emitir uma versão não destrói, não congela e não converte o orçamento de trabalho em proposta.
4. Uma revisão começa copiando a última versão emitida para um novo orçamento de trabalho editável. Os preços e valores copiados são preservados no novo rascunho. Consultar preços atuais dos catálogos não autoriza atualização automática do rascunho; qualquer atualização de preços exige ação explícita do usuário.
5. Alterações feitas no orçamento de trabalho copiado nunca modificam a versão emitida de origem.
6. Emitir a revisão cria outra versão da mesma proposta, mantendo a anterior preservada e identificável.

O rascunho editável pertence a uma `Proposal`, e cada proposta possui um único rascunho corrente. O comportamento atual, com um único rascunho identificado como `"active"` e vínculo opcional a um projeto, não representa esse modelo e será substituído.

## 5. Emissão e snapshot imutável

Na emissão, a versão deve preservar informação suficiente para reproduzir fielmente a oferta sem depender de consultas futuras aos cadastros. Referências aos registros de origem podem ser mantidas para rastreabilidade, mas não devem substituir os valores copiados no snapshot.

O snapshot da versão emitida deverá incluir, conforme aplicável:

- Identificação do projeto, da proposta e da versão.
- Número comercial da proposta e número da versão.
- Dados do cliente e do destinatário utilizados na oferta. Quando houver um contato comercial associado ao destinatário, preservar `displayName` obrigatório e `phone` e `email` opcionais, sem exigir ambos nem incluir endereço ou documentos pessoais.
- Descrições dos produtos, materiais, ferragens e serviços.
- Quantidades e unidades.
- Preços unitários, valores totais e moeda da proposta.
- Custos de materiais, ferragens, fitas de borda, mão de obra e máquinas.
- Parâmetros utilizados nos cálculos.
- Descontos por item e gerais, com tipo e valor.
- Tratamento tributário, alíquotas e valores calculados de cada item ou componente aplicável.
- Condições de pagamento, prazo estimado de fabricação, condições e prazo de entrega, instalação quando aplicável e observações comerciais.
- Data de emissão, prazo de validade, data de vencimento e valores finais.

Depois de emitido, o conteúdo comercial e financeiro do snapshot não pode ser editado nem recalculado a partir dos cadastros atuais. Mudanças em nomes, preços, unidades, dados de cliente, fórmulas ou configurações futuras não podem modificar retroativamente uma versão emitida. As transições de estado e a referência de substituição são informações de histórico da versão, separadas do conteúdo comercial congelado.

A emissão deve obter de forma coerente os dados usados para compor o snapshot, evitando misturar preços ou descrições de momentos diferentes durante uma mesma operação. A persistência da versão imutável, do estado `ISSUED` e de `currentIssuedVersionId` deve ocorrer atomicamente na mesma operação.

Cada `Proposal` mantém `currentIssuedVersionId`, que identifica sua versão emitida atual, quando existir. Versões anteriores preservam seu estado histórico e recebem `replacedByVersionId` quando forem substituídas por uma nova emissão. Não existe o estado `SUPERSEDED`.

## 6. Regras monetárias, tributárias e comerciais

### 6.1 Moedas e precisão monetária

- As moedas suportadas serão **UYU** e **USD**.
- Cada proposta terá uma única moeda principal; propostas independentes do mesmo projeto podem usar moedas distintas.
- A moeda utilizada deverá ser preservada no snapshot. A apresentação do PDF não poderá inferir a moeda somente a partir do idioma ou localidade da aplicação.
- Cálculos e persistência monetária deverão utilizar `BigDecimal`.
- A precisão decimal interna deverá ser mantida sem arredondamento prematuro.
- Valores apresentados ao usuário e no documento comercial terão duas casas decimais.
- Quando necessário, a conversão será manual, com taxa positiva expressa em UYU por 1 USD.
- Cada rascunho possui no máximo uma taxa de conversão aplicável, que deve ser preservada no snapshot da versão emitida.
- Ajuste residual exclusivamente visual será aplicado somente na apresentação final, no último item elegível, após os valores internos serem mantidos sem arredondamento prematuro. Esse ajuste não altera valores internos, cálculos, dados persistidos ou o snapshot imutável.

### 6.2 IVA e base tributável

O IVA é definido por item. O sistema deverá permitir registrar preços informados com IVA incluído ou sem IVA e a alíquota aplicável. O snapshot deverá registrar o modo tributário, a alíquota e os valores calculados de cada item ou componente sujeito ao tratamento aplicável.

Na apresentação da proposta, separar:

- Subtotal.
- Base tributável.
- Valor do IVA.
- Total final.

Não presumir que todos os produtos e serviços possuem o mesmo tratamento fiscal. O snapshot deve preservar os dados e resultados usados na emissão, mesmo se as regras fiscais mudarem posteriormente.

### 6.3 Descontos

Deverão ser suportados descontos individuais por item e desconto geral da proposta. Cada desconto poderá ser percentual ou por valor fixo. Os descontos individuais são aplicados primeiro. O desconto geral é distribuído proporcionalmente às bases após os descontos individuais e antes do cálculo do IVA.

O sistema deverá impedir que um desconto exceda o valor correspondente ou produza valor negativo. O snapshot deve preservar os descontos aplicados e os valores resultantes.

### 6.4 Validade e condições comerciais

Cada versão emitida deverá preservar:

- Data de emissão.
- Prazo de validade configurável de 15, 30 ou 45 dias.
- Data de vencimento calculada no momento da emissão.
- Condições de pagamento.
- Prazo estimado de fabricação.
- Condições e prazo de entrega.
- Instalação, quando aplicável.
- Observações comerciais.

Depois da emissão, o vencimento registrado não deverá mudar em função de configurações futuras. Uma versão vencida passa a `EXPIRED` se ainda não tiver sido aceita, rejeitada ou retirada.

A validade é calculada com vencimento congelado. A versão permanece válida até o final do último dia do prazo e expira no dia seguinte. A verificação ocorre na inicialização da aplicação, na abertura da proposta e antes de operações relevantes. Não há scheduler para essa verificação.

Os custos internos devem permanecer separados dos itens comerciais apresentados ao cliente. O modelo deve suportar margem por item e margem padrão global. Margens abaixo dos limites definidos exigem aprovação independente e justificativa obrigatória, preservadas para auditoria.

## 7. Estados e histórico das versões

Os seguintes estados pertencem a `ProposalVersion`:

| Estado | Significado |
|---|---|
| `ISSUED` | Versão emitida ao cliente e dentro do ciclo de resposta comercial. |
| `ACCEPTED` | Versão aceita pelo cliente. |
| `REJECTED` | Versão recusada pelo cliente. |
| `EXPIRED` | Versão cujo prazo venceu sem ter sido aceita, rejeitada ou retirada. |
| `WITHDRAWN` | Versão retirada pela marcenaria. |

`APPROVED` não faz parte dos estados aprovados; `ACCEPTED` representa a aceitação comercial pelo cliente. Aprovação interna, se necessária, é um processo distinto e deverá ser definida sem confundi-la com o aceite.

Estado comercial e versão são dimensões distintas. Uma proposta possui versões identificáveis; cada versão tem seu próprio estado e histórico. A substituição de uma versão não cria estado adicional nem apaga ou sobrescreve a versão anterior.

Quando uma nova versão for emitida, a versão anterior deverá permanecer preservada e receber `replacedByVersionId`. Essa relação de substituição é registrada separadamente do estado. Assim, substituição não implica automaticamente `REJECTED`, `EXPIRED` ou outro estado: o estado da versão anterior continua sujeito às regras do seu ciclo comercial.

Somente a versão emitida atual, identificada por `currentIssuedVersionId`, pode ser aceita. `REJECTED`, `EXPIRED` e `WITHDRAWN` são estados terminais. A aceitação não cria uma `Sale` automaticamente.

## 8. Relação com o PDF

O PDF de uma proposta emitida deverá ser gerado a partir do snapshot da `ProposalVersion` correspondente. Não deverá consultar preços atuais de materiais, ferragens ou serviços, nem usar os dados correntes do projeto para reconstruir valores históricos.

O documento deverá refletir os dados congelados da versão, incluindo identificação e número da versão, destinatário e, quando presente, os dados congelados do contato comercial, moeda, itens, condições, tratamento tributário, descontos, data de emissão, vencimento e totais aplicáveis. A formatação poderá ser localizada, mas a localização não poderá alterar valores, alíquotas ou moeda registrada.

O `ProposalPdfService` atual é uma base de infraestrutura de geração, mas sua exportação por ID de projeto e leitura do valor atual do projeto não satisfazem o requisito de reprodução histórica. A exportação de uma versão emitida deve receber e usar seu snapshot. O PDF da versão emitida, baseado no snapshot, deve ser preservado localmente, fora do SQLite. A estrutura de diretórios, a nomenclatura dos arquivos e a política específica de backup permanecem pendentes.

## 9. Princípios arquiteturais

- Manter modelos e regras de domínio sem dependências de Spring, JavaFX ou JPA em `woodlogix-core`.
- Manter integração JavaFX, serviços de aplicação, entidades JPA, repositórios e configuração SQLite em `woodlogix-desktop`.
- Preservar responsabilidades distintas para projeto comercial, proposta, versão emitida e orçamento editável.
- Não usar `SalesStage` como estado de proposta; ele continua representando o andamento do projeto no funil.
- Não tratar o rascunho como proposta emitida nem como histórico imutável.
- Preferir evolução incremental e aditiva, reutilizando o projeto e a infraestrutura existentes quando apropriado.
- Evitar hierarquias genéricas ou estruturas excessivas para uma pequena marcenaria; modelar os dados e as regras efetivamente necessários.
- Preservar valores emitidos independentemente dos cadastros, sem inventar snapshots retroativos para registros antigos que não contêm os dados necessários.

## 10. Decisões pendentes

As decisões abaixo continuam pendentes e não devem ser presumidas durante a implementação. Elas são separadas pelo marco que bloqueiam.

### 10.1 Bloqueiam o cálculo comercial

1. **Regras fiscais detalhadas:** tratamentos e alíquotas por categoria de produto ou serviço, inclusive exceções ainda não especificadas.
2. **Condições comerciais:** estrutura dos campos e quais condições são obrigatórias ou opcionais.
3. **Limites de margem:** valores dos limites, autoridade da aprovação independente e estrutura da justificativa.
4. **Moeda entre versões:** se uma nova versão da mesma proposta pode usar moeda diferente ou se a mudança exige uma nova proposta independente.

### 10.2 Bloqueiam a emissão e o histórico

5. **Transação de emissão:** limites da operação atômica, concorrência e comportamento em falhas parciais.
6. **Revisão:** identificação do novo rascunho e detalhes operacionais da cópia da última versão emitida. Os preços e valores copiados são preservados, e qualquer atualização exige ação explícita do usuário.
7. **Estados e aceite — detalhes pendentes:** evidências de aceite/recusa, transições não descritas, tratamento de ações reversíveis e aspectos operacionais dos estados e versões substituídas. Já está aprovado que somente a versão emitida atual pode ser aceita e que `REJECTED`, `EXPIRED` e `WITHDRAWN` são estados terminais.
8. **Registros antigos:** regras para tratar projetos, valores e rascunhos existentes. Não há dados suficientes para criar versões históricas completas automaticamente.

### 10.3 Podem ser decididas em etapas posteriores

9. **PDF preservado:** estrutura de diretórios, nomenclatura dos arquivos e política específica de backup para o PDF local baseado no snapshot.
10. **Exclusão e arquivamento:** política para excluir ou arquivar propostas não emitidas, rascunhos e itens editáveis. Versões emitidas permanecem imutáveis.
11. **Validade operacional adicional:** tratamento administrativo após a verificação de expiração já definida e eventuais rotinas de consulta ou manutenção.

## 11. Etapas futuras recomendadas

1. Definir as regras ainda pendentes de tributação, condições comerciais, limites de margem e moeda entre versões.
2. Detalhar a transação de emissão, concorrência e comportamento em falhas parciais.
3. Projetar a evolução conceitual de forma aditiva: projeto comercial, propostas independentes, versões emitidas e um rascunho editável por proposta.
4. Planejar persistência e compatibilidade com os bancos SQLite existentes, incluindo cenários para projetos, rascunhos e cadastros legados.
5. Definir e testar a criação consistente de uma versão a partir do rascunho, com valores e dados copiados dos cadastros na emissão.
6. Adaptar a geração de PDF para receber uma versão específica e usar exclusivamente seu snapshot.
7. Validar o histórico, imutabilidade, transições, cálculo monetário, tributação, descontos e apresentação com testes automatizados antes de disponibilizar o fluxo.

As etapas descrevem uma sequência recomendada de trabalho futuro; não afirmam que qualquer uma delas já tenha sido implementada.
