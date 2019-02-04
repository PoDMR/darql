package com.gitlab.ctt.arq.core;

import com.gitlab.ctt.arq.core.format.TNode;
import com.gitlab.ctt.arq.core.format.TNodeBuilderFormat;
import com.gitlab.ctt.arq.utilx.Resources;
import com.gitlab.ctt.arq.vfs.FileEntry;
import com.gitlab.ctt.arq.vfs.LocalVfs;
import com.gitlab.ctt.arq.vfs.TarVfs;
import com.gitlab.ctt.arq.vfs.Vfs;
import fj.data.Either;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public class BatchProcessor {
	public static class BailException extends RuntimeException {
		private Object[] context;

		public BailException(Object... context) {
			this.context = context;
		}

		@Override
		public String toString() {
			return Arrays.toString(context);
		}

		public Object[] getContext() {
			return context;
		}
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(BatchProcessor.class);

	public static void main(String[] args) {
		if (args.length <= 0) {
			args = new String[]{ Resources.getLocalProperty("arq.arg") };
		}
		LOGGER.debug("Args: {}", Arrays.toString(args));
		LOGGER.info("Start: {}", System.currentTimeMillis());
		for (String arg : args) {
			new BatchProcessor().processFromMasterFile(arg, true);
		}
		LOGGER.info("Stop: {}", System.currentTimeMillis());
	}

	public BatchProcessor() {
		this(new FileDispatcher(), System.getenv("JOB_NAME"));
	}

	public BatchProcessor(FileDispatcher consumer, String defaultInputPattern) {
		this.consumer = consumer;
		this.defaultInputPattern = defaultInputPattern;
	}

	private FileDispatcher consumer;
	private String defaultInputPattern;
	private String batchTag = "batchTag";

	public void processFromMasterFile(String masterFilename, boolean jobsFromConfig) {
		File masterFile = new File(masterFilename);
		String pwd = Resources.getLocalProperty("arq.pwd");
		LOGGER.debug("pwd: {}", pwd);
		File dir = new File(pwd);
		try (InputStream fis = new FileInputStream(masterFile)) {
			InputStream inputStream = fis;

			if (masterFilename.endsWith(".yaml")) {
				Yaml yaml = new Yaml();
				Map<?, ?> config = yaml.loadAs(fis, Map.class);
				List<?> input = (List<?>) config.get("input");
				if (input != null) {
					String inputPattern = this.defaultInputPattern;
					inputPattern = inputPattern == null ? ".*" : inputPattern;
					LOGGER.debug("{}: {}", "JOB_NAME", inputPattern);
					for (Object itemObj : input) {
						Map<?, ?> item = (Map<?, ?>) itemObj;
						dir = null;
						if (item.get("key").toString().matches(inputPattern)) {
							inputStream = new ByteArrayInputStream(
								item.get("filter").toString().getBytes());
							List<?> srcs = (List<?>) item.get("src");
							for (Object srcObj : srcs) {
								Map<?, ?> src = (Map<?, ?>) srcObj;
								String hostPattern = String.valueOf(src.get("host"));
								hostPattern = hostPattern == null ? ".*" : hostPattern;
								String hostname = Resources.getHostname();
								hostname = hostname == null ? "" : hostname;
								if (Pattern.compile(hostPattern, Pattern.CASE_INSENSITIVE)
										.matcher(hostname).matches()) {
									dir = new File(src.get("dir").toString());
								}
							}
							break;
						}
					}
				}
				if (dir == null) {
					throw new IOException("incomplete config");
				}

				List<?> handlers = (List<?>) config.get("handlers");
				if (handlers != null) {
					Map<String, String> handlerMap = new LinkedHashMap<>();
					for (Object item : handlers) {
						Map<?, ?> map = (Map<?, ?>) item;
						String pattern = map.get("pattern").toString();
						String handler = map.get("handler").toString();
						handlerMap.put(pattern, handler);
					}
					if (!handlerMap.isEmpty()) {
						consumer.setHandlerMap(handlerMap);
					}
				}

				List<?> validJobs = Collections.emptyList();
				List<?> jobSets = (List<?>) config.get("job_sets");
				if (jobSets != null) {
					String jobSetPattern = System.getenv("JOB_SET");
					jobSetPattern = jobSetPattern == null ? "default" : jobSetPattern;
					LOGGER.debug("{}: {}", "JOB_SET", jobSetPattern);
					for (Object item : jobSets) {
						Map<?, ?> map = (Map<?, ?>) item;
						String name = map.get("name").toString();
						if (name.equals(jobSetPattern)) {
							validJobs = (List<?>) map.get("jobs");
							break;
						}
					}
				}
				if (validJobs.size() > 0 && jobsFromConfig) {
					consumer.setValidJobs(validJobs.stream()
						.map(Object::toString).collect(Collectors.toList()));
				}
			}

			Either<Exception, TNode<String>> maybeNode = TNodeBuilderFormat.build(inputStream);
			if (maybeNode.isRight()) {
				TNode<String> node = maybeNode.right().value();
				processRootNode(dir, node);
			}
		} catch (IOException | ClassCastException e) {
			LOGGER.warn("Unhandled", e);
		}
	}

	private void processRootNode(File dir, TNode<String> node) {
		Vfs vfs = new LocalVfs(dir);
		DateTime dt = DateTime.now();  
		DateTimeFormatter dtf = DateTimeFormat.forPattern("yyMMddHHmmss");
		batchTag = dt.withZone(DateTimeZone.UTC).toString(dtf);

		try {
			processNode(vfs, node, true);
		} catch (BailException e) {
			LOGGER.info("Bailed: {}", e.toString());
		} catch (Throwable e) {
			LOGGER.warn("Unhandled", e);
		}
		if (consumer.getTag() != null) {
			consumer.commit();
		}
	}

	private void processNode(Vfs vfs, TNode<String> node, boolean root) {
		for (FileEntry vf : vfs) {
			filterByNode(vf, node, root);
		}
		try {
			vfs.close();
		} catch (IOException ignored) {
		}
	}


	private void filterByNode(FileEntry vf, TNode<String> parent, boolean root) {
		String name = vf.getName();



		boolean prepared = false;
		for (TNode<String> node : parent.getChildren()) {
			String regex = node.getValue();

			if (name.matches(regex)) {
				if (!prepared) {
					prepare(node, parent);
					prepared = true;
				}
				filterByFileType(vf, node);
			}
		}
	}

	private void filterByFileType(FileEntry vf, TNode<String> parent) {
		String name = vf.getName();

		if (name.endsWith(".tar")) {
			TarVfs tarVfs = new TarVfs(vf);
			processNode(tarVfs, parent, false);
		} else if (name.endsWith(".gz")) {
			try {
				GZIPInputStream cis = new GZIPInputStream(vf.getStream());
				consumer.accept(new FileEntry(vf.getVfs(), name, FileEntry.SIZE_UNKNOWN, cis));
			} catch (IOException e) {
				LOGGER.warn("Unhandled", e);
			}
		} else if (name.endsWith(".bz2")) {
			try {
				BZip2CompressorInputStream cis = new BZip2CompressorInputStream(vf.getStream());
				consumer.accept(new FileEntry(vf.getVfs(), name, FileEntry.SIZE_UNKNOWN, cis));
			} catch (IOException e) {
				LOGGER.warn("Unhandled", e);
			}
		} else {  




			consumer.accept(vf);
		}
	}

	
	private void prepare(TNode<String> node, TNode<String> parent) {
		if (batchTag != null && "/".equals(parent.getValue())) {
			consumer.waitForAll();  
			String nodeTag = node.getValue().replaceAll("/.*", "");
			if (!nodeTag.equals(currentNodeTag)) {
				currentNodeTag = nodeTag;
				nodeTag = new File(batchTag, nodeTag).toPath().toString();
				consumer.commit();
				consumer.setTag(nodeTag);
				consumer.init();
			}
		}
	}

	private String currentNodeTag;
}
