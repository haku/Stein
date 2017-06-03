package com.vaguehope.stein;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SessionAware;
import org.apache.sshd.server.Signal;
import org.apache.sshd.server.SignalListener;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.terminal.Terminal;
import com.googlecode.lanterna.terminal.ansi.UnixTerminal;
import com.googlecode.lanterna.terminal.ansi.UnixTerminalSizeQuerier;

public class ConsoleCommand implements Command, SessionAware {

	private static final AtomicInteger COUNTER = new AtomicInteger(0);

	private final ExecutorService es;

	private InputStream in;
	private OutputStream out;
	private ExitCallback callback;

	private volatile DesuTerm term = null;

	public ConsoleCommand (final ExecutorService es) {
		this.es = es;
	}

	@Override
	public void setInputStream (final InputStream in) {
		this.in = in;
	}

	@Override
	public void setOutputStream (final OutputStream out) {
		this.out = out;
	}

	@Override
	public void setErrorStream (final OutputStream err) {/* Unused. */}

	@Override
	public void setExitCallback (final ExitCallback callback) {
		this.callback = callback;
	}

	@Override
	public void setSession (final ServerSession session) {/* Unused. */}

	@Override
	public void start (final Environment env) throws IOException {
		final Terminal terminal = new SshTerminal(this.in, this.out, Charset.forName("UTF8"), env);
		this.term = new DesuTerm("desuTerm" + COUNTER.getAndIncrement(), env, terminal, this.callback);
		this.es.submit(this.term);
	}

	@Override
	public void destroy () {
		if (this.term == null) throw new IllegalStateException();
		this.term.stopAndJoin();
	}

	private static class SshTerminalSizeQuerier implements UnixTerminalSizeQuerier {

		private final Environment env;

		public SshTerminalSizeQuerier (final Environment env) {
			this.env = env;
		}

		@Override
		public TerminalSize queryTerminalSize () {
			final String colsStr = this.env.getEnv().get(Environment.ENV_COLUMNS);
			final int cols = colsStr != null ? Integer.parseInt(colsStr) : 80;
			final String linesStr = this.env.getEnv().get(Environment.ENV_LINES);
			final int rows = linesStr != null ? Integer.parseInt(linesStr) : 22;
			return new TerminalSize(cols, rows);
		}

	}

	private static class SshTerminal extends UnixTerminal implements SignalListener {

		private static final Logger LOG = LoggerFactory.getLogger(ConsoleCommand.SshTerminal.class);

		public SshTerminal (final InputStream terminalInput, final OutputStream terminalOutput, final Charset terminalCharset, final Environment env) throws IOException {
			super(terminalInput, terminalOutput, terminalCharset, new SshTerminalSizeQuerier(env));
			env.addSignalListener(this, Signal.WINCH);
		}

		@Override
		public void signal (final Signal signal) {
			switch (signal) {
				case WINCH:
					notifyResized();
					break;
				default:
			}
		}

		public void notifyResized () {
			try {
				final TerminalSize size = getTerminalSize();
				onResized(size.getColumns(), size.getRows());
			}
			catch (final IOException e) {
				LOG.warn("Failed to read terminal size after being notified of a resize.", e);
			}
		}

	}

}
