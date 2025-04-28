package com.furia.commands;

import com.furia.bot.FuriaBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LojaCommand implements Command {

    private static final Logger logger = LoggerFactory.getLogger(LojaCommand.class);
    private static final String LOJA_URL = "https://www.furia.gg/";

    @Override
    public void execute(Long chatId, FuriaBot bot) {
        logger.info("Comando /loja executado para chatId: {}", chatId);
        bot.sendMessage(chatId, "Loja oficial da FURIA: " + LOJA_URL);
    }
}