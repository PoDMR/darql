import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.FileAppender

import static ch.qos.logback.classic.Level.DEBUG
import static ch.qos.logback.classic.Level.ERROR
import static ch.qos.logback.classic.Level.INFO

appender("STDOUT", ConsoleAppender) {
	encoder(PatternLayoutEncoder) {
		pattern = "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
	}
}


//"target/output/log/app.log"
//"${java.io.tmpdir}/.arq/log/app.log"

appender("FILE", FileAppender) {
	file = "${arq.dir.log}/app.log"
	encoder(PatternLayoutEncoder) {
		pattern = "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
	}
}

logger("STATUS", DEBUG)
logger("com.gitlab.ctt", DEBUG)
logger("org.apache.jena.arq.info", ERROR)
logger("org.apache.jena.arq.info", ERROR)
root(INFO, ["STDOUT", "FILE"])
