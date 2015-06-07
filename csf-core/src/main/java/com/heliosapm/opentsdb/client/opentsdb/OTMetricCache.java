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
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import javax.management.ObjectName;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistryListener;
import com.codahale.metrics.Timer;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheBuilderSpec;
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

public class OTMetricCache implements RemovalListener<String, OTMetric>, MetricRegistryListener {
	/** The singleton instance */
	private static volatile OTMetricCache instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	
	/** The cache stats JMX object name */
	public final ObjectName OBJECT_NAME;

	
	/** An empty OTMetric array const */
	private static final OTMetric[] EMPTY_OT_METRIC_ARR = {};
	
	/** Instance logger */
	private final Logger log;
	/** The OTMetric spec */
	private final Cache<String, OTMetric> cache;
//	/** The cache loader */
//	private final CacheLoader<String, OTMetric> loader;
	
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
		OBJECT_NAME = Util.objectName(Util.getJMXDomain() + ":service=OTMetricCache");
		CacheBuilderSpec spec = null;
		String cacheSpec = ConfigurationReader.conf(Constants.PROP_OTMETRIC_CACHE_SPEC, Constants.DEFAULT_OTMETRIC_CACHE_SPEC);
		try {	
			spec = CacheBuilderSpec.parse(cacheSpec);
		} catch (Exception ex) {
			log.warn("Invalid Cache Spec [{}]. Reverting to default: [{}]", cacheSpec, Constants.DEFAULT_OTMETRIC_CACHE_SPEC);
			cacheSpec = Constants.DEFAULT_OTMETRIC_CACHE_SPEC;
			spec = CacheBuilderSpec.parse(cacheSpec);
		}
//		loader = new CacheLoader<String, OTMetric>() {
//			@Override
//			public OTMetric load(String key) throws Exception {				
//				return new OTMetric(key);
//			}
//		};
		cache = CacheBuilder.from(spec)
				.removalListener(this)				
				.build();
		if(cacheSpec.contains("recordStats")) {
			OTMetricCacheStats stats = new OTMetricCacheStats(cache);
			try {
				Util.registerMBean(stats, OBJECT_NAME);
			} catch (Exception ex) {
				log.warn("Failed to register OTMetricCacheStats. Will Continue without");
			}
		}
	}
	
	/**
	 * Clears the cache
	 */
	public void clear() {
		cache.invalidateAll();
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
		if(name==null || name.trim().isEmpty()) return null;
		final String key = new StringBuilder()
			.append(post(nprefix, "."))
			.append(name.trim())
			.append(pre(extension, "."))
			.append(extraTags==null || extraTags.isEmpty() ? "" : extraTags.toString())
			.toString();
		try {
			return cache.get(key, new Callable<OTMetric>(){
				@Override
				public OTMetric call() throws Exception {
					return new OTMetric(name, nprefix, extension, extraTags);
				}
			});
		} catch (Exception ex) {
			log.error("Failed to create OTMetric for key [{}]", key, ex);
			throw new RuntimeException("Failed to create OTMetric", ex);
		}
	}
	
	private String pre(final String s, final String pre) {
		if(s!=null && !s.trim().isEmpty()) return pre + s.trim();
		return "";
	}
	
	private String post(final String s, final String post) {
		if(s!=null && !s.trim().isEmpty()) return s.trim() + post;
		return "";
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

	@Override
	public void onGaugeAdded(final String name, final Gauge<?> gauge) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onGaugeRemoved(final String name) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onCounterAdded(final String name, final Counter counter) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onCounterRemoved(final String name) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onHistogramAdded(final String name, final Histogram histogram) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onHistogramRemoved(final String name) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onMeterAdded(final String name, final Meter meter) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onMeterRemoved(final String name) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onTimerAdded(final String name, final Timer timer) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onTimerRemoved(final String name) {
		// TODO Auto-generated method stub
		
	}

}
