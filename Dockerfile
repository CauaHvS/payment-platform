# ===== Estagio 1: build =====
# Imagem com JDK 21 + Maven para compilar o projeto
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Copia o Maven wrapper e o pom primeiro (aproveita cache de camadas)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Baixa as dependencias (camada cacheada enquanto o pom nao mudar)
RUN ./mvnw dependency:go-offline -B

# Copia o codigo-fonte e compila o jar (pulando testes no build da imagem)
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# ===== Estagio 2: runtime =====
# Imagem enxuta (apenas JRE) para rodar a aplicacao
FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

# Copia apenas o jar do estagio de build
COPY --from=build /app/target/payment-platform-0.0.1-SNAPSHOT.jar app.jar

# Porta que a aplicacao expoe
EXPOSE 8080

# Comando de inicializacao
ENTRYPOINT ["java", "-jar", "app.jar"]