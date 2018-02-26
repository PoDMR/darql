package com.gitlab.ctt.arq.analysis.aspect;

import com.gitlab.ctt.arq.analysis.FileOutput;
import com.gitlab.ctt.arq.analysis.Job;
import com.gitlab.ctt.arq.analysis.aspect.util.FlagWalker;
import com.gitlab.ctt.arq.core.format.LineDelimFormat;
import com.gitlab.ctt.arq.sparql.SparqlProperties;
import com.gitlab.ctt.arq.sparql.SparqlTransducer;
import com.gitlab.ctt.arq.sparql.check.DesignCheck;
import fj.data.Either;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.syntax.Element;

import java.io.PrintWriter;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public class DesignChecker implements Job<Either<Exception, Query>, Void> {
	private static final String BASENAME = "opt_design.txt";
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
	public void commit() {
		if (fileOutput != null) {
			fileOutput.commit();
		}
		fileOutput = null;
	}

	@Override
	public Void apply(Either<Exception, Query> maybeQuery) {
		if (maybeQuery.isRight()) {
			Query query = maybeQuery.right().value();
			Element element = query.getQueryPattern();
			if (element != null) {
				boolean hasRegex = SparqlProperties.get().hasPath(element);
				FlagWalker flagWalker = new FlagWalker();
				flagWalker.consume(element);
				long flagLong = flagWalker.asLong();
				boolean hasOptional = ((flagLong & 4L) != 0);
				boolean afou = ((flagLong & ~0b1111) == 0); 
				boolean optAFU = !hasRegex && hasOptional && afou;
				Element unionNormalized = SparqlTransducer.get().unionNormalize(element);
				if (optAFU) {
					Optional<Function<Predicate<Element>, Boolean>> decomposition =
						DesignCheck.unionDecomposition(unionNormalized);
					if (!decomposition.isPresent()) {

					} else {
						boolean uwdComp = decomposition.map(x -> x.apply(DesignCheck::isUwd)).orElse(false);

						if (uwdComp) {
							submit(query, "wd");
						}
					}
				}
			}
		}
		return null;
	}

	private synchronized void submit(Query query, String tag) {
		writer.print(LineDelimFormat.HASH_DELIM);
		writer.print(" ");
		writer.print(tag);
		writer.print("\n");
		writer.print(query.toString());
		writer.print(LineDelimFormat.HASH_DELIM);
		writer.print("\n");
		writer.flush();
	}
}
