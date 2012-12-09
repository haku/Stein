package com.vaguehope.stein;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class Quietly {

	private Quietly () {
		throw new AssertionError();
	}

	public static boolean await (CountDownLatch latch, long timeout, TimeUnit unit) {
		try {
			return latch.await(timeout, unit);
		}
		catch (InterruptedException e) {
			return false;
		}
	}

}
