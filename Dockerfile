# Etapa de construção
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Etapa de execução
FROM eclipse-temurin:21-jdk
WORKDIR /app

# Instalar dependências, Google Chrome e ChromeDriver
RUN apt-get update && \
    apt-get install -y unzip wget curl fonts-liberation libappindicator3-1 libasound2t64 libatk-bridge2.0-0 libatk1.0-0 \
    libc6 libcairo2 libcups2 libdbus-1-3 libexpat1 libfontconfig1 libgbm1 libgcc1 libglib2.0-0 libgtk-3-0 \
    libnspr4 libnss3 libpango-1.0-0 libpangocairo-1.0-0 libstdc++6 libx11-6 libx11-xcb1 libxcb1 libxcomposite1 \
    libxcursor1 libxdamage1 libxext6 libxfixes3 libxi6 libxrandr2 libxrender1 libxss1 libxtst6 xdg-utils && \
    # Instalar Google Chrome
    wget https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb && \
    apt-get install -y ./google-chrome-stable_current_amd64.deb && \
    rm google-chrome-stable_current_amd64.deb && \
    # Obter a versão do Chrome instalada
    CHROME_VERSION=$(google-chrome --version | grep -oP '\d+\.\d+\.\d+') && \
    # Tentar obter a versão do ChromeDriver compatível
    CHROMEDRIVER_VERSION=$(curl -sS https://chromedriver.storage.googleapis.com/LATEST_RELEASE_${CHROME_VERSION} | grep -oP '^\d+\.\d+\.\d+\.\d+$' || echo "") && \
    if [ -z "$CHROMEDRIVER_VERSION" ]; then \
        echo "Versão do ChromeDriver não encontrada, usando versão fixa compatível"; \
        CHROMEDRIVER_VERSION="136.0.7103.92"; \
    fi && \
    wget https://storage.googleapis.com/chrome-for-testing-public/${CHROMEDRIVER_VERSION}/linux64/chromedriver-linux64.zip && \
    unzip chromedriver-linux64.zip && \
    mkdir -p /opt/chromedriver && \
    mv chromedriver-linux64/chromedriver /opt/chromedriver/chromedriver && \
    chmod +x /opt/chromedriver/chromedriver && \
    rm -rf chromedriver-linux64* && \
    apt-get remove --purge -y unzip wget curl && \
    apt-get autoremove -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Copiar o artefato construído
COPY --from=builder /app/target/furia-bot-0.0.1-SNAPSHOT.jar /app/furia-bot.jar

# Definir variável de ambiente para o ChromeDriver
ENV crawler_chromedriver_path=/opt/chromedriver/chromedriver

# Comando de inicialização
CMD ["java", "-jar", "/app/furia-bot.jar"]
