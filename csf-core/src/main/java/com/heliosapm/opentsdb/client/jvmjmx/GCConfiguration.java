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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.heliosapm.opentsdb.client.util.JMXHelper;

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
	public final Map<String, Counter> allCountersByName;

	/** All the name values keyed by the counter name */
	public final Map<String, String> nameValuesByCounterName = new HashMap<String, String>();

	public final Map<String, String> resolvedSingleTags = new  HashMap<String, String>();
	
	
	/** A map of tags keyed by the original counter name for all counters which can be traced */
	public final Map<String, Map<String, String>> counterNameToTags = new HashMap<String, Map<String, String>>(); 
	/** The collector name counters map (counter-name --> collector-name) */
	protected final LinkedHashMap<String, String> collectorNamesByName = new LinkedHashMap<String, String>(2); 
	/** The collector name counters keyed by the id  (collector-id --> collector-name) */
	protected final TreeMap<Integer, String> collectorNamesById = new TreeMap<Integer, String>(); 
	
	/** This is the only counter class we would trace values for */
	protected final Class<?> TRACE_COUNTER = sun.management.counter.perf.PerfLongCounter.class;
	
	/** The GC policy name */
	public final String gcPolicy;
	
	/** The GC Collector Name Pattern */
	public static final Pattern COLLECTOR_NAME_PATTERN = Pattern.compile("sun\\.gc\\.collector\\.(\\d+)\\.name");
	/** The GC counter index extractor Pattern for names */
	public static final Pattern INDEX_NAME_PATTERN = Pattern.compile(".*\\.(\\d)\\..*name");
	/** The GC counter index extractor Pattern */
	public static final Pattern INDEX_PATTERN = Pattern.compile(".*\\.(\\d)\\..*");
	
	//public static final Pattern INDEX_PATTERN = Pattern.compile(".*\\.(\\d)\\.");
	/** A simple int Pattern */
	public static final Pattern INT_PATTERN = Pattern.compile("(\\d)");
	
	// Generation Names:  ".*\\.generation\\.(\\d)\\.name"
	/*
		[sun.gc.generation.0.name] value:new, type:PerfStringCounter, unit:String, var:Constant
		[sun.gc.generation.1.name] value:old, type:PerfStringCounter, unit:String, var:Constant
		[sun.gc.generation.2.name] value:perm, type:PerfStringCounter, unit:String, var:Constant
	 */
	
	
	public static void main(String[] args) {
		JMXHelper.registerHotspotInternal();
		List<Object> counters = (List<Object>)JMXHelper.getAttribute(MBeanObserver.HOTSPOT_MEMORY_MBEAN.objectName, "InternalMemoryCounters");
		GCConfiguration conf = new GCConfiguration(counters);
	}
	
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
				counterNameToTags.put(ctr.getName(), new LinkedHashMap<String, String>(4));
				if(ctr.getName().endsWith(".name")) {
					nameValuesByCounterName.put(ctr.getName(), ctr.getValue().toString());
				}
			}
		}
		/*
			[sun.gc.generation.0.space.0.name] value:eden, type:PerfStringCounter, unit:String, var:Constant
			[sun.gc.generation.0.space.1.name] value:s0, type:PerfStringCounter, unit:String, var:Constant
			[sun.gc.generation.0.space.2.name] value:s1, type:PerfStringCounter, unit:String, var:Constant
			[sun.gc.generation.1.space.0.name] value:space, type:PerfStringCounter, unit:String, var:Constant
			[sun.gc.generation.2.space.0.name] value:perm, type:PerfStringCounter, unit:String, var:Constant
		 */
		
		allCountersByName = Collections.unmodifiableMap(ctrs);
		gcPolicy = allCountersByName.get("sun.gc.policy.name").getValue().toString();
		log("GCPolicy: %s", gcPolicy);
		final Map<String, Counter> allNameCounters = new HashMap<String, Counter>();
		for(Map.Entry<String, Counter> entry: allCountersByName.entrySet()) {
			if(entry.getKey().endsWith(".name")) allNameCounters.put(entry.getKey(), entry.getValue());
			Matcher m = COLLECTOR_NAME_PATTERN.matcher(entry.getKey());
			if(m.matches()) {
				// collectorNamesByName:  counter-name --> collector-name
				// collectorNamesById: collector-id --> collector-name
				
				collectorNamesByName.put(entry.getKey(), entry.getValue().toString());
				collectorNamesById.put(Integer.parseInt(m.group(1)), entry.getValue().toString());
			}
		}
		final Set<String> nsc = new HashSet<String>(allNameCounters.keySet());
		int indexQ = 1;
		while(!allNameCounters.isEmpty()) {
			for(String key: nsc) {
				final Counter ctr = allNameCounters.get(key);
				if(ctr==null) continue;
				final String nm = ctr.getName();
				final int idx = getIndexCount(INDEX_NAME_PATTERN, nm);
				
				if(idx==0) {
					allNameCounters.remove(key);
					continue;
				}
				if(idx==indexQ) {
					resolveTags(INDEX_NAME_PATTERN, ctr);
					allNameCounters.remove(key);
				}
				
			}
			indexQ++;
		}
		log("\nSingle Tags:");
		for(Map.Entry<String, String> entry: resolvedSingleTags.entrySet()) {
			log("\t%s : %s", entry.getKey(), entry.getValue());
		}
		for(String s: allCountersByName.keySet()) {
			if(!s.endsWith(".name") && counterNameToTags.get(s).isEmpty()) {
				Counter ctr = allCountersByName.get(s);
				if(TRACE_COUNTER.isInstance(ctr)) {
					resolveTags(INDEX_PATTERN, ctr);
				} else {
					counterNameToTags.remove(s);
				}
			}
		}
		log("\nTags:");
		for(Map.Entry<String, Map<String, String>> entry: counterNameToTags.entrySet()) {
			log("\t%s : %s", entry.getKey(), entry.getValue());
		}		
		
		//loadTree();
	}
	
	public static int getIndexCount(final Pattern p, final String counterName) {
		Matcher m = p.matcher(counterName);
		if(!m.matches()) return 0;
		int count = 0;
		m = INT_PATTERN.matcher(counterName);
		while(m.find()) count++;
		return count;		
	}
	
//	public int[] getIndexes(final String counterName) {
//		int count = getIndexCount(counterName);
//		final int[] indexes = new int[count];
//		Matcher m = INT_PATTERN.matcher(counterName);
//		count = 0;
//		while(m.find()) {
//			indexes[count] = Integer.parseInt(m.group(1));
//			count++;
//		}
//		return indexes;		
//	}
	
	public void resolveTags(final Pattern p, final Counter counter) {
		final String name = counter.getName();
		final int indexCount = getIndexCount(p, name);
		int currentIndexCount = 0;
		if(indexCount==0) return;
		// nameValuesByCounterName
		final Map<String, String> tags = counterNameToTags.get(name);
		final StringBuilder b = new StringBuilder();
		final String[] frags = name.split("\\.");
		for(int i = 0; i < frags.length; i++) {
			b.append(frags[i]).append(".");
			int idx = toInt(frags[i]);
			if(idx < 0) continue;
			currentIndexCount++;
			final String key = frags[i-1];
			if(currentIndexCount==indexCount) {
				tags.put(key, counter.getValue().toString());
				resolvedSingleTags.put(key + "." + idx, counter.getValue().toString());
			} else {
				String parentValue = counterNameToTags.get(new StringBuilder(b.toString()).append("name").toString()).get(key);
				tags.put(key, parentValue);
				resolvedSingleTags.put(key + "." + idx, parentValue);
			}
		}		
	}
	
	
	/**
	 * Format out logger
	 * @param fmt The format
	 * @param args The token values
	 */
	public static void log(final Object fmt, final Object...args) {
		System.out.println(String.format(fmt.toString(), args));
	}
	
	
	public static int toInt(final String v) {
		try {
			return Integer.parseInt(v);
		} catch (Exception ex) {
			return -1;
		}
	}
	
	
	private LinkedHashMap<String, Counter> getIndexedStartingWith(final String prefix) {
		final LinkedHashMap<String, Counter> matches = new LinkedHashMap<String, Counter>();
		int index = 0;
//		while(true) {
//			
//		}
//		for(Map.Entry<String, Counter> entry: countersByName.entrySet()) {
//			
//		}
		return new LinkedHashMap<String, Counter>(matches);
	}
}
