# BizAgent 运行时镜像（需先执行 ./build.sh 或 mvn package 产出 jar）
FROM eclipse-temurin:22-jre

WORKDIR /app

# 运行时目录：主密钥、扩展包、导出文件
RUN mkdir -p /app/data/extensions /app/data/exports /app/data/workspace /app/data/drivers

COPY backend/target/backend-1.0.0.jar /app/app.jar

ENV BIZAGENT_DATA_DIR=/app/data \
    BIZAGENT_EXT_HOME=/app/data/extensions \
    APP_EXPORT_DIR=/app/data/exports \
    BIZAGENT_WORKSPACE_DIR=/app/data/workspace \
    BIZAGENT_DRIVER_DIR=/app/data/drivers \
    JAVA_OPTS="-Xms256m -Xmx1g"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && echo -e "GET /actuator/health HTTP/1.0\r\n\r\n" >&3 && head -c 64 <&3 | grep -q UP'

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
