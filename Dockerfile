FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
COPY application-container.yml /workspace/src/main/resources/application.yml
RUN mvn -B -ntp package

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
ENV PORT=10000 \
    SERVER_PORT=8080 \
    SOCKET_PORT=5001 \
    SERVER_ADDRESS=127.0.0.1 \
    SOCKET_HOST=127.0.0.1

RUN apt-get update \
    && apt-get install -y --no-install-recommends nginx supervisor gettext-base curl \
    && rm -rf /var/lib/apt/lists/* \
    && rm -f /etc/nginx/sites-enabled/default \
    && mkdir -p /app/uploads /tmp/nginx/client_temp /tmp/nginx/proxy_temp \
       /tmp/nginx/fastcgi_temp /tmp/nginx/uwsgi_temp /tmp/nginx/scgi_temp \
    && chown -R 10001:10001 /app /tmp/nginx /var/log/nginx /var/lib/nginx

COPY --from=build --chown=10001:10001 /workspace/target/findora-backend-1.0.0.jar /app/app.jar
COPY nginx.conf.template /app/nginx.conf.template
COPY supervisord.conf /app/supervisord.conf
COPY start-container.sh /app/start-container.sh
RUN chmod 0555 /app/start-container.sh \
    && chown 10001:10001 /app/nginx.conf.template /app/supervisord.conf /app/start-container.sh

USER 10001:10001
EXPOSE 10000

HEALTHCHECK --interval=10s --timeout=5s --start-period=150s --retries=3 \
    CMD curl --fail --silent http://127.0.0.1:10000/api/health || exit 1

ENTRYPOINT ["/app/start-container.sh"]
