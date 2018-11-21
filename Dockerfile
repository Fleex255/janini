FROM openjdk:10.0.2-13-jdk-slim
COPY build/libs/janini.jar /
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "janini.jar"]
