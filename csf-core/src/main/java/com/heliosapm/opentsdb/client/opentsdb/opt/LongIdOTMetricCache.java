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

package com.heliosapm.opentsdb.client.opentsdb.opt;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cliffc.high_scale_lib.NonBlockingHashMapLong;
import org.cliffc.high_scale_lib.NonBlockingHashSet;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.heliosapm.opentsdb.client.opentsdb.ConfigurationReader;
import com.heliosapm.opentsdb.client.opentsdb.Constants;
import com.heliosapm.opentsdb.client.opentsdb.MetricBuilder;
import com.heliosapm.opentsdb.client.opentsdb.OTMetric;
import com.heliosapm.opentsdb.client.opentsdb.Threading;

/**
 * <p>Title: LongIdOTMetricCache</p>
 * <p>Description: A long keyed cache for OTMetrics for faster lookup times and lower memory usage</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.LongIdOTMetricCache</code></p>
 */

public class LongIdOTMetricCache implements LongIdOTMetricCacheMBean, RemovalListener<OTMetric, String> {
	/** The singleton instance */
	private static volatile LongIdOTMetricCache instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	
	/** An empty OTMetric array const */
	private static final OTMetric[] EMPTY_OT_METRIC_ARR = {};
	
	/** Instance logger */
	private final Logger log;
	/** The OTMetric spec */
	private final NonBlockingHashMapLong<OTMetric> cache;
	/** A global cache of counters keyed by the OTMetric long hash code within a map keyed by the calling thread id */
	private final Cache<Thread, NonBlockingHashMapLong<AtomicInteger>> threadCounters = CacheBuilder.newBuilder()
			.concurrencyLevel(Constants.CORES)
			.initialCapacity(512)
			.recordStats()
			.weakKeys()
			.build();
	/** A global cache of counters keyed by the OTMetric */
	private final Cache<OTMetric, AtomicInteger> counters = CacheBuilder.newBuilder()
			.concurrencyLevel(Constants.CORES)
			.initialCapacity(512)
			.recordStats()
			.weakKeys()
			.build();
	
	/** A global cache of a string const keyed by the OTMetric. Used to track enqueued OTMetrics no longer in use */
	private final Cache<OTMetric, String> enqueueWatcher = CacheBuilder.newBuilder()
			.concurrencyLevel(1)
			.initialCapacity(512)
			.recordStats()
			.weakKeys()
			.removalListener(this)
			.build();
	
	/** A set of registered metric id listeners */
	final NonBlockingHashSet<OTMetricIdListener> metricIdListeners = new NonBlockingHashSet<OTMetricIdListener>(); 
	/** The initial size of the opt cache */
	final int initialSize;
	/** The space for speed option of the opt cache */
	final boolean space4Speed;
	
	
	
	/**
	 * Acquires the LongIdOTMetricCache singleton instance
	 * @return the LongIdOTMetricCache singleton instance
	 */
	public static LongIdOTMetricCache getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new LongIdOTMetricCache();
				}
			}
		}
		return instance;
	}
	
	/**
	 * Creates a new LongIdOTMetricCache
	 */
	private LongIdOTMetricCache() {
		this.log = LogManager.getLogger(getClass());
		this.initialSize = ConfigurationReader.confInt(Constants.PROP_OPT_CACHE_INIT_SIZE, Constants.DEFAULT_OPT_CACHE_INIT_SIZE);
		this.space4Speed = ConfigurationReader.confBool(Constants.PROP_OPT_CACHE_SPACE_FOR_SPEED, Constants.DEFAULT_OPT_CACHE_SPACE_FOR_SPEED);
		this.cache = new NonBlockingHashMapLong<OTMetric>(this.initialSize, this.space4Speed);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.google.common.cache.RemovalListener#onRemoval(com.google.common.cache.RemovalNotification)
	 */
	@Override
	public void onRemoval(RemovalNotification<OTMetric, String> notification) {
		if(notification!=null) {
			final OTMetric otm = notification.getKey();
			if(otm!=null) {
				counters.invalidate(otm);
				final long metricId = otm.longHashCode();
				cache.remove(metricId);
				final Set<OTMetricIdListener> listeners = new HashSet<OTMetricIdListener>(metricIdListeners);
				Threading.getInstance().async(new Runnable() {
					@Override
					public void run() {
						for(final OTMetricIdListener listener: listeners) {
							Threading.getInstance().async(new Runnable() {
								@Override
								public void run() {
									listener.onRemoved(metricId, otm);
								}
							});
						}
					}
				});				
			}
		}
	}
	
	
	/**
	 * Increments a counter for the passed OTMetric Id and returns the new value
	 * @param otMetricId the OTMetric long hash code 
	 * @return the value of the incremented counter
	 */
	public int incrCounter(final long otMetricId) {		
		final OTMetric metric =  cache.get(otMetricId);
		if(metric==null) throw new RuntimeException("No otmeric for id [" + otMetricId + "]");
		return incrCounter(metric);				
	}
	
	/**
	 * Decrements a counter for the passed OTMetric Id and returns the new value
	 * @param otMetricId the OTMetric long hash code 
	 * @return the value of the decremented counter
	 */
	public int decrCounter(final long otMetricId) {		
		final OTMetric metric =  cache.get(otMetricId);
		if(metric==null) throw new RuntimeException("No otmeric for id [" + otMetricId + "]");
		return decrCounter(metric);				
	}
	
	
	
	/**
	 * Returns the counter for the passed OTMetric
	 * @param otMetric the OTMetric to get the counter for
	 * @return the counter
	 */
	public AtomicInteger getCounter(final OTMetric otMetric) {
		try {
			return this.counters.get(otMetric, new Callable<AtomicInteger>() {
				@Override
				public AtomicInteger call() throws Exception {					
					return new AtomicInteger(0);
				}			
			});
		} catch (Exception ex) {
			// this should never happen
			this.log.error("Unexpected exception getting counter", ex);
			throw new RuntimeException(ex);
		}		
	}
	
	/**
	 * Returns the counter for the passed OTMetric id
	 * @param otMetricId the OTMetric id to get the counter for
	 * @return the counter
	 */
	public AtomicInteger getCounter(final long otMetricId) {
		final OTMetric metric =  cache.get(otMetricId);
		if(metric==null) throw new RuntimeException("No otmeric for id [" + otMetricId + "]");
		return getCounter(metric);
	}

	
	
	/**
	 * Increments a counter for the passed OTMetric and returns the new value
	 * @param otMetric the OTMetric 
	 * @return the value of the incremented counter
	 */
	public int incrCounter(final OTMetric otMetric) {
		try {
			final AtomicInteger ai = this.counters.get(otMetric, new Callable<AtomicInteger>() {
				@Override
				public AtomicInteger call() throws Exception {					
					return new AtomicInteger(0);
				}			
			});
			return ai.incrementAndGet();
		} catch (Exception ex) {
			// this should never happen
			this.log.error("Unexpected exception getting counter", ex);
			throw new RuntimeException(ex);
		}		
	}
	
	/**
	 * Decrements a counter for the passed OTMetric and returns the new value
	 * @param otMetric the OTMetric 
	 * @return the value of the decremented counter unless it did not exist in which case returns zero after creating the counter
	 */
	public int decrCounter(final OTMetric otMetric) {
		try {
			final AtomicInteger ai = this.counters.get(otMetric, new Callable<AtomicInteger>() {
				@Override
				public AtomicInteger call() throws Exception {					
					return new AtomicInteger(0);
				}			
			});
			return ai.decrementAndGet();
		} catch (Exception ex) {
			// this should never happen
			this.log.error("Unexpected exception getting counter", ex);
			throw new RuntimeException(ex);
		}		
	}
	
	/**
	 * Increments a current thread exclusive counter for the passed OTMetric ID and returns the new value
	 * @param otId the OTMetric ID
	 * @return the value of the incremented counter
	 */
	public int incrThreadCounter(final long otId) {
		try {
			final NonBlockingHashMapLong<AtomicInteger> ctrs = this.threadCounters.get(Thread.currentThread(), new Callable<NonBlockingHashMapLong<AtomicInteger>>() {
				@Override
				public NonBlockingHashMapLong<AtomicInteger> call() throws Exception {
					final NonBlockingHashMapLong<AtomicInteger> ctrCache = new NonBlockingHashMapLong<AtomicInteger>(128);
					ctrCache.put(otId, new AtomicInteger(0));
					return ctrCache;
				}			
			});
			// This part is thread safe too, so no need to get fancy
			AtomicInteger ai = ctrs.get(otId);
			if(ai==null) {
				ai = new AtomicInteger(0);
				ctrs.put(otId, ai);
			}
			return ai.incrementAndGet();
		} catch (Exception ex) {
			// this should never happen
			this.log.error("Unexpected exception getting counter", ex);
			throw new RuntimeException(ex);
		}		
	}
	
	/**
	 * Decrements a current thread exclusive counter for the passed OTMetric ID and returns the new value
	 * @param otId the OTMetric ID
	 * @return the value of the decremented counter unless it did not exist in which case returns zero (after creating the counter)
	 */
	public int decrThreadCounter(final long otId) {
		try {			
			final boolean[] newctr = new boolean[]{false}; 
			final NonBlockingHashMapLong<AtomicInteger> ctrs = this.threadCounters.get(Thread.currentThread(), new Callable<NonBlockingHashMapLong<AtomicInteger>>() {
				@Override
				public NonBlockingHashMapLong<AtomicInteger> call() throws Exception {
					newctr[0] = true;
					final NonBlockingHashMapLong<AtomicInteger> ctrCache = new NonBlockingHashMapLong<AtomicInteger>(128);
					ctrCache.put(otId, new AtomicInteger(0));
					return ctrCache;
				}			
			});
			if(newctr[0]) return 0;
			// This part is thread safe too, so no need to get fancy
			AtomicInteger ai = ctrs.get(otId);
			if(ai==null) {
				ai = new AtomicInteger(0);
				ctrs.put(otId, ai);
			}
			return ai.decrementAndGet();
		} catch (Exception ex) {
			// this should never happen
			this.log.error("Unexpected exception getting counter", ex);
			throw new RuntimeException(ex);
		}		
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.opt.LongIdOTMetricCacheMBean#getOTMetric(long)
	 */
	@Override
	public OTMetric getOTMetric(final long id) {
		return this.cache.get(id);
	}
	
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.opt.LongIdOTMetricCacheMBean#getSize()
	 */
	@Override
	public int getSize() {
		return this.cache.size();
	}
	
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.opt.LongIdOTMetricCacheMBean#getCacheInternals()
	 */
	@Override
	public String getCacheInternals() {
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final PrintStream ps = new PrintStream(baos, true);
		final PrintStream out = System.out;
		try {
			System.setOut(ps);
			this.cache.print();
		} finally {
			System.setOut(out);
		}
		try {
			return baos.toString(Charset.defaultCharset().name());
		} catch (Exception e) {
			return "Failed to get internals:" + e;
		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.opt.LongIdOTMetricCacheMBean#getReprobes()
	 */
	@Override
	public long getReprobes() {
		return this.cache.reprobes();
	}
	
	/**
	 * Returns the OTMetric for the provided parameters
	 * @param name The plain flat name from the Metric name
	 * @return the OTMetric
	 */
	public OTMetric getOTMetric(final String name) {
		return getOTMetric(name, null, null, null);
	}
	
	
	/**
	 * Returns the OTMetric for the provided parameters
	 * @param name The plain flat name from the Metric name
	 * @param nprefix The optional prefix which is prefixed to the flat name
	 * @return the OTMetric
	 */
	public OTMetric getOTMetric(final String name, final String nprefix) {
		return getOTMetric(name, nprefix, null, null);			
	}
	
	/**
	 * Adds an OTMetric to the cache
	 * @param otMetric The OTMetric to add
	 * @return true if the metric was added, false if it was already in the cache
	 */
	public boolean put(final OTMetric otMetric) {
		return this.cache.putIfAbsent(otMetric.longHashCode(), otMetric)==null;
	}
	
	
	/**
	 * Returns the OTMetric for the provided parameters
	 * @param name The plain flat name from the Metric name
	 * @param nprefix The optional prefix which is prefixed to the flat name
	 * @param extension The optional extension which is appended to the TSDB metric name
	 * @return the OTMetric
	 */
	public OTMetric getOTMetric(final String name, final String nprefix, final String extension) {
		return getOTMetric(name, nprefix, extension, null);			
	}
	
	/**
	 * Returns the OTMetric for the provided parameters
	 * @param name The plain flat name from the Metric name
	 * @param nprefix The optional prefix which is prefixed to the flat name
	 * @param extension The optional extension which is appended to the TSDB metric name
	 * @param extraTags The optional extra tags to add
	 * @return the OTMetric
	 */
	public OTMetric getOTMetric(final String name, final String nprefix, final String extension, final Map<String, String> extraTags) {
		final MetricBuilder mb = MetricBuilder.metric(name).pre(nprefix).ext(extension).tags(extraTags);
		return getOTMetric(mb);
	}
	
	private static final String ENQUEUE_VALUE = "X";
	
	/**
	 * Creates the OTMetric represented by the passed metric builder if it not already in cache, and caches it.
	 * Otherwise finds the existing by long hash code and returns it. 
	 * @param metricBuilder A loaded but not built metric builder
	 * @return the OTMetric
	 */
	public OTMetric getOTMetric(final MetricBuilder metricBuilder) {
		final long id = metricBuilder.longHashCode();
		OTMetric otm = this.cache.get(id);
		if(otm==null) {
			synchronized(this.cache) {
				otm = this.cache.get(id);
				if(otm==null) {
					otm = metricBuilder.buildNoCache();
					this.cache.put(id, otm);
					enqueueWatcher.put(otm, ENQUEUE_VALUE);
				}
			}
		}
		return otm;		
	}
	
	
//	/**
//	 * Returns the OTMetrics matching the passed pattern
//	 * @param pattern The regular expression to match
//	 * @param caseInsensitive true for a case insensitive match, false otherwise
//	 * @return A possibly empty array of the matching OTMetrics
//	 */
//	public OTMetric[] getOTMetrics(final String pattern, final boolean caseInsensitive) {
//		if(pattern==null || pattern.trim().isEmpty()) return EMPTY_OT_METRIC_ARR;
//		final Pattern p = caseInsensitive ? Pattern.compile(pattern.trim(), Pattern.CASE_INSENSITIVE) : Pattern.compile(pattern.trim());
//		final List<OTMetric> matches = new ArrayList<OTMetric>();
//		for(String name: cache.asMap().keySet()) {
//			if(p.matcher(name).matches()) {
//				matches.add(getOTMetric(name));
//			}
//		}
//		return matches.isEmpty() ? EMPTY_OT_METRIC_ARR : matches.toArray(new OTMetric[0]);
//	}
	
//	/**
//	 * Returns the OTMetrics matching the passed pattern with case sensitivity
//	 * @param pattern The regular expression to match
//	 * @return A possibly empty array of the matching OTMetrics
//	 */
//	public OTMetric[] getOTMetrics(final String pattern) {
//		return getOTMetrics(pattern, false);
//	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.opt.LongIdOTMetricCacheMBean#getInitialSize()
	 */
	@Override
	public int getInitialSize() {
		return this.initialSize;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.opt.LongIdOTMetricCacheMBean#isSpace4Speed()
	 */
	@Override
	public boolean isSpace4Speed() {
		return this.space4Speed;
	}
	
	/**
	 * Adds a metric id listener
	 * @param listener The listener to add
	 */
	public void registerMetricIdListener(final OTMetricIdListener listener) {
		if(listener!=null) {
			metricIdListeners.add(listener);
		}
	}

	/**
	 * Removes a metric id listener
	 * @param listener The listener to remove
	 */
	public void removeMetricIdListener(final OTMetricIdListener listener) {
		if(listener!=null) {
			metricIdListeners.remove(listener);
		}
	}


	/**
	 * <p>Title: OTMetricIdListener</p>
	 * <p>Description: Defines a listener notified of a removed metric</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.opentsdb.opt.LongIdOTMetricCache.OTMetricIdListener</code></p>
	 */
	public static interface OTMetricIdListener {
		/**
		 * Callback when an OTMetric is removed from cache
		 * @param otMetricId The long hash code of the removed metric
		 * @param otMetric The metric removed from cache
		 */
		public void onRemoved(long otMetricId, OTMetric otMetric);
	}

}
