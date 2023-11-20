FROM gcr.io/distroless/java17-debian12:latest
COPY /build/libs/hm-infotrygd-poller-fat-1.0-SNAPSHOT.jar /app.jar
CMD ["/app.jar"]

