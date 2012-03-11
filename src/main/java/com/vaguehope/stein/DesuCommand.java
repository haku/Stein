package com.vaguehope.stein;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SessionAware;
import org.apache.sshd.server.session.ServerSession;
import org.lantern.LanternException;
import org.lantern.LanternTerminal;
import org.lantern.TerminalFactory;
import org.lantern.input.Key;
import org.lantern.input.Key.Kind;
import org.lantern.screen.Screen;
import org.lantern.screen.ScreenWriter;
import org.lantern.terminal.Terminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DesuCommand implements Command, SessionAware, Runnable {

	private static final long POLL_CYCLE = 200L;
	private static final long PRINT_CYCLE = 1000L;
	private static final long SHUTDOWN_TIMEOUT = 5000L;

	private static final Logger LOG = LoggerFactory.getLogger(DesuCommand.class);

	private static final AtomicInteger SESSION_COUNTER = new AtomicInteger();

	private InputStream in;
	private OutputStream out;
	private ExitCallback callback;

	private Thread thread;
	private final AtomicBoolean alive = new AtomicBoolean(true);

	private Environment environment;
	private LanternTerminal lterm;
	private ScreenWriter writer;

	private final AtomicInteger inputCounter = new AtomicInteger();

	@Override
	public void setInputStream (InputStream in) {
		this.in = in;
	}

	@Override
	public void setOutputStream (OutputStream out) {
		this.out = out;
	}

	@Override
	public void setErrorStream (OutputStream err) {/* Unused. */}

	@Override
	public void setExitCallback (ExitCallback callback) {
		this.callback = callback;
	}

	@Override
	public void setSession (ServerSession session) {/* Unused. */}

	@Override
	public void start (Environment env) throws IOException {
		this.environment = env;
		try {
			this.lterm = new LanternTerminal(new TerminalFactory.Common(), this.in, new FlushingOutputStream(this.out), Charset.forName("UTF8"));
			this.lterm.start();
		}
		catch (LanternException e) {
			throw new IOException("Failed to start Lanterna session.", e);
		}

		this.writer = new ScreenWriter(this.lterm.getScreen());

		/*
		 * 1 thread per client could become a scaling issue. Given it will spend
		 * most of its time sleeping, it could be redesigned to use a shared
		 * pool of threads.
		 */
		this.thread = new Thread(this, "DesuCommand" + SESSION_COUNTER.incrementAndGet()); // TODO better name.
		this.thread.start();
	}

	@Override
	public void run () {
		LOG.info("Session created.");
		long lastPrint = 0L;
		try {
			while (this.alive.get()) {
				boolean changed = readInput();
				if (changed || System.currentTimeMillis() - lastPrint > PRINT_CYCLE) {
					printScreen();
					lastPrint = System.currentTimeMillis();
				}
				Thread.sleep(POLL_CYCLE); // TODO measure how long cycle took and sleep remaining.
			}
		}
		catch (InterruptedException e) {/* Do not care. */}
		LOG.info("Session destroyed.");
	}

	private boolean readInput () {
		boolean changed = false;
		try {
			Key k;
			while ((k = this.lterm.getUnderlyingTerminal().readInput()) != null) {
				if (k.getKind() == Kind.NormalKey) {
					if (k.getCharacter() == 'q') {
						quit();
						return false; // We are quitting.  Do not try and update UI.
					}
					this.inputCounter.incrementAndGet();
					changed = true;
				}
			}
		}
		catch (LanternException e) {
			LOG.warn("Failed to read terminal input.", e);
		}
		return changed;
	}

	private void printScreen () {
		if (this.lterm == null) return;
		Screen screen = this.lterm.getScreen();
		try {
			this.writer.drawString(0, 0, "" + (System.currentTimeMillis() / 1000L)); // NOSONAR not a magic number.
			this.writer.drawString(1, 2, "Hello desu~", Terminal.Style.Bold);
			this.writer.drawString(1, 3, "env: " + this.environment.getEnv()); // NOSONAR not a magic number.
			this.writer.drawString(1, 4, "debug: " + this.inputCounter.get()); // NOSONAR not a magic number.
			screen.refresh();
		}
		catch (LanternException e) {
			LOG.warn("Failed to write to terminal.", e);
		}
	}

	/**
	 * Terminate Lanterna wrapper and discard instance.
	 * Schedule thread for shutdown.
	 * Must be called on this console's thread.
	 */
	private void quit () {
		if (Thread.currentThread().getId() != this.thread.getId()) throw new IllegalStateException("quit() called on incorrect thread.");
		try {
			this.lterm.stopAndRestoreTerminal();
			this.lterm = null;
		}
		catch (LanternException e) {
			LOG.warn("Failed to cleanly detach Lanterna terminal.", e);
		}
		this.alive.set(false);
		this.callback.onExit(0);
	}

	@Override
	public void destroy () {
		try {
			if (this.alive.get()) {
				this.alive.set(false);
				this.thread.join(SHUTDOWN_TIMEOUT);
			}
		}
		catch (InterruptedException e) {/* Do not care. */}
	}

	private static class FlushingOutputStream extends FilterOutputStream {

		public FlushingOutputStream (OutputStream out) {
			super(out);
		}

		@Override
		public void write (int b) throws IOException {
			super.write(b);
			flush();
		}

	}

}
