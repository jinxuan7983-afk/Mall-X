# Mall-X 微服务通用镜像：JRE17 运行 Spring Boot fat jar
FROM eclipse-temurin:17-jre

ARG JAR_FILE
WORKDIR /app

COPY ${JAR_FILE} app.jar

# 上传目录（商品图片/退款凭证）：镜像内预建，避免容器首次上传时目录不存在而失败；
# 运行时可由 docker-compose 挂载宿主机目录覆盖，以实现持久化。
RUN mkdir -p /app/uploads

# preferIPv4Stack：host.docker.internal 在容器内同时存在 IPv6 记录，强制 IPv4
# 避免 RocketMQ 客户端解析到不可达的 IPv6 网关地址
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m -Djava.net.preferIPv4Stack=true"

EXPOSE 10010 10020 10030

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
