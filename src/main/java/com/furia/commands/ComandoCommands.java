package com.furia.commands;

import com.furia.bot.FuriaBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ComandoCommands implements Command {

    private static final Logger logger = LoggerFactory.getLogger(ComandoCommands.class);

    @Override
    public void execute(Long chatId, FuriaBot bot) {
        // Este método não será chamado diretamente, mas é necessário pela interface
        logger.warn("Método execute padrão chamado para ComandoCommands, chatId: {}", chatId);
    }

    public void executeStart(Long chatId, FuriaBot bot) {
        logger.info("Comando /start executado para chatId: {}", chatId);
        String message = """
                Bem-vindo ao bot da FURIA! 🐾
                Aqui estão os comandos disponíveis:
                /time - Veja a escalação atual do time da FURIA 
                /jogo ou /partida - Confira as próximas partidas da FURIA 
                /resultado - Veja os últimos resultados da FURIA 
                /loja - Acesse a loja oficial da FURIA
                /live - Acompanhe partidas ao vivo da FURIA 
                /contato - Entre em contato com a FURIA
                /start ou /help - Mostrar esta mensagem
                """;
        bot.sendMessage(chatId, message);
    }

    public void executeHelp(Long chatId, FuriaBot bot) {
        logger.info("Comando /help executado para chatId: {}", chatId);
        String message = """
                Aqui estão os comandos do bot FURIA: 🐾
                /time - Veja a escalação atual do time da FURIA 
                /jogo ou /partida - Confira as próximas partidas da FURIA 
                /resultado - Veja os últimos resultados da FURIA 
                /loja - Acesse a loja oficial da FURIA
                /live - Acompanhe partidas ao vivo da FURIA 
                /contato - Entre em contato com a FURIA
                /start ou /help - Mostrar esta mensagem
                """;
        bot.sendMessage(chatId, message, true);
    }
}