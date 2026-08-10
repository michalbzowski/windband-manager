FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
RUN apk add --no-cache curl bash
# Instalacja fontconfig oraz czcionek TrueType (w tym Arial, Times New Roman itp.)
RUN apk add --no-cache fontconfig msttcorefonts-installer \
    && update-ms-fonts \
    && fc-cache -f
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
