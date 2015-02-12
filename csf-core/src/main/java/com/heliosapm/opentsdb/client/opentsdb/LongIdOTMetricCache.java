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

package com.heliosapm.opentsdb.client.opentsdb;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

/**
 * <p>Title: LongIdOTMetricCache</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.LongIdOTMetricCache</code></p>
 */

public class LongIdOTMetricCache implements LongIdOTMetricCacheMBean {
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
		log = LogManager.getLogger(getClass());
		initialSize = ConfigurationReader.confInt(Constants.PROP_OPT_CACHE_INIT_SIZE, Constants.DEFAULT_OPT_CACHE_INIT_SIZE);
		space4Speed = ConfigurationReader.confBool(Constants.PROP_OPT_CACHE_SPACE_FOR_SPEED, Constants.DEFAULT_OPT_CACHE_SPACE_FOR_SPEED);
		cache = new NonBlockingHashMapLong<OTMetric>(initialSize, space4Speed);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.LongIdOTMetricCacheMBean#getOTMetric(long)
	 */
	@Override
	public OTMetric getOTMetric(final long id) {
		return cache.get(id);
	}
	
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.LongIdOTMetricCacheMBean#getSize()
	 */
	@Override
	public int getSize() {
		return cache.size();
	}
	
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.LongIdOTMetricCacheMBean#getCacheInternals()
	 */
	@Override
	public String getCacheInternals() {
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final PrintStream ps = new PrintStream(baos, true);
		final PrintStream out = System.out;
		try {
			System.setOut(ps);
			cache.print();
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
	 * @see com.heliosapm.opentsdb.client.opentsdb.LongIdOTMetricCacheMBean#getReprobes()
	 */
	@Override
	public long getReprobes() {
		return cache.reprobes();
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
		return cache.putIfAbsent(otMetric.longHashCode(), otMetric)==null;
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
	
	/**
	 * Creates the OTMetric represented by the passed metric builder if it not already in cache, and caches it.
	 * Otherwise finds the existing by long hash code and returns it. 
	 * @param metricBuilder A loaded but not built metric builder
	 * @return the OTMetric
	 */
	public OTMetric getOTMetric(final MetricBuilder metricBuilder) {
		final long id = metricBuilder.longHashCode();
		OTMetric otm = cache.get(id);
		if(otm==null) {
			synchronized(cache) {
				otm = cache.get(id);
				if(otm==null) {
					otm = metricBuilder.buildNoCache();
					cache.put(id, otm);
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
	 * @see com.heliosapm.opentsdb.client.opentsdb.LongIdOTMetricCacheMBean#getInitialSize()
	 */
	@Override
	public int getInitialSize() {
		return initialSize;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.LongIdOTMetricCacheMBean#isSpace4Speed()
	 */
	@Override
	public boolean isSpace4Speed() {
		return space4Speed;
	}


}
