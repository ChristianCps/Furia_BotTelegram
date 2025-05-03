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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private WebDriver driver;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_TEAM_INFO = TimeUnit.HOURS.toMillis(1); // 1 hora
    private static final long CACHE_TTL_MATCHES = TimeUnit.MINUTES.toMillis(10); // 10 minutos

    private static class CacheEntry {
        private final Document document;
        private final long timestamp;

        public CacheEntry(Document document) {
            this.document = document;
            this.timestamp = System.currentTimeMillis();
        }

        public Document getDocument() {
            return document;
        }

        public boolean isExpired(long ttl) {
            return System.currentTimeMillis() - timestamp > ttl;
        }
    }

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
        initializeWebDriver();
        crawlHltv();
    }

    @PreDestroy
    public void destroy() {
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception e) {
                logger.warn("Erro ao fechar o WebDriver: {}", e.getMessage());
            }
        }
    }

    private synchronized void initializeWebDriver() {
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception e) {
                logger.warn("Erro ao fechar WebDriver antigo: {}", e.getMessage());
            } finally {
                driver = null; // Garante que a referência seja limpa
            }
        }
        try {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--disable-gpu");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36");
            options.addArguments("--blink-settings=imagesEnabled=false");
            options.addArguments("--disable-extensions");
            options.addArguments("--disable-notifications");
            options.addArguments("--disable-popup-blocking");
            options.addArguments("--window-size=1280,720");
            driver = new ChromeDriver(options);
            logger.info("WebDriver inicializado com sucesso.");
            System.gc(); // Forçar garbage collection após inicialização
        } catch (Exception e) {
            logger.error("Erro ao inicializar WebDriver: {}", e.getMessage(), e);
            driver = null;
        }
    }

    @Scheduled(cron = "0 0 4 * * ?", zone = "America/Sao_Paulo")
    public void restartWebDriverDaily() {
        logger.info("Reiniciando WebDriver às 4h (horário de Brasília).");
        initializeWebDriver();
    }

    @Scheduled(fixedRate = 3600000) // A cada hora
    public void cleanCache() {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired(CACHE_TTL_TEAM_INFO) || 
                                          entry.getValue().isExpired(CACHE_TTL_MATCHES));
        logger.info("Cache limpo, tamanho atual: {}", cache.size());
        System.gc(); // Forçar garbage collection
    }    

    @Scheduled(fixedRate = 420000) // A cada 7 minutos
    public void logMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        logger.info("Memória usada: {} MB / Máximo: {} MB", usedMemory, maxMemory);
        System.gc(); // Forçar garbage collection para testar
    }

    @Scheduled(fixedRateString = "${crawlInterval}")
    public void crawlHltv() {
        try {
            crawlTeamInfo();
            crawlMatches();
            // Verifica partidas ao vivo apenas se não há partida em andamento
            if (liveMatch.get() == null) {
                crawlLiveMatch();
            }
        } catch (Exception e) {
            logger.error("Erro ao executar crawlHltv: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedRate = 180000) // A cada 3 minutos
    public void checkLiveMatch() {
        try {
            if (liveMatch.get() != null) {
                // Partida ao vivo em andamento, atualizar
                updateLiveMatch(null);
                logger.info("Partida ao vivo em andamento, próxima verificação em 3 minutos.");
            }
        } catch (Exception e) {
            logger.error("Erro ao executar checkLiveMatch: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedRate = 600000) // A cada 20 minutos
    public void checkPotentialLiveMatch() {
        try {
            if (liveMatch.get() == null && hasMatchToday()) {
                // Partida programada para hoje, verificar se está ao vivo
                if (shouldCheckLiveMatch()) {
                    updateLiveMatch(null);
                } else {
                    logger.info("Nenhuma partida ao vivo detectada, próxima verificação em 20 minutos.");
                }
            }
        } catch (Exception e) {
            logger.error("Erro ao executar checkPotentialLiveMatch: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedRateString = "${crawlInterval}")
    public void checkNoScheduledMatches() {
        try {
            if (liveMatch.get() == null && !hasMatchToday()) {
                logger.info("Nenhum jogo programado para hoje, próxima verificação em {} minutos.", crawlInterval / 1000 / 60);
                // Evitar verificar partidas ao vivo se não há jogos próximos
                LocalDate tomorrow = LocalDate.now().plusDays(1);
                boolean hasMatchSoon = upcomingMatches.get().stream()
                    .anyMatch(match -> {
                        try {
                            if ("Hoje".equals(match.getDate()) || "TBA".equals(match.getDate())) {
                                return true;
                            }
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yy");
                            LocalDate matchDate = LocalDate.parse(match.getDate(), formatter);
                            return matchDate.equals(LocalDate.now()) || matchDate.equals(tomorrow);
                        } catch (Exception e) {
                            return false;
                        }
                    });
                if (!hasMatchSoon) {
                    logger.info("Nenhum jogo próximo, pulando verificação de partidas ao vivo.");
                    return;
                }
                updateLiveMatch(null);
            }
        } catch (Exception e) {
            logger.error("Erro ao executar checkNoScheduledMatches: {}", e.getMessage(), e);
        }
    }

    private boolean hasMatchToday() {
        List<Match> matches = upcomingMatches.get();
        LocalDate today = LocalDate.now();
        for (Match match : matches) {
            if ("Hoje".equals(match.getDate())) {
                return true;
            }
            try {
                String dateStr = match.getDate();
                if (!dateStr.isEmpty() && !"TBA".equals(dateStr)) {
                    LocalDate matchDate = null;
                    if (dateStr.matches("\\d{1,2}/\\d{1,2}/\\d{2}")) {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yy");
                        matchDate = LocalDate.parse(dateStr, formatter);
                    } else {
                        String url = String.format("https://www.hltv.org/team/%s/%s#tab-matchesBox", teamCode, teamName);
                        CacheEntry cached = cache.get(url);
                        Document doc = null;
                        if (cached != null && !cached.isExpired(CACHE_TTL_MATCHES)) {
                            doc = cached.getDocument();
                        } else {
                            doc = fetchDocumentWithSelenium(url, ".table-container.match-table");
                            if (doc != null) {
                                cache.put(url, new CacheEntry(doc));
                            }
                        }
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
        try {
            String url = String.format("https://www.hltv.org/team/%s/%s#tab-infoBox", teamCode, teamName);
            logger.info("Acessando URL (infoBox): {}", url);

            CacheEntry cached = cache.get(url);
            if (cached != null && !cached.isExpired(CACHE_TTL_TEAM_INFO)) {
                logger.info("Usando cache para URL: {}", url);
                updateTeamLineup(cached.getDocument());
                return;
            }

            Document doc = fetchDocumentWithSelenium(url, "div.bodyshot-team.g-grid");
            if (doc != null) {
                cache.put(url, new CacheEntry(doc));
                updateTeamLineup(doc);
            } else {
                logger.error("Falha ao obter documento para URL: {}", url);
            }
        } catch (Exception e) {
            logger.error("Erro ao executar crawlTeamInfo: {}", e.getMessage(), e);
        }
    }

    private void crawlMatches() {
        try {
            String url = String.format("https://www.hltv.org/team/%s/%s#tab-matchesBox", teamCode, teamName);
            logger.info("Acessando URL (matchesBox): {}", url);

            CacheEntry cached = cache.get(url);
            if (cached != null && !cached.isExpired(CACHE_TTL_MATCHES)) {
                logger.info("Usando cache para URL: {}", url);
                updateUpcomingMatches(cached.getDocument());
                updateLastResults(cached.getDocument());
                return;
            }

            Document doc = fetchDocumentWithSelenium(url, ".table-container.match-table");
            if (doc != null) {
                cache.put(url, new CacheEntry(doc));
                updateUpcomingMatches(doc);
                updateLastResults(doc);
            } else {
                logger.error("Falha ao obter documento para URL: {}", url);
            }
        } catch (Exception e) {
            logger.error("Erro ao executar crawlMatches: {}", e.getMessage(), e);
        }
    }

    private Document fetchDocumentWithSelenium(String url, String waitForSelector) {
        int maxRetries = 2;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                if (driver == null) {
                    initializeWebDriver();
                    if (driver == null) {
                        logger.error("WebDriver não inicializado após tentativa.");
                        return null;
                    }
                }
                logger.info("Selenium acessando URL: {}", url);
                driver.get(url);
    
                int timeoutSeconds = url.contains("/matches") ? 15 : 10; // Aumentar para 15s em /matches
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
                wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(waitForSelector)));
    
                String pageSource = driver.getPageSource();
                logger.info("Acessado HLTV com sucesso via Selenium: {}", url);
    
                Document doc = Jsoup.parse(pageSource);
                System.gc(); // Forçar garbage collection após parsing
                return doc;
            } catch (Exception e) {
                logger.error("Erro ao acessar HLTV com Selenium (tentativa {}/{}): {}", attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) {
                    initializeWebDriver();
                }
            }
        }
        logger.error("Falha após {} tentativas para URL: {}", maxRetries, url);
        return null;
    }

    private void crawlLiveMatch() {
        try {
            updateLiveMatch(null);
        } catch (Exception e) {
            logger.error("Erro ao executar crawlLiveMatch: {}", e.getMessage(), e);
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
            logger.warn("Nenhuma tabela '.table-container.match-table' encontrada, usando fallback 'table.match-table'");
        }
    
        for (Element table : tables) {
            Elements headers = table.select("thead");
            Elements bodies = table.select("tbody");
    
            String currentTournament = "Desconhecido";
            int bodyIndex = 0;
    
            for (int i = 0; i < headers.size() && bodyIndex < bodies.size(); i++) {
                Element header = headers.get(i);
                // Buscar o torneio no cabeçalho do evento
                Element tournamentElement = header.select("tr.event-header-cell th.text-ellipsis a").first();
                if (tournamentElement != null) {
                    currentTournament = tournamentElement.text();
                    logger.info("Torneio detectado: {}", currentTournament);
    
                    // Processar o <tbody> correspondente ao torneio
                    if (bodyIndex < bodies.size()) {
                        Element body = bodies.get(bodyIndex);
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
                                    // Priorizar data-unix para determinar data e hora
                                    String unixTime = match.select("td.date-cell span").attr("data-unix");
                                    if (unixTime.isEmpty()) {
                                        unixTime = match.select("td.date-cell").attr("data-unix");
                                    }
                                    if (!unixTime.isEmpty()) {
                                        try {
                                            long unixMillis = Long.parseLong(unixTime);
                                            Instant instant = Instant.ofEpochMilli(unixMillis);
                                            LocalDateTime matchDateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
                                            LocalDate matchDate = matchDateTime.toLocalDate();
                                            time = matchDateTime.format(DateTimeFormatter.ofPattern("HH:mm"));
    
                                            LocalDate today = LocalDate.now();
                                            LocalDate tomorrow = today.plusDays(1);
    
                                            if (matchDate.equals(today)) {
                                                date = "Hoje";
                                            } else if (matchDate.equals(tomorrow)) {
                                                date = "Amanhã";
                                            } else {
                                                date = matchDate.format(DateTimeFormatter.ofPattern("dd/MM/yy"));
                                            }
                                        } catch (NumberFormatException e) {
                                            logger.warn("Erro ao converter unixTime: {}", unixTime);
                                            time = "TBA";
                                        }
                                    } else if (dateTime.matches("\\d{2}:\\d{2}")) {
                                        // Fallback para HH:mm, assumindo que é hoje
                                        date = "Hoje";
                                        time = dateTime;
                                        logger.info("Usando fallback HH:mm para partida: date={}, time={}", date, time);
                                    } else {
                                        logger.warn("Nenhum data-unix ou HH:mm válido encontrado: dateTime={}", dateTime);
                                    }
    
                                    if (!date.isEmpty() && !opponent.isEmpty() && !currentTournament.isEmpty()) {
                                        newMatches.add(new Match(date, time, opponent, currentTournament));
                                        logger.info("Partida adicionada: {} vs {} - {} ({}, {})", opponent, score, currentTournament, date, time);
                                    } else {
                                        logger.warn("Partida ignorada devido a dados incompletos: date={}, opponent={}, tournament={}", date, opponent, currentTournament);
                                    }
                                }
                            }
                            if (newMatches.size() >= 5) {
                                break;
                            }
                        }
                        bodyIndex++; // Avançar para o próximo <tbody>
                    }
                } else {
                    logger.info("Cabeçalho sem torneio, pulando: {}", header.outerHtml());
                    // Não avançar bodyIndex, pois este <thead> não tem torneio associado
                }
                if (newMatches.size() >= 5) {
                    break;
                }
            }
            if (newMatches.size() >= 5) {
                break;
            }
        }
        upcomingMatches.set(newMatches);
        logger.info("Total de partidas futuras coletadas: {}", newMatches.size());
    }

    private void updateLastResults(Document doc) {
        List<MatchResult> newResults = new ArrayList<>();
        Elements tables = doc.select(".table-container.match-table");
    
        if (tables.isEmpty()) {
            tables = doc.select("table.match-table");
            logger.warn("Nenhuma tabela '.table-container.match-table' encontrada, usando fallback 'table.match-table'");
        }
    
        int resultCount = 0;
    
        for (Element table : tables) {
            Elements headers = table.select("thead");
            Elements bodies = table.select("tbody");
            
            String currentTournament = "Desconhecido";
            int bodyIndex = 0;
    
            for (int i = 0; i < headers.size() && bodyIndex < bodies.size(); i++) {
                Element header = headers.get(i);
                // Buscar o torneio no cabeçalho do evento
                Element tournamentElement = header.select("tr.event-header-cell th.text-ellipsis a").first();
                if (tournamentElement != null) {
                    currentTournament = tournamentElement.text();
                    logger.info("Torneio detectado: {}", currentTournament);
                    
                    // Processar o <tbody> correspondente ao torneio
                    if (bodyIndex < bodies.size()) {
                        Element body = bodies.get(bodyIndex);
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
                                    if (!opponent.isEmpty()) {
                                        newResults.add(new MatchResult(score, opponent, currentTournament, isVictory));
                                        resultCount++;
                                        logger.info("Resultado adicionado: {} vs {} - {} ({})", opponent, score, currentTournament, isVictory ? "Vitória" : "Derrota");
                                    } else {
                                        logger.warn("Resultado ignorado: score={}, opponent vazio, tournament={}", score, currentTournament);
                                    }
                                }
                            }
                            if (resultCount >= 3) {
                                break;
                            }
                        }
                        bodyIndex++; // Avançar para o próximo <tbody>
                    }
                } else {
                    logger.info("Cabeçalho sem torneio, pulando: {}", header.outerHtml());
                    // Não avançar bodyIndex, pois este <thead> não tem torneio associado
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
        logger.info("Total de resultados coletados: {}", newResults.size());
    }

    private void updateLiveMatch(Document doc) {
        try {
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
                    String opponentId = teamCode.equals(team1Id) ? team2Id : team1Id;
                    String opponent = match.select(teamCode.equals(team1Id) ? "div.match-team:nth-child(2) .match-teamname" : "div.match-team:nth-child(1) .match-teamname").text();
                    String currentMapScore = match.select("span.current-map-score[data-livescore-team='" + teamCode + "']").text() + "-" + match.select("span.current-map-score[data-livescore-team='" + opponentId + "']").text();
                    String mapsWon = match.select("span[data-livescore-maps-won-for][data-livescore-team='" + teamCode + "']").text() + "-" + match.select("span[data-livescore-maps-won-for][data-livescore-team='" + opponentId + "']").text();
                    System.out.println("|||||||||||||||||||||||||||||||PONTOS: "+currentMapScore + "MAPS: "+mapsWon);
                    String tournament = match.select("div.match-event.text-ellipsis").text();
                    String matchLink = "https://www.hltv.org" + match.select("a.match-top").attr("href");

                    Document matchDoc = fetchDocumentWithSelenium(matchLink, ".standard-box.veto-box");
                    if (matchDoc == null) {
                        logger.warn("Falha ao obter página da partida: {}", matchLink);
                        continue;
                    }

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

                    List<String> vetoDetails = new ArrayList<>();
                    Elements vetoItems = matchDoc.select("div.standard-box.veto-box .padding div");
                    for (Element vetoItem : vetoItems) {
                        vetoDetails.add(vetoItem.text());
                    }

                    List<String> streamLinks = new ArrayList<>();
                    Elements streamElements = matchDoc.select("div.stream-box");
                    List<Element> sortedStreams = new ArrayList<>(streamElements);
                    sortedStreams.sort((a, b) -> {
                        String viewersA = a.select("span.viewers").text().replaceAll("[^0-9]", "");
                        String viewersB = b.select("span.viewers").text().replaceAll("[^0-9]", "");
                        int countA = viewersA.isEmpty() ? 0 : Integer.parseInt(viewersA);
                        int countB = viewersB.isEmpty() ? 0 : Integer.parseInt(viewersB);
                        return Integer.compare(countB, countA);
                    });

                    for (int i = 0; i < Math.min(3, sortedStreams.size()); i++) {
                        Element stream = sortedStreams.get(i);
                        String streamUrl = stream.select("a[href]").attr("href");
                        if (!streamUrl.isEmpty()) {
                            streamLinks.add(streamUrl);
                        }
                    }

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
                    break;
                }
            }

            if (!matchFound) {
                liveMatch.set(null);
                logger.info("Nenhuma partida ao vivo encontrada para o time: {}", teamName);
            } else {
                liveMatch.set(newLiveMatch);
                logger.info("Partida ao vivo atualizada: {}", newLiveMatch);
            }
        } catch (Exception e) {
            logger.error("Erro ao executar updateLiveMatch: {}", e.getMessage(), e);
            liveMatch.set(null);
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