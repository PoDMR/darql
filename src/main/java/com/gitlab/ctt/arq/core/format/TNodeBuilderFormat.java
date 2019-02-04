package com.gitlab.ctt.arq.core.format;

import fj.data.Either;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayDeque;

public class TNodeBuilderFormat {
	public static Either<Exception, TNode<String>> build(InputStream inputStream) {
		try (InputStreamReader isr = new InputStreamReader(inputStream)) {
			BufferedReader br = new BufferedReader(isr);
			TNodeBuilderFormat builder = new TNodeBuilderFormat();
			String line = br.readLine();
			while (line != null) {
				if (!line.startsWith("#") && StringUtils.isNotEmpty(line)) {
					builder.accept(line);
				}
				line = br.readLine();
			}
			return Either.right(builder.getResult());
		} catch (IOException e) {
			return Either.left(e);
		}
	}

	private static int leftTrimCount(String string) {
		int i = 0;
		while (i+1 < string.length() && string.substring(i, i+1).matches("\\s")) {
			i++;
		}
		return i;
	}
	private int d = 0;

	private ArrayDeque<TNode<String>> stack = new ArrayDeque<>();

	public TNodeBuilderFormat() {
		stack.add(new TNode<>("/"));
	}

	public void accept(String string) {
		int c = leftTrimCount(string);
		String label = string.substring(c);
		if (c > d) {
			d++;
			stack.push(stack.peek().getChildren().getLast());
		} else if (c < d) {
			while (c < d) {
				d--;
				stack.pop();
			}
		}
		stack.peek().getChildren().add(new TNode<>(label));
	}

	public TNode<String> getResult() {
		return stack.getLast();
	}
}
