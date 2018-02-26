package com.gitlab.ctt.arq.analysis;

public interface Job<S, T> {

	void init();
	T apply(S object);
	void commit();
	default void setTag(String tag) {}
}
