# Mall-X 微服务通用镜像：JRE17 运行 Spring Boot fat jar
FROM eclipse-temurin:17-jre

ARG JAR_FILE
WORKDIR /app

COPY ${JAR_FILE} app.jar

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m"

EXPOSE 10010 10020 10030

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
