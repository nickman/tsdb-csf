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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheBuilderSpec;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: OTMetricCache</p>
 * <p>Description: The OTMetric cache service.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.OTMetricCache</code></p>
 */

public class OTMetricCache implements RemovalListener<String, OTMetric> {
	/** The singleton instance */
	private static volatile OTMetricCache instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	
	/** An empty OTMetric array const */
	private static final OTMetric[] EMPTY_OT_METRIC_ARR = {};
	
	/** Instance logger */
	private final Logger log;
	/** The OTMetric spec */
	private final LoadingCache<String, OTMetric> cache;
	/** The cache loader */
	private final CacheLoader<String, OTMetric> loader;
	
	/**
	 * Acquires the OTMetricCache singleton instance
	 * @return the OTMetricCache singleton instance
	 */
	public static OTMetricCache getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new OTMetricCache();
				}
			}
		}
		return instance;
	}
	
	/**
	 * Creates a new OTMetricCache
	 */
	private OTMetricCache() {
		log = LogManager.getLogger(getClass());
		CacheBuilderSpec spec = null;
		String cacheSpec = ConfigurationReader.conf(Constants.PROP_OTMETRIC_CACHE_SPEC, Constants.DEFAULT_OTMETRIC_CACHE_SPEC);
		try {	
			spec = CacheBuilderSpec.parse(cacheSpec);
		} catch (Exception ex) {
			log.warn("Invalid Cache Spec [{}]. Reverting to default: [{}]", cacheSpec, Constants.DEFAULT_OTMETRIC_CACHE_SPEC);
			cacheSpec = Constants.DEFAULT_OTMETRIC_CACHE_SPEC;
			spec = CacheBuilderSpec.parse(cacheSpec);
		}
		loader = new CacheLoader<String, OTMetric>() {
			@Override
			public OTMetric load(String key) throws Exception {				
				return new OTMetric(key);
			}
		};
		cache = CacheBuilder.from(spec)
				.removalListener(this)				
				.build(loader);
		if(cacheSpec.contains("recordStats")) {
			OTMetricCacheStats stats = new OTMetricCacheStats(cache);
			try {
				Util.registerMBean(stats, OTMetricCacheStats.OBJECT_NAME);
			} catch (Exception ex) {
				log.warn("Failed to register OTMetricCacheStats. Will Continue without");
			}
		}
	}
	
	/**
	 * Returns the OTMetric for the passed name
	 * @param name The metric name 
	 * @return the OTMetric or null if the passed name was null or empty
	 */
	public OTMetric getOTMetric(final String name) {
		if(name==null || name.trim().isEmpty()) return null;
		return cache.getUnchecked(name.trim());
	}
	
	/**
	 * Returns the OTMetrics matching the passed pattern
	 * @param pattern The regular expression to match
	 * @param caseInsensitive true for a case insensitive match, false otherwise
	 * @return A possibly empty array of the matching OTMetrics
	 */
	public OTMetric[] getOTMetrics(final String pattern, final boolean caseInsensitive) {
		if(pattern==null || pattern.trim().isEmpty()) return EMPTY_OT_METRIC_ARR;
		final Pattern p = caseInsensitive ? Pattern.compile(pattern.trim(), Pattern.CASE_INSENSITIVE) : Pattern.compile(pattern.trim());
		final List<OTMetric> matches = new ArrayList<OTMetric>();
		for(String name: cache.asMap().keySet()) {
			if(p.matcher(name).matches()) {
				matches.add(getOTMetric(name));
			}
		}
		return matches.isEmpty() ? EMPTY_OT_METRIC_ARR : matches.toArray(new OTMetric[0]);
	}
	
	/**
	 * Returns the OTMetrics matching the passed pattern with case sensitivity
	 * @param pattern The regular expression to match
	 * @return A possibly empty array of the matching OTMetrics
	 */
	public OTMetric[] getOTMetrics(final String pattern) {
		return getOTMetrics(pattern, false);
	}

	
	
	/**
	 * <p>Cleans removed OTMetrics</p>
	 * {@inheritDoc}
	 * @see com.google.common.cache.RemovalListener#onRemoval(com.google.common.cache.RemovalNotification)
	 */
	@Override
	public void onRemoval(final RemovalNotification<String, OTMetric> notification) {
		OTMetric otm = notification.getValue();
		if(otm!=null) {
			if(log.isDebugEnabled()) {
				log.debug("OTMetric [{}] Removed from Cache", otm);
			}
			otm.clean();
		}
		
	}

}