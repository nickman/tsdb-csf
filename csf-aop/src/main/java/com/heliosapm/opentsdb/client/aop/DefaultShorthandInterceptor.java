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

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

import sun.misc.Unsafe;

import com.heliosapm.opentsdb.client.opentsdb.OTMetric;
import com.heliosapm.opentsdb.client.opentsdb.opt.LongIdOTMetricCache;
import com.heliosapm.opentsdb.client.opentsdb.opt.LongIdOTMetricCache.OTMetricIdListener;
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
@SuppressWarnings("restriction")
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
	/** A map of interceptors keyed by the mask within a map of interceptors keyed by the metricId */
	private static final NonBlockingHashMapLong<NonBlockingHashMapLong<DefaultShorthandInterceptor>> interceptors = new NonBlockingHashMapLong<NonBlockingHashMapLong<DefaultShorthandInterceptor>>();
	
    /** The unsafe instance */    	
	private static final Unsafe UNSAFE;

	/**
	 * System out pattern logger
	 * @param fmt The message format
	 * @param args The tokens
	 */
	public static void log(final Object fmt, final Object...args) {
		System.out.println(String.format(fmt.toString(), args));
	}

	
	static {
        try {        	
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafe.get(null);
        } catch (Exception ex) {
        	throw new RuntimeException(ex);
        }
		// register a listener on metricId removal
		LongIdOTMetricCache.getInstance().registerMetricIdListener(new OTMetricIdListener() {
			@Override
			public void onRemoved(final long otMetricId, final OTMetric otMetric) {
				interceptors.remove(otMetricId);
			}
		});
	}
	
	
	/**
	 * Retrieves the interceptor for the passed metric id and mask, building it if not present.
	 * @param metricId the parent metric id
	 * @param mask the measurement mask
	 * @return the interceptor
	 */
	public static DefaultShorthandInterceptor install(final long metricId, final int mask) {
		NonBlockingHashMapLong<DefaultShorthandInterceptor> byMask = interceptors.get(metricId);
		if(byMask==null) {
			synchronized(interceptors) {
				byMask = interceptors.get(metricId);
				if(byMask==null) {
					byMask = new NonBlockingHashMapLong<DefaultShorthandInterceptor>();
					interceptors.put(metricId, byMask);
				}
			}
		}
		DefaultShorthandInterceptor inter = byMask.get(mask);
		if(inter==null) {
			synchronized(byMask) {
				inter = byMask.get(mask);
				if(inter==null) {
					inter = new DefaultShorthandInterceptor(metricId, mask);
					byMask.put(mask, inter);
				}
			}
		}
		return new DefaultShorthandInterceptor(metricId, mask);
	}
	
	/**
	 * Retrieves the interceptor for the passed metric id and mask.
	 * This method should only be called once the interceptor has been installed since it makes no null checks.
	 * @param metricId the parent metric id
	 * @param mask the measurement mask
	 * @return the interceptor
	 */
	public static DefaultShorthandInterceptor get(final long metricId, final int mask) {
		return interceptors.get(metricId).get(mask);
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
		sink = MetricSink.sink();		
		concurrencyCounter = Measurement.CONCURRENT.isEnabledFor(this.mask) ? sink.getConcurrencyCounter(metricId) : null;
		exSub = Measurement.hasFinallyBlock(mask) ? new long[]{Measurement.ERROR.mask, metricId, -1, 1} : null;		
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandInterceptor#enter(int, long)
	 */
	@Override
	public long[] enter(final int mask, final long parentMetricId) {
		if(hasConcurrent) concurrencyCounter.incrementAndGet();
		return Measurement.enter(mask, parentMetricId);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandInterceptor#exit(long[])
	 */
	@Override
	public void exit(final long[] entryState) {
		try {
			Measurement.exit(entryState);
		} catch (Throwable t) {
			t.printStackTrace(System.err);
		}
//		log("Exit Values: %s", Arrays.toString(entryState));
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
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandInterceptor#throwExit(java.lang.Throwable)
	 */
	@Override
	public void throwExit(final Throwable t) {
		sink.submit(exSub);
		if(t!=null) UNSAFE.throwException(t);
	}
	

}
