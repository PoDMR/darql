package com.gitlab.ctt.arq.utilx;

import org.jgrapht.graph.DefaultEdge;

public class LabeledEdge<T> extends DefaultEdge {
	private T label;

	public LabeledEdge() {
		this(null);
	}

	public LabeledEdge(T label) {
		this.label = label;
	}

	public T getLabel() {
		return label;
	}

	@Override
	public int hashCode() {
		int result = label != null ? label.hashCode() : 0;
		Object src = getSource();
		Object tgt = getTarget();
		result = 31 * result + (src != null ? src.hashCode() : 0);
		result = 31 * result + (tgt != null ? tgt.hashCode() : 0);
		return result;
	}













	@Override
	public String toString() {
		return String.valueOf(label);
	}
}
