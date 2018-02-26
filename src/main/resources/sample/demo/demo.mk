default:
	@echo not defined

run:
	mvn compile exec:java #-Xms8g -Xmx8g -Xss64m
#	 -Dexec.mainClass=... -Dexec.args="%classpath"

HOST := $(shell hostname)
#MAVEN_OPTS := -Xms60g -Xmx60g
#EXEC_ARGS := -Dexec.args="src/main/resources/config.yaml"
EXEC_ARGS := -Dexec.args="src/main/resources/demo/demo_config.yaml"

work:
	(export JOB_NAME && export HOSTNAME && export MAVEN_OPTS="$(MAVEN_OPTS)" && \
	  mvn clean compile exec:java $(EXEC_ARGS))

DEBUG := -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:5005
MAVEN_OPTS_DEBUG := ${MAVEN_OPTS} ${DEBUG}

debug:
	(export JOB_NAME && export HOSTNAME && export MAVEN_OPTS="$(MAVEN_OPTS_DEBUG)" && \
	  mvn clean compile exec:java $(EXEC_ARGS))

remoterun:
	ssh ${HOST} 'tmux select-window -t $(tmux list-windows |
	  grep ": "${NAME} | grep -o -P "^\d") &&
	  tmux send-keys -l "make run" && tmux send-keys Enter'

http:
	http-server src/main/resources/web -a localhost -p 8080 -c-1

db:
	DB_URL="jdbc:postgresql://localhost:5432/sparql" DB_USER=${HOST} DB_PASS=${HOST} \
	  JOB_SET=db_fill JOB_NAME=raw_mod_part make work

db_d:
	DB_URL="jdbc:postgresql://localhost:5432/sparql" DB_USER=${HOST} DB_PASS=${HOST} \
	  JOB_SET=db_fill JOB_NAME=raw_mod_part make debug

db_dev:
	DB_URL="jdbc:postgresql://localhost:5432/db1" DB_USER=${HOST} DB_PASS=${HOST} \
	  JOB_SET=db_fill JOB_NAME=test make work

db_dev_d:
	DB_URL="jdbc:postgresql://localhost:5432/db1" DB_USER=${HOST} DB_PASS=${HOST} \
	  JOB_SET=db_fill JOB_NAME=streaks make debug

web:
	DB_URL="jdbc:postgresql://localhost:5432/sparql" DB_USER=demo DB_PASS=demo \
	  MAIN_CLASS=com.gitlab.ctt.arq.analysis.aspect.db.Http \
	  JOB_NAME=$JOB_NAME HOSTNAME=$HOSTNAME MAVEN_OPTS="$(MAVEN_OPTS)" \
	  mvn clean compile exec:java

web_d:
	DB_URL="jdbc:postgresql://localhost:5432/sparql" DB_USER=demo DB_PASS=demo \
	  MAIN_CLASS=com.gitlab.ctt.arq.analysis.aspect.db.Http \
	  JOB_NAME=${JOB_NAME} HOSTNAME=${HOSTNAME} MAVEN_OPTS="$(MAVEN_OPTS_DEBUG)" \
	  mvn clean compile exec:java

pg_start:
	sudo docker run \
	  --name postgres \
	  -e POSTGRES_PASSWORD=${HOST} \
	  -e POSTGRES_USER=${HOST} \
	  -e PGDATA=/var/lib/postgresql/data/pgdata \
	  -p 127.0.0.1:5432:5432 \
	  -v ./data:/var/lib/postgresql/data \
	  -d postgres:10.2

pg_stop:
	sudo docker stop postgres
