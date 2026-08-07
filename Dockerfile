FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
RUN apk add --no-cache curl bash
COPY pom.xml .
COPY mvnw .
COPY .mvn ./.mvn
COPY checkstyle-suppressions.xml .
COPY checkstyle.xml .
COPY src ./src

RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
