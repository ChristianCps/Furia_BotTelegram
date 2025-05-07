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
    # Instalar Google Chrome na versão fixa 136.0.7103.92
    wget -q https://dl.google.com/linux/chrome/deb/pool/main/g/google-chrome-stable/google-chrome-stable_136.0.7103.92-1_amd64.deb && \
    apt-get install -y ./google-chrome-stable_136.0.7103.92-1_amd64.deb && \
    rm google-chrome-stable_136.0.7103.92-1_amd64.deb && \
    # Instalar ChromeDriver na versão fixa 136.0.7103.92
    wget -q https://storage.googleapis.com/chrome-for-testing-public/136.0.7103.92/linux64/chromedriver-linux64.zip && \
    unzip chromedriver-linux64.zip && \
    mv chromedriver-linux64/chromedriver /usr/bin/chromedriver && \
    chmod +x /usr/bin/chromedriver && \
    rm -rf chromedriver-linux64* && \
    # Verificar versões instaladas
    google-chrome --version && \
    /usr/bin/chromedriver --version && \
    apt-get remove --purge -y unzip wget curl && \
    apt-get autoremove -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Copiar o artefato construído
COPY --from=builder /app/target/furia-bot-0.0.1-SNAPSHOT.jar /app/furia-bot.jar

# Definir variável de ambiente para o ChromeDriver (opcional, caso seu código use)
ENV crawler_chromedriver_path=/usr/bin/chromedriver

# Expor a porta 8080
EXPOSE 8080

# Comando de inicialização
CMD ["java", "-jar", "/app/furia-bot.jar"]
