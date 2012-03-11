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

public class DesuCommand implements Command, SessionAware, Runnable {

	private static final long POLL_CYCLE = 200L;
	private static final long PRINT_CYCLE = 1000L;

	private InputStream in;
	private OutputStream out;
	private OutputStream err;
	private ExitCallback callback;
	private ServerSession session;

	private Thread thread;
	private final AtomicBoolean alive = new AtomicBoolean(true);
	private Environment environment;
	private LanternTerminal lterm;
	private ScreenWriter writer;

	private final AtomicInteger inputCounter = new AtomicInteger();

	public DesuCommand () {}

	@Override
	public void setInputStream (InputStream in) {
		this.in = in;
	}

	@Override
	public void setOutputStream (OutputStream out) {
		this.out = out;
	}

	@Override
	public void setErrorStream (OutputStream err) {
		this.err = err;
	}

	@Override
	public void setExitCallback (ExitCallback callback) {
		this.callback = callback;
	}

	@Override
	public void setSession (ServerSession session) {
		this.session = session;
	}

	@Override
	public void start (Environment env) throws IOException {
		this.environment = env;
		try {
			this.lterm = new LanternTerminal(new TerminalFactory.Common(), this.in, new FlushingOutputStream(this.out), Charset.forName("UTF8"));
			this.lterm.start();
		}
		catch (LanternException e) {
			throw new RuntimeException(e);
		}

		this.writer = new ScreenWriter(this.lterm.getScreen());

		/* 1 thread per client could become a scaling issue.
		 * Given it will spend most of its time sleeping,
		 * it could be redesigned to use a shared pool of threads.
		 */
		this.thread = new Thread(this, "DesuCommand"); // TODO better name.
		this.thread.start();
	}

	@Override
	public void run () {
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
	}

	private boolean readInput () {
		boolean changed = false;
		try {
			Key k;
			while ((k = this.lterm.getUnderlyingTerminal().readInput()) != null) {
				if (k.getKind() == Kind.NormalKey) {
					if (k.getCharacter() == 'q') {
						quit();
					}
					else {
						this.inputCounter.incrementAndGet();
						changed = true;
					}
				}
			}
		}
		catch (LanternException e) {
			e.printStackTrace();
		}
		return changed;
	}

	private void printScreen () {
		Screen screen = this.lterm.getScreen();
		try {
			this.writer.drawString(0, 0, "" + (System.currentTimeMillis() / 1000L));
			this.writer.drawString(1, 2, "Hello desu~", Terminal.Style.Bold);
			this.writer.drawString(1, 3, "env: " + this.environment.getEnv());
			this.writer.drawString(1, 4, "debug: " + this.inputCounter.get());
			screen.refresh();
		}
		catch (LanternException e) {
			e.printStackTrace();
		}
	}

	private void quit () {
		this.callback.onExit(0);
	}

	@Override
	public void destroy () {
		this.alive.set(false);
		try {
			this.thread.interrupt();
		}
		finally {
			try {
				this.lterm.stopAndRestoreTerminal();
			}
			catch (LanternException e) {
				e.printStackTrace();
			}
		}
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
