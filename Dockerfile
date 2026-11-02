FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -B package
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --gid 10001 banking && useradd --uid 10001 --gid banking --no-create-home banking
COPY --from=build /app/target/banking-platform-*.jar app.jar
USER banking
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
