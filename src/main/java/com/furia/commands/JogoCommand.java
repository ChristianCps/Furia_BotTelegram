package com.furia.commands;

import com.furia.bot.FuriaBot;
import com.furia.crawler.HltvCrawlerService;
import com.furia.crawler.HltvCrawlerService.Match;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class JogoCommand implements Command {

    private static final Logger logger = LoggerFactory.getLogger(JogoCommand.class);
    private final HltvCrawlerService crawlerService;

    public JogoCommand(HltvCrawlerService crawlerService) {
        this.crawlerService = crawlerService;
    }

    @Override
    public void execute(Long chatId, FuriaBot bot) {
        logger.info("Comando /jogo executado para chatId: {}", chatId);
        List<Match> matches = crawlerService.getUpcomingMatches();
        if (matches == null || matches.isEmpty()) {
            bot.sendMessage(chatId, "Nenhuma partida futura encontrada. Tente novamente mais tarde.");
            logger.warn("Partidas futuras vazias para chatId: {}", chatId);
            return;
        }

        // Agrupa as partidas por torneio
        Map<String, List<Match>> matchesByTournament = new LinkedHashMap<>();
        for (Match match : matches) {
            String tournament = match.getTournament();
            matchesByTournament.computeIfAbsent(tournament, k -> new ArrayList<>()).add(match);
        }

        // Formata a mensagem
        StringBuilder message = new StringBuilder("Próximas partidas da FURIA:\n\n");
        for (Map.Entry<String, List<Match>> entry : matchesByTournament.entrySet()) {
            String tournament = entry.getKey();
            List<Match> tournamentMatches = entry.getValue();
            message.append("🏆 ").append(tournament).append("\n");
            for (Match match : tournamentMatches) {
                String dateTime = match.getDate().equals("Hoje")
                    ? "Hoje às " + match.getTime()
                    : match.getDate() + " às " + match.getTime();
                message.append("🔥 vs ").append(match.getOpponent()).append(" - ").append(dateTime).append("\n");
            }
            message.append("\n");
        }

        bot.sendMessage(chatId, message.toString().trim());
        logger.info("Mensagem de /jogo enviada para chatId: {}", chatId);
    }
}