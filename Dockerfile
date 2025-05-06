# Etapa 1: Build da aplicação
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Etapa 2: Runtime
FROM eclipse-temurin:21-jdk

# Instalar dependências do ChromeDriver
RUN apt-get update && \
    apt-get install -y unzip wget curl && \
    wget -q https://storage.googleapis.com/chrome-for-testing-public/124.0.6367.91/linux64/chromedriver-linux64.zip && \
    unzip chromedriver-linux64.zip && \
    mkdir -p /opt/chromedriver && \
    mv chromedriver-linux64/chromedriver /opt/chromedriver/chromedriver && \
    chmod +x /opt/chromedriver/chromedriver && \
    rm -rf chromedriver-linux64* && \
    apt-get clean

# Definir variável de ambiente com caminho do ChromeDriver
ENV crawler.chromedriver.path=/opt/chromedriver/chromedriver

WORKDIR /app

# Copiar JAR da fase de build
COPY --from=build /app/target/furia-bot-0.0.1-SNAPSHOT.jar app.jar

# Expor porta (caso precise para actuator ou web)
EXPOSE 8080

# Comando para rodar o bot
ENTRYPOINT ["java", "-jar", "app.jar"]
