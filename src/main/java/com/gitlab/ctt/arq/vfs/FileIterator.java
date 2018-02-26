package com.gitlab.ctt.arq.vfs;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;

public class FileIterator implements Iterator<File> {
	private File baseDir;
	private Iterator<File> fileIterator;
	private FileIterator recursiveIterator;
	private File currentFile;

	public FileIterator(File file) {
		baseDir = file;
		init(file);
	}

	private void init(File baseDir) {
		File[] fileArray = null;
		try {
			fileArray = baseDir.listFiles();
		} catch (SecurityException ignored) {
		}
		if (fileArray == null) {
			fileArray = new File[0];
		}
		Arrays.sort(fileArray, Comparator.comparing(File::getName));
		fileIterator = Arrays.asList(fileArray).iterator();
	}

	@Override
	public boolean hasNext() {
		if (fileIterator == null) {
			return false;
		} else if (recursiveIterator != null && recursiveIterator.hasNext()) {
			return true;
		} else {
			recursiveIterator = null;
			if (fileIterator.hasNext()) {
				if (currentFile == null) {
					currentFile = fileIterator.next();
				}
				if (currentFile.isFile()) {
					return true;
				} else {
					recursiveIterator = new FileIterator(currentFile);
					if (recursiveIterator.hasNext()) {
						return true;
					} else {
						currentFile = null;
						return hasNext();
					}
				}
			} else {
				return false;
			}
		}
	}

	@Override
	public File next() {
		if (recursiveIterator != null) {
			currentFile = recursiveIterator.next();
		}
		File last = currentFile;
		currentFile = null;
		return last;
	}
}
