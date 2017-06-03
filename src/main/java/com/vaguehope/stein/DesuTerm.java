package com.vaguehope.stein;

import java.io.IOException;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalTextUtils;
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
		int i = 0;
		final long utime = System.currentTimeMillis() / 1000L;
		tg.putString(0, i++, "" + utime);
		i++;

		final String flashing = "こねちわ　～";
		final String flashingSpaces = repeatString(" ", TerminalTextUtils.getColumnWidth(flashing));
		final String fixed = " abc";

		if (utime % 2 == 0) {
			tg.putString(1, i++, flashing + fixed);
		}
		else {
			tg.putString(1, i++, flashingSpaces + fixed);
		}
		tg.putString(30, i - 1, "123");

		tg.putString(1, i++, "Hello desu~", SGR.BOLD);
		tg.putString(1, i++, "size: " + scr.getTerminalSize());
		tg.putString(1, i++, "debug: " + this.inputCounter.get());
		for (final Entry<String, String> e : getEnv().getEnv().entrySet()) {
			tg.putString(1, i++, String.format("env: %s=%s", e.getKey(), e.getValue()));
		}
	}

	private String repeatString (final String s, final int length) {
		String ret = "";
		for (int i = 0; i < length; i++) {
			ret += s;
		}
		return ret;
	}

}
