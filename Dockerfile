FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /tmp
COPY ./pom.xml ./pom.xml
COPY ./src ./src
RUN mvn install

FROM eclipse-temurin:23-alpine
WORKDIR /opt/lbry/globe/
EXPOSE 25/tcp
EXPOSE 465/tcp
EXPOSE 587/tcp
COPY --from=build /tmp/target/lbry-globe-latest-jar-with-dependencies.jar ./lbry-globe.jar
CMD ["java","-jar","lbry-globe.jar"]