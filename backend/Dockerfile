FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
ENV MAVEN_OPTS="-Xms32m -Xmx256m -XX:ActiveProcessorCount=2"
RUN mvn -B package
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && mkdir /app/uploads && chown 10001:10001 /app/uploads
COPY --from=build /app/target/reconflow-0.1.0.jar app.jar
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx384m -XX:ActiveProcessorCount=2"
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
