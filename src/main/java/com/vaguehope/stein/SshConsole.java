package com.vaguehope.stein;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.lanterna.TerminalFacade;
import com.googlecode.lanterna.input.Key;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.ScreenWriter;
import com.googlecode.lanterna.terminal.Terminal;

public abstract class SshConsole implements Runnable {

	private static final long POLL_CYCLE = 200L;
	private static final long PRINT_CYCLE = 500L;
	private static final long SHUTDOWN_TIMEOUT = 5000L;

	private static final Logger LOG = LoggerFactory.getLogger(SshConsole.class);

	private final String name;
	private final Environment env;
	private final Terminal terminal;
	private final ExitCallback callback;

	private final Screen screen;
	private final ScreenWriter screenWriter;

	private final AtomicBoolean up = new AtomicBoolean(true);
	private final CountDownLatch shutdownLatch = new CountDownLatch(1);
	private ScheduledFuture<?> schFuture;
	private boolean inited = false;
	private long lastPrint = 0L;

	public SshConsole (final String name, final Environment env, final Terminal terminal, final ExitCallback callback) {
		this.name = name;
		this.env = env;
		this.terminal = terminal;
		this.callback = callback;
		this.screen = TerminalFacade.createScreen(this.terminal);
		this.screenWriter = new ScreenWriter(this.screen);
	}

	public void schedule (final ScheduledExecutorService schEx) {
		if (this.schFuture != null) throw new IllegalStateException("Already scheduled: " + this.schFuture);
		this.schFuture = schEx.scheduleWithFixedDelay(this, 0L, POLL_CYCLE, TimeUnit.MILLISECONDS);
	}

	public void stopAndJoin () {
		if (this.up.compareAndSet(true, false)) {
			LOG.info("Killing session {}...", this.name);
			Quietly.await(this.shutdownLatch, SHUTDOWN_TIMEOUT, TimeUnit.MILLISECONDS);
		}
	}

	protected void scheduleQuit () {
		this.up.set(false);
	}

	protected Environment getEnv () {
		return this.env;
	}

	private void init () {
		if (!this.inited) {
			this.screen.startScreen();
			this.inited = true;
			LOG.info("Session created: {}", this.name);
		}
	}

	@Override
	public void run () {
		init();
		if (this.up.get()) {
			if (readInput() || System.currentTimeMillis() - this.lastPrint > PRINT_CYCLE) {
				printScreen();
				this.lastPrint = System.currentTimeMillis();
			}
		}
		else {
			this.schFuture.cancel(false);
			this.screen.stopScreen();
			this.terminal.flush(); // Workaround as stopScreen() does not trigger flush().
			this.callback.onExit(0, "baibai!");
			LOG.info("Session destroyed: {}", this.name);
			this.shutdownLatch.countDown();
		}
	}

	private boolean readInput () {
		boolean changed = false;
		Key k;
		while ((k = this.terminal.readInput()) != null) {
			changed = readInput(k);
		}
		return changed;
	}

	private void printScreen () {
		if (this.screen.resizePending()) {
			this.screenWriter.fillScreen(' ');
			this.screen.refresh();
		}
		this.screen.clear();
		writeScreen(this.screenWriter);
		this.screen.refresh();
	}

	protected abstract boolean readInput (Key k);

	protected abstract void writeScreen (ScreenWriter writer);

}
