FROM gradle:8.7-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle shadowJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/ktor-mdeditor-all.jar app.jar
RUN mkdir -p uploads
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
