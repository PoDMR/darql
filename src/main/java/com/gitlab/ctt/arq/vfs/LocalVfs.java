package com.gitlab.ctt.arq.vfs;

import com.gitlab.ctt.arq.utilx.Resources;

import java.io.*;
import java.util.Iterator;

public class LocalVfs implements Iterator<FileEntry>, Vfs  {
	private static final int DEFAULT_FILE_BUFFER_BYTES = 8 * 1024 * 1024;
	private static int bufferSize = 8 * 1024 * 1024;
	static {
		String bufferSizeStr = Resources.getLocalPropertyOr(
			"arq.file_buffer_size", String.valueOf(DEFAULT_FILE_BUFFER_BYTES));
		try {
			bufferSize = Integer.parseInt(bufferSizeStr);
		} catch (NumberFormatException ignored) {

		}
	}

	private File baseDir;
	private FileIterator fileIterator;

	@Override
	public void close() throws IOException {

	}

	public LocalVfs(File baseDir) {
		this.baseDir = baseDir;
		fileIterator = new FileIterator(baseDir);
	}

	@Override
	public Iterator<FileEntry> iterator() {
		return this;
	}

	@Override
	public boolean hasNext() {
		return fileIterator.hasNext();
	}

	@Override
	public FileEntry next() {


		File file = fileIterator.next();

		String name = baseDir.toPath().relativize(file.toPath()).toString();
		return new FileEntry(this, name, file.length(), null) {
			@Override
			public InputStream getStream() {
				try {
					return new BufferedInputStream(
						new FileInputStream(file), bufferSize);
				} catch (FileNotFoundException e) {
					throw new RuntimeException(e);
				}
			}
		};
	}

	@Override
	public Vfs getParent() {
		return null;
	}

	@Override
	public String toString() {

		return "";
	}
}
