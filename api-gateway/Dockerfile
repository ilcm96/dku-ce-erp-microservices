FROM gradle:8-jdk21-ubi-minimal AS builder

WORKDIR /workspace

COPY . .

RUN --mount=type=cache,target=/home/gradle/.gradle/caches,id=gradle-cache,sharing=locked \
    --mount=type=cache,target=/home/gradle/.gradle/wrapper,id=gradle-wrapper,sharing=locked \
    gradle :api-gateway:bootJar --no-daemon -x check -x test

FROM eclipse-temurin:21-jre-ubi10-minimal

WORKDIR /app

COPY --from=builder /workspace/api-gateway/build/libs/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
