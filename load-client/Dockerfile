FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY LoadClient.java .
RUN javac LoadClient.java

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/LoadClient.class .
CMD ["java", "LoadClient"]
