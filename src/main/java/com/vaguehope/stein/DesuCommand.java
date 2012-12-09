package com.vaguehope.stein;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.lanterna.TerminalFacade;
import com.googlecode.lanterna.input.Key;
import com.googlecode.lanterna.input.Key.Kind;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.ScreenCharacterStyle;
import com.googlecode.lanterna.screen.ScreenWriter;
import com.googlecode.lanterna.terminal.Terminal;

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
	private Terminal terminal;
	private Screen screen;
	private ScreenWriter screenWriter;

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
		this.terminal = TerminalFacade.createTextTerminal(this.in, this.out, Charset.forName("UTF8"));
		this.screen = TerminalFacade.createScreen(this.terminal);
		this.screen.startScreen();
		this.screen.setCursorPosition(null);

		this.screenWriter = new ScreenWriter(this.screen);

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
		Key k;
		while ((k = this.terminal.readInput()) != null) {
			if (k.getKind() == Kind.NormalKey) {
				if (k.getCharacter() == 'q') {
					quit();
					return false; // We are quitting.  Do not try and update UI.
				}
				this.inputCounter.incrementAndGet();
				changed = true;
			}
		}
		return changed;
	}

	private void printScreen () {
		if (this.terminal == null) return;
		if (this.screen.resizePending()) {
			this.screenWriter.fillScreen(' ');
			this.screen.refresh();
		}
		this.screen.clear();
		this.screenWriter.drawString(0, 0, "" + (System.currentTimeMillis() / 1000L)); // NOSONAR not a magic number.
		this.screenWriter.drawString(1, 2, "Hello desu~", ScreenCharacterStyle.Bold);
		this.screenWriter.drawString(1, 3, "env: " + this.environment.getEnv()); // NOSONAR not a magic number.
		this.screenWriter.drawString(1, 4, "debug: " + this.inputCounter.get()); // NOSONAR not a magic number.
		this.screen.refresh();
	}

	/**
	 * Terminate Lanterna wrapper and discard instance. Schedule thread for
	 * shutdown. Must be called on this console's thread.
	 */
	private void quit () {
		if (Thread.currentThread().getId() != this.thread.getId()) throw new IllegalStateException("quit() called on incorrect thread.");
		this.screenWriter = null;
		this.screen.stopScreen();
		this.screen = null;
		this.terminal = null;
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

}
