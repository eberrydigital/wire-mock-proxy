# Stage 1: Build
FROM gradle:8.5-jdk21 AS build
WORKDIR /app
COPY . .
# Build the distribution (this creates a script in /app/build/install/app/bin/app)
RUN gradle installDist --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the built application from the build stage
COPY --from=build /app/app/build/install/app /app

# Ensure executable permissions (sometimes lost in copy)
RUN chmod +x /app/bin/app

# Expose ports
# 8080: Proxy
# 8081: API
EXPOSE 8222 8333

# Environment variables (overridable via docker-compose)
ENV AWS_REGION=us-west-2
ENV AWS_ACCESS_KEY_ID=local
ENV AWS_SECRET_ACCESS_KEY=local
ENV DYNAMO_ENDPOINT=http://dynamodb:8000

# Run the application
ENTRYPOINT ["/app/bin/app"]
