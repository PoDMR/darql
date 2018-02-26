package com.gitlab.ctt.arq.sparql;

import com.gitlab.ctt.arq.util.SparqlUtil;
import com.gitlab.ctt.arq.utilx.Resources;
import fj.data.Either;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.syntax.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


public class SparqlTransducer {









	public static void main(String[] args) {
		Resources.getLocalProperty("null");  








		mainProcess(Resources.getResourceAsString("sample/misc/no_norm2.sparql"));
	}

	private static void mainProcess(String sparqlStr) {
		Either<Exception, Query> maybeQuery = SparqlUtil.get().toQuery(sparqlStr);
		if (maybeQuery.isRight()) {
			Query query = maybeQuery.right().value();
			Element element = query.getQueryPattern();
			Element transformed = get().unionNormalize(element);
			System.out.println(transformed);
		} else {
			throw new RuntimeException(maybeQuery.left().value());
		}
	}

	private static final SparqlTransducer INSTANCE = new SparqlTransducer();

	public static SparqlTransducer get() {
		return INSTANCE;
	}

	public Element unionNormalize(Element element) {
		if (element instanceof ElementGroup) {
			ElementGroup group = (ElementGroup) element;
			Element newElement = processGroup(group);
			if (newElement instanceof ElementGroup) {
				ElementGroup newGroup = (ElementGroup) newElement;
				return makePure(newGroup);
			}
			return newElement;
		} else if (element instanceof ElementUnion) {
			ElementUnion union = (ElementUnion) element;
			ElementUnion newUnion = new ElementUnion();
			union.getElements().stream()
				.map(this::unionNormalize).forEach(newUnion::addElement);
			return makePure(newUnion);
		} else if (element instanceof ElementOptional) {
			ElementOptional optional = (ElementOptional) element;
			Element optionalPart = unionNormalize(optional.getOptionalElement());
			return new ElementOptional(optionalPart);
		}
		return element;
	}

	private Element processGroup(ElementGroup group) {
		Optional<Element> optionalPair = tryAsOptionalPair(group);
		if (optionalPair.isPresent()) {
			return optionalPair.get();
		}
		Optional<Element> filterPair = tryAsFilterPair(group);
		if (filterPair.isPresent()) {
			return filterPair.get();
		}
		Optional<Element> conjunction = tryAsConjunction(group);
		if (conjunction.isPresent()) {
			return conjunction.get();
		}
		return unionNormalize(asHeteroGroup(group));
	}

	private ElementGroup asHeteroGroup(ElementGroup group) {
		List<Element> patternElements = new ArrayList<>();
		List<ElementFilter> filters = new ArrayList<>();
		List<Element> groupElements = group.getElements();
		for (Element part : groupElements) {
			if (part instanceof ElementFilter) {
				ElementFilter filter = (ElementFilter) part;
				filters.add(filter);
			} else if (part instanceof ElementOptional) {
				ElementGroup group1 = new ElementGroup();
				patternElements.forEach(group1::addElement);
				patternElements.clear();
				ElementGroup optionalGroup = new ElementGroup();
				optionalGroup.addElement(group1);
				optionalGroup.addElement(part);
				patternElements.add(optionalGroup);
			} else {
				patternElements.add(part);
			}
		}
		ElementGroup newGroup = new ElementGroup();
		patternElements.forEach(newGroup::addElement);
		for (ElementFilter filter : filters) {
			ElementGroup filterGroup = new ElementGroup();
			filterGroup.addElement(newGroup);
			filterGroup.addElement(filter);
			newGroup = filterGroup;
		}
		return newGroup;
	}

	private Optional<Element> tryAsOptionalPair(ElementGroup group) {
		if (group.size() == 2) {
			Element element2 = unionNormalize(group.get(1));
			if (element2 instanceof ElementOptional) {
				Element element1 = unionNormalize(group.get(0));
				return Optional.of(transformPair(element1, element2));
			}
		}
		return Optional.empty();
	}

	private Optional<Element> tryAsFilterPair(ElementGroup group) {
		if (group.size() == 2) {
			Element element2 = group.get(1);
			if (element2 instanceof ElementFilter) {
				Element element1 = unionNormalize(group.get(0));
				return Optional.of(transformPair(element1, element2));
			}
		}
		return Optional.empty();
	}

	private Element transformPair(Element element1, Element element2) {
		if (element1 instanceof ElementUnion) {
			ElementUnion union = (ElementUnion) element1;
			return unionProduct(union, Collections.singletonList(element2));
		} else {
			ElementGroup newgroup = new ElementGroup();
			newgroup.addElement(element1);
			newgroup.addElement(element2);
			return newgroup;
		}
	}

	private Optional<Element> tryAsConjunction(ElementGroup group) {
		List<Element> nonUnions = new ArrayList<>();
		List<ElementUnion> unions = new ArrayList<>();
		List<Element> groupElements = group.getElements().stream()
			.map(this::unionNormalize).collect(Collectors.toList());
		for (Element element : groupElements) {
			if (element instanceof ElementOptional) {
				return Optional.empty();
			} else if (element instanceof ElementFilter) {
				return Optional.empty();
			} else if (element instanceof ElementUnion) {
				ElementUnion union = (ElementUnion) element;
				unions.add(union);
			} else {
				nonUnions.add(element);
			}
		}
		ElementGroup newGroup = new ElementGroup();
		nonUnions.forEach(newGroup::addElement);
		for (ElementUnion union : unions) {
			Element newUnion = unionProduct(union, newGroup.getElements());

			newGroup = new ElementGroup();
			newGroup.addElement(newUnion);
		}





		return Optional.of(makePure(newGroup));
	}












	private Element unionProduct(ElementUnion union, List<Element> elements) {
		ElementUnion newUnion = new ElementUnion();
		List<Element> unionElements = union.getElements();
		for (Element unionElement : unionElements) {
			ElementGroup group = new ElementGroup();
			group.addElement(unionElement);
			elements.forEach(group::addElement);
			newUnion.addElement(unionNormalize(group));  
		}
		return makePure(newUnion);
	}

	private Element makePure(ElementGroup variadic) {
		List<Element> children = variadic.getElements();
		if (children.size() == 1) {
			return variadic.getElements().get(0);
		}
		ElementGroup newResult = new ElementGroup();
		for (Element element : children) {
			if (element instanceof ElementGroup) {
				ElementGroup subElement = (ElementGroup) element;
				subElement.getElements().forEach(newResult::addElement);
			} else {
				return variadic;
			}
		}
		return newResult;
	}

	private Element makePure(ElementUnion variadic) {
		List<Element> children = variadic.getElements();
		if (children.size() == 1) {
			return variadic.getElements().get(0);
		}
		ElementUnion newResult = new ElementUnion();
		for (Element element : children) {
			if (element instanceof ElementUnion) {
				ElementUnion subElement = (ElementUnion) element;
				subElement.getElements().forEach(newResult::addElement);
			} else {
				return variadic;
			}
		}
		return newResult;
	}
}
