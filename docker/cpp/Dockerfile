FROM alpine:3.19

RUN apk add --no-cache g++ && \
    addgroup -S sandbox && adduser -S sandbox -G sandbox

WORKDIR /sandbox
RUN chown sandbox:sandbox /sandbox

USER sandbox
