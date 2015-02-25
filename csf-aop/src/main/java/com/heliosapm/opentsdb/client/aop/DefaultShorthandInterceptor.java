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

import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

import com.heliosapm.opentsdb.client.opentsdb.opt.LongIdOTMetricCache;
import com.heliosapm.opentsdb.client.opentsdb.opt.Measurement;
import com.heliosapm.opentsdb.client.opentsdb.sink.IMetricSink;
import com.heliosapm.opentsdb.client.opentsdb.sink.MetricSink;

/**
 * <p>Title: DefaultShorthandInterceptor</p>
 * <p>Description: An empty {@link ShorthandInterceptor} implementation</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.DefaultShorthandInterceptor</code></p>
 */

public class DefaultShorthandInterceptor implements ShorthandInterceptor {
	/** The parent OTMetric long hash code */
	protected final long metricId;
	/** The enabled measurement mask */
	protected final int mask;
	/** Indicates if the measurement mask is enabled for the concurrency measurement */
	protected final boolean hasConcurrent;
	/** The concurrency counter */
	protected final AtomicInteger concurrencyCounter;
	/** The metric sink hub */
	protected final IMetricSink sink;
	/** The exception submission if error tracking is enabled */
	protected final long[] exSub;
	/** The concurrency counter accessor for the calling thread */
	protected static final ThreadLocal<AtomicInteger> concurrencyAccessor = Measurement.ConcurrencyMeasurement.concurrencyAccessor;
	
	/** A map of interceptors keyed by the mask within a map of interceptors keyed by the metricId */
	private static final NonBlockingHashMapLong<NonBlockingHashMapLong<DefaultShorthandInterceptor>> interceptors = new NonBlockingHashMapLong<NonBlockingHashMapLong<DefaultShorthandInterceptor>>();
	
	static {
		// register a listener on metricId removal
	}
	
	
	public static DefaultShorthandInterceptor instance(final long metricId, final int mask) {
		return new DefaultShorthandInterceptor(metricId, mask);
	}

	/**
	 * Creates a new DefaultShorthandInterceptor
	 * @param metricId The parent OTMetric long hash code
	 * @param mask The enabled measurement mask
	 */
	private DefaultShorthandInterceptor(final long metricId, final int mask) {
		this.metricId = metricId;
		this.mask = mask;	
		hasConcurrent = Measurement.CONCURRENT.isEnabledFor(mask);
		sink = MetricSink.hub();		
		concurrencyCounter = Measurement.CONCURRENT.isEnabledFor(this.mask) ? sink.getConcurrencyCounter(metricId) : null;
		exSub = Measurement.hasFinallyBlock(mask) ? new long[]{mask, metricId, 1} : null;		
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandInterceptor#enter(int, long)
	 */
	@Override
	public long[] enter(final int mask, final long parentMetricId) {
		if(hasConcurrent) {
			final AtomicInteger ai = concurrencyAccessor.get(); 
			try {				
				concurrencyAccessor.set(concurrencyCounter);
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
		sink.submit(entryState);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandInterceptor#finalExit()
	 */
	@Override
	public void finalExit() {		
		if(hasConcurrent) concurrencyCounter.decrementAndGet();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandInterceptor#throwExit()
	 */
	@Override
	public void throwExit() {
		sink.submit(exSub);		
	}
	

}
