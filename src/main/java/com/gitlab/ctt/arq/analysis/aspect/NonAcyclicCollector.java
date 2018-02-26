package com.gitlab.ctt.arq.analysis.aspect;

import com.gitlab.ctt.arq.analysis.FileOutput;
import com.gitlab.ctt.arq.analysis.Job;
import com.gitlab.ctt.arq.core.format.LineDelimFormat;
import com.gitlab.ctt.arq.sparql.SparqlProperties;
import fj.data.Either;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.syntax.Element;

import java.io.PrintWriter;


public class NonAcyclicCollector implements Job<Either<Exception, Query>, Void> {



	private static final String BASENAME = "cyclic_hg_cpf.txt";
	private String tag;
	private FileOutput fileOutput;
	private PrintWriter writer;


	@Override
	public void setTag(String tag) {
		this.tag = tag;  
	}

	@Override
	public void init() {
		fileOutput = FileOutput.from(tag, BASENAME);
		writer = fileOutput.getWriter();
	}

	@Override
	public Void apply(Either<Exception, Query> maybeQuery) {
		if (maybeQuery.isRight()) {
			Query query = maybeQuery.right().value();
			Element element = query.getQueryPattern();
			if (element != null) {
				boolean cpf = SparqlProperties.get().isCpf(element);
				if (cpf) {
					boolean acyclic = SparqlProperties.get().isAcyclic(element);
					if (!acyclic) {
						submit(query);
					}
				}
			}
		}
		return null;
	}

	private synchronized void submit(Query query) {
		writer.print(query.toString());
		writer.print(LineDelimFormat.HASH_DELIM);
		writer.print("\n");
	}

	@Override
	public void commit() {
		if (fileOutput != null) {

			fileOutput.commit();
		}
		fileOutput = null;
	}
}
