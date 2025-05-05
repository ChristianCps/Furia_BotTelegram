# FURIA Bot

## Link

https://web.telegram.org/k/#@ChatFuriaCS_bot

O FURIA Bot é uma aplicação desenvolvida em Java com Spring Boot que integra um crawler para coletar informações sobre o time de eSports FURIA no site HLTV.org e um bot do Telegram para interagir com os fãs. O bot fornece informações como escalação do time, próximas partidas, resultados recentes, partidas ao vivo, links para a loja oficial e canais de contato da FURIA.

## Funcionalidades

- `/time`: Exibe a escalação atual do time FURIA, incluindo nomes e fotos dos jogadores.
- `/jogo` ou `/partida`: Lista as próximas partidas da FURIA, organizadas por torneio, com data e horário.
- `/resultado`: Mostra os últimos resultados das partidas da FURIA, indicando vitórias ou derrotas.
- `/live`: Fornece detalhes de partidas ao vivo, incluindo placar, mapas vencidos, formato (ex.: MD3), picks e bans, links de transmissão e detalhes da partida.
- `/loja`: Envia o link para a loja oficial da FURIA.
- `/contato`: Lista os canais oficiais de contato, como Instagram, X, WhatsApp e Discord.
- `/start` ou `/help`: Exibe a lista de comandos disponíveis.

## Estrutura do Projeto

- `com.furia`: Contém a classe principal `FuriaBotApplication` que inicializa a aplicação Spring Boot.
- `com.furia.bot`: Inclui a configuração do bot (`BotConfig`) e a lógica principal do bot Telegram (`FuriaBot`).
- `com.furia.commands`: Contém a interface `Command` e as implementações dos comandos do bot:
  - `ComandoCommands`: Gerencia os comandos /start e /help.
  - `TimeCommand`: Lida com o comando /time.
  - `JogoCommand`: Lida com os comandos /jogo e /partida.
  - `ResultadoCommand`: Lida com o comando /resultado.
  - `LiveCommand`: Lida com o comando /live.
  - `LojaCommand`: Lida com o comando /loja.
  - `ContatoCommand`: Lida com o comando /contato.
- `com.furia.crawler`: Contém a classe `HltvCrawlerService`, responsável por realizar o web scraping no site HLTV.org.

## Pré-requisitos

- Java 17 ou superior
- Maven (para gerenciar dependências e compilar o projeto)
- ChromeDriver (compatível com a versão do Google Chrome instalada)
- Conta no Telegram e um bot criado via BotFather
- Ambiente Windows (devido ao caminho do ChromeDriver no exemplo)

## Instalação

### 1. Clonar o Repositório

```bash
git clone <URL_DO_REPOSITORIO>
cd furia-bot
```

### 2. Configurar o ChromeDriver

Baixe o ChromeDriver em: https://chromedriver.chromium.org/downloads

Coloque em um diretório acessível e anote o caminho.

### 3. Configurar o Token do Bot Telegram

Crie um bot com o BotFather e obtenha o token.

Crie o arquivo `application.properties` em `src/main/resources`:

```properties
spring.application.name=furia-bot
crawler.fallback.enabled=true
telegram.bot.username=@SeuBotUsername
telegram.bot.token=SeuBotToken
crawler.team.code=8297
crawler.team.name=FURIA
crawlInterval=3600000
crawler.chromedriver.path=/caminho/para/chromedriver
```

### 4. Instalar Dependências

```bash
mvn clean install
```

### 5. Executar a Aplicação

```bash
mvn spring-boot:run
# ou
mvn package
java -jar target/furia-bot-0.0.1-SNAPSHOT.jar
```

### 6. Testar o Bot

Pesquise o bot no Telegram, envie `/start` e utilize os comandos.

## Configurações Avançadas

- **crawlInterval**: Define o intervalo entre verificações (padrão: 1h).
- **Cache**: TTL de 1h para o time e 10min para partidas.
- **WebDriver**: Reiniciado diariamente às 4h. Logs a cada 7min.
- **Live**: Verificação a cada 3min (ativa) ou 20min (programada).

## Estrutura do Código

### HltvCrawlerService

- Coleta: jogadores, próximas partidas, últimos resultados, partidas ao vivo.
- Tecnologias: Selenium WebDriver (headless), Jsoup.
- Agendamento com `@Scheduled`.

### FuriaBot

- Gerencia interações com o Telegram.
- Usa TelegramBots para comunicação.
- Comandos separados em classes.

## Resolução de Problemas

- **ChromeDriver**: Verifique a versão e o caminho no `application.properties`.
- **Bot inativo**: Verifique o token e os logs.
- **Dados desatualizados**: Aumente o intervalo ou reinicie a aplicação.
- **Memória**: Ajuste os tempos de limpeza do cache.
