FROM confluentinc/cp-base:latest

ARG KMACHINE_VERSION
ARG ARTIFACT_ID

EXPOSE ${REST_APP_PORT}

VOLUME /tmp
ARG JAR_FILE=target/kmachine-rest-app-${KMACHINE_VERSION}.jar
COPY ${JAR_FILE} app.jar
CMD ["/usr/bin/java", "-Dfile.encoding=UTF-8", "-Dserver.port=${REST_APP_PORT}", "-jar", "app.jar"]

