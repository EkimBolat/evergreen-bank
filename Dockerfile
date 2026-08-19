# 1. Aşama: Uygulamayı derle (build)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# 2. Aşama: Sadece çalıştırılabilir jar'ı al, hafif bir image'e koy
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache tzdata
ENV TZ=Europe/Istanbul
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]