package com.vaguehope.stein;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sshd.common.Factory;
import org.apache.sshd.server.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsoleCommandFactory implements Factory<Command> {

	private static final String THREAD_NAME_PREFIX = "ConsoleSch";
	private static final int MAX_CLIENTS = 10;

	private final ThreadPoolExecutor es;

	public ConsoleCommandFactory () {
		this.es = new ThreadPoolExecutor(0, MAX_CLIENTS,
				1L, TimeUnit.MINUTES,
				new SynchronousQueue<Runnable>(),
				new NamedThreadFactory(new LoggingThreadGroup(Thread.currentThread().getThreadGroup(), THREAD_NAME_PREFIX), THREAD_NAME_PREFIX));
		this.es.allowCoreThreadTimeOut(true);
	}

	@Override
	public Command create () {
		return new ConsoleCommand(this.es);
	}

	private static class LoggingThreadGroup extends ThreadGroup {

		private static final Logger LOG = LoggerFactory.getLogger(ConsoleCommandFactory.LoggingThreadGroup.class);

		public LoggingThreadGroup (final ThreadGroup parent, final String namePrefix) {
			super(parent, "tg-" + namePrefix);
		}

		@Override
		public void uncaughtException (final Thread t, final Throwable e) {
			LOG.error("Thread died: " + t.toString(), e);
		}

	}

	private static class NamedThreadFactory implements ThreadFactory {

		private final AtomicInteger counter = new AtomicInteger(0);
		private final ThreadGroup threadGroup;
		private final String namePrefix;

		public NamedThreadFactory (final ThreadGroup threadGroup, final String namePrefix) {
			this.threadGroup = threadGroup;
			this.namePrefix = namePrefix;
		}

		@Override
		public Thread newThread (final Runnable r) {
			Thread t = new Thread(this.threadGroup, r, this.namePrefix + this.counter.getAndIncrement());
			t.setDaemon(true);
			t.setPriority(Thread.NORM_PRIORITY);
			return t;
		}

	}

}
