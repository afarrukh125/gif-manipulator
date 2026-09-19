FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/giftools.jar ./giftools.jar

# Without ffmpeg the only video the editor can open is MP4 carrying H.264, since nothing in Java decodes VP8 or VP9.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --uid 1001 --create-home giftools
USER giftools

ENV HOST=0.0.0.0 \
    PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.awt.headless=true", "-jar", "/app/giftools.jar"]
CMD ["serve", "--no-open"]
