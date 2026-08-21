# Stage 1: Build the React Frontend
FROM node:20-alpine AS frontend-builder
WORKDIR /app/frontend
# Copy package.json and install dependencies
COPY frontend/package*.json ./
RUN npm install
# Copy the rest of the frontend source and build
COPY frontend/ ./
RUN npm run build

# Stage 2: Build the Spring Boot Backend
FROM maven:3.9-eclipse-temurin-21 AS backend-builder
WORKDIR /app
# Cache maven dependencies
COPY pom.xml ./
RUN mvn dependency:go-offline

# Copy the backend source
COPY src ./src
# Copy the built frontend into the expected directory so Maven can bundle it
COPY --from=frontend-builder /app/frontend/.output/public ./frontend/.output/public

# Build the final JAR
RUN mvn clean package -DskipTests

# Stage 3: Minimal Runtime Environment
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y libstdc++6 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=backend-builder /app/target/nexusai-gateway-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx256m", "-XX:MaxMetaspaceSize=128m", "-Xss512k", "-XX:+UseSerialGC", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
