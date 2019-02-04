package com.gitlab.ctt.arq.utilx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;



public class BlockingExecutor extends ThreadPoolExecutor {
	private static final Logger LOGGER = LoggerFactory.getLogger(BlockingExecutor.class);
	private final Semaphore semaphore;

	public BlockingExecutor(final int poolSize, final int queueSize) {
		super(poolSize, poolSize, 0L, TimeUnit.MILLISECONDS,
			new LinkedBlockingQueue<>());
		semaphore = new Semaphore(poolSize + queueSize);
	}

	@Override
	public void execute(final Runnable task) {
		boolean acquired = false;

		do {
			try {
				semaphore.acquire();
				acquired = true;
			} catch (final InterruptedException e) {

				throw new RuntimeException("Interrupted");
			}
		} while (!acquired);
		try {
			super.execute(task);
		} catch (final RejectedExecutionException e) {
			semaphore.release();
			throw e;
		}
	}

	@Override
	protected void afterExecute(final Runnable r, final Throwable t) {
		super.afterExecute(r, t);
		semaphore.release();
		if (t != null) {
			LOGGER.error("uncaught in executor", t);

		}
	}
}
