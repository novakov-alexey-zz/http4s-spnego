FROM adoptopenjdk/openjdk11:debian-slim as stage0
WORKDIR /opt/docker
COPY out/test-server/packageIt/dest /opt/docker
USER root
RUN ["chmod", "-R", "u=rX,g=rX", "/opt/docker"]
RUN ["chmod", "u+x,g+x", "/opt/docker/bin/run"]
USER 1001:0

FROM adoptopenjdk/openjdk11:debian
USER root
RUN id -u demiourgos728 1>/dev/null 2>&1 || (( getent group 0 1>/dev/null 2>&1 || ( type groupadd 1>/dev/null 2>&1 && groupadd -g 0 root || addgroup -g 0 -S root )) && ( type useradd 1>/dev/null 2>&1 && useradd --system --create-home --uid 1001 --gid 0 demiourgos728 || adduser -S -u 1001 -G root demiourgos728 ))
WORKDIR /opt/docker
COPY --from=stage0 --chown=demiourgos728:root /opt/docker /opt/docker
EXPOSE 8080

# Kerberos stuff
COPY test-server/krb5.conf /etc/krb5.conf
RUN apt-get update && apt-get install -y --no-install-recommends krb5-user=1.17-3 && \
    mkdir /krb5 && chmod 755 /krb5 && \
    apt-get clean && rm -rf /tmp/* /var/lib/apt/lists/*
# Kerberos stuff - end

USER 1001:0
ENTRYPOINT ["/opt/docker/bin/run"]
CMD []
