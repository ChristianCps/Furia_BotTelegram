package com.furia.bot;

import com.furia.commands.Command;
import com.furia.commands.JogoCommand;
import com.furia.commands.LiveCommand;
import com.furia.commands.LojaCommand;
import com.furia.commands.ResultadoCommand;
import com.furia.commands.TimeCommand;
import com.furia.commands.ContatoCommand;
import com.furia.commands.ComandoCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Component
public class FuriaBot extends TelegramLongPollingBot {

    private static final Logger logger = LoggerFactory.getLogger(FuriaBot.class);

    private final BotConfig botConfig;
    private final TimeCommand timeCommand;
    private final JogoCommand jogoCommand;
    private final ResultadoCommand resultadoCommand;
    private final LojaCommand lojaCommand;
    private final LiveCommand liveCommand;
    private final ContatoCommand contatoCommand;
    private final ComandoCommands comandoCommands;

    public FuriaBot(BotConfig botConfig, TimeCommand timeCommand, JogoCommand jogoCommand,
                    ResultadoCommand resultadoCommand, LojaCommand lojaCommand, LiveCommand liveCommand,
                    ContatoCommand contatoCommand, ComandoCommands comandoCommands) {
        super(botConfig.getBotToken());
        this.botConfig = botConfig;
        this.timeCommand = timeCommand;
        this.jogoCommand = jogoCommand;
        this.resultadoCommand = resultadoCommand;
        this.lojaCommand = lojaCommand;
        this.liveCommand = liveCommand;
        this.contatoCommand = contatoCommand;
        this.comandoCommands = comandoCommands;
        logger.info("FuriaBot inicializado com username: {}", botConfig.getBotUsername());

        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(this);
            logger.info("Bot registrado com sucesso na TelegramBotsApi");
        } catch (TelegramApiException e) {
            logger.error("Erro ao registrar o bot: {}", e.getMessage(), e);
        }
    }

    @Override
    public String getBotUsername() {
        return botConfig.getBotUsername();
    }

    @Override
    public String getBotToken() {
        return botConfig.getBotToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText().trim().toLowerCase();
            Long chatId = update.getMessage().getChatId();
            logger.info("Mensagem recebida: {} de chatId: {}", messageText, chatId);

            Command command = null;
            if (messageText.startsWith("/time")) {
                command = timeCommand;
            } else if (messageText.startsWith("/jogo") || messageText.startsWith("/partida")) {
                command = jogoCommand;
            } else if (messageText.startsWith("/resultado")) {
                command = resultadoCommand;
            } else if (messageText.startsWith("/loja")) {
                command = lojaCommand;
            } else if (messageText.startsWith("/live")) {
                command = liveCommand;
            } else if (messageText.startsWith("/contato")) {
                command = contatoCommand;
            } else if (messageText.startsWith("/start")) {
                try {
                    comandoCommands.executeStart(chatId, this);
                    return;
                } catch (Exception e) {
                    logger.error("Erro ao executar /start para chatId {}: {}", chatId, e.getMessage(), e);
                    sendMessage(chatId, "Erro ao processar o comando. Tente novamente mais tarde.");
                    return;
                }
            } else if (messageText.startsWith("/help")) {
                try {
                    comandoCommands.executeHelp(chatId, this);
                    return;
                } catch (Exception e) {
                    logger.error("Erro ao executar /help para chatId {}: {}", chatId, e.getMessage(), e);
                    sendMessage(chatId, "Erro ao processar o comando. Tente novamente mais tarde.");
                    return;
                }
            }

            if (command != null) {
                logger.info("Executando comando: {} para chatId: {}", messageText, chatId);
                try {
                    command.execute(chatId, this);
                } catch (Exception e) {
                    logger.error("Erro ao executar comando {} para chatId {}: {}", messageText, chatId, e.getMessage(), e);
                    sendMessage(chatId, "Erro ao processar o comando. Tente novamente mais tarde.");
                }
            } else {
                logger.warn("Comando não reconhecido: {} para chatId: {}", messageText, chatId);
                sendMessage(chatId, "Comando não reconhecido. Tente: /start, /help, /time, /jogo, /partida, /resultado, /loja, /live ou /contato.");
            }
        } else {
            logger.warn("Atualização sem mensagem de texto: {}", update);
        }
    }

    public void sendMessage(Long chatId, String text, boolean disableWebPagePreview) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setDisableWebPagePreview(disableWebPagePreview);
    
        try {
            logger.debug("Enviando mensagem para chatId {}: {}", chatId, text);
            execute(message);
            logger.info("Mensagem enviada para chatId: {}: {}", chatId, text);
        } catch (TelegramApiException e) {
            logger.error("Erro ao enviar mensagem para chatId {}: {}", chatId, e.getMessage(), e);
        }
    }
    
    public void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, false);
    }
}