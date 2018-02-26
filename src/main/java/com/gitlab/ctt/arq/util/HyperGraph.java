package com.gitlab.ctt.arq.util;

import com.gitlab.ctt.arq.sparql.SparqlProperties;
import com.gitlab.ctt.arq.utilx.Resources;
import fj.data.Either;
import org.apache.jena.ext.com.google.common.collect.Sets;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.syntax.Element;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class HyperGraph<T> {
	public static void main(String[] args) {
		Resources.getLocalProperty("null");  
		process(true,  "PREFIX : <p:> SELECT * WHERE { ?x :p ?y . ?y :p ?z }");
		process(true,  "PREFIX : <p:> SELECT * WHERE { ?x :p ?y . ?x :p ?x }");
		process(false, "PREFIX : <p:> SELECT * WHERE { ?x :p ?y . ?y :p ?z . ?z :p ?x }");
		process(true,  "PREFIX : <p:> SELECT * WHERE { ?a :p ?b . ?b :p ?c . ?c :p ?d }");
		process(true,  "PREFIX : <p:> SELECT DISTINCT * WHERE { ?s a :c . ?o a :c . ?s :s ?o }");
		process(true,  "PREFIX : <p:> SELECT * WHERE { ?a :p ?b . ?c :p ?d }");
	}

	private static void process(boolean expect, String sparqlStr) {
		Either<Exception, Query> maybeQuery = SparqlUtil.get().toQuery(sparqlStr);
		if (maybeQuery.isRight()) {
			Query query = maybeQuery.right().value();
			Element element = query.getQueryPattern();
			boolean acyclic = SparqlProperties.get().isAcyclic(element);
			String suffix = expect == acyclic ? "" : "!!!";
			System.out.println("acyclic: " + acyclic + suffix);
		} else {
			throw new RuntimeException(maybeQuery.left().value());
		}
	}


	private Set<Set<T>> hes = new LinkedHashSet<>();

	public final void cover(Set<T> vertices) {
		hes.add(vertices);
	}

	
	public void gyoReduce() {
		Set<T> ear = findAnyEar();
		while (ear != null) {
			hes.remove(ear);
			ear = findAnyEar();
		}
	}

	public boolean isEmpty() {
		return hes.isEmpty();
	}

	
	private Set<T> findAnyEar() {
		if (hes.size() == 1) {
			return hes.stream().findFirst().orElse(null);
		}

		for (Set<T> h1 : hes) {
			for (Set<T> h2 : hes) {
				if (!h1.equals(h2)) {
					Set<Set<T>> h1c = Sets.difference(hes, Collections.singleton(h1));
					Set<T> h1cVerts = h1c.stream().reduce(Sets::union).orElse(Collections.emptySet());
					Set<T> subset = Sets.intersection(h1, h1cVerts);
					boolean containsAll = h2.containsAll(subset);
					if (containsAll) {
						return h1;
					}
				}
			}
		}
		return null;
	}
}
