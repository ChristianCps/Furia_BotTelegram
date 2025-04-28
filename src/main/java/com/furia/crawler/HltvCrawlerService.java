package com.furia.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.FileWriter;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class HltvCrawlerService {

    private static final Logger logger = LoggerFactory.getLogger(HltvCrawlerService.class);

    private final String teamCode;
    private final String teamName;
    private final String chromeDriverPath;
    private final long crawlInterval;
    private final AtomicReference<List<Player>> teamLineup = new AtomicReference<>(Collections.emptyList());
    private final AtomicReference<List<Match>> upcomingMatches = new AtomicReference<>(Collections.emptyList());
    private final AtomicReference<List<MatchResult>> lastResults = new AtomicReference<>(Collections.emptyList());
    private final AtomicReference<LiveMatch> liveMatch = new AtomicReference<>(null);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public HltvCrawlerService(
            @Value("${crawler.team.code}") String teamCode,
            @Value("${crawler.team.name}") String teamName,
            @Value("${crawler.chromedriver.path}") String chromeDriverPath,
            @Value("${crawlInterval}") long crawlInterval) {
        this.teamCode = teamCode;
        this.teamName = teamName;
        this.chromeDriverPath = chromeDriverPath;
        this.crawlInterval = crawlInterval;
    }

    @PostConstruct
    public void init() {
        System.setProperty("webdriver.chrome.driver", chromeDriverPath);
        crawlHltv();
        startLiveMatchScheduler();
    }

    @PreDestroy
    public void destroy() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }

    private void startLiveMatchScheduler() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                crawlLiveMatch();
            } catch (Exception e) {
                logger.error("Erro no agendamento de crawlLiveMatch: {}", e.getMessage(), e);
            }
        }, 0, 1, TimeUnit.MINUTES); // Start immediately, adjust interval dynamically
    }

    private void crawlLiveMatch() {
        if (liveMatch.get() != null) {
            // Live match is ongoing, update every 1 minute
            updateLiveMatch(null);
            // Schedule next check in 1 minute
            scheduler.schedule(this::crawlLiveMatch, 1, TimeUnit.MINUTES);
        } else if (hasMatchToday()) {
            // Match scheduled for today, check every 10 minutes
            if (shouldCheckLiveMatch()) {
                updateLiveMatch(null);
            } else {
                logger.info("Nenhuma partida ao vivo detectada, verificando novamente em 10 minutos.");
            }
            // Schedule next check in 10 minutes
            scheduler.schedule(this::crawlLiveMatch, 10, TimeUnit.MINUTES);
        } else {
            // No match scheduled for today, check every hour
            logger.info("Nenhum jogo programado para hoje, verificando partidas ao vivo na próxima hora.");
            // Schedule next check in 1 hour
            scheduler.schedule(this::crawlLiveMatch, crawlInterval / 1000 / 60, TimeUnit.MINUTES);
        }
    }

    private boolean hasMatchToday() {
        List<Match> matches = upcomingMatches.get();
        LocalDate today = LocalDate.now();
        for (Match match : matches) {
            if ("Hoje".equals(match.getDate())) {
                return true;
            }
            // Parse date if not "Hoje"
            try {
                String dateStr = match.getDate();
                if (!dateStr.isEmpty() && !"TBA".equals(dateStr)) {
                    LocalDate matchDate = null;
                    if (dateStr.matches("\\d{1,2}/\\d{1,2}/\\d{2}")) {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yy");
                        matchDate = LocalDate.parse(dateStr, formatter);
                    } else {
                        // Try to extract unix time if available
                        String url = String.format("https://www.hltv.org/team/%s/%s#tab-matchesBox", teamCode, teamName);
                        Document doc = fetchDocumentWithSelenium(url, ".table-container.match-table");
                        if (doc != null) {
                            Elements matchRows = doc.select("tr.team-row");
                            for (Element row : matchRows) {
                                if (row.select(".team-name.team-2").text().equals(match.getOpponent()) ||
                                    row.select(".team-flex:not(.team-1) .team-name").text().equals(match.getOpponent())) {
                                    String unixTime = row.select("td.date-cell span").attr("data-unix");
                                    if (unixTime.isEmpty()) {
                                        unixTime = row.select("td.date-cell").attr("data-unix");
                                    }
                                    if (!unixTime.isEmpty()) {
                                        long unixMillis = Long.parseLong(unixTime);
                                        matchDate = Instant.ofEpochMilli(unixMillis)
                                                .atZone(ZoneId.systemDefault())
                                                .toLocalDate();
                                    }
                                }
                            }
                        }
                    }
                    if (matchDate != null && matchDate.equals(today)) {
                        return true;
                    }
                }
            } catch (Exception e) {
                logger.warn("Erro ao verificar data da partida: {}", match.getDate(), e);
            }
        }
        return false;
    }

    @Scheduled(fixedRateString = "${crawlInterval}")
    public void crawlHltv() {
        crawlTeamInfo();
        crawlMatches();
        // Check for live match on hourly crawl if no match is scheduled today
        if (!hasMatchToday() && liveMatch.get() == null) {
            updateLiveMatch(null);
        }
    }

    private boolean shouldCheckLiveMatch() {
        String matchesUrl = "https://www.hltv.org/matches";
        Document matchesDoc = fetchDocumentWithSelenium(matchesUrl, ".live-matches-wrapper");
        if (matchesDoc == null) {
            return false;
        }

        Elements liveMatches = matchesDoc.select("div.match-wrapper.live-match-container");
        for (Element match : liveMatches) {
            String team1Id = match.attr("team1");
            String team2Id = match.attr("team2");
            if (teamCode.equals(team1Id) || teamCode.equals(team2Id)) {
                return true;
            }
        }
        return false;
    }

    private void crawlTeamInfo() {
        String url = String.format("https://www.hltv.org/team/%s/%s#tab-infoBox", teamCode, teamName);
        logger.info("Acessando URL (infoBox): {}", url);
        Document doc = fetchDocumentWithSelenium(url, "div.bodyshot-team.g-grid");
        if (doc != null) {
            updateTeamLineup(doc);
        } else {
            logger.error("Falha ao obter documento para URL: {}", url);
        }
    }

    private void crawlMatches() {
        String url = String.format("https://www.hltv.org/team/%s/%s#tab-matchesBox", teamCode, teamName);
        logger.info("Acessando URL (matchesBox): {}", url);
        Document doc = fetchDocumentWithSelenium(url, ".table-container.match-table");
        if (doc != null) {
            updateUpcomingMatches(doc);
            updateLastResults(doc);
        } else {
            logger.error("Falha ao obter documento para URL: {}", url);
        }
    }

    private Document fetchDocumentWithSelenium(String url, String waitForSelector) {
        WebDriver driver = null;
        try {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--disable-gpu");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36");
            driver = new ChromeDriver(options);
            logger.info("Selenium acessando URL: {}", url);
            driver.get(url);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(waitForSelector)));

            String pageSource = driver.getPageSource();
            logger.info("Acessado HLTV com sucesso via Selenium: {}", url);

            try (FileWriter writer = new FileWriter("hltv_debug_" + url.hashCode() + ".html")) {
                writer.write(pageSource);
            } catch (IOException e) {
                logger.warn("Erro ao salvar HTML bruto: {}", e.getMessage());
            }

            return Jsoup.parse(pageSource);
        } catch (Exception e) {
            logger.error("Erro ao acessar HLTV com Selenium: {}", e.getMessage(), e);
            return null;
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    logger.warn("Erro ao fechar o WebDriver: {}", e.getMessage());
                }
            }
        }
    }

    private void updateTeamLineup(Document doc) {
        List<Player> newLineup = new ArrayList<>();
        Elements playerElements = doc.select("div.bodyshot-team.g-grid a");

        if (playerElements.isEmpty()) {
            playerElements = doc.select("div.team-roster a.col-custom");
        }

        for (Element element : playerElements) {
            String name = element.select("div.text-ellipsis.nickname-container span.text-ellipsis.bold").text();
            if (name.isEmpty()) {
                name = element.select("div.nickname").text();
            }
            String imageUrl = element.select("div.overlayImageFrame img").attr("src");
            if (imageUrl.isEmpty()) {
                imageUrl = element.select("div.overlayImageFrame img").attr("data-src");
                if (imageUrl.isEmpty()) {
                    imageUrl = element.select("img.bodyshot-team-img").attr("src");
                }
            }
            if (!name.isEmpty()) {
                newLineup.add(new Player(name, imageUrl));
            } else {
                logger.warn("Nome do jogador vazio para elemento: {}", element.outerHtml());
            }
        }
        teamLineup.set(newLineup);
    }

    private void updateUpcomingMatches(Document doc) {
        List<Match> newMatches = new ArrayList<>();
        Elements tables = doc.select(".table-container.match-table");

        if (tables.isEmpty()) {
            tables = doc.select("table.match-table");
        }

        String currentTournament = "";
        for (Element table : tables) {
            Elements headers = table.select("thead");
            Elements bodies = table.select("tbody");

            for (Element header : headers) {
                Element tournamentElement = header.select("tr.event-header-cell a").first();
                if (tournamentElement == null) {
                    tournamentElement = header.select("th.text-ellipsis a").first();
                }
                if (tournamentElement != null) {
                    currentTournament = tournamentElement.text();
                }
            }

            for (Element body : bodies) {
                Elements matches = body.select("tr.team-row");
                for (Element match : matches) {
                    String dateTime = match.select("td.date-cell span").text();
                    if (dateTime.isEmpty()) {
                        dateTime = match.select("td.date-cell").text();
                    }
                    String date = dateTime;
                    String time = "TBA";
                    String opponent = match.select(".team-name.team-2").text();
                    if (opponent.isEmpty()) {
                        opponent = match.select(".team-flex:not(.team-1) .team-name").text();
                    }
                    String score = match.select(".score-cell").text();
                    if (score.isEmpty()) {
                        score = match.select("div.score-cell").text();
                    }

                    if (score.contains(":")) {
                        String[] scores = score.split(":");
                        if (scores.length == 2 && scores[0].trim().equals("-") && scores[1].trim().equals("-")) {
                            if (dateTime.matches("\\d{2}:\\d{2}")) {
                                date = "Hoje";
                                time = dateTime;
                            } else {
                                String unixTime = match.select("td.date-cell span").attr("data-unix");
                                if (unixTime.isEmpty()) {
                                    unixTime = match.select("td.date-cell").attr("data-unix");
                                }
                                if (!unixTime.isEmpty()) {
                                    try {
                                        long unixMillis = Long.parseLong(unixTime);
                                        Instant instant = Instant.ofEpochMilli(unixMillis);
                                        LocalDate matchDate = instant.atZone(ZoneId.systemDefault()).toLocalDate();
                                        time = instant.atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
                                        if (matchDate.equals(LocalDate.now())) {
                                            date = "Hoje";
                                        } else {
                                            date = matchDate.format(DateTimeFormatter.ofPattern("dd/MM/yy"));
                                        }
                                    } catch (NumberFormatException e) {
                                        logger.warn("Erro ao converter unixTime: {}", unixTime);
                                        time = "TBA";
                                    }
                                }
                            }

                            if (!date.isEmpty() && !opponent.isEmpty() && !currentTournament.isEmpty()) {
                                newMatches.add(new Match(date, time, opponent, currentTournament));
                            } else {
                                logger.warn("Partida ignorada devido a dados incompletos: date={}, opponent={}, tournament={}", date, opponent, currentTournament);
                            }
                        }
                    }
                }
            }
        }
        upcomingMatches.set(newMatches);
    }

    private void updateLastResults(Document doc) {
        List<MatchResult> newResults = new ArrayList<>();
        Elements tables = doc.select(".table-container.match-table");

        if (tables.isEmpty()) {
            tables = doc.select("table.match-table");
        }

        String currentTournament = "";
        int resultCount = 0;

        for (Element table : tables) {
            Elements headers = table.select("thead");
            Elements bodies = table.select("tbody");

            for (Element header : headers) {
                Element tournamentElement = header.select("tr.event-header-cell a").first();
                if (tournamentElement == null) {
                    tournamentElement = header.select("th.text-ellipsis a").first();
                }
                if (tournamentElement != null) {
                    currentTournament = tournamentElement.text();
                }
            }

            for (Element body : bodies) {
                Elements matches = body.select("tr.team-row");
                for (Element match : matches) {
                    String score = match.select(".score-cell").text();
                    if (score.isEmpty()) {
                        score = match.select("div.score-cell").text();
                    }
                    if (score.contains(":")) {
                        String[] scores = score.split(":");
                        if (scores.length == 2 && !scores[0].trim().equals("-") && !scores[1].trim().equals("-")) {
                            String opponent = match.select(".team-name.team-2").text();
                            if (opponent.isEmpty()) {
                                opponent = match.select(".team-flex:not(.team-1) .team-name").text();
                            }
                            boolean isVictory = match.select(".team-flex.lost .team-name.team-1").isEmpty();
                            if (isVictory) {
                                isVictory = !match.select(".team-flex.team-1.lost").hasClass("lost");
                            } else {
                                isVictory = match.select(".team-flex.team-2.lost").hasClass("lost");
                            }
                            if (!opponent.isEmpty() && !currentTournament.isEmpty()) {
                                newResults.add(new MatchResult(score, opponent, currentTournament, isVictory));
                                resultCount++;
                            } else {
                                logger.warn("Resultado ignorado devido a dados incompletos: score={}, opponent={}, tournament={}", score, opponent, currentTournament);
                            }
                        }
                    }
                    if (resultCount >= 3) {
                        break;
                    }
                }
                if (resultCount >= 3) {
                    break;
                }
            }
            if (resultCount >= 3) {
                break;
            }
        }
        lastResults.set(newResults);
    }

    private void updateLiveMatch(Document doc) {
        String matchesUrl = "https://www.hltv.org/matches";
        logger.info("Verificando partidas ao vivo em: {}", matchesUrl);
        Document matchesDoc = fetchDocumentWithSelenium(matchesUrl, ".live-matches-wrapper");
        if (matchesDoc == null) {
            logger.error("Falha ao obter documento para URL de partidas: {}", matchesUrl);
            liveMatch.set(null);
            return;
        }

        LiveMatch newLiveMatch = null;
        Elements liveMatches = matchesDoc.select("div.match-wrapper.live-match-container");
        boolean matchFound = false;

        for (Element match : liveMatches) {
            String team1Id = match.attr("team1");
            String team2Id = match.attr("team2");
            if (teamCode.equals(team1Id) || teamCode.equals(team2Id)) {
                matchFound = true;
                // Match found for the selected team
                String opponentId = teamCode.equals(team1Id) ? team2Id : team1Id;
                String opponent = match.select(teamCode.equals(team1Id) ? "div.match-team:nth-child(2) .match-teamname" : "div.match-team:nth-child(1) .match-teamname").text();
                String currentMapScore = match.select("span.current-map-score[data-livescore-team='" + teamCode + "']").text() + "-" + match.select("span.current-map-score[data-livescore-team='" + opponentId + "']").text();
                String mapsWon = match.select("span[data-livescore-maps-won-for][data-livescore-team='" + teamCode + "']").text() + "-" + match.select("span[data-livescore-maps-won-for][data-livescore-team='" + opponentId + "']").text();
                String tournament = match.select("div.match-event.text-ellipsis").text();
                String matchLink = "https://www.hltv.org" + match.select("a.match-top").attr("href");

                // Access match page for format, veto, and streams
                Document matchDoc = fetchDocumentWithSelenium(matchLink, ".standard-box.veto-box");
                if (matchDoc == null) {
                    logger.warn("Falha ao obter página da partida: {}", matchLink);
                    continue;
                }

                // Extract format (bo1, bo3, bo5)
                String format = "Unknown";
                Element formatElement = matchDoc.select("div.standard-box.veto-box .padding.preformatted-text").first();
                if (formatElement != null) {
                    String formatText = formatElement.text().toLowerCase();
                    if (formatText.contains("best of 1")) {
                        format = "bo1";
                    } else if (formatText.contains("best of 3")) {
                        format = "bo3";
                    } else if (formatText.contains("best of 5")) {
                        format = "bo5";
                    }
                }

                // Extract veto details
                List<String> vetoDetails = new ArrayList<>();
                Elements vetoItems = matchDoc.select("div.standard-box.veto-box .padding div");
                for (Element vetoItem : vetoItems) {
                    vetoDetails.add(vetoItem.text());
                }

                // Extract up to three streams, sorted by viewers
                List<String> streamLinks = new ArrayList<>();
                Elements streamElements = matchDoc.select("div.stream-box");
                List<Element> sortedStreams = new ArrayList<>(streamElements);
                // Sort by viewers (if available)
                sortedStreams.sort((a, b) -> {
                    String viewersA = a.select("span.viewers").text().replaceAll("[^0-9]", "");
                    String viewersB = b.select("span.viewers").text().replaceAll("[^0-9]", "");
                    int countA = viewersA.isEmpty() ? 0 : Integer.parseInt(viewersA);
                    int countB = viewersB.isEmpty() ? 0 : Integer.parseInt(viewersB);
                    return Integer.compare(countB, countA); // Descending order
                });

                for (int i = 0; i < Math.min(3, sortedStreams.size()); i++) {
                    Element stream = sortedStreams.get(i);
                    String streamUrl = stream.select("a[href]").attr("href");
                    if (!streamUrl.isEmpty()) {
                        streamLinks.add(streamUrl);
                    }
                }

                // Create LiveMatch object
                newLiveMatch = new LiveMatch(
                    opponent,
                    currentMapScore,
                    mapsWon,
                    tournament,
                    format,
                    matchLink,
                    vetoDetails,
                    streamLinks
                );
                break; // Stop after finding the first live match for the team
            }
        }

        if (!matchFound) {
            // Clear liveMatch if no live match is found for the team
            liveMatch.set(null);
            logger.info("Nenhuma partida ao vivo encontrada para o time: {}", teamName);
        } else {
            liveMatch.set(newLiveMatch);
            logger.info("Partida ao vivo atualizada: {}", newLiveMatch);
        }
    }

    public List<Player> getTeamLineup() {
        return teamLineup.get();
    }

    public List<Match> getUpcomingMatches() {
        return upcomingMatches.get();
    }

    public List<MatchResult> getLastResults() {
        return lastResults.get();
    }

    public LiveMatch getLiveMatch() {
        return liveMatch.get();
    }

    public static class Player {
        private final String name;
        private final String imageUrl;

        public Player(String name, String imageUrl) {
            this.name = name;
            this.imageUrl = imageUrl;
        }

        public String getName() {
            return name;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        @Override
        public String toString() {
            return "Player{name='" + name + "', imageUrl='" + imageUrl + "'}";
        }
    }

    public static class Match {
        private final String date;
        private final String time;
        private final String opponent;
        private final String tournament;

        public Match(String date, String time, String opponent, String tournament) {
            this.date = date;
            this.time = time;
            this.opponent = opponent;
            this.tournament = tournament;
        }

        public String getDate() {
            return date;
        }

        public String getTime() {
            return time;
        }

        public String getOpponent() {
            return opponent;
        }

        public String getTournament() {
            return tournament;
        }

        @Override
        public String toString() {
            return "Match{date='" + date + "', time='" + time + "', opponent='" + opponent + "', tournament='" + tournament + "'}";
        }
    }

    public static class MatchResult {
        private final String score;
        private final String opponent;
        private final String tournament;
        private final boolean victory;

        public MatchResult(String score, String opponent, String tournament, boolean victory) {
            this.score = score;
            this.opponent = opponent;
            this.tournament = tournament;
            this.victory = victory;
        }

        public String getScore() {
            return score;
        }

        public String getOpponent() {
            return opponent;
        }

        public String getTournament() {
            return tournament;
        }

        public boolean isVictory() {
            return victory;
        }

        @Override
        public String toString() {
            return "MatchResult{score='" + score + "', opponent='" + opponent + "', tournament='" + tournament + "', victory=" + victory + "}";
        }
    }

    public static class LiveMatch {
        private final String opponent;
        private final String currentMapScore;
        private final String mapsWon;
        private final String tournament;
        private final String format;
        private final String matchLink;
        private final List<String> vetoDetails;
        private final List<String> streamLinks;

        public LiveMatch(String opponent, String currentMapScore, String mapsWon, String tournament,
                         String format, String matchLink, List<String> vetoDetails, List<String> streamLinks) {
            this.opponent = opponent;
            this.currentMapScore = currentMapScore;
            this.mapsWon = mapsWon;
            this.tournament = tournament;
            this.format = format;
            this.matchLink = matchLink;
            this.vetoDetails = vetoDetails;
            this.streamLinks = streamLinks;
        }

        public String getOpponent() {
            return opponent;
        }

        public String getCurrentMapScore() {
            return currentMapScore;
        }

        public String getMapsWon() {
            return mapsWon;
        }

        public String getTournament() {
            return tournament;
        }

        public String getFormat() {
            return format;
        }

        public String getMatchLink() {
            return matchLink;
        }

        public List<String> getVetoDetails() {
            return vetoDetails;
        }

        public List<String> getStreamLinks() {
            return streamLinks;
        }

        @Override
        public String toString() {
            return "LiveMatch{opponent='" + opponent + "', currentMapScore='" + currentMapScore + "', mapsWon='" + mapsWon +
                   "', tournament='" + tournament + "', format='" + format + "', matchLink='" + matchLink + "'}";
        }
    }
}