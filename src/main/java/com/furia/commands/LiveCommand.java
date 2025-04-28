package com.furia.commands;

import com.furia.bot.FuriaBot;
import com.furia.crawler.HltvCrawlerService;
import com.furia.crawler.HltvCrawlerService.LiveMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LiveCommand implements Command {

    private static final Logger logger = LoggerFactory.getLogger(LiveCommand.class);
    private final HltvCrawlerService crawlerService;

    public LiveCommand(HltvCrawlerService crawlerService) {
        this.crawlerService = crawlerService;
    }

    @Override
    public void execute(Long chatId, FuriaBot bot) {
        logger.info("Comando /live executado para chatId: {}", chatId);
        LiveMatch liveMatch = crawlerService.getLiveMatch();
        logger.debug("Partida ao vivo retornada: {}", liveMatch != null ? liveMatch.getOpponent() : "null");
        if (liveMatch == null) {
            bot.sendMessage(chatId, "Nenhuma partida ao vivo no momento.");
            logger.info("Nenhuma partida ao vivo detectada para chatId: {}", chatId);
            return;
        }

        StringBuilder response = new StringBuilder("🔥 Partida da FURIA ao vivo! 🔥\n");
        // Tournament and format
        String format = liveMatch.getFormat().toUpperCase().replace("BO", "MD");
        response.append(String.format("🏆 %s - %s\n", liveMatch.getTournament(), format));

        // Current map score and maps won
        String[] currentMapScore = liveMatch.getCurrentMapScore().split("-");
        String[] mapsWon = liveMatch.getMapsWon().split("-");
        String furiaScore = currentMapScore.length == 2 ? currentMapScore[0].trim() : "0";
        String opponentScore = currentMapScore.length == 2 ? currentMapScore[1].trim() : "0";
        String furiaMapsWon = mapsWon.length == 2 ? mapsWon[0].trim() : "0";
        String opponentMapsWon = mapsWon.length == 2 ? mapsWon[1].trim() : "0";
        response.append(String.format("FURIA %s (%s) - (%s) %s %s\n", 
            furiaScore, furiaMapsWon, opponentMapsWon, opponentScore, liveMatch.getOpponent()));

        // Veto details
        if (!liveMatch.getVetoDetails().isEmpty()) {
            response.append("\nPicks e Bans:\n");
            for (String veto : liveMatch.getVetoDetails()) {
                // Replace "Sharks" with "FURIA" in veto details
                String formattedVeto = veto.replace("Sharks", "FURIA");
                response.append("  - ").append(formattedVeto).append("\n");
            }
        }

        // Stream links
        if (!liveMatch.getStreamLinks().isEmpty()) {
            response.append("\nPrincipais Transmissões:\n");
            for (String stream : liveMatch.getStreamLinks()) {
                // Skip internal HLTV links like "/live?matchId=..."
                if (!stream.contains("/live?matchId=")) {
                    response.append("  - ").append(stream).append("\n");
                }
            }
        }

        // Match link
        response.append("\n📊 Detalhes da partida:\n").append(liveMatch.getMatchLink());

        bot.sendMessage(chatId, response.toString());
    }
}