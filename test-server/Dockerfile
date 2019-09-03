FROM azul/zulu-openjdk-debian:8u222 as stage0
WORKDIR /opt/docker
COPY target/universal/stage /opt/docker
USER root
RUN ["chmod", "-R", "u=rX,g=rX", "/opt/docker"]
RUN ["chmod", "u+x,g+x", "/opt/docker/bin/test-server"]

FROM azul/zulu-openjdk-debian:8u222
USER root
RUN id -u demiourgos728 1>/dev/null 2>&1 || \
    (( getent group 0 1>/dev/null 2>&1 || ( type groupadd 1>/dev/null 2>&1 && groupadd -g 0 root || addgroup -g 0 -S root )) && \
      ( type useradd 1>/dev/null 2>&1 && useradd --system --create-home --uid 1001 --gid 0 demiourgos728 || adduser -S -u 1001 -G root demiourgos728 ))
WORKDIR /opt/docker
COPY --from=stage0 --chown=demiourgos728:root /opt/docker /opt/docker
EXPOSE 8080

# Kerberos stuff
COPY krb5.conf /etc/krb5.conf
RUN apt-get install -y krb5-user && \
    mkdir /krb5 && chmod 755 /krb5 && \
    apt-get clean && rm -rf /tmp/* /var/lib/apt/lists/*
# Kerberos stuff - end

USER 1001:0
ENTRYPOINT ["/opt/docker/bin/test-server"]
CMD []
