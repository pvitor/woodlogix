---
name: javafx-frontend
description: Desenvolve e revisa a interface desktop JavaFX do WoodLogix, incluindo FXML, CSS e controladores.
---

# JavaFX Frontend Specialist

## Contexto do projeto
- Java 17, OpenJFX 17 e Maven.
- Aplicativo desktop dividido nos módulos `woodlogix-core` e `woodlogix-desktop`.
- Spring Boot roda embutido no processo JavaFX; não há servidor web.
- A interface está em `woodlogix-desktop`, com FXML em `src/main/resources`.

## Diretrizes
- Use sempre inglês para nomes de classes, métodos, campos, parâmetros, variáveis e constantes; não traduza textos apresentados ao usuário por causa dessa regra.
- Mantenha a interface (`.fxml`), estilos (`.css`) e controladores Java separados.
- Controladores JavaFX devem ser gerenciados pelo contexto Spring e obtidos pelo `FXMLLoader` via `controllerFactory`.
- Inicie o contexto Spring no ciclo de vida da aplicação desktop e encerre-o ao fechar a janela/aplicação.
- Controladores devem conter lógica de apresentação e coordenação de eventos; delegue casos de uso aos serviços e não acesse SQLite diretamente.
- Nunca execute I/O ou processamento demorado na JavaFX Application Thread. Use `javafx.concurrent.Task` ou `Service` para operações assíncronas.
- Atualize a interface somente na JavaFX Application Thread; use os callbacks das tarefas (`setOnSucceeded`, `setOnFailed` e equivalentes) para tratar resultados e exibir erros amigáveis, sem falhas silenciosas.
- Registre falhas inesperadas com SLF4J e stack trace, sem dados pessoais. Mostre mensagens localizadas e compreensíveis ao usuário e disponibilize detalhes técnicos selecionáveis em um painel expansível, sem apresentar exceções brutas como mensagem principal.
- Novas operações da interface devem registrar eventos relevantes de sucesso/falha sem registrar dados pessoais; os logs devem permanecer disponíveis no console e em arquivo configurável.
- Preserve a separação entre a interface desktop e as regras de negócio mantidas em `woodlogix-core`.
- Injete serviços por construtor nos controladores quando necessário; não introduza dependências sem necessidade.
- Siga as versões Java, JavaFX e Spring Boot configuradas no Maven.
- Não implemente clientes HTTP nem presuma a existência de um backend REST: a aplicação é desktop e o contexto Spring é local ao processo.
