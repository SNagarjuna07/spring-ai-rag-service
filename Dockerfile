# ==========================
# Build Stage
# ==========================
FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /app

# Install curl (optional, but useful for some Maven plugins)
RUN apk add --no-cache curl

# Copy Maven wrapper and project metadata
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Make wrapper executable
RUN chmod +x mvnw

# Download dependencies first (better layer caching)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src/ src/

# Build the application
RUN ./mvnw clean package -DskipTests -B

# ==========================
# Runtime Stage
# ==========================
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Copy the generated JAR from the build stage
COPY --from=build /app/target/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]