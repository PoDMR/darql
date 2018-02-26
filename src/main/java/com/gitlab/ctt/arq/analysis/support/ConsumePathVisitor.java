package com.gitlab.ctt.arq.analysis.support;

import org.apache.jena.sparql.path.*;

import java.util.function.Consumer;

public class ConsumePathVisitor extends PathVisitorBase {
	private Consumer<Path> pathConsumer;

	public ConsumePathVisitor(Consumer<Path> pathConsumer) {
		this.pathConsumer = pathConsumer;
	}

	private void accept(Path path) {
		pathConsumer.accept(path);
	}

	@Override
	public void visit(P_Link path) {

	}

	@Override
	public void visit(P_ReverseLink path) {
		accept(path);
	}

	@Override
	public void visit(P_NegPropSet path) {
		accept(path);
	}

	@Override
	public void visit(P_Inverse path) {
		accept(path);
	}

	@Override
	public void visit(P_Mod path) {
		accept(path);
	}

	@Override
	public void visit(P_FixedLength path) {
		accept(path);
	}

	@Override
	public void visit(P_Distinct path) {
		accept(path);
	}

	@Override
	public void visit(P_Multi path) {
		accept(path);
	}

	@Override
	public void visit(P_Shortest path) {
		accept(path);
	}

	@Override
	public void visit(P_ZeroOrOne path) {
		accept(path);
	}

	@Override
	public void visit(P_ZeroOrMore1 path) {
		accept(path);
	}

	@Override
	public void visit(P_ZeroOrMoreN path) {
		accept(path);
	}

	@Override
	public void visit(P_OneOrMore1 path) {
		accept(path);
	}

	@Override
	public void visit(P_OneOrMoreN path) {
		accept(path);
	}

	@Override
	public void visit(P_Alt path) {
		accept(path);
	}

	@Override
	public void visit(P_Seq path) {
		accept(path);
	}
}
