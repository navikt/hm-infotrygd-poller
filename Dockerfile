FROM gcr.io/distroless/java25-debian13:nonroot
COPY build/libs/hm-infotrygd-poller-all.jar /app.jar
ENV TZ="Europe/Oslo"
EXPOSE 8080
CMD ["/app.jar"]
