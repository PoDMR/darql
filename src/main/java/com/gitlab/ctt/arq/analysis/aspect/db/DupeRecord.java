package com.gitlab.ctt.arq.analysis.aspect.db;

import jdk.nashorn.internal.ir.annotations.Ignore;

public class DupeRecord {
	public int id;
	public String origin;
	public int copyOfId;

	@Ignore
	public String originMajor;
	@Ignore
	public String originMinor;
	@Ignore
	public int originLinum;

}
