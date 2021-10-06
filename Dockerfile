# searchlab dockerfile
# examples:
# docker build -t searchlab .
# docker run -d --rm -p 8400:8400 --name searchlab searchlab

# prepare front-end
FROM python:3.7-alpine AS sitebuilder

ADD . /app
WORKDIR /app
ENV PYTHONHTTPSVERIFY 0

# build ui
RUN \
    apk add --update ca-certificates bash build-base && \
    export PYTHONHTTPSVERIFY=0 && \
    pip install --trusted-host=pypi.python.org --trusted-host=pypi.org --trusted-host=files.pythonhosted.org mkdocs && \
    pip install --trusted-host=pypi.python.org --trusted-host=pypi.org --trusted-host=files.pythonhosted.org --upgrade certifi && \
    rm -rf /tmp/* /var/tmp/* /var/cache/apk/* /var/cache/distfiles/* && \
    cd ui && \
    mkdocs build

# prepare server
FROM adoptopenjdk/openjdk8:alpine AS appbuilder
LABEL maintainer="Michael Peter Christen <mc@yacy.net>"
ENV DEBIAN_FRONTEND noninteractive
ARG default_branch=master
EXPOSE 8400

COPY . /app
COPY --from=sitebuilder /app/ui/site/ ./app/ui/site/
WORKDIR /app
RUN ./gradlew assemble
CMD ["java", "-jar", "/app/build/libs/searchlab-0.0.1-SNAPSHOT-all.jar"]

