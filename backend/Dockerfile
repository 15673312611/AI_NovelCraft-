FROM openjdk:17-jdk-slim

WORKDIR /app

# 复制Maven配置文件
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

# 下载依赖
RUN ./mvnw dependency:go-offline -B

# 复制源代码
COPY src src

# 构建应用
RUN ./mvnw package -DskipTests

# 运行阶段
FROM openjdk:17-jre-slim

WORKDIR /app

# 复制构建的jar文件
COPY --from=0 /app/target/*.jar app.jar

# 暴露端口
EXPOSE 8080

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"] 