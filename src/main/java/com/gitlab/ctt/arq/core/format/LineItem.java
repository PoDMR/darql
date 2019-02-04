package com.gitlab.ctt.arq.core.format;


public class LineItem {
	public String lineStr;
	public long num;
	public String origin;




	public LineItem(String lineStr, long ti, String origin) {
		this.lineStr = lineStr;
		this.origin = origin;
		this.num = ti;
	}

	public LineItem(String lineStr, LineItem lineItem) {
		this.lineStr = lineStr;
		this.origin = lineItem.origin;
		this.num = lineItem.num;
	}
}
