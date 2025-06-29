# 基础镜像，使用官方 OpenJDK 21 运行时
FROM eclipse-temurin:21-jre

# 作者信息（可选）
LABEL maintainer="your-email@example.com"

# 创建工作目录
WORKDIR /app

# 复制 jar 包到容器中（假设 jar 包名为 api-gateway.jar，可根据实际情况修改）
COPY build/libs/*.jar api-gateway.jar

# 暴露 Spring Boot 默认端口
EXPOSE 8080

# 启动应用，加入指定 JVM 参数
ENTRYPOINT ["java", \
    "-XX:+UseG1GC", \
    "-XX:MaxGCPauseMillis=100", \
    "-XX:G1NewSizePercent=60", \
    "-XX:G1MaxNewSizePercent=80", \
    "-XX:NewRatio=1", \
    "-XX:SurvivorRatio=8", \
    "-XX:ParallelGCThreads=2", \
    "-XX:ConcGCThreads=1", \
    "-XX:+AlwaysPreTouch", \
    "-XX:+UseStringDeduplication", \
    "-Xms4g", \
    "-Xmx5g", \
    "-jar", "api-gateway.jar"] 