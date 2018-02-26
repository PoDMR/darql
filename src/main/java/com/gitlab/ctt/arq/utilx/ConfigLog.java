package com.gitlab.ctt.arq.utilx;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggerContextListener;

import java.util.Properties;

public class ConfigLog implements LoggerContextListener {
	public ConfigLog() {
		Resources.getLocalProperty("null");  

	}

	@Override
	public boolean isResetResistant() {
		return true;
	}

	@Override
	public void onStart(LoggerContext context) {

	}

	@Override
	public void onReset(LoggerContext context) {

	}

	@Override
	public void onStop(LoggerContext context) {

	}

	@Override
	public void onLevelChange(Logger logger, Level level) {

	}

	private static void loadOnlyNeededProperties() {
		Properties properties = new Properties();
		String resource = "local.prop";
		Resources.loadProperties(properties, resource);
		String key = "arq.dir.log";
		String value = properties.getProperty(key);
		System.getProperties().setProperty(key, value);
	}
}
