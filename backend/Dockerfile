FROM eclipse-temurin:17-jre-jammy
RUN groupadd -g 10001 reader && useradd -u 10001 -g reader reader
WORKDIR /app
COPY build/server.jar server.jar
COPY build/lib/ lib/
COPY build/native/ native/
COPY assets/ assets/
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-Xms24m -Xmx128m -XX:MaxMetaspaceSize=64m -XX:ReservedCodeCacheSize=32m -XX:+UseSerialGC -Dorg.sqlite.lib.path=/app/native"
CMD ["java","-cp","server.jar:lib/*","com.example.yuejie.Server"]
