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
package com.heliosapm.opentsdb.client.jvmjmx;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;

import javax.management.MBeanServerConnection;

import sun.management.counter.Counter;

import com.heliosapm.opentsdb.client.opentsdb.MetricBuilder;
import com.heliosapm.opentsdb.client.opentsdb.OTMetric;
import com.heliosapm.utils.jmx.JMXHelper;

/**
 * <p>Title: AbstractHotSpotInternalMBean</p>
 * <p>Description: Base hotspot internal mbean sampler</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.AbstractHotSpotInternalMBean</code></p>
 */
@SuppressWarnings("restriction")
public class AbstractHotSpotInternalMBean implements HotSpotInternalMBean {
	/** The MBeanServer this mbean came from */
	protected MBeanServerConnection mbsc;
	/** The pattern applied to the counter names to filter in traceable counters */
	protected Pattern counterPattern = null;
	/** The counter pattern matching counter names */
	protected final CopyOnWriteArraySet<String> matchingCounterNames = new CopyOnWriteArraySet<String>();
	/** A map of the OTMetrics for currently matching counter names keyed by the counter name */
	protected final ConcurrentHashMap<String, OTMetric> matchingOTMetrics = new ConcurrentHashMap<String, OTMetric>();
	/** Maps of TSDB metric tags keyed by the performance counter name */
	protected final Map<String, Map<String, String>> counterNameToTags = new HashMap<String, Map<String, String>>();
	/** Maps of TSDB metric names keyed by the performance counter name */
	protected final Map<String, String> counterNameToMetricNames = new HashMap<String, String>();	
	/** The observer type */
	protected final MBeanObserver hotspotObserver;
	
	/**
	 * Format out logger
	 * @param fmt The format
	 * @param args The token values
	 */
	public static void log(final Object fmt, final Object...args) {
		System.out.println(String.format(fmt.toString(), args));
	}
	

	/**
	 * Creates a new AbstractHotSpotInternalMBean
	 * @param hotspotObserver The hotspot observer type
	 * @param counterPattern The counter pattern matching counter names.
	 * Matches all traceable if not specified.
	 * @param mbsc The MBeanServer this mbean came from.
	 * Uses the default Helios MBeanServer if not specified
	 */
	protected AbstractHotSpotInternalMBean(final MBeanObserver hotspotObserver, final Pattern counterPattern, final MBeanServerConnection mbsc) {
		this.hotspotObserver = hotspotObserver;
		this.counterPattern = counterPattern==null ? MATCH_ALL : counterPattern;
		this.mbsc = mbsc==null ? JMXHelper.getHeliosMBeanServer() : mbsc;		
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.HotSpotInternalMBean#setCounterPattern(java.lang.String)
	 */
	@Override
	public Set<String> setCounterPattern(final String counterPattern) {
		if(counterPattern==null || counterPattern.trim().isEmpty()) throw new IllegalArgumentException("The passed counter pattern was null or empty");
		return setCounterPattern(Pattern.compile(counterPattern));
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.HotSpotInternalMBean#setCounterPattern(java.util.regex.Pattern)
	 */
	@Override
	public Set<String> setCounterPattern(final Pattern counterPattern) {
		if(counterPattern==null) throw new IllegalArgumentException("The passed counter pattern was null");
		this.counterPattern = counterPattern;
		final Set<String> matched = new HashSet<String>();
		for(String tcounterName: counterNameToTags.keySet()) {
			if(this.counterPattern.matcher(tcounterName).matches()) {
				matched.add(tcounterName);
			}
		}
		matchingCounterNames.addAll(matched);
		matchingCounterNames.retainAll(matched);
		final Map<String, OTMetric> tmpMap = new HashMap<String, OTMetric>(matchingCounterNames.size());
		for(String name: matchingCounterNames) {
			final Map<String, String> tags = counterNameToTags.get(name);
			final String metricName = counterNameToMetricNames.get(name);
			if(tags==null || metricName==null) {
				log("!!!  Missing meta for [%s]", name);
				continue;
			}
			tmpMap.put(name, MetricBuilder.metric(metricName).tags(tags).optBuild());
		}
		matchingOTMetrics.putAll(tmpMap);
		matchingOTMetrics.keySet().retainAll(tmpMap.keySet());
		return matched;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.HotSpotInternalMBean#trace()
	 */
	@Override
	public void trace() {
		// TODO Auto-generated method stub
		
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.HotSpotInternalMBean#getCounterPattern()
	 */
	@Override
	public Pattern getCounterPattern() {
		return counterPattern;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.HotSpotInternalMBean#getMatchedCounterNames()
	 */
	@Override
	public Set<String> getMatchedCounterNames() {
		return new HashSet<String>(matchingCounterNames);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.HotSpotInternalMBean#getAllCounters()
	 */
	@Override
	public Map<String, Counter> getAllCounters() {
		return getAllCounters(null);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.HotSpotInternalMBean#getAllCounters(java.util.regex.Pattern)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Map<String, Counter> getAllCounters(final Pattern pattern) {
		try {
			final Map<String, Object> allCounters = JMXHelper.getAttributes(hotspotObserver.objectName, mbsc, hotspotObserver.getAttributeNames());
			final Map<String, Counter> map = new TreeMap<String, Counter>();
			for(Object list: allCounters.values()) {
				final List<Counter> counters = (List<Counter>)list;
				for(Counter ctr: counters) {
					if(pattern==null || pattern.matcher(ctr.getName()).matches()) {
						map.put(ctr.getName(), ctr);
					}
				}				
			}
			return map;
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.HotSpotInternalMBean#getFilteredCounters()
	 */
	@Override
	public Map<String, Counter> getFilteredCounters() {
		final Map<String, Counter> map = getAllCounters();
		for(Iterator<String> iter = map.keySet().iterator(); iter.hasNext();) {
			final String name = iter.next();
			if(!matchingCounterNames.contains(name)) {
				iter.remove();
			}
		}
		return map;
	}

}
