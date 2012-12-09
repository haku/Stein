package com.vaguehope.stein;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sshd.common.Factory;
import org.apache.sshd.server.Command;

public class ConsoleCommandFactory implements Factory<Command> {

	private static final String THREAD_NAME_PREFIX = "ConsoleSch";
	private static final int CLIENT_THREADS = 5;

	private final ScheduledExecutorService schEx = Executors.newScheduledThreadPool(CLIENT_THREADS, new NamedThreadFactory(THREAD_NAME_PREFIX));

	@Override
	public Command create () {
		return new ConsoleCommand(this.schEx);
	}

	private static class NamedThreadFactory implements ThreadFactory {

		private final AtomicInteger counter = new AtomicInteger(0);
		private final String namePrefix;

		public NamedThreadFactory (String namePrefix) {
			this.namePrefix = namePrefix;
		}

		@Override
		public Thread newThread (Runnable r) {
			SecurityManager s = System.getSecurityManager();
			Thread t = new Thread(
					s != null ? s.getThreadGroup() : Thread.currentThread().getThreadGroup(),
					r,
					this.namePrefix + this.counter.getAndIncrement());
			t.setDaemon(true);
			t.setPriority(Thread.NORM_PRIORITY);
			return t;
		}

	}

}
