package com.vaguehope.stein;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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

public class DesuTerm implements Runnable {

	private static final long POLL_CYCLE = 200L;
	private static final long PRINT_CYCLE = 1000L;
	private static final long SHUTDOWN_TIMEOUT = 5000L;

	private static final Logger LOG = LoggerFactory.getLogger(DesuTerm.class);

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

	private final AtomicInteger inputCounter = new AtomicInteger();

	public DesuTerm (String name, Environment env, Terminal terminal, ExitCallback callback) {
		this.name = name;
		this.env = env;
		this.terminal = terminal;
		this.callback = callback;
		this.screen = TerminalFacade.createScreen(this.terminal);
		this.screenWriter = new ScreenWriter(this.screen);
	}

	public void schedule (ScheduledExecutorService schEx) {
		this.schFuture = schEx.scheduleWithFixedDelay(this, 0L, POLL_CYCLE, TimeUnit.MILLISECONDS);
	}

	public void init () {
		this.screen.startScreen();
		this.inited = true;
		LOG.info("Session created: {}", this.name);
	}

	public void stopAndJoin () {
		if (this.up.compareAndSet(true, false)) {
			LOG.info("Killing session {}...", this.name);
			Quietly.await(this.shutdownLatch, SHUTDOWN_TIMEOUT, TimeUnit.MILLISECONDS);
		}
	}

	private void quit () {
		this.up.set(false);
	}

	@Override
	public void run () {
		if (!this.inited) init();
		if (this.up.get()) {
			boolean changed = readInput();
			if (changed || System.currentTimeMillis() - this.lastPrint > PRINT_CYCLE) {
				printScreen();
				this.lastPrint = System.currentTimeMillis();
			}
		}
		else {
			this.schFuture.cancel(false);
			this.screen.stopScreen();
			this.terminal.flush(); // Workaround as stopScreen() does not trigger flush().
			this.callback.onExit(0);
			LOG.info("Session destroyed: {}", this.name);
			this.shutdownLatch.countDown();
		}
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

}
