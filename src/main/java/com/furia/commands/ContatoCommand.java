package com.furia.commands;

import com.furia.bot.FuriaBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ContatoCommand implements Command {

    private static final Logger logger = LoggerFactory.getLogger(ContatoCommand.class);
    private static final String INSTAGRAM_URL = "https://www.instagram.com/furia/";
    private static final String X_URL = "https://x.com/FURIA";
    private static final String WHATSAPP_URL = "https://wa.me/5511993404466";
    private static final String DISCORD_URL = "https://discord.gg/CTWQtMpa";

    @Override
    public void execute(Long chatId, FuriaBot bot) {
        logger.info("Comando /contato executado para chatId: {}", chatId);

        String message = """
                Redes sociais:
                - Instagram: %s
                - X: %s
                
                WhatsApp da FURIA:
                - %s
                
                Quer conversar com a galera? Entre no nosso Discord:
                - %s
                """.formatted(INSTAGRAM_URL, X_URL, WHATSAPP_URL, DISCORD_URL);

        bot.sendMessage(chatId, message, true);
    }
}