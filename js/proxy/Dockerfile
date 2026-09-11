# Pinned deliberately, so bump the patch with each release: a pinned tag never picks up a fixed
# node on rebuild. The apk upgrade below covers the OS packages either way.
FROM node:24.21.0-alpine3.24

# apk upgrade picks up OS package fixes published since the base image was cut -- the
# base is always at least a little older than the newest openssl/musl. docker:push builds
# with --pull --no-cache, so this layer is never reused from an earlier release.
# entr ships in the stable community repository as of Alpine 3.24, so there is no
# longer any need to pull it from edge.
RUN apk upgrade --no-cache && apk add --no-cache tini entr
ENV ENTR_INOTIFY_WORKAROUND=1

# This image only ever runs the pre-bundled server: esbuild inlines every runtime
# dependency into bundled/index.js, so the npm, npx and yarn CLIs that ship in the
# base image are dead weight. Dropping them also drops the dependencies vendored
# inside them, which is where every npm-ecosystem CVE reported against this image
# came from -- none of them were ever reachable from our own code.
RUN rm -rf /usr/local/lib/node_modules/npm \
           /usr/local/lib/node_modules/corepack \
           /usr/local/bin/npm \
           /usr/local/bin/npx \
           /usr/local/bin/corepack \
           /usr/local/bin/yarn \
           /usr/local/bin/yarnpkg \
           /opt/yarn-v*

WORKDIR /server/
COPY bundled bundled

ENTRYPOINT ["/sbin/tini", "--"]
CMD [ "sh", "-c", "echo /server/bundled/index.js | entr -rn node --enable-source-maps /_"]

# vim: tw=0
