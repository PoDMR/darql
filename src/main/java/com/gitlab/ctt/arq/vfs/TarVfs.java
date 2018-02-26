package com.gitlab.ctt.arq.vfs;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.io.input.ProxyInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

public class TarVfs implements Iterator<FileEntry>, Vfs {
	private static final Logger LOGGER = LoggerFactory.getLogger(TarVfs.class);
	private static final int BUFFER_SIZE = 8 * 1024 * 1024;

	private InputStream inputStream;
	private TarArchiveInputStream tis;
	private TarArchiveEntry tae;
	private Vfs parent;
	private String id;



	private TarVfs(Vfs parent, InputStream inputStream) {
		this.parent = parent;
		this.inputStream = inputStream;
		tis = new TarArchiveInputStream(this.inputStream);
		id = String.valueOf(this.hashCode());
	}

	public TarVfs(FileEntry vf) {
		this(vf.getVfs(), vf.getStream());
		id = vf.getName();
	}

	@Override
	public void close() throws IOException {

	}

	@Override
	public Iterator<FileEntry> iterator() {
		return this;
	}

	@Override
	public boolean hasNext() {
		proceedCurrentFile();
		return tae != null;
	}
	private void proceedCurrentFile() {
		try {
			tae = tis.getNextTarEntry();
			while (tae != null && !tae.isFile()) {
				tae = tis.getNextTarEntry();
			}
		} catch (IOException e) {
			LOGGER.warn("Unhandled", e);
			tae = null;
		}
	}

	@Override
	public FileEntry next() {
		InputStream eis = nonClose(new BufferedInputStream(
			new BoundedInputStream(tis, tae.getSize()), BUFFER_SIZE));
		return new FileEntry(this, tae.getName(), tae.getSize(), eis);
	}

	@Override
	public Vfs getParent() {
		return parent;
	}

	public static InputStream nonClose(InputStream inputStream) {
		return new ProxyInputStream(inputStream) {
			@Override
			public void close() throws IOException {

			}
		};
	}

	@Override
	public String toString() {
		return id;
	}
}
