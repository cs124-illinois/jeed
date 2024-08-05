FROM openjdk:21-jdk-slim

# Not needed unless supporting containerized execution
#
# RUN apt update && \
#     apt install -y --no-install-recommends ca-certificates curl && \
#     install -m 0755 -d /etc/apt/keyrings && \
#     curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc && \
#     chmod a+r /etc/apt/keyrings/docker.asc && \
#     echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null && \
#     apt update && \
#     apt install -y --no-install-recommends docker-ce-cli

ARG TARGETARCH
ENV TINI_VERSION=v0.19.0
ADD https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini-${TARGETARCH} /tini
RUN chmod +x /tini
ENTRYPOINT ["/tini", "--"]

WORKDIR /
COPY *.jar jeed.jar
CMD ["java", "-ea", "--enable-preview", "-Dfile.encoding=UTF-8", \
    "-Djava.security.manager=allow", "-Xss256k", "-XX:+UseZGC", \
    "-XX:ZCollectionInterval=8", "-XX:-OmitStackTraceInFastThrow", \
    "-XX:+UnlockExperimentalVMOptions", "-XX:-VMContinuations", \
    "--add-opens", "java.base/java.lang=ALL-UNNAMED", \
    "--add-opens", "java.base/java.util=ALL-UNNAMED", \
    "--add-exports", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED", \
    "--add-exports", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED", \
    "--add-exports", "jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED", \
    "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED", \
    "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED", \
    "--add-exports", "java.management/sun.management=ALL-UNNAMED", \
    "$JAVA_OPTS", "-jar", "jeed.jar"]

# vim: tw=0
