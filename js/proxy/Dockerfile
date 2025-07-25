FROM node:24.4.1-alpine

RUN apk add --no-cache tini
RUN apk add --no-cache entr --repository=http://dl-cdn.alpinelinux.org/alpine/edge/community
ENV ENTR_INOTIFY_WORKAROUND=1

WORKDIR /server/
COPY bundled bundled

ENTRYPOINT ["/sbin/tini", "--"]
CMD [ "sh", "-c", "echo /server/bundled/index.js | entr -rn node --enable-source-maps /_"]

# vim: tw=0
