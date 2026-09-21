FROM eclipse-temurin:17-jdk-jammy

RUN groupadd -r sandbox && useradd -r -g sandbox sandbox

WORKDIR /sandbox
RUN chown sandbox:sandbox /sandbox

USER sandbox
