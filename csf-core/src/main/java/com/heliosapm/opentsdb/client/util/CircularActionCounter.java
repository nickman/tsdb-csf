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

package com.heliosapm.opentsdb.client.util;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>Title: CircularActionBuffer</p>
 * <p>Description: A circular counter that executes a defined task when it comes full circle.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.util.CircularActionBuffer</code></p>
 */

public class CircularActionCounter {
	/** The counter */
	private final AtomicInteger counter = new AtomicInteger();
	/** The counter size */
	final int counterSize;
	/** The reset value */
	final int resetTo;
	/** The action to fire on reset */
	final Runnable action;

	/**
	 * Creates a new CircularActionCounter
	 * @param counterSize The number of increments in 
	 * @param resetTo The value to reset to once the counter size is reached
	 * @param startAt The value to start the counter at
	 * @param action The action to fire on reset
	 */
	public CircularActionCounter(final int counterSize, final int resetTo, final int startAt, final Runnable action) {
		counter.set(startAt);
		this.counterSize = counterSize;
		this.resetTo = resetTo;
		this.action = action;
	}
	
	/**
	 * Creates a new CircularActionCounter which starts at 0 and resets to 0
	 * @param counterSize The number of increments in 
	 * @param action The action to fire on reset
	 */
	public CircularActionCounter(final int counterSize, final Runnable action) {
		this(counterSize, 0, 0, action);
	}

	
	/**
	 * Increments the counter
	 * @return the value incremented to
	 */
	public int incr() {
		final int current;
		synchronized(counter) {
			current = counter.incrementAndGet();
			if(current > counterSize) {
				reset();
				return counter.incrementAndGet();
			}
		}
		return current;
	}
	
	/**
	 * Resets the counter and fires the action
	 */
	protected void reset() {
		counter.set(resetTo);
		if(action!=null) action.run();
	}
	
	public static void main(String[] args) {
		log("CircularActionCounter Test");
		CircularActionCounter cac = new CircularActionCounter(4, new Runnable() { public void run() {log("BANG !");} });
		for(int i = 0; i < 10; i++) {
			int x = cac.incr();
			log("X: " + x);
		}
	}
	
	public static void log(Object msg) {
		System.out.println(msg);
	}
 
}
