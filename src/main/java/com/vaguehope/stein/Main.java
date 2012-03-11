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

	private static final int SSHD_PORT = 14022;
	private static final String HOSTKEY_NAME = "hostkey.ser";
	private static final long IDLE_TIMEOUT = 24 * 60 * 60 * 1000L; // A day.

	private static final Logger LOG = LoggerFactory.getLogger(DesuCommand.class);

	private Main () {}

	public static void main (String[] args) throws IOException, InterruptedException {
		SshServer sshd = SshServer.setUpDefaultServer();
		sshd.setPort(SSHD_PORT);
		sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(HOSTKEY_NAME));
		sshd.setPasswordAuthenticator(new PasswordAuthenticator() {
			@Override
			public boolean authenticate (String username, String password, ServerSession session) {
				return username != null && username.equals(password); // FIXME dodge test auth.
			}
		});
		sshd.setShellFactory(new DesuCommandFactory());
		sshd.getProperties().put(ServerFactoryManager.IDLE_TIMEOUT, String.valueOf(IDLE_TIMEOUT));
		sshd.start();

		LOG.info("Server ready on port {}.", String.valueOf(SSHD_PORT));
		new CountDownLatch(1).await();
	}

}
