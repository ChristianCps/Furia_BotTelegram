# Etapa 1: Build da aplicação com Maven e Java 21
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Etapa 2: Imagem final com Java 21, Chrome e ChromeDriver
FROM eclipse-temurin:21-jdk

# Instalar dependências, Google Chrome e ChromeDriver
RUN apt-get update && \
    apt-get install -y unzip wget curl fonts-liberation libappindicator3-1 libasound2 libatk-bridge2.0-0 libatk1.0-0 \
    libc6 libcairo2 libcups2 libdbus-1-3 libexpat1 libfontconfig1 libgbm1 libgcc1 libglib2.0-0 libgtk-3-0 \
    libnspr4 libnss3 libpango-1.0-0 libpangocairo-1.0-0 libstdc++6 libx11-6 libx11-xcb1 libxcb1 libxcomposite1 \
    libxcursor1 libxdamage1 libxext6 libxfixes3 libxi6 libxrandr2 libxrender1 libxss1 libxtst6 xdg-utils && \
    # Instalar Google Chrome
    wget -q https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb && \
    dpkg -i google-chrome-stable_current_amd64.deb || apt-get -f install -y && \
    rm google-chrome-stable_current_amd64.deb && \
    # Instalar ChromeDriver
    CHROMEDRIVER_VERSION=$(curl -sS https://chromedriver.storage.googleapis.com/LATEST_RELEASE) && \
    wget -q https://storage.googleapis.com chrome-for-testing-public/$CHROMEDRIVER_VERSION/linux64/chromedriver-linux64.zip && \
    unzip chromedriver-linux64.zip && \
    mkdir -p /opt/chromedriver && \
    mv chromedriver-linux64/chromedriver /opt/chromedriver/chromedriver && \
    chmod +x /opt/chromedriver/chromedriver && \
    rm -rf chromedriver-linux64* && \
    apt-get remove --purge -y unzip wget curl && \
    apt-get autoremove -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Variável de ambiente para o ChromeDriver
ENV crawler_chromedriver_path=/opt/chromedriver/chromedriver

# Definir diretório da aplicação
WORKDIR /app

# Copiar o JAR construído
COPY --from=build /app/target/furia-bot-0.0.1-SNAPSHOT.jar app.jar

# Expor porta 8080 (opcional)
EXPOSE 8080

# Comando para executar a aplicação
ENTRYPOINT ["java", "-jar", "app.jar"]
