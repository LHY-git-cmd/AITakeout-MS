# 这个 Dockerfile 使用多阶段构建为 sky-server 应用程序创建一个精简且安全的 Docker 镜像。

# --- 构建阶段 ---
# 这个阶段使用 Maven 镜像来构建 Java 应用程序。
FROM maven:3.9.11-eclipse-temurin-21 AS build

# 在容器内设置工作目录。
WORKDIR /workspace

# 首先复制 pom.xml 文件。这利用了 Docker 的层缓存。
# 如果 pom.xml 文件没有改变，Docker 将重用先前构建的缓存层，
# 从而加快构建过程。
COPY pom.xml ./
COPY sky-common/pom.xml sky-common/pom.xml
COPY sky-pojo/pom.xml sky-pojo/pom.xml
COPY sky-server/pom.xml sky-server/pom.xml

# 复制模块的源代码。
COPY sky-common/src sky-common/src
COPY sky-pojo/src sky-pojo/src
COPY sky-server/src sky-server/src

# 使用 Maven 构建应用程序。
# -B: 以非交互（批处理）模式运行。
# -pl sky-server: 只构建 sky-server 模块。
# -am: 同时构建指定模块的依赖项。
# -DskipTests: 在构建过程中跳过运行测试。
# 然后将生成的 JAR 文件复制到 /workspace/app.jar。
RUN mvn -B -pl sky-server -am package -DskipTests \
    && cp sky-server/target/sky-server-*.jar /workspace/app.jar

# --- 最终阶段 ---
# 这个阶段使用一个轻量级的 JRE 镜像来运行应用程序。
FROM eclipse-temurin:21-jre-jammy

# 安装用于健康检查的 curl，并为安全起见创建一个非 root 用户。
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 sky \
    && useradd --system --uid 10001 --gid sky --home-dir /app --shell /usr/sbin/nologin sky

# 为最终镜像设置工作目录。
WORKDIR /app

# 创建一个用于应用程序数据的目录，并将所有权设置为非 root 用户。
RUN mkdir -p /app/data/knowledge && chown -R sky:sky /app/data

# 从构建阶段将应用程序 JAR 复制到最终镜像中。
# --chown=sky:sky 设置复制文件的用户和组。
COPY --from=build --chown=sky:sky /workspace/app.jar /app/app.jar

# 切换到非 root 用户。
USER 10001:10001

# 暴露应用程序运行的端口。
EXPOSE 8080

# 设置 Java VM 选项以控制内存使用。
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0"

# 定义健康检查以确保应用程序正常运行。
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
  CMD curl --fail --silent http://127.0.0.1:8080/actuator/health/readiness || exit 1

# 设置入口点以运行应用程序。
ENTRYPOINT ["java", "-jar", "/app/app.jar"]