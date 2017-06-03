package com.vaguehope.stein;

import java.io.IOException;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalTextUtils;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.Terminal;

public class DesuTerm extends SshConsole {

	private static final Logger LOG = LoggerFactory.getLogger(DesuTerm.class);

	private final AtomicInteger inputCounter = new AtomicInteger();
	private WindowBasedTextGUI gui;

	public DesuTerm (final String name, final Environment env, final Terminal terminal, final ExitCallback callback) throws IOException {
		super(name, env, terminal, callback);
	}

	@Override
	protected void initScreen (final Screen scr) {
		scr.setCursorPosition(null);
		this.gui = new MultiWindowTextGUI(scr);
	}

	@Override
	protected boolean readInput (final KeyStroke k, final AtomicBoolean alive) throws InterruptedException {
		if (k.getKeyType() == KeyType.Character) {
			if (k.getCharacter() == 'q') {
				scheduleQuit("User pressed q.");
				return false; // We are quitting.  Do not try and update UI.
			}
			else if (k.getCharacter() == 'd') {
				LOG.info("Showing dialog...");
				showDialog(this.gui, alive);
				LOG.info("Dialog shown.");
				return true;
			}
			this.inputCounter.incrementAndGet();
			return true;
		}
		return false;
	}

	private void showDialog (final WindowBasedTextGUI gui, final AtomicBoolean alive) throws InterruptedException {
		gui.getGUIThread().invokeAndWait(new Runnable() {
			boolean run = false; // FIXME workaround for https://github.com/mabe02/lanterna/issues/310
			@Override
			public void run () {
				if (this.run) return;
				this.run = true;
				final MessageDialogButton result = MessageDialog.showMessageDialog(gui, "foo", "desu", MessageDialogButton.OK, MessageDialogButton.Cancel);
				LOG.info("Dialog result: {}", result);
			}
		});
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
