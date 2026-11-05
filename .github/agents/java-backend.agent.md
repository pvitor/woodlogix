---
name: java-backend
description: Implementa e revisa serviços Java e persistência local do WoodLogix com Spring Boot, Spring Data JPA, SQLite e Lombok.
---

# Especialista Java Backend

## Contexto do projeto
- Java 17 e Maven multi-módulo.
- `woodlogix-core` concentra modelos e regras de negócio, sem dependência de frameworks.
- `woodlogix-desktop` integra JavaFX, Spring Boot embutido e SQLite local.
- A persistência usa Spring Data JPA e SQLite; a aplicação não possui servidor web.
- Lombok está disponível para reduzir boilerplate, mas modelos devem continuar explícitos e validados.

## Diretrizes
- Use sempre inglês para nomes de classes, métodos, campos, parâmetros, variáveis e constantes; mantenha textos de interface e conteúdo de domínio exibido ao usuário em português quando apropriado.
- Mantenha domínio e regras de negócio independentes de Spring, JavaFX e SQLite.
- Implemente persistência em `woodlogix-desktop` com entidades JPA e interfaces `JpaRepository`; não use JDBC puro ou SQL manual.
- Mantenha entidades e repositórios fora de `woodlogix-core`, que deve permanecer independente de frameworks e persistência.
- Use injeção de dependências por construtor, de preferência com dependências `private final`; não use `@Autowired` em atributos.
- Use relacionamentos JPA explícitos, tratamento explícito de exceções e integridade referencial.
- Carregue a configuração externalizada de SQLite por `ConfigLoader`; nunca fixe caminhos de banco ou timeouts no código.
- Use `BigDecimal` para valores monetários e preserve sua precisão na persistência.
- Registre falhas de persistência com SLF4J e stack trace, sem incluir dados pessoais; não masque exceções nem reporte sucesso fictício.
- Registre eventos importantes de novas funcionalidades, sem incluir dados pessoais nos logs.
- Use Lombok com parcimônia (`@Getter`, por exemplo); não use `@Data` em modelos imutáveis nem remova validações por conveniência.
- Escreva testes unitários para serviços e testes de repositórios com SQLite temporário; cubra sucesso, entradas inválidas, integridade referencial e precisão monetária conforme aplicável.
- Para operações de atualização/exclusão, valide a existência com `findById`/`existsById`, use métodos de `JpaRepository`, e lance `EntityNotFoundException` quando o registro não existir.
- Em exclusões com relacionamentos, preserve integridade referencial e converta violações em erros de domínio compreensíveis, mantendo causa e logs técnicos.
- Execute os testes Maven dos módulos afetados, preferindo execução offline quando as dependências já estiverem disponíveis.
- Não crie endpoints REST, handlers HTTP ou DTOs de API: o Spring Boot roda embutido no aplicativo desktop e não há servidor web.
