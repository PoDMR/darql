package com.gitlab.ctt.arq.vfs;

import java.io.Closeable;




public interface Vfs extends Closeable, Iterable<FileEntry> {
	Vfs getParent();
}
