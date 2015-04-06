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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sun.management.counter.Counter;

import com.heliosapm.opentsdb.client.util.JMXHelper;

/**
 * <p>Title: GCConfiguration</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.GCConfiguration</code></p>
 */
@SuppressWarnings("restriction")
public class GCConfiguration {

	/** The GC Collector Name Pattern */
	public static final Pattern COLLECTOR_NAME_PATTERN = Pattern.compile("sun\\.gc\\.collector\\.(\\d+)\\.name");
	/** The GC counter index extractor Pattern for names */
	public static final Pattern INDEX_NAME_PATTERN = Pattern.compile(".*\\.(\\d)\\..*name");
	/** The GC counter index extractor Pattern */
	public static final Pattern INDEX_PATTERN = Pattern.compile(".*\\.(\\d)\\..*");
	
	//public static final Pattern INDEX_PATTERN = Pattern.compile(".*\\.(\\d)\\.");
	/** A simple int Pattern */
	public static final Pattern INT_PATTERN = Pattern.compile("(\\d)");

	public static final Class<?> TRACE_COUNTER = sun.management.counter.perf.PerfLongCounter.class;
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		log("GCConfiguration");
		try {
			JMXHelper.registerHotspotInternal();
			load((List<Object>)JMXHelper.getAttribute(MBeanObserver.HOTSPOT_MEMORY_MBEAN.objectName, "InternalMemoryCounters"));
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
			Runtime.getRuntime().halt(-99);
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

	
	public static Map<String, Map<String, String>> load(final List<Object> counters) {		
		final Map<String, Counter> allCountersByName = new HashMap<String, Counter>(counters.size());
		final Map<String, Counter> allNameCountersByName = new HashMap<String, Counter>(16);
		final Map<String, Map<String, String>> counterNameToTags = new HashMap<String, Map<String, String>>();
		final Map<String, String> resolvedIndexNames = new  HashMap<String, String>();
		final Set<String> plurals = new HashSet<String>();
		
		final String gcPolicy;
		for(Object obj: counters) {
			if(!(obj instanceof Counter)) continue;
			final Counter counter = (Counter)obj;
			final String counterName = counter.getName();			
			if(counterName.endsWith(".name")) {
				allNameCountersByName.put(counterName, counter);
			} else {
				allCountersByName.put(counterName, counter);
			}
		}
		log("Indexed [%s] Counters By Name and [%s] Name Counters", allCountersByName.size(), allNameCountersByName.size());
		gcPolicy = allNameCountersByName.remove("sun.gc.policy.name").getValue().toString();
		log("GC Policy: %s", gcPolicy);
		int indexCounts = 0;
		while(true) {
			Map<String, Counter> extracted = extractIndexedNames(allNameCountersByName, indexCounts);
			if(indexCounts>0 && extracted.isEmpty()) break;
			printMap("Extracted Names with " + indexCounts + " Indexes", extracted);
			if(!extracted.isEmpty() && indexCounts > 0) {
				Pattern p = extractNameIndexPatternBuilder(indexCounts, "\\.name");
				for(Map.Entry<String, Counter> ent: extracted.entrySet()) {
					String name = ent.getKey();
					Counter ctr = ent.getValue();
					String extractedName = extractNameIndex(p, name, indexCounts, plurals);
					if(extractedName!=null) {
						resolvedIndexNames.put(extractedName, ctr.getValue().toString());
					}
					
				}
			}
			indexCounts++;
		}
		printMap("resolvedIndexNames:", resolvedIndexNames);
		final Set<String> pluralsRemoved = new HashSet<String>();
		for(Iterator<String> iter = allCountersByName.keySet().iterator(); iter.hasNext();) {
			final String name = iter.next();
			for(String pl: plurals) {
				if(name.endsWith("." + pl)) {
					pluralsRemoved.add(name);
					iter.remove();
					break;
				}
			}
		}
		//printSet("pluralsRemoved:", pluralsRemoved);
		//printMap("remainingCounters:", allCountersByName);
		for(Map.Entry<String, Counter> ent: allCountersByName.entrySet()) {
			final String name = ent.getKey();
			final Counter ctr = ent.getValue();
			if(!(TRACE_COUNTER.isInstance(ctr)) || "Constant".equals(ctr.getVariability().toString())) continue;
			final LinkedHashMap<String, String> tags = new LinkedHashMap<String, String>(4);
			counterNameToTags.put(ent.getKey(), tags);
			tags.putAll(getTagsFor(ctr, resolvedIndexNames));
		}
		printMap("counterNameToTags:", counterNameToTags);
		// clean out any remaining constants
		for(String name: new HashSet<String>(counterNameToTags.keySet())) {
			Counter ctr = allCountersByName.get(name);
			if(ctr==null) continue;
			if("Constant".equals(ctr.getVariability().toString())) {
				counterNameToTags.remove(name);
			}
		}
		printMap("counterNameToTags:", counterNameToTags);
		return counterNameToTags;
	}

	
	public static int getIndexCount(final String counterName) {
		Matcher m = INDEX_PATTERN.matcher(counterName);
		if(!m.matches()) return 0;
		int count = 0;
		m = INT_PATTERN.matcher(counterName);
		while(m.find()) count++;
		return count;		
	}
	
	public static Map<String, Counter> extractIndexedNames(final Map<String, Counter> nameCounters, final int indexCount) {
		if(nameCounters.isEmpty()) return nameCounters;
		final Map<String, Counter> extracts = new HashMap<String, Counter>();
		for(Map.Entry<String, Counter> entry: nameCounters.entrySet()) {
			String name = entry.getKey();
			Counter ctr = entry.getValue();
			if(getIndexCount(name)==indexCount) {
				if(indexCount>0) {
					extracts.put(name, ctr);
				}
			}
		}
		for(String key: extracts.keySet()) { nameCounters.remove(key); }
		return extracts;
	}
	
	public static Map<String, String> getTagsFor(final Counter ctr, final Map<String, String> resolvedIndexNames) {
		final Map<String, String> tags = new LinkedHashMap<String, String>(4);
		final String[] frags = ctr.getName().split("\\.");
		StringBuilder ck = new StringBuilder();
		for(int i = 0; i < frags.length; i++) {
			int idx = toInt(frags[i]);
			if(idx<0) continue;
			if(i!=0) {
				String key = frags[i-1];
				ck.append(key).append(".").append(idx);
				String value = resolvedIndexNames.get(ck.toString());
				tags.put(key, value);
				ck.append(".");
			}
		}
		String units = ctr.getUnits().toString();
		if("Ticks".equals(units)) units = "ms";
		else if("None".equals(units)) {
			units = null;
//			log("'None' Unit Counter: %s, value:[%s], var:[%s]", ctr, ctr.getValue(), ctr.getVariability());
		}
		if(units!=null) {
			tags.put("unit", units);
		}
		return tags;
	}
	
	public static String extractNameIndex(final Pattern p, final String name, int indexCount, final Set<String> plurals) {
		if(getIndexCount(name)!=indexCount) return null;
		StringBuilder b = new StringBuilder();
		Matcher m = p.matcher(name);
		if(!m.matches()) return null;
		if(m.groupCount() > 0) {
			for(int i = 1; i <=m.groupCount(); i++) {
				final String frag = m.group(i);
				final int idx = toInt(frag);
				if(idx==-1) {
					// we're at a name
					plurals.add(frag + "s");
				} else {
					// we're at an index					
					b.append(m.group(i-1)).append(".").append(idx).append(".");
				}
			}
			b.deleteCharAt(b.length()-1);
		}
		return b.toString();
	}
									
	public static int toInt(final String v) {
		try {
			return Integer.parseInt(v);
		} catch (Exception ex) {
			return -1;
		}
	}

	
	public static Pattern extractNameIndexPatternBuilder(final int indexCount, final String suffix) {
		StringBuilder b = new StringBuilder(".*");
		for(int i = 0; i < indexCount; i++) {
			b.append("\\.(.*)?\\.(\\d)");
		}
		if(suffix!=null) {
			b.append(suffix);
		}
		return Pattern.compile(b.toString());
	}
	
	public static void printMap(final String title, final Map<?, ?> map) {
//		log("====== %s ======", title);
//		for(Map.Entry<?, ?> entry: map.entrySet()) {
//			log("\t[%s]  :  [%s]", entry.getKey(), entry.getValue());
//		}
	}
	
	public static void printSet(final String title, final Set<?> set) {
//		log("====== %s ======", title);
//		for(Object o: set) {
//			log("\t[%s]", o);
//		}
	}
	



}
