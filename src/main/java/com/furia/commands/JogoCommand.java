package com.furia.commands;

import com.furia.bot.FuriaBot;
import com.furia.crawler.HltvCrawlerService;
import com.furia.crawler.HltvCrawlerService.Match;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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

            // Ordenar partidas por data e horário
            tournamentMatches.sort((m1, m2) -> {
                try {
                    LocalDateTime dateTime1 = parseMatchDateTime(m1);
                    LocalDateTime dateTime2 = parseMatchDateTime(m2);
                    return dateTime1.compareTo(dateTime2);
                } catch (Exception e) {
                    return 0;
                }
            });

            message.append("🏆 ").append(tournament).append("\n");
            for (Match match : tournamentMatches) {
                String dateTime = formatMatchDateTime(match);
                message.append("🔥 vs ").append(match.getOpponent()).append(" - ").append(dateTime).append("\n");
            }
            message.append("\n");
        }

        bot.sendMessage(chatId, message.toString().trim());
        logger.info("Mensagem de /jogo enviada para chatId: {}", chatId);
    }

    private LocalDateTime parseMatchDateTime(Match match) {
        String dateStr = match.getDate();
        String timeStr = match.getTime();
        LocalDate date;
        LocalTime time;

        try {
            if ("Hoje".equals(dateStr)) {
                date = LocalDate.now();
            } else if ("Amanhã".equals(dateStr)) {
                date = LocalDate.now().plusDays(1);
            } else {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yy");
                date = LocalDate.parse(dateStr, formatter);
            }

            if ("TBA".equals(timeStr)) {
                time = LocalTime.of(0, 0);
            } else {
                DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
                time = LocalTime.parse(timeStr, timeFormatter);
            }

            return LocalDateTime.of(date, time);
        } catch (Exception e) {
            logger.warn("Erro ao parsear data/horário para partida: {} às {}", dateStr, timeStr);
            return LocalDateTime.now();
        }
    }

    private String formatMatchDateTime(Match match) {
        String dateStr = match.getDate();
        String timeStr = match.getTime();

        if ("Hoje".equals(dateStr)) {
            return "Hoje às " + timeStr;
        } else if ("Amanhã".equals(dateStr)) {
            return "Amanhã às " + timeStr;
        } else {
            return dateStr + " às " + timeStr;
        }
    }
}