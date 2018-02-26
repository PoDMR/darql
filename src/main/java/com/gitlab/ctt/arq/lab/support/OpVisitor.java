package com.gitlab.ctt.arq.lab.support;

import org.apache.jena.sparql.path.*;

import java.util.Map;

public class OpVisitor extends PathVisitorBase {
	private Map<String, Integer> map;

	public OpVisitor(Map<String, Integer> map) {
		this.map = map;
	}

	private void inc(String str) {
		Integer integer = map.get(str);
		map.put(str, integer == null ? 1 : integer + 1);
	}

	@Override
	public void visit(P_Seq path) {
		path.getLeft().visit(this);
		path.getRight().visit(this);
		inc("/");
	}

	@Override
	public void visit(P_Alt path) {
		path.getLeft().visit(this);
		path.getRight().visit(this);
		inc("|");
	}


	@Override
	public void visit(P_OneOrMore1 path) {
		path.getSubPath().visit(this);
		inc("+");
	}

	@Override
	public void visit(P_ZeroOrMore1 path) {
		path.getSubPath().visit(this);
		inc("*");
	}

	@Override
	public void visit(P_ZeroOrOne path) {
		inc("?");
	}

	@Override
	public void visit(P_Inverse path) {
		inc("^");
	}

	@Override
	public void visit(P_NegPropSet path) {
		for (P_Path0 path0 : path.getNodes()) {
			inc("!");
		}
	}

	@Override
	public void visit(P_Link path) {

	}
}
