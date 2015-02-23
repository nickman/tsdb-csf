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
package com.heliosapm.opentsdb.client.aop;

import java.util.concurrent.atomic.AtomicInteger;

import com.heliosapm.opentsdb.client.opentsdb.opt.Measurement;

/**
 * <p>Title: EmptyShorthandInterceptor</p>
 * <p>Description: An empty {@link ShorthandInterceptor} implementation</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.EmptyShorthandInterceptor</code></p>
 */

public class EmptyShorthandInterceptor implements ShorthandInterceptor {
	/** The parent OTMetric long hash code */
	protected final long metricId;
	/** The enabled measurement mask */
	protected final int mask;
	/** The concurrency counter */
	protected final AtomicInteger concurrencyCounter;
	
	
	/** The concurrency counter accessor for the calling thread */
	public static final ThreadLocal<AtomicInteger> concurrencyAccessor = new ThreadLocal<AtomicInteger>();  


	/**
	 * Creates a new EmptyShorthandInterceptor
	 * @param metricId The parent OTMetric long hash code
	 * @param mask The enabled measurement mask
	 */
	public EmptyShorthandInterceptor(long metricId, int mask) {
		super();
		this.metricId = metricId;
		this.mask = mask;
		concurrencyCounter = Measurement.CONCURRENT.isEnabledFor(this.mask) ? new AtomicInteger() : null;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandInterceptor#enter(int, long)
	 */
	@Override
	public long[] enter(final int mask, final long parentMetricId) {
		if(Measurement.CONCURRENT.isEnabledFor(mask)) {
			final AtomicInteger ai = concurrencyAccessor.get();
			try {
				concurrencyAccessor.set(ai);
				return Measurement.enter(mask, parentMetricId);
			} finally {
				if(ai!=null) concurrencyAccessor.set(ai);
				else concurrencyAccessor.remove();
			}			
		}		
		return Measurement.enter(mask, parentMetricId);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandInterceptor#exit(long[])
	 */
	@Override
	public void exit(final long[] entryState) {
		Measurement.exit(entryState);
		// enqueue state buffer
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandInterceptor#texit(long)
	 */
	@Override
	public void texit(long parentMetricId) {
		// enque abend		
	}

}
