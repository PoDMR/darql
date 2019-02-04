package com.gitlab.ctt.arq.core.format;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class WikiTsvLineFormat {
	private Consumer<LineItem> queryConsumer;

	public WikiTsvLineFormat(Consumer<LineItem> queryConsumer) {
		this.queryConsumer = queryConsumer;
	}


	public void acceptLine(LineItem line) {

		if ('#' != line.lineStr.charAt(0)) {  






			int index0 = line.lineStr.indexOf('\t');
			String col0 = line.lineStr.substring(0, index0);







			try {
				String queryStr = URLDecoder.decode(col0, StandardCharsets.UTF_8.name());
				LineItem lineItem = new LineItem(queryStr, line);



				queryConsumer.accept(lineItem);
			} catch (UnsupportedEncodingException ignored) {
			}
		}
	}
}
