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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheStats;

/**
 * <p>Title: OTMetricCacheStats</p>
 * <p>Description: OTMetricCache stats service</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.OTMetricCacheStats</code></p>
 */

public class OTMetricCacheStats implements OTMetricCacheStatsMBean {
	/** The wrapped cache instance */
	protected final Cache<String, OTMetric> cache;	
	/** A reference to the most recent cached cache stats */
	protected final AtomicReference<CacheStats> stats = new AtomicReference<CacheStats>(null);
	/** The timstamp of the most recent cached cache stats */
	protected final AtomicLong statsTs = new AtomicLong(0);
	
	/**
	 * Creates a new OTMetricCacheStats
	 * @param cache The cache being instrumented
	 */
	public OTMetricCacheStats(final Cache<String, OTMetric> cache) {
		this.cache = cache;
		refresh();		
	}
	
	/**
	 * Checks the timestamp on the cache
	 * @return The current cache stats
	 */
	protected CacheStats check() {
		final long now = System.currentTimeMillis();
		if(now-statsTs.get() > 2000) refresh();
		return stats.get();
	}
	
	/**
	 * Refresh the cache stats
	 */
	protected void refresh() {
		stats.set(cache.stats());
		statsTs.set(System.currentTimeMillis());
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.OTMetricCacheStatsMBean#getCacheClass()
	 */
	@Override
	public String getCacheClass() {
		return cache.getClass().getName();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.OTMetricCacheStatsMBean#invalidateAll()
	 */
	@Override
	public void invalidateAll() {
		cache.invalidateAll();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.OTMetricCacheStatsMBean#cleanup()
	 */
	@Override
	public void cleanup() {
		cache.cleanUp();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.OTMetricCacheStatsMBean#getSize()
	 */
	@Override
	public long getSize() {
		return cache.size();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.OTMetricCacheStatsMBean#getRequestCount()
	 */
	@Override
	public long getRequestCount() {
		return check().requestCount();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.OTMetricCacheStatsMBean#getHitCount()
	 */
	@Override
	public long getHitCount() {
		return check().hitCount();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.OTMetricCacheStatsMBean#getHitRate()
	 */
	@Override
	public double getHitRate() {
		return check().hitRate();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.OTMetricCacheStatsMBean#getMissCount()
	 */
	@Override
	public long getMissCount() {
		return check().missCount();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.OTMetricCacheStatsMBean#getMissRate()
	 */
	@Override
	public double getMissRate() {
		return check().missRate();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.OTMetricCacheStatsMBean#getLoadCount()
	 */
	@Override
	public long getLoadCount() {
		return check().loadCount();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.OTMetricCacheStatsMBean#getLoadSuccessCount()
	 */
	@Override
	public long getLoadSuccessCount() {
		return check().loadSuccessCount();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.OTMetricCacheStatsMBean#getLoadExceptionCount()
	 */
	@Override
	public long getLoadExceptionCount() {
		return check().loadExceptionCount();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.OTMetricCacheStatsMBean#getLoadExceptionRate()
	 */
	@Override
	public double getLoadExceptionRate() {
		return check().loadExceptionRate();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.OTMetricCacheStatsMBean#getTotalLoadTime()
	 */
	@Override
	public long getTotalLoadTime() {
		return check().totalLoadTime();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.OTMetricCacheStatsMBean#getTotalLoadTimeMs()
	 */
	@Override
	public long getTotalLoadTimeMs() {		
		return TimeUnit.MILLISECONDS.convert(check().totalLoadTime(), TimeUnit.NANOSECONDS);				
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.OTMetricCacheStatsMBean#getAverageLoadPenalty()
	 */
	@Override
	public double getAverageLoadPenalty() {
		return check().averageLoadPenalty();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.OTMetricCacheStatsMBean#getAverageLoadPenaltyMs()
	 */
	@Override
	public double getAverageLoadPenaltyMs() {		
		return TimeUnit.MILLISECONDS.convert((long)check().averageLoadPenalty(), TimeUnit.NANOSECONDS);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.OTMetricCacheStatsMBean#getEvictionCount()
	 */
	@Override
	public long getEvictionCount() {
		return check().evictionCount();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.OTMetricCacheStatsMBean#printStats()
	 */
	@Override
	public String printStats() {
		return check().toString();
	}

}
