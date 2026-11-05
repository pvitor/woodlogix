# Instruções do Copilot — WoodLogix

## Arquitetura do projeto
- Use Java 17 e respeite a estrutura multi-módulo Maven.
- `woodlogix-core` contém modelos e regras de negócio, sem dependência de Spring, JavaFX ou persistência.
- `woodlogix-desktop` integra JavaFX com Spring Boot embutido, Spring Data JPA e SQLite local.
- `WoodLogixDesktopLauncher` é o ponto de entrada Java com `main`; `AppLauncher` controla o ciclo de vida JavaFX.
- Use Spring Boot 3.x compatível com Java 17 e Lombok conforme já configurados no Maven.
- Não crie um servidor web: o contexto Spring roda dentro do processo desktop e deve ser encerrado junto com a aplicação.
- Preserve responsabilidades claras: controladores JavaFX coordenam a interface, serviços aplicam casos de uso e DAOs cuidam da persistência.

## Persistência local
- Use SQLite offline com Spring Data JPA e interfaces `JpaRepository`; não implemente DAOs com JDBC nem SQL manual.
- Mantenha as entidades persistentes e repositórios em `woodlogix-desktop`; não acople JPA ao `woodlogix-core`.
- Configure a conexão e o dialeto em propriedades externalizadas; carregue a configuração com `ConfigLoader` e permita substituir o caminho do banco por configuração externa ou variável de ambiente.
- Preserve chaves estrangeiras, integridade referencial e precisão monetária (`BigDecimal`).
- Propague/trate erros de persistência explicitamente; não esconda falhas nem retorne sucesso fictício.
- Em atualizações e exclusões, valide a existência do ID antes da operação e use `EntityNotFoundException` para registros inexistentes; preserve integridade referencial em exclusões relacionadas.
- Registre falhas com SLF4J contendo contexto e stack trace, sem dados pessoais; mantenha logs de diagnóstico no console e em arquivo configurável.
- Registre eventos relevantes de novas funcionalidades sem incluir nomes, telefones, e-mails, endereços ou outros dados pessoais nos logs.
- Mensagens de erro apresentadas na UI devem ser amigáveis e localizadas; disponibilize detalhes técnicos selecionáveis para diagnóstico, sem expor exceções brutas ao usuário como mensagem principal.
- Não realize I/O ou consultas ao banco na JavaFX Application Thread; use `Task`/`Service` para trabalho demorado e atualize a UI na thread JavaFX.

## Modelos e Lombok
- Use Lombok apenas para reduzir código repetitivo, com anotações explícitas e compatíveis com Java 17.
- Não use `@Data` indiscriminadamente em entidades/modelos imutáveis; prefira `@Getter` e construtores explícitos para preservar validações.
- Não introduza dependências de infraestrutura em `woodlogix-core`.
- Escreva todos os identificadores de código em inglês: nomes de classes, métodos, campos, parâmetros, variáveis, enums, pacotes e testes. Mantenha nomes de tabelas/colunas e textos de interface localizados quando necessários para compatibilidade ou UX.

## Testes
- Escreva testes em `src/test/java` para comportamento novo ou alterado.
- Testes de persistência JPA devem usar bancos SQLite temporários, nunca o banco local do usuário.
- Verifique operações bem-sucedidas, entradas inválidas, integridade referencial e precisão monetária, conforme aplicável.
- Execute os testes Maven dos módulos afetados; prefira modo offline quando as dependências já estiverem disponíveis.
- Centralize textos da interface e documentos nos `ResourceBundle` em `woodlogix-desktop/src/main/resources/com/woodlogix/desktop/i18n`; mantenha `woodlogix.ui.locale` configurável e não persista rótulos traduzidos no banco.

## Segurança e alterações
- Não registre dados pessoais ou segredos; não adicione credenciais ao código.
- Antes de adicionar frameworks ou trocar a arquitetura, siga as decisões registradas aqui e explique impactos que exijam mudança de escopo.
- Faça mudanças pequenas e coerentes com os padrões e versões definidos nos POMs.
