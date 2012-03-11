package com.vaguehope.stein;

import java.util.concurrent.CountDownLatch;

import org.apache.sshd.SshServer;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;

public class Main {

	private static final int SSHD_PORT = 14022;
	private static final String HOSTKEY_NAME = "hostkey.ser";

	public static void main (String[] args) throws Exception {
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
		sshd.start();

		new CountDownLatch(1).await();
	}

}
