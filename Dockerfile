FROM anapsix/alpine-java:8
COPY build/libs/janini.jar /
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "janini.jar"]
