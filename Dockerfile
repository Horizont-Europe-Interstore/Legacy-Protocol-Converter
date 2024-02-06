FROM openjdk:17

WORKDIR /app
#COPY --from=builder /app /app
COPY . .
ENTRYPOINT ["java", "-jar", "transformation-framework/target/transformation-framework-1.0-SNAPSHOT.jar"]
EXPOSE 9094