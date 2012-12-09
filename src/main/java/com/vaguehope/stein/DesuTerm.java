package com.vaguehope.stein;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.lanterna.TerminalFacade;
import com.googlecode.lanterna.input.Key;
import com.googlecode.lanterna.input.Key.Kind;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.ScreenCharacterStyle;
import com.googlecode.lanterna.screen.ScreenWriter;
import com.googlecode.lanterna.terminal.Terminal;

public class DesuTerm extends Thread {

	private static final long POLL_CYCLE = 200L;
	private static final long PRINT_CYCLE = 1000L;
	private static final long SHUTDOWN_TIMEOUT = 5000L;

	private static final Logger LOG = LoggerFactory.getLogger(DesuTerm.class);
	private static final AtomicInteger SESSION_COUNTER = new AtomicInteger();

	private final Environment env;
	private final Terminal terminal;
	private final ExitCallback callback;

	private final Screen screen;
	private final ScreenWriter screenWriter;
	private final AtomicBoolean up = new AtomicBoolean(true);
	private final AtomicInteger inputCounter = new AtomicInteger();

	public DesuTerm (Environment env, Terminal terminal, ExitCallback callback) {
		super("DesuTerm" + SESSION_COUNTER.incrementAndGet());
		this.setDaemon(true);
		this.env = env;
		this.terminal = terminal;
		this.callback = callback;
		this.screen = TerminalFacade.createScreen(this.terminal);
		this.screenWriter = new ScreenWriter(this.screen);
	}

	public void stopAndJoin () {
		if (this.up.compareAndSet(true, false)) {
			LOG.info("Killing session...");
			joinQuietly(SHUTDOWN_TIMEOUT);
		}
	}

	private void quit () {
		this.up.set(false);
	}

	@Override
	public void run () {
		LOG.info("Session created.");
		this.screen.startScreen();
		long lastPrint = 0L;
		while (this.up.get()) {
			boolean changed = readInput();
			if (changed || System.currentTimeMillis() - lastPrint > PRINT_CYCLE) {
				printScreen();
				lastPrint = System.currentTimeMillis();
			}
			sleepQuietly(POLL_CYCLE); // TODO measure how long cycle took and sleep remaining.
		}
		this.screen.stopScreen();
		this.terminal.flush(); // Workaround as stopScreen() does not trigger flush().
		this.callback.onExit(0);
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
		this.screenWriter.drawString(1, 3, "env: " + this.env.getEnv()); // NOSONAR not a magic number.
		this.screenWriter.drawString(1, 4, "debug: " + this.inputCounter.get()); // NOSONAR not a magic number.
		this.screen.refresh();
	}

	private void joinQuietly (long timeoutInMilliseconds) {
		try {
			this.join(timeoutInMilliseconds);
		}
		catch (InterruptedException e) {/* Do not care. */}
	}

	private static void sleepQuietly (long time) {
		try {
			Thread.sleep(time);
		}
		catch (InterruptedException e) {/* Do not care. */}
	}

}
