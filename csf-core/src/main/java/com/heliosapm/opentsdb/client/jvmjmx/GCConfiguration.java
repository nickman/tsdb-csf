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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import sun.management.counter.Counter;

/**
 * <p>Title: GCConfiguration</p>
 * <p>Description: A container to parse and structure the GC configuration from the performance counters</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.GCConfiguration</code></p>
 */

@SuppressWarnings("restriction")
public class GCConfiguration {
	/** All the memory counters keyed by the counter name */
	public final Map<String, Counter> countersByName;
	/** The collector name counters keyed by the name */
	protected final LinkedHashMap<String, Counter> collectorNamesByName;
	
	/** The GC policy name */
	public final String gcPolicy;
	
	/**
	 * Creates a new GCConfiguration
	 * @param counters A list of the Hotspot internal memory performance counters
	 */
	public GCConfiguration(final List<Object> counters) {
		if(counters==null || counters.isEmpty()) throw new IllegalArgumentException("The passed list of counters was null or empty");
		final Map<String, Counter> ctrs = new LinkedHashMap<String, Counter>(counters.size());
		for(Object obj: counters) {
			if(obj instanceof Counter) {
				Counter ctr = (Counter)obj;
				ctrs.put(ctr.getName(), ctr);
			}
		}
		countersByName = Collections.unmodifiableMap(ctrs);
		gcPolicy = countersByName.get("sun.gc.policy.name").getValue().toString();
		
		//loadTree();
	}
	
	
	private LinkedHashMap<String, Counter> getIndexedStartingWith(final String prefix) {
		final LinkedHashMap<String, Counter> matches = new LinkedHashMap<String, Counter>();
		int index = 0;
		while(true) {
			
		}
		for(Map.Entry<String, Counter> entry: countersByName.entrySet()) {
			
		}
		return new LinkedHashMap<String, Counter>(matches);
	}
}
