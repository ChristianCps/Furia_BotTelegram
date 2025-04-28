package com.furia.commands;

import com.furia.bot.FuriaBot;
import com.furia.crawler.HltvCrawlerService;
import com.furia.crawler.HltvCrawlerService.MatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ResultadoCommand implements Command {

    private static final Logger logger = LoggerFactory.getLogger(ResultadoCommand.class);
    private final HltvCrawlerService crawlerService;

    public ResultadoCommand(HltvCrawlerService crawlerService) {
        this.crawlerService = crawlerService;
    }

    @Override
    public void execute(Long chatId, FuriaBot bot) {
        logger.info("Comando /resultado executado para chatId: {}", chatId);
        List<MatchResult> results = crawlerService.getLastResults();
        if (results == null || results.isEmpty()) {
            bot.sendMessage(chatId, "Nenhum resultado encontrado. Tente novamente mais tarde.");
            logger.warn("Resultados vazios para chatId: {}", chatId);
            return;
        }

        // Agrupa os resultados por torneio
        Map<String, List<MatchResult>> resultsByTournament = new LinkedHashMap<>();
        for (MatchResult result : results) {
            String tournament = result.getTournament();
            resultsByTournament.computeIfAbsent(tournament, k -> new ArrayList<>()).add(result);
        }

        // Formata a mensagem
        StringBuilder message = new StringBuilder("Últimos resultados da FURIA:\n\n");
        for (Map.Entry<String, List<MatchResult>> entry : resultsByTournament.entrySet()) {
            String tournament = entry.getKey();
            List<MatchResult> tournamentResults = entry.getValue();
            message.append("🏆 ").append(tournament).append("\n");
            for (MatchResult result : tournamentResults) {
                String indicator = result.isVictory() ? "✅" : "❌";
                message.append(indicator).append(" vs ").append(result.getOpponent())
                       .append(" - ").append(result.getScore()).append("\n");
            }
            message.append("\n");
        }

        bot.sendMessage(chatId, message.toString().trim());
        logger.info("Mensagem de /resultado enviada para chatId: {}", chatId);
    }
}