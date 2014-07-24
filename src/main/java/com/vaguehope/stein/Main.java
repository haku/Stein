package com.vaguehope.stein;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import org.apache.sshd.SshServer;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.ServerFactoryManager;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Main {

	private static final int SSHD_PORT = 15022; // TODO make config.
	private static final String HOSTKEY_NAME = "hostkey.ser";
	private static final long IDLE_TIMEOUT = 24 * 60 * 60 * 1000L; // A day.

	private static final Logger LOG = LoggerFactory.getLogger(Main.class);

	private Main () {
		throw new AssertionError();
	}

	public static void main (final String[] args) throws IOException, InterruptedException {
		SshServer sshd = SshServer.setUpDefaultServer();
		sshd.setPort(SSHD_PORT);
		sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(HOSTKEY_NAME));
		sshd.setPasswordAuthenticator(new TestPasswordAuthenticator());
		sshd.setShellFactory(new ConsoleCommandFactory());
		sshd.getProperties().put(ServerFactoryManager.IDLE_TIMEOUT, String.valueOf(IDLE_TIMEOUT));
		sshd.start();

		LOG.info("Server ready on port {}.", Integer.valueOf(sshd.getPort()));
		new CountDownLatch(1).await();
	}

	private static final class TestPasswordAuthenticator implements PasswordAuthenticator {

		public TestPasswordAuthenticator () {}

		@Override
		public boolean authenticate (final String username, final String password, final ServerSession session) {
			return username != null && username.equals(password); // FIXME dodge test auth.
		}

	}

}
