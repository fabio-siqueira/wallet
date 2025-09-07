# Use a imagem oficial do Maven com Eclipse Temurin JDK 21
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

# Definir diretório de trabalho
WORKDIR /app

# Copiar arquivos de configuração do Maven primeiro (para cache de dependências)
COPY pom.xml .

# Baixar dependências (será cachado se o pom.xml não mudar)
RUN mvn dependency:go-offline -B

# Copiar código fonte
COPY src ./src

# Compilar a aplicação
RUN mvn clean package -DskipTests

# Segunda etapa - imagem de runtime
FROM eclipse-temurin:21-jre-alpine

# Instalar curl para health checks
RUN apk add --no-cache curl

# Criar usuário não-root para segurança
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Definir diretório de trabalho
WORKDIR /app

# Copiar o JAR da etapa de build
COPY --from=builder /app/target/*.jar app.jar

# Mudar ownership para o usuário não-root
RUN chown -R appuser:appgroup /app

# Mudar para usuário não-root
USER appuser

# Expor a porta da aplicação
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Comando para executar a aplicação
ENTRYPOINT ["java", "-jar", "app.jar"]