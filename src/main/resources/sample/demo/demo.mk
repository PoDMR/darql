default:
	@echo not defined

run:
	mvn compile exec:java

HOST := $(shell hostname)
#MAVEN_OPTS := -Xms60g -Xmx60g
#EXEC_ARGS := -Dexec.args="src/main/resources/config.yaml"
EXEC_ARGS := -Dexec.args="src/main/resources/sample/demo/demo_config.yaml"

work:
	(export JOB_NAME && export HOSTNAME && export MAVEN_OPTS="$(MAVEN_OPTS)" && \
	  mvn clean compile exec:java $(EXEC_ARGS) -Darq.pwd=.)

DEBUG := -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:5005
MAVEN_OPTS_DEBUG := ${MAVEN_OPTS} ${DEBUG}

debug:
	(export JOB_NAME && export HOSTNAME && export MAVEN_OPTS="$(MAVEN_OPTS_DEBUG)" && \
	  mvn clean compile exec:java $(EXEC_ARGS))

http:
	http-server src/main/resources/web -a localhost -p 8080 -c-1

DB_USER=postgres
DB_PASS=postgres
DB_NAME=postgres
DB_PORT=5432

db:
	DB_URL="jdbc:postgresql://localhost:${DB_PORT}/${DB_NAME}" DB_USER=${DB_USER} DB_PASS=${DB_PASS} \
	  JOB_SET=db_fill JOB_NAME=wikidata make work -f src/main/resources/sample/demo/demo.mk

db_d:
	DB_URL="jdbc:postgresql://localhost:${DB_PORT}/${DB_NAME}" DB_USER=${DB_USER} DB_PASS=${DB_PASS} \
	  JOB_SET=db_fill JOB_NAME=wikidata make debug -f src/main/resources/sample/demo/demo.mk

db_dev:
	DB_URL="jdbc:postgresql://localhost:5432/db1" DB_USER=${DB_USER} DB_PASS=${DB_PASS} \
	  JOB_SET=db_fill JOB_NAME=test make work -f src/main/resources/sample/demo/demo.mk

db_dev_d:
	DB_URL="jdbc:postgresql://localhost:5432/db1" DB_USER=${DB_USER} DB_PASS=${DB_PASS} \
	  JOB_SET=db_fill JOB_NAME=streaks make debug -f src/main/resources/sample/demo/demo.mk

web:
	DB_URL="jdbc:postgresql://localhost:${DB_PORT}/${DB_NAME}" DB_USER=${DB_USER} DB_PASS=${DB_PASS} \
	  MAIN_CLASS=com.gitlab.ctt.arq.analysis.aspect.db.Http \
	 MAVEN_OPTS="$(MAVEN_OPTS)" \
	  mvn clean compile exec:java

web_d:
	DB_URL="jdbc:postgresql://localhost:${DB_PORT}/${DB_NAME}" DB_USER=${DB_USER} DB_PASS=${DB_PASS} \
	  MAIN_CLASS=com.gitlab.ctt.arq.analysis.aspect.db.Http \
	  MAVEN_OPTS="$(MAVEN_OPTS_DEBUG)" \
	  mvn clean compile exec:java

pg_start:
	sudo docker run \
	  --name postgres \
	  -e POSTGRES_PASSWORD=${DB_PASS} \
	  -e POSTGRES_USER=${DB_USER} \
	  -e POSTGRES_DB=postgres \
	  -e PGDATA=/var/lib/postgresql/data/pgdata \
	  -p 127.0.0.1:${DB_PORT}:${DB_PORT} \
	  -v ./data:/var/lib/postgresql/data \
	  -d postgres:10.2

pg_stop:
	sudo docker stop postgres
