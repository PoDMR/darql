package com.gitlab.ctt.arq.analysis.aspect;

import com.gitlab.ctt.arq.analysis.aspect.util.FlagWalker;
import com.gitlab.ctt.arq.sparql.ElementDeepWalker;
import org.apache.jena.sparql.syntax.Element;
import org.apache.jena.sparql.syntax.ElementOptional;
import org.apache.jena.sparql.syntax.ElementVisitorBase;

public class OptAnalysis extends OpDistribution {
	@Override
	protected String getBasename() {
		return "opt_op_distribution.yaml";
	}

	@Override
	public void accept(Element element) {
		FlagWalker flagWalker = new FlagWalker();
		ElementDeepWalker.walk(element, new ElementVisitorBase() {
			@Override
			public void visit(ElementOptional el) {

				flagWalker.consume(el.getOptionalElement());
			}
		});
		flush(flagWalker);
	}

	private synchronized void flush(FlagWalker flagWalker) {
		long flagLong = flagWalker.asLong();
		int bitset = (int) (flagLong & 0b11111);
		Integer val = bitset2count.get(bitset);
		if (val == null) {
			val = 0;
		}
		bitset2count.put(bitset, val + 1);
	}
}
