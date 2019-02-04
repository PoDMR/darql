package com.gitlab.ctt.arq.core.format;

import java.util.function.Consumer;

public class LineDelimFormat {
	public static final String HASH_DELIM = "####";
	public static final String DASH_DELIM = "------------";
	private final String delim;
	private StringBuilder sb = new StringBuilder();
	private Consumer<LineItem> queryConsumer;

	public LineDelimFormat(Consumer<LineItem> queryConsumer) {
		this(queryConsumer, HASH_DELIM);
	}

	public LineDelimFormat(Consumer<LineItem> queryConsumer, String delim) {
		this.queryConsumer = queryConsumer;
		this.delim = delim;
	}

	public void acceptLine(LineItem line) {
		if (testLine(line)) {
			flush(line);
		} else {
			sb.append(line.lineStr);
			sb.append("\n");
		}
	}


	@SuppressWarnings("RedundantIfStatement")
	private boolean testLine(LineItem line) {
		boolean startsWith = line.lineStr.startsWith(delim);
		if (!startsWith) {
			return false;
		}





		return true;
	}



	public void flush(LineItem line) {
		if (sb.length() > 0) {
			String string = sb.toString();
			handle(new LineItem(string, line));
			reset();
		}
	}

	private void reset() {
		sb.setLength(0);

	}

	protected void handle(LineItem queryStr) {
		queryConsumer.accept(queryStr);
	}
}
