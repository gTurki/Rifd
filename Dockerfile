# ---------- build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 1) dependencies only: this layer is reused from cache as long as pom.xml does not change
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# 2) the code: only this part rebuilds when you change something in src
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- run ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
