# Searchlab Dockerfile
#
# This image must be build with a command one level below the current path.
# Furthermore the git project searchlab_apps must be pulled as well
# and must be stored in parallel to searchlab. Do:
# cd ..
# docker build -t searchlab -f searchlab/Dockerfile .
# docker run -d --rm -p 8400:8400 --name searchlab searchlab

# prepare front-end
FROM python:3.10-alpine AS sitebuilder
ADD searchlab/ui /app/ui
WORKDIR /app
ENV PYTHONHTTPSVERIFY 0
RUN \
    apk add --update ca-certificates bash build-base && \
    export PYTHONHTTPSVERIFY=0 && \
    pip install --trusted-host=pypi.python.org --trusted-host=pypi.org --trusted-host=files.pythonhosted.org --upgrade certifi && \
    pip install --trusted-host=pypi.python.org --trusted-host=pypi.org --trusted-host=files.pythonhosted.org jinja2==3.0.0 && \
    pip install --trusted-host=pypi.python.org --trusted-host=pypi.org --trusted-host=files.pythonhosted.org mkdocs && \
    rm -rf /tmp/* /var/tmp/* /var/cache/apk/* /var/cache/distfiles/* && \
    cd ui && \
    mkdocs build

# prepare server
FROM eclipse-temurin:11-jdk-alpine AS appbuilder
ADD searchlab/src /app/src/
ADD searchlab/build.gradle /app/
ADD searchlab/.gradle /app/
ADD searchlab/gradle /app/gradle/
ADD searchlab/gradle.properties /app/
ADD searchlab/gradlew /app/
WORKDIR /app
RUN ./gradlew clean shadowDistTar

# prepare distribution image
FROM eclipse-temurin:11-jre-alpine
LABEL maintainer="Michael Peter Christen <mc@yacy.net>"
ENV DEBIAN_FRONTEND noninteractive
ARG default_branch=master

ADD searchlab_apps /searchlab_apps/
ADD searchlab/conf /app/conf/
ADD searchlab/htdocs /app/htdocs/
COPY --from=sitebuilder /app/ui/site/ ./app/ui/site/
COPY --from=appbuilder /app/build/libs/searchlab-0.0.1-SNAPSHOT-all.jar ./app/build/libs/

EXPOSE 8400
WORKDIR /app
CMD ["java", "-Xms320M", "-Xmx2G", "-jar", "/app/build/libs/searchlab-0.0.1-SNAPSHOT-all.jar"]
