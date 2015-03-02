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
import java.util.Map;
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
	/** The non-noop enabled measurement mask */
	protected final int nonoopmask;	
	/** Indicates if the measurement mask is enabled for the concurrency measurement */
	protected final boolean hasConcurrent;
	/** The concurrency counter */
	protected final AtomicInteger concurrencyCounter;
	/** The metric sink hub */
	protected final MetricSink sink;
	/** The exception submission if error tracking is enabled */
	protected final long[] exSub;
	/** The swap map for this interceptor's mask */
	protected final Map<Measurement, Integer> swapMap;
	/** A map of interceptors keyed by the mask within a map of interceptors keyed by the metricId */
	private static final NonBlockingHashMapLong<NonBlockingHashMapLong<DefaultShorthandInterceptor>> interceptors = new NonBlockingHashMapLong<NonBlockingHashMapLong<DefaultShorthandInterceptor>>();
	
	/** A noop interceptor */
	private static final DefaultShorthandInterceptor NOOP_INTERCEPTOR = new NoopShorthandInterceptor();
	
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

	/**
	 * System err pattern logger
	 * @param fmt The message format
	 * @param args The tokens
	 */
	public static void loge(final Object fmt, final Object...args) {
		System.err.println(String.format(fmt.toString(), args));
	}
	
	static {
		Unsafe us = null;
        try {        	
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            us = (Unsafe) theUnsafe.get(null);
        } catch (Throwable ex) {
        	us = null;
        }
        UNSAFE = null;
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
		try {
			return interceptors.get(metricId).get(mask);
		} catch (NullPointerException npe) {
			loge("Failed to get DefaultShorthandInterceptor for [%s]/[%s] because it was not installed first. Programmer error");
			return NOOP_INTERCEPTOR;
		}
	}

	/**
	 * Creates a new DefaultShorthandInterceptor
	 * @param metricId The parent OTMetric long hash code
	 * @param mask The enabled measurement mask
	 */
	private DefaultShorthandInterceptor(final long metricId, final int mask) {
		this.metricId = metricId;
		this.mask = mask;			
		nonoopmask = Measurement.swapDependees(mask);
		hasConcurrent = Measurement.CONCURRENT.isEnabledFor(mask);
		sink = MetricSink.sink();
		swapMap = sink.getSwapMap(mask);
		concurrencyCounter = Measurement.CONCURRENT.isEnabledFor(this.mask) ? sink.getConcurrencyCounter(metricId) : null;
		if(Measurement.INVOKE.isEnabledFor(mask) || Measurement.INVOKERATE.isEnabledFor(mask)) {
			exSub = Measurement.hasCatchBlock(mask) ? new long[]{Measurement.swapDependees(Measurement.ERROR.mask | Measurement.INVOKE.mask), metricId, -1, 1, 1} : null;
		} else {
			exSub = Measurement.hasCatchBlock(mask) ? new long[]{Measurement.swapDependees(Measurement.ERROR.mask), metricId, -1, 1} : null;
		}
	}
	
	private DefaultShorthandInterceptor() {
		sink = null;
		nonoopmask = -1;
		mask = -1;
		metricId = -1;
		hasConcurrent = false;
		exSub = null;
		concurrencyCounter = null;
		swapMap = null;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandInterceptor#enter(int, long)
	 */
	@Override
	public long[] enter(final int mask, final long parentMetricId) {
		final int concurrency = hasConcurrent ?  concurrencyCounter.incrementAndGet() : 0;		
		log("Concurrency -------------> %s   id: %s", concurrency, System.identityHashCode(concurrencyCounter));
		final long[] valueArr = Measurement.enter(nonoopmask, parentMetricId);
		if(hasConcurrent) valueArr[swapMap.get(Measurement.CONCURRENT)] = concurrency;
		return valueArr;
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
		entryState[0] = mask;
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
		if(t!=null) {
			t.printStackTrace(System.err);
//			if(UNSAFE!=null) 
				UNSAFE.throwException(t);
//			throw new RuntimeException(t.getMessage(), t);  // FIXME: we should take note of the methods declared exceptions, try to match the type and cast/throw. 
		}
	}
	
	
	
	/**
	 * <p>Title: NoopShorthandInterceptor</p>
	 * <p>Description: A noop shorthand interceptor returned when an error occurs trying to get one.</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.aop.DefaultShorthandInterceptor.NoopShorthandInterceptor</code></p>
	 */
	private static final class NoopShorthandInterceptor extends DefaultShorthandInterceptor {
		private static final long[] EMPTY_LONG_ARR = {};
		/**
		 * Creates a new NoopShorthandInterceptor
		 */
		public NoopShorthandInterceptor() {
			super();
		}

		@Override
		public final long[] enter(final int mask, final long parentMetricId) {
			/* No Op */
			return EMPTY_LONG_ARR;
		}

		@Override
		public final void exit(final long[] entryState) {
			/* No Op */			
		}

		@Override
		public final void finalExit() {
			/* No Op */			
		}

		@Override
		public final void throwExit(Throwable t) {
			/* No Op */			
		}
		
	}

}
