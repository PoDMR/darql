package com.gitlab.ctt.arq.lab;

import org.apache.jena.sparql.path.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class StringMakePathVisitor extends PathVisitorBase {
	private Deque<String> stack = new ArrayDeque<>();

	public String getResult() {
		return stack.peek();
	}

	@Override
	public void visit(P_Seq path) {
		path.getLeft().visit(this);
		path.getRight().visit(this);
		String b = stack.pop();
		String a = stack.pop();
		stack.push(String.format("(%s/%s)", a, b));
	}

	@Override
	public void visit(P_Alt path) {
		path.getLeft().visit(this);
		path.getRight().visit(this);
		String b = stack.pop();
		String a = stack.pop();
		stack.push(String.format("(%s|%s)", a, b));
	}








	@Override
	public void visit(P_OneOrMore1 path) {
		path.getSubPath().visit(this);
		String a = stack.pop();
		stack.push(String.format("(%s)*", a));
	}








	@Override
	public void visit(P_ZeroOrMore1 path) {
		path.getSubPath().visit(this);
		String a = stack.pop();
		stack.push(String.format("(%s)*", a));
	}

	@Override
	public void visit(P_ZeroOrOne path) {
		path.getSubPath().visit(this);
		String a = stack.pop();
		stack.push(String.format("(%s)?", a));
	}


























	@Override
	public void visit(P_Inverse path) {
		path.getSubPath().visit(this);
		String a = stack.pop();
		stack.push(String.format("^(%s)", a));
	}






	@Override
	public void visit(P_NegPropSet path) {
		List<String> list = new ArrayList<>();

		for (P_Path0 path0 : path.getNodes()) {
			path0.visit(this);
			String a = stack.pop();
			list.add(a);
		}
		String l = String.join("|", list);
		stack.push(String.format("!(%s)", l));
	}

	@Override
	public void visit(P_Link path) {
		stack.push(path.toString());
	}
}
