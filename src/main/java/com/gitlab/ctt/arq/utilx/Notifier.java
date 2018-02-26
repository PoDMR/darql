package com.gitlab.ctt.arq.utilx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


public class Notifier {
	private static final Logger LOGGER = LoggerFactory.getLogger(Notifier.class);
	private static final String SENDMAIL_DEFAULT = "sendmail";
	private String sendMailCommand = SENDMAIL_DEFAULT;

	public Notifier() {
		this(SENDMAIL_DEFAULT);
	}

	public Notifier(String sendMailCommand) {
		this.sendMailCommand = sendMailCommand;
	}

	public void sendMail(String message) {
		try {
			ProcessBuilder processBuilder = new ProcessBuilder(sendMailCommand, message);
			processBuilder.start().waitFor();
		} catch (InterruptedException | IOException e) {
			LOGGER.warn("Unhandled", e);
		}
	}
}
