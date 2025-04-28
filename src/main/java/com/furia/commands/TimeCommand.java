package com.furia.commands;

import com.furia.bot.FuriaBot;
import com.furia.crawler.HltvCrawlerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

@Component
public class TimeCommand implements Command {

    private static final Logger logger = LoggerFactory.getLogger(TimeCommand.class);
    private final HltvCrawlerService crawlerService;

    public TimeCommand(HltvCrawlerService crawlerService) {
        this.crawlerService = crawlerService;
    }

    @Override
    public void execute(Long chatId, FuriaBot bot) {
        logger.info("Comando /time executado para chatId: {}", chatId);

        List<HltvCrawlerService.Player> lineup = crawlerService.getTeamLineup();
        if (lineup == null || lineup.isEmpty()) {
            bot.sendMessage(chatId, "Nenhuma escalação encontrada. Tente novamente mais tarde.");
            logger.warn("Escalação vazia para chatId: {}", chatId);
            return;
        }

        // Monta a mensagem com os nomes dos jogadores
        StringBuilder lineupText = new StringBuilder("Nossa Seleção FURIOSA:\n");
        for (HltvCrawlerService.Player player : lineup) {
            lineupText.append("• ").append(player.getName()).append("\n");
        }
        bot.sendMessage(chatId, lineupText.toString());

        // Envia as fotos em um álbum
        List<InputMedia> media = new ArrayList<>();
        for (HltvCrawlerService.Player player : lineup) {
            String imageUrl = player.getImageUrl();
            if (imageUrl != null && !imageUrl.isEmpty()) {
                InputMediaPhoto photo = new InputMediaPhoto();
                photo.setMedia(imageUrl);
                photo.setCaption(player.getName());
                media.add(photo);
            } else {
                logger.warn("Imagem não disponível para o jogador: {}", player.getName());
            }
        }

        if (!media.isEmpty()) {
            SendMediaGroup mediaGroup = new SendMediaGroup();
            mediaGroup.setChatId(chatId.toString());
            mediaGroup.setMedias(media);
            try {
                bot.execute(mediaGroup);
                logger.info("Fotos dos jogadores enviadas para chatId: {}", chatId);
            } catch (TelegramApiException e) {
                logger.error("Erro ao enviar fotos para chatId {}: {}", chatId, e.getMessage(), e);
                bot.sendMessage(chatId, "Erro ao enviar as fotos dos jogadores. Verifique a escalação acima.");
            }
        } else {
            logger.warn("Nenhuma imagem válida encontrada para enviar no chatId: {}", chatId);
            bot.sendMessage(chatId, "Nenhuma imagem disponível para a escalação atual.");
        }
    }
}