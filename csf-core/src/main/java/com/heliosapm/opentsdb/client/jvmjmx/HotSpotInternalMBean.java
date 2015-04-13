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

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import sun.management.counter.Counter;

/**
 * <p>Title: HotSpotInternalMBean</p>
 * <p>Description: Defines a hotspot internal sampling manager</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.HotSpotInternalMBean</code></p>
 */
@SuppressWarnings("restriction")
public interface HotSpotInternalMBean {
	
	/** Dot Split Pattern */
	public static final Pattern DOT_SPLIT = Pattern.compile("\\.");
	/** Match all counters pattern */
	public static final Pattern MATCH_ALL = Pattern.compile(".*");
	/** The long type perf counter */
	public static final Class<?> TRACE_COUNTER = sun.management.counter.perf.PerfLongCounter.class;


	
	/**
	 * Sets a new counter pattern to define the traceable counters
	 * @param counterPattern The pattern to filter in counters by counter name
	 * @return A set of the matched counter names
	 */
	public Set<String> setCounterPattern(final String counterPattern);
	
	
	/**
	 * Sets a new counter pattern to define the traceable counters
	 * @param counterPattern The pattern to filter in counters by counter name
	 * @return A set of the matched counter names
	 */
	public Set<String> setCounterPattern(final Pattern counterPattern);
	
	/**
	 * Traces all the matching counter names
	 */
	public void trace();
	
	/**
	 * Returns the current counter matching pattern
	 * @return the current counter matching pattern
	 */
	public Pattern getCounterPattern();
	
	/**
	 * Returns a set of the currently matched counter names
	 * @return a set of the currently matched counter names
	 */
	public Set<String> getMatchedCounterNames();
	
	/**
	 * Returns a map of all the counters keyed by the counter name
	 * @return a map of all the counters keyed by the counter name
	 */	
	public Map<String, Counter> getAllCounters();
	
	/**
	 * Returns a map of all the counters keyed by the counter name 
	 * where the counter name matches the passed pattern
	 * @param pattern The counter name matching pattern
	 * @return a map of all the counters keyed by the counter name
	 */
	public Map<String, Counter> getAllCounters(final Pattern pattern);
	
	/**
	 * Returns a map of the currently filtered counters keyed by the counter name
	 * @return a map of the currently filtered counters keyed by the counter name
	 */
	public Map<String, Counter> getFilteredCounters();

}
