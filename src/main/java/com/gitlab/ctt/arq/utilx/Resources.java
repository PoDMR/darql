package com.gitlab.ctt.arq.utilx;

import com.gitlab.ctt.arq.Main;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class Resources {

	private static Logger getLazyLogger() {
		return LoggerFactory.getLogger(Resources.class);
	}

	private static final ThreadLocal<ClassLoader> cl = new ThreadLocal<ClassLoader>() {
		@Override
		protected ClassLoader initialValue() {
			return Thread.currentThread().getContextClassLoader();
		}
	};

	public static ClassLoader currentClassLoader() {
		return cl.get();
	}


private static final Properties properties = System.getProperties();

	static {
		if (!loadHostProperties()) {
			if (loadProperties(properties, "local.prop")) {
				getLazyLogger().debug("Loaded {}", "local.prop");
			}
			if (loadProperties(properties, "local.properties")) {
				getLazyLogger().debug("Loaded {}", "local.properties");
			}
		} else {
			getLazyLogger().debug("Loaded host specific properties");
		}
	}

	private static boolean loadHostProperties() {
		String hostname = getHostname();
		if (hostname != null) {
			String filename = String.format("local-%s.prop", hostname);
			return loadProperties(properties, filename);
		}
		return false;
	}

	public static String getHostname() {
		String hostname = System.getenv("HOSTNAME");
		if (hostname == null) {
			hostname = System.getenv("COMPUTERNAME");
		}
		if (hostname == null) {
			try {
				hostname = InetAddress.getLocalHost().getHostName();
			} catch (UnknownHostException ignored) {
			}
		}
		if (hostname != null) {
			return hostname;
		} else {


			return null;
		}
	}

	public static boolean loadProperties(Properties properties, String resource) {
		resource = String.format("%s/%s", Main.class.getPackage().getName().replace('.', '/'), resource);
		try (InputStream stream = currentClassLoader().getResourceAsStream(resource)) {
			if (stream == null) {
				throw new IOException("null stream");
			}
			properties.load(stream);

			return true;
		} catch (IOException | Error e) {

			return false;
		}
	}

	public static List<String> listResources(String path) {
		try (InputStream inputStream = currentClassLoader().getResourceAsStream(path)) {
			BufferedReader br = new BufferedReader(new InputStreamReader(
				inputStream, StandardCharsets.UTF_8));
			return br.lines().collect(Collectors.toList());
		} catch (IOException e) {

			return Collections.emptyList();
		}
	}

	public static String getLocalProperty(String key) {
		return properties.getProperty(key);
	}

	public static String getLocalPropertyOr(String key, String alternative) {
		String localProperty = getLocalProperty(key);
		return localProperty != null ? localProperty : alternative;
	}


	public static String getMainResourceFilename(String resourceName) {
		String base = "fxl/src/main/resources/";
		File file = new File(base, resourceName);
		return file.getPath();
	}

	public static String getTestResourceFilename(String resourceName) {
		String base = "fxl/src/test/resources/";
		File file = new File(base, resourceName);
		return file.getPath();
	}

	public static String getResourceAsString(String resourceName) {
		InputStream inputStream = getResourceAsStream(resourceName);
		try {
			if (inputStream != null) {
				return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
			}
		} catch (IOException ignored) {
		}
		return null;
	}

	
	public static InputStream getResourceAsStream(String resourceName) {
		return currentClassLoader().getResourceAsStream(resourceName);
	}

	
	public static InputStream getFileStreamFromProperty(String key) {
		try {
			return new FileInputStream(getLocalProperty(key));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
}
