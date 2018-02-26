package com.gitlab.ctt.arq.core.format;

import java.util.ArrayDeque;

public class TNode<T> {
	private T value;
	private ArrayDeque<TNode<T>> children;

	public TNode(T value) {
		this.value = value;
		this.children = new ArrayDeque<>();
	}

	public T getValue() {
		return value;
	}

	public ArrayDeque<TNode<T>> getChildren() {

		return children;
	}

	@Override
	public String toString() {
		return String.valueOf(value);
	}
}
