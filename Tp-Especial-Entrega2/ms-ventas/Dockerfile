# Imagen de runtime: parte de un jar ya compilado (mvn package). Solo necesita el JRE.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java","-jar","app.jar"]
