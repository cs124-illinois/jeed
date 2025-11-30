FROM eclipse-temurin:21-jdk-jammy

ARG TARGETARCH
ENV TINI_VERSION=v0.19.0
ADD https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini-${TARGETARCH} /tini
RUN chmod +x /tini
ENTRYPOINT ["/tini", "--"]

RUN apt update && apt install -y procps libjemalloc2 && rm -rf /var/lib/apt/lists/*
RUN cp /usr/lib/`arch`-linux-gnu/libjemalloc.so.2 /usr/lib/
ENV LD_PRELOAD=/usr/lib/libjemalloc.so.2

WORKDIR /
COPY *.jar jeed.jar
SHELL ["/bin/sh", "-c"]
CMD java -ea --enable-preview -Dfile.encoding=UTF-8 \
    -Djava.security.manager=allow -XX:-OmitStackTraceInFastThrow \
    -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations \
    -XX:NativeMemoryTracking=summary \
    -Xss256k -XX:+UseZGC -XX:+ZGenerational \
    --add-opens java.base/java.lang=ALL-UNNAMED \
    --add-opens java.base/java.util=ALL-UNNAMED \
    --add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED \
    --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
    --add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
    --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
    --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED \
    --add-exports java.management/sun.management=ALL-UNNAMED \
    $JAVA_OPTS \
    -jar jeed.jar

# vim: tw=0
