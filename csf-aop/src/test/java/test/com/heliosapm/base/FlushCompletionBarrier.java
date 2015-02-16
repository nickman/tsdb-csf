/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2013, Helios Development Group and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org. 
 *
 */
package test.com.heliosapm.base;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.heliosapm.accumulator.PeriodEventCompletionListener;

/**
 * <p>Title: FlushCompletionBarrier</p>
 * <p>Description: A flush completion listener that acts as a barrier which is dropped when the flush completes</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.shorthand.FlushCompletionBarrier</code></p>
 */

public class FlushCompletionBarrier implements PeriodEventCompletionListener {
	/** The barrier to wait on completion */
	protected final CyclicBarrier barrier = new CyclicBarrier(2);
	/** The timeout to wait for flush completion */
	private final long timeout;
	/** The unit of the timeout */
	private final TimeUnit unit;
	
	/**
	 * Out printer
	 * @param fmt the message format
	 * @param args the message values
	 */
	public static void log(String fmt, Object...args) {
		System.out.println(String.format("[FlushCompletionBarrier]" + fmt, args));
	}
	
	/**
	 * Creates a new FlushCompletionBarrier
	 * @param timeout The timeout to wait for flush completion
	 * @param unit The unit of the timeout
	 */
	public FlushCompletionBarrier(long timeout, TimeUnit unit) {
		super();
		this.timeout = timeout;
		this.unit = unit;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.accumulator.PeriodEventListener#onNewPeriod(long, long, long, long)
	 */
	@Override
	public void onNewPeriod(long newStartTime, long newEndTime, long priorStartTime, long priorEndTime) {
		log("Flush Started");
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.shorthand.accumulator.PeriodEventCompletionListener#periodEventComplete(long[], long)
	 */
	@Override
	public void periodEventComplete(long[] period, long elapsedNanos) {
		if(barrier.isBroken()) {
			log("Barrier Broken. Returning....");
			return;
		}
		try {
			log("Thread [%s] waiting on completion barrier", Thread.currentThread().getName());
			barrier.await(10, TimeUnit.NANOSECONDS);
			log("Thread [%s] passed completion barrier", Thread.currentThread().getName());
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
		}
	}
	
	/**
	 * Resets the barrier
	 */
	public void reset() {
		barrier.reset();
	}
	
	/**
	 * Waits for the flush to complete
	 * @throws InterruptedException
	 * @throws BrokenBarrierException
	 * @throws TimeoutException
	 */
	public void waitForCompletion() throws InterruptedException, BrokenBarrierException, TimeoutException {
		log("Thread [%s] waiting on completion barrier", Thread.currentThread().getName());
		barrier.await(timeout, unit);
		log("Thread [%s] passed completion barrier", Thread.currentThread().getName());
	}
	

}
