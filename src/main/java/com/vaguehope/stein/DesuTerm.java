package com.vaguehope.stein;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.Terminal;

public class DesuTerm extends SshConsole {

	private final AtomicInteger inputCounter = new AtomicInteger();

	public DesuTerm (final String name, final Environment env, final Terminal terminal, final ExitCallback callback) throws IOException {
		super(name, env, terminal, callback);
	}

	@Override
	protected boolean readInput (final KeyStroke k) {
		if (k.getKeyType() == KeyType.Character) {
			if (k.getCharacter() == 'q') {
				scheduleQuit();
				return false; // We are quitting.  Do not try and update UI.
			}
			this.inputCounter.incrementAndGet();
			return true;
		}
		return false;
	}

	@Override
	protected void writeScreen (final Screen scr, final TextGraphics tg) {
		tg.putString(0, 0, "" + (System.currentTimeMillis() / 1000L)); // NOSONAR not a magic number.
		tg.putString(1, 2, "Hello desu~", SGR.BOLD);
		tg.putString(1, 3, "size: " + scr.getTerminalSize()); // NOSONAR not a magic number.
		tg.putString(1, 4, "env: " + getEnv().getEnv()); // NOSONAR not a magic number.
		tg.putString(1, 5, "debug: " + this.inputCounter.get()); // NOSONAR not a magic number.
	}

}
