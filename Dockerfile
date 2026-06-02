FROM eclipse-temurin:17-jre

WORKDIR /app

COPY target/order-service-1.0.0.jar app.jar

EXPOSE 8086

ENTRYPOINT ["java", "-jar", "app.jar"]