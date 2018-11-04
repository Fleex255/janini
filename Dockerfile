FROM anapsix/alpine-java:8_jdk
COPY build/libs/janini.jar /
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "janini.jar"]
