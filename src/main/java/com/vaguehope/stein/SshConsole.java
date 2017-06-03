package com.vaguehope.stein;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.Terminal;

public abstract class SshConsole implements Runnable {

	private static final long PRINT_CYCLE = 500L;
	private static final long SHUTDOWN_TIMEOUT = 5000L;

	private static final Logger LOG = LoggerFactory.getLogger(SshConsole.class);

	private final String name;
	private final Environment env;
	private final Terminal terminal;
	private final ExitCallback callback;

	private final Screen screen;
	private final TextGraphics textGraphics;

	private final AtomicBoolean up = new AtomicBoolean(true);
	private final CountDownLatch shutdownLatch = new CountDownLatch(1);
	private boolean inited = false;
	private long lastPrint = 0L;

	public SshConsole (final String name, final Environment env, final Terminal terminal, final ExitCallback callback) throws IOException {
		this.name = name;
		this.env = env;
		this.terminal = terminal;
		this.callback = callback;
		this.screen = new TerminalScreen(terminal);
		this.textGraphics = this.screen.newTextGraphics();
	}

	public void stopAndJoin () {
		if (this.up.compareAndSet(true, false)) {
			LOG.info("Killing session {}...", this.name);
			Quietly.await(this.shutdownLatch, SHUTDOWN_TIMEOUT, TimeUnit.MILLISECONDS);
		}
	}

	protected void scheduleQuit (final String reason) {
		if (this.up.get()) LOG.info("Killing session {}: {} ...", this.name, reason);
		this.up.set(false);
	}

	protected Environment getEnv () {
		return this.env;
	}

	private void init () throws IOException {
		if (!this.inited) {
			this.inited = true; // Only try once.
			initScreen(this.screen);
			this.screen.startScreen();
			LOG.info("Session created: {}", this.name);
		}
	}

	@Override
	public void run () {
		try {
			init();
			while (this.up.get()) {
				tick();
				Thread.sleep(10); // FIXME I wish terminal.readInput() used blocking-with-timeout IO.
			}
		}
		catch (final Throwable t) { // NOSONAR Report all errors and clean up session.
			LOG.error("Session error.", t);
			scheduleQuit("session error");
		}
		finally {
			try {
				this.screen.stopScreen();
				this.terminal.flush(); // Workaround as stopScreen() does not trigger flush().
			}
			catch (IOException e) {
				LOG.error("Shutdown error.", e);
			}
			finally {
				this.callback.onExit(0, "baibai!");
				LOG.info("Session destroyed: {}", this.name);
				this.shutdownLatch.countDown();
			}
		}
	}

	private void tick () throws IOException, InterruptedException {
		if (readInput() || System.currentTimeMillis() - this.lastPrint > PRINT_CYCLE) {
			printScreen();
			this.lastPrint = System.currentTimeMillis();
		}
	}

	private boolean readInput () throws IOException, InterruptedException {
		boolean changed = false;
		KeyStroke k;
		while ((k = this.terminal.pollInput()) != null) {
			changed = readInput(k, this.up) || changed;
		}
		return changed;
	}

	private void printScreen () throws IOException {
		this.screen.doResizeIfNecessary();

		this.screen.clear();
		writeScreen(this.screen, this.textGraphics);
		this.screen.refresh();
	}

	protected abstract boolean readInput (KeyStroke k, AtomicBoolean alive) throws InterruptedException;

	protected abstract void initScreen (Screen scr);

	protected abstract void writeScreen (Screen scr, TextGraphics tg);

}
