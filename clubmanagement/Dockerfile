# Stage 1: Build the Maven project
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml and dependencies first to leverage Docker cache
COPY pom.xml .
COPY src ./src

# Package the application without running tests
RUN mvn clean package -DskipTests

# Stage 2: Create lightweight runtime image
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy built jar from build stage
COPY --from=build /app/target/clubmanagement-0.0.1-SNAPSHOT.jar app.jar

# Expose port (Render uses $PORT dynamically)
EXPOSE 8080

# Run the spring boot app
ENTRYPOINT ["java", "-jar", "app.jar"]
