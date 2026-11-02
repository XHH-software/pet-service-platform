# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-11 AS build
WORKDIR /build

# 先只拷 pom，把依赖层缓存下来，改代码时不用重新拉包
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- 运行阶段 ----
FROM eclipse-temurin:11-jre-jammy
WORKDIR /app

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m"

COPY --from=build /build/target/*.jar /app/app.jar
RUN mkdir -p /app/uploads

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
