# Imagen de runtime: parte de un jar ya compilado (mvn package). Solo necesita el JRE.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java","-jar","app.jar"]
