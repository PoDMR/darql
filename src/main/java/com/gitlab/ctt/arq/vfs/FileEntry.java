package com.gitlab.ctt.arq.vfs;

import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;

public class FileEntry {
	public static long SIZE_UNKNOWN = -1;

	private String name;
	private long size;
	private InputStream stream;
	private Vfs vfs;

	public String getName() {
		return name;
	}

	public long getSize() {
		return size;
	}

	public InputStream getStream() {
		return stream;
	}


	public FileEntry(Vfs vfs, String name, long size, InputStream stream) {
		this.vfs = vfs;
		this.name = name;
		this.size = size;
		this.stream = stream;
	}

	public Vfs getVfs() {
		return vfs;
	}

	@Override
	public String toString() {
		String vfsName = vfs.toString();
		if (StringUtils.isNotBlank(vfsName)) {
			return String.format("%s!%s", vfsName, getName());
		} else {
			return getName();
		}
	}
}
