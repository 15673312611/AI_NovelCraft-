FROM maven:3.9.6-eclipse-temurin-17-alpine AS build

WORKDIR /app

# Copy Maven descriptor to leverage Docker layer cache
COPY pom.xml .

# Pre-download dependencies
RUN mvn dependency:go-offline -B

# Copy application sources
COPY src src

# Build the application
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the built jar from the previous stage
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
