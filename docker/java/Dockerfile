FROM eclipse-temurin:17-jdk-jammy

RUN groupadd -r sandbox && useradd -r -g sandbox sandbox

# Add Gson for JSON parsing in user solutions
RUN apt-get update -qq && apt-get install -y -qq wget \
 && wget -q https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar \
      -O /opt/gson.jar \
 && apt-get remove -y wget && apt-get autoremove -y && rm -rf /var/lib/apt/lists/*

WORKDIR /sandbox
RUN chown sandbox:sandbox /sandbox

USER sandbox
