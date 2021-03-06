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
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cliffc.high_scale_lib.NonBlockingHashMapLong;
import org.cliffc.high_scale_lib.NonBlockingHashMapLong.IteratorLong;
import org.cliffc.high_scale_lib.NonBlockingHashSet;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.heliosapm.opentsdb.client.opentsdb.ConfigurationReader;
import com.heliosapm.opentsdb.client.opentsdb.Constants;
import com.heliosapm.opentsdb.client.opentsdb.MetricBuilder;
import com.heliosapm.opentsdb.client.opentsdb.OTMetric;
import com.heliosapm.opentsdb.client.opentsdb.Threading;
import com.heliosapm.opentsdb.client.util.ManagedNonBlockingMap;

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
	/** Sets of submetrics keyed by the parent id */
	final NonBlockingHashMapLong<NonBlockingHashMapLong<OTMetric>> subMetrics = new NonBlockingHashMapLong<NonBlockingHashMapLong<OTMetric>>(); 
	
	/** A set of registered metric id listeners */
	final NonBlockingHashSet<OTMetricIdListener> metricIdListeners = new NonBlockingHashSet<OTMetricIdListener>(); 
	/** The initial size of the opt cache */
	final int initialSize;
	/** The space for speed option of the opt cache */
	final boolean space4Speed;
	
	// ==================================================================================================
	//		Temp Dev Constructs
	// ==================================================================================================
	/** The aggregate metrics */
	protected final NonBlockingHashMapLong<Map<Measurement, Metric>> aggregateMetrics = new NonBlockingHashMapLong<Map<Measurement, Metric>>();
	/** Keeps references for metrics in play */
	protected final NonBlockingHashMapLong<OTMetric> refKeeper = new NonBlockingHashMapLong<OTMetric>();
	/** Keeps a cache of swap maps */
	protected final NonBlockingHashMapLong<Map<Measurement, Integer>> swapMaps = new NonBlockingHashMapLong<Map<Measurement, Integer>>();

	// ==================================================================================================
	
	
	
	
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
		ManagedNonBlockingMap.manage(cache, "OTMetricCache");
		ManagedNonBlockingMap.manage(aggregateMetrics, "AggregateMetrics");
		ManagedNonBlockingMap.manage(refKeeper, "RefKeeper");
		ManagedNonBlockingMap.manage(swapMaps, "SwapMaps");
		ManagedNonBlockingMap.manage(subMetrics, "OTSubMetricCache");
		
		
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
				subMetrics.remove(metricId);
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
		if(otMetric==null) throw new IllegalArgumentException("The passed OTMetric was null");
		final boolean added = this.cache.putIfAbsent(otMetric.longHashCode(), otMetric)==null;
		if(added) postCachePut(otMetric);
		return added;
	}
	
	/**
	 * Registers the OTMetric as a subMetric if it has a parent
	 * @param otMetric The OTMetric that was just added to cache
	 */
	protected void postCachePut(final OTMetric otMetric) {
		if(otMetric!=null) {
			if(otMetric.getParentId()!=0L) {
				NonBlockingHashMapLong<OTMetric> subMetricSet = subMetrics.get(otMetric.getParentId());
				if(subMetricSet==null) {
					synchronized(subMetrics) {
						subMetricSet = subMetrics.get(otMetric.getParentId());
						if(subMetricSet==null) {
							subMetricSet = new NonBlockingHashMapLong<OTMetric>();
							subMetrics.put(otMetric.getParentId(), subMetricSet);
						}
					}
				}
				subMetricSet.put(otMetric.longHashCode(), otMetric);
			}
		}
	}
	
	/**
	 * Returns a map of direct submetrics for the OTMetric identified by the passed metricId
	 * @param metricId The metricId of the parent to report
	 * @return the map of metrics
	 */
	public NonBlockingHashMapLong<OTMetric> getSubMetrics(final long metricId) {
		return getSubMetrics(metricId, false);
	}
	
	
	/**
	 * Returns a map of submetrics for the OTMetric identified by the passed metricId
	 * @param metricId The metricId of the parent to report
	 * @param recurse if true, the metric tree will be recursed, otherwise will only report one level
	 * @return the map of metrics
	 */
	public NonBlockingHashMapLong<OTMetric> getSubMetrics(final long metricId, final boolean recurse) {
		final NonBlockingHashMapLong<OTMetric> subs = new NonBlockingHashMapLong<OTMetric>();
		if(!recurse) {
			NonBlockingHashMapLong<OTMetric> readSubs = subMetrics.get(metricId);
			if(readSubs!=null) {
				subs.putAll(readSubs);  // FIXME: optimize to not auto-box 
			}
		} else {
			getSubMetricsRecursive(metricId, subs);
		}		
		return subs;
	}
	
	protected void getSubMetricsRecursive(final long metricId, final NonBlockingHashMapLong<OTMetric> accumulator) {
		NonBlockingHashMapLong<OTMetric> readSubs = subMetrics.get(metricId);
		if(readSubs!=null) {
			final IteratorLong li = (IteratorLong) readSubs.keySet().iterator();
			while(li.hasNext()) {
				final long key = li.nextLong();
				final OTMetric metric = readSubs.get(key);
				accumulator.put(key, metric);
				if(metric.isSubMetric()) {
					getSubMetricsRecursive(metric.getParentId(), accumulator);
				}
			}
		}
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
					postCachePut(otm);
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
	 * Puts a new OTMetric refkeeper instance if it is not registered already
	 * @param metricId The long hash code of the metric
	 * @param otMetric The OTMetric to keep a reference for
	 * @return Null if no mapping already existed, the OTMetric for the passed key if one did
	 */
	public OTMetric putRefKeeperIfAbsent(final long metricId, final OTMetric otMetric) {
		return refKeeper.putIfAbsent(metricId, otMetric);
	}
	
	/**
	 * Puts an OTMetric refkeeper instance
	 * @param metricId The long hash code of the metric
	 * @param otMetric The OTMetric to keep a reference for
	 * @return The prior OTMetric bound to the key or null if no mapping existed
	 */
	public OTMetric putRefKeeper(final long metricId, final OTMetric otMetric) {
		return refKeeper.put(metricId, otMetric);
	}
	
	/**
	 * Returns the SwapMap for the passed measurement map
	 * @param mask The mask to get a SwapMap for
	 * @return the SwapMap
	 */
	public Map<Measurement, Integer> getSwapMap(final int mask) {
		Map<Measurement, Integer> swapMap = swapMaps.get(mask);
		if(swapMap==null) {
			synchronized(swapMaps) {
				swapMap = swapMaps.get(mask);
				if(swapMap==null) {
					swapMap = Collections.unmodifiableMap(Measurement.getSwapMap(mask));
					swapMaps.put(mask, swapMap);					
				}
			}
		}
		return swapMap;
	}
	
	/**
	 * Renders the current state of the aggregated metrics to a formated string
	 * @return the metric report
	 */
	@SuppressWarnings("rawtypes")
	public String renderMetrics() {
		final StringBuilder b = new StringBuilder("=====================================");
		final IteratorLong il = (IteratorLong)aggregateMetrics.keySet().iterator();
		while(il.hasNext()) {
			final long metricId = il.nextLong();
			final Map<Measurement, Metric> metricMap = aggregateMetrics.get(metricId);
			final OTMetric metric = getOTMetric(metricId);
			b.append("\nMetric:").append(metric.toString());
			for(Map.Entry<Measurement, Metric> entry: metricMap.entrySet()) {
				b.append("\n\t").append(entry.getKey().shortName).append(":").append(print(entry.getValue()));
			}
		}
		return b.toString();
	}
	
	protected String print(final Metric metric) {
		final Map<String, Object> values = new HashMap<String, Object>();
		String type = "Unknown:";
		if(metric instanceof Gauge) {
			values.put("value", ((Gauge)metric).getValue());
			type = "Gauge:";
		} else if(metric instanceof Histogram) {
			type = "Histogram:";
			Histogram hist = (Histogram)metric;
			Snapshot snap = hist.getSnapshot();
			values.put("max", snap.getMax());
			values.put("min", snap.getMin());
			values.put("med", snap.getMedian());
			values.put("mean", snap.getMean());
		} else if(metric instanceof Timer) {
			type = "Timer:";
			Timer timer = (Timer)metric;
			values.put("count", timer.getCount());
			values.put("15m", timer.getFifteenMinuteRate());
			values.put("5m", timer.getFiveMinuteRate());
			values.put("1m", timer.getOneMinuteRate());
			values.put("meanRate", timer.getMeanRate());
			Snapshot snap = timer.getSnapshot();
			values.put("max", snap.getMax());
			values.put("min", snap.getMin());
			values.put("med", snap.getMedian());
			values.put("mean", snap.getMean());
			values.put("stdev", snap.getStdDev());
			values.put("75pct", snap.get75thPercentile());
			values.put("95pct", snap.get95thPercentile());
			values.put("98pct", snap.get98thPercentile());
			values.put("99pct", snap.get99thPercentile());
			values.put("999pct", snap.get999thPercentile());			
		} else if(metric instanceof Counter) {
			type = "Counter:";
			Counter counter = (Counter)metric;
			values.put("count", counter.getCount());
		} else if(metric instanceof Meter) {
			type = "Meter:";
			Meter meter = (Meter)metric;
			values.put("count", meter.getCount());
			values.put("15m", meter.getFifteenMinuteRate());
			values.put("5m", meter.getFiveMinuteRate());
			values.put("1m", meter.getOneMinuteRate());
			values.put("meanRate", meter.getMeanRate());
		}				
		return type + values.toString();
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.opt.LongIdOTMetricCacheMBean#getSwapMapCount()
	 */
	@Override
	public int getSwapMapCount() {
		return swapMaps.size();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.opt.LongIdOTMetricCacheMBean#getMetricMapCount()
	 */
	@Override
	public int getMetricMapCount() {
		return aggregateMetrics.size();
	}
	
	
	/**
	 * Returns a map of Metrics keyed by the measurement type for the sub-metrics of the passed parent metric id
	 * @param parentMetricId The parent metric id
	 * @return the metric map
	 */
	public Map<Measurement, Metric> getMetricMap(final long parentMetricId) {
		Map<Measurement, Metric> metricMap = aggregateMetrics.get(parentMetricId);
		if(metricMap==null) {
			synchronized(aggregateMetrics) {
				metricMap = aggregateMetrics.get(parentMetricId);
				if(metricMap==null) {
					metricMap = new EnumMap<Measurement, Metric>(Measurement.class);
					final OTMetric parentMetric = getOTMetric(parentMetricId);
					for(Measurement m: parentMetric.getMeasurements()) {
						MetricBuilder.metric(parentMetric).tag("m", m.shortName).parent(parentMetricId).optBuild();
						metricMap.put(m, m.chMetric.createNewMetric());
					}
					aggregateMetrics.put(parentMetricId, metricMap);
					
				}
			}
		}
		return metricMap;
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
