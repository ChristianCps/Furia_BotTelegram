# Etapa 1: Build da aplicação com Maven e Java 21
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Etapa 2: Imagem final com Java 21 e ChromeDriver
FROM eclipse-temurin:21-jdk

# Instalar dependências e ChromeDriver
RUN apt-get update && \
    apt-get install -y unzip wget curl && \
    wget -q https://storage.googleapis.com/chrome-for-testing-public/124.0.6367.91/linux64/chromedriver-linux64.zip && \
    unzip chromedriver-linux64.zip && \
    mkdir -p /opt/chromedriver && \
    mv chromedriver-linux64/chromedriver /opt/chromedriver/chromedriver && \
    chmod +x /opt/chromedriver/chromedriver && \
    rm -rf chromedriver-linux64* && \
    apt-get remove --purge -y unzip wget curl && \
    apt-get autoremove -y && \
    apt-get clean

# Variável de ambiente acessível via @Value("${crawler.chromedriver.path}")
ENV crawler_chromedriver_path=/opt/chromedriver/chromedriver

# Definir diretório da aplicação
WORKDIR /app

# Copiar o JAR construído
COPY --from=build /app/target/furia-bot-0.0.1-SNAPSHOT.jar app.jar

# Expor porta 8080 (opcional, pode ser ignorado em Worker)
EXPOSE 8080

# Comando que mantém o processo rodando (Worker)
ENTRYPOINT ["java", "-jar", "app.jar"]
