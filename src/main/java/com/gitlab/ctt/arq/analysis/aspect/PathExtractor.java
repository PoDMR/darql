package com.gitlab.ctt.arq.analysis.aspect;

import com.gitlab.ctt.arq.analysis.FileOutput;
import com.gitlab.ctt.arq.analysis.Job;
import com.gitlab.ctt.arq.analysis.support.ConsumePathVisitor;
import com.gitlab.ctt.arq.sparql.ElementDeepWalker;
import fj.data.Either;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.path.Path;
import org.apache.jena.sparql.path.PathVisitor;
import org.apache.jena.sparql.syntax.Element;
import org.apache.jena.sparql.syntax.ElementPathBlock;
import org.apache.jena.sparql.syntax.ElementVisitorBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicLong;

public class PathExtractor implements Job<Either<Exception, Query>, Void> {
	private static final Logger LOGGER = LoggerFactory.getLogger(PathExtractor.class);


	private static final String BASENAME = "prop_paths.txt";
	private String tag;
	private FileOutput fileOutput;
	private PrintWriter writer;
	private AtomicLong counter = new AtomicLong();

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
				handleElement(element);
			}
		}
		return null;
	}

	private synchronized void handleElement(Element element) {
		MutableBoolean nonEmpty = new MutableBoolean(false);
		PathVisitor pathVisitor = new ConsumePathVisitor(path -> {
			handlePath(nonEmpty, path);
		});
		ElementDeepWalker.walk(element, new ElementVisitorBase() {
			@Override
			public void visit(ElementPathBlock el) {
				el.patternElts().forEachRemaining(triplePath -> {
					Path path = triplePath.getPath();
					if (path != null) {
						path.visit(pathVisitor);
					}
				});
			}
		});
		if (nonEmpty.isTrue()) {
			writer.println();
		}
	}

	private void handlePath(MutableBoolean nonEmpty, Path path) {
		counter.getAndIncrement();
		writer.println(path.toString());
		nonEmpty.setTrue();
	}

	@Override
	public void commit() {
		if (fileOutput != null) {
			LOGGER.debug("Number of extracted expressions: {}", counter);
			fileOutput.commit();
		}
		fileOutput = null;
	}
}
