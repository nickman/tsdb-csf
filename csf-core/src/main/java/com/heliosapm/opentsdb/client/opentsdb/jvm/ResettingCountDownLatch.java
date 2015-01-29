/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.heliosapm.opentsdb.client.opentsdb.jvm;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p>Title: ResettingCountDownLatch</p>
 * <p>Description: A latch that resets once it is counted down to zero</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.ResettingCountDownLatch</code></p>
 */

public class ResettingCountDownLatch {
	protected final AtomicReference<CountDownLatch> latch = new AtomicReference<CountDownLatch>(null);
	protected final int count;
	
	/** A noop latch. Does nothing. Can be shared. Used when the count starts at zero (or less) */
	public static final ResettingCountDownLatch NOOP_INSTANCE = new NoopResettingCountDownLatch();
	
	/**
	 * Returns a new ResettingCountDownLatch for the passed count
	 * @param count The latch count
	 * @return the new latch
	 */
	public static ResettingCountDownLatch newInstance(final int count) {
		if(count<1) return NOOP_INSTANCE;
		return new ResettingCountDownLatch(count);
		
	}
	
	/**
	 * Creates a new ResettingCountDownLatch
	 * @param count The count down
	 */
	private ResettingCountDownLatch(final int count) {		
		this.count = count+1;
		latch.set(new CountDownLatch(this.count));
	}
	
	/**
	 * Causes the current thread to wait until the latch has counted down to zero, unless the thread is interrupted. 
	 * @throws InterruptedException if the current thread is interrupted while waiting
	 */
	public void await() throws InterruptedException {
		latch.get().await();
	}
	
	/**
	 * Causes the current thread to wait until the latch has counted down to zero, unless the thread is interrupted, or the specified waiting time elapses. 
	 * @param timeout the maximum time to wait
	 * @param unit the time unit of the timeout argument 
	 * @return true if the count reached zero and false if the waiting time elapsed before the count reached zero 
	 * @throws InterruptedException if the current thread is interrupted while waiting
	 */
	public boolean await(final long timeout, final TimeUnit unit) throws InterruptedException {
		return latch.get().await(timeout, unit);
	}
	
	/**
	 * Returns the current count. 
	 * @return the current count
	 */
	public long getCount() {
		return latch.get().getCount();
	}
	
	/**
	 * Decrements the count of the latch, releasing all waiting threads if the count reaches zero, then allocating a new CountDownLatch. 
	 * @return The count of the original latch after decrementing.
	 */
	public long countDown() {
		CountDownLatch cdl = latch.get();
		cdl.countDown();
		long cnt = cdl.getCount();
		if(cnt==1) {
			latch.set(new CountDownLatch(count));
			cdl.countDown();			
		}
		return cdl.getCount();
	}
	
	/**
	 * <p>Title: NoopResettingCountDownLatch</p>
	 * <p>Description: A No Op ResettingCountDownLatch</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.ResettingCountDownLatch.NoopResettingCountDownLatch</code></p>
	 */
	private static class NoopResettingCountDownLatch extends ResettingCountDownLatch {
		public NoopResettingCountDownLatch() {
			super(-1);
		}
		@Override
		public void await() {
			/* No Op */
		}
		@Override
		public boolean await(final long timeout, final TimeUnit unit) {			
			return true;
		}		
		@Override
		public long countDown() {			
			return 0L;
		}
		@Override
		public long getCount() {			
			return 0L;
		}
	}

}
