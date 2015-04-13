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

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MBeanServerConnection;

import sun.management.counter.Counter;

import com.heliosapm.opentsdb.client.opentsdb.Constants;
import com.heliosapm.opentsdb.client.opentsdb.MetricBuilder;
import com.heliosapm.opentsdb.client.opentsdb.OTMetric;
import com.heliosapm.opentsdb.client.util.JMXHelper;

/**
 * <p>Title: GCConfiguration</p>
 * <p>Description: Builds a model of the GC Memory performance counters with tag references for each non-meta-data counter</p> 
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
	/** The GC counter IDX extractor Pattern */
	public static final Pattern IDX_PATTERN = Pattern.compile(".*\\.(\\d)\\.(.*)$");
	
	/** A simple int Pattern */
	public static final Pattern INT_PATTERN = Pattern.compile("(\\d)");
	/** Dot Split Pattern */
	public static final Pattern DOT_SPLIT = Pattern.compile("\\.");
	/** Match all counters pattern */
	public static final Pattern MATCH_ALL = Pattern.compile(".*");
	/** The long type perf counter */
	public static final Class<?> TRACE_COUNTER = sun.management.counter.perf.PerfLongCounter.class;

	
	/** Maps of TSDB metric tags keyed by the performance counter name */
	public final Map<String, Map<String, String>> counterNameToTags;
	/** Maps of TSDB metric names keyed by the performance counter name */
	public final Map<String, String> counterNameToMetricNames;	
	/** The GC Policy name */
	public final String gcPolicy;
	/** The collector, generation and space names keyed by the counter index segments */
	public final Map<String, String> resolvedIndexNames;
	/** An ordered set of the collector names */
	public final Set<String> collectorNames;
	/** Ordered sets of the memory space names keyed by the name of the generation they reside in */
	public final Map<String, Set<String>> generations;	
	/** The MBeanServer this GCC came from */
	protected final MBeanServerConnection mbsc;
	/** The pattern applied to the counter names to filter in traceable counters */
	protected Pattern counterPattern = null;
	/** The counter pattern matching counter names */
	protected final CopyOnWriteArraySet<String> matchingCounterNames = new CopyOnWriteArraySet<String>();
	/** A map of the OTMetrics for currently matching counter names keyed by the counter name */
	protected final ConcurrentHashMap<String, OTMetric> matchingOTMetrics = new ConcurrentHashMap<String, OTMetric>();
	
	/**
	 * Prints the current GC Config
	 * @param args none
	 */
	public static void main(String[] args) {
		final GCConfiguration gcc = GCConfiguration.getInstance();
		log(gcc);
//		if(args.length>0) {
//			Arrays.sort(args);
//			final boolean printDetails = Arrays.binarySearch(args, "--print-tags") >= 0;
//			if(printDetails) {
//				log("Counter Tags:");
//				for(Map.Entry<String, Map<String, String>> entry: gcc.counterNameToTags.entrySet()) {
//					final String mn = gcc.counterNameToMetricNames.get(entry.getKey());
//					if(mn!=null) {
//						log("\t%s  :  %s:%s", entry.getKey(), mn, entry.getValue().toString());
//					} else {
//						log("\t%s  :  %s", entry.getKey(), entry.getValue().toString());
//					}
//					
//				}
//			}
//		}
		System.setProperty(Constants.PROP_TRACE_TO_STDOUT, "true");
		System.setProperty(Constants.PROP_STDOUT_JSON, "true");
		MetricBuilder.reconfig();
		while(true) {
			try { Thread.sleep(3000); } catch (Exception ex) {}
			gcc.trace();
		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder("GConfiguration:")
			.append("\n\tGC Policy:").append(gcPolicy)
			.append("\n\tCollectors:").append(collectorNames)
			.append("\n\tGenerations[spaces]:");
		for(Map.Entry<String, Set<String>> entry: generations.entrySet()) {
			b.append("\n\t\t").append(entry.getKey()).append(":").append(entry.getValue());
		}
		b.append("\n\tIndexed TagSets:").append(counterNameToTags.size());
		b.append("\n\tFiltered Counters:").append(matchingOTMetrics.size());
		return b.toString();
	}
	
	/**
	 * Sets a new counter pattern to define the traceable counters
	 * @param counterPattern The pattern to filter in counters by counter name
	 * @return A set of the matched counter names
	 */
	public Set<String> setCounterPattern(final String counterPattern) {
		if(counterPattern==null || counterPattern.trim().isEmpty()) throw new IllegalArgumentException("The passed counter pattern was null or empty");
		return setCounterPattern(Pattern.compile(counterPattern));
	}
	
	
	/**
	 * Sets a new counter pattern to define the traceable counters
	 * @param counterPattern The pattern to filter in counters by counter name
	 * @return A set of the matched counter names
	 */
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
	 * Traces all the matching counter names
	 */
	public void trace() {
		final Map<String, Counter> filtered = getFilteredCounters(); 
		for(Map.Entry<String, OTMetric> entry: matchingOTMetrics.entrySet()) {
			final Counter ctr = filtered.get(entry.getKey());
			if(ctr==null) {
				log("!!!  Missing counter for [%s]", entry.getKey());
				continue;				
			}
			entry.getValue().trace(ctr.getValue());
		}
	}
	
	/**
	 * Returns the current counter matching pattern
	 * @return the current counter matching pattern
	 */
	public Pattern getCounterPattern() {
		return counterPattern;
	}
	
	/**
	 * Returns a set of the currently matched counter names
	 * @return a set of the currently matched counter names
	 */
	public Set<String> getMatchedCounterNames() {
		return new HashSet<String>(matchingCounterNames);
	}
	
	/**
	 * Returns a map of all the GC/Memory counters keyed by the counter name
	 * @return a map of all the GC/Memory counters keyed by the counter name
	 */
	public Map<String, Counter> getAllCounters() {
		return getAllCounters(null);
	}
	
	/**
	 * Returns a map of all the GC/Memory counters keyed by the counter name 
	 * where the counter name matches the passed pattern
	 * @param pattern The counter name matching pattern
	 * @return a map of all the GC/Memory counters keyed by the counter name
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Counter> getAllCounters(final Pattern pattern) {
		try {
			final List<Counter> ctrs = (List<Counter>)JMXHelper.getAttribute(mbsc, MBeanObserver.HOTSPOT_MEMORY_MBEAN.objectName, "InternalMemoryCounters");
			final Map<String, Counter> map = new TreeMap<String, Counter>();
			for(Counter ctr: ctrs) {
				if(pattern==null || pattern.matcher(ctr.getName()).matches()) {
					map.put(ctr.getName(), ctr);
				}
			}
			return map;
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
	/**
	 * Returns a map of the currently filtered counters keyed by the counter name
	 * @return a map of the currently filtered counters keyed by the counter name
	 */
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
	
//	public Map<String, Counter> readCounters(final Pattern counterNameFilter) {
//		
//	}

	/**
	 * Acquires a fresh instance of the GCConfiguration from the local JVM for all traceable counters
	 * @return a fresh instance of the GCConfiguration from the local JVM
	 */
	public static GCConfiguration getInstance() {
		return getInstance(JMXHelper.getHeliosMBeanServer(), null);
	}
	
	/**
	 * Acquires a fresh instance of the GCConfiguration from the local JVM
	 * @param counterPattern The pattern defining which traceable counters should be traced. All counters will be traced if null.
	 * @return a fresh instance of the GCConfiguration from the local JVM
	 */
	public static GCConfiguration getInstance(final Pattern counterPattern) {
		return getInstance(JMXHelper.getHeliosMBeanServer(), counterPattern);
	}
	
	/**
	 * Acquires a fresh instance of the GCConfiguration from the passed MBeanServerConnection for all traceable counters
	 * @param mbsc The MBeanServerConnection to acquire the GCConfiguration from. If null, will use the Helios default MBeanServer. 
	 * @return a fresh instance of the GCConfiguration from the passed MBeanServerConnection
	 */
	public static GCConfiguration getInstance(final MBeanServerConnection mbsc) {
		return getInstance(mbsc, null);
	}

	
	/**
	 * Acquires a fresh instance of the GCConfiguration from the passed MBeanServerConnection
	 * @param mbsc The MBeanServerConnection to acquire the GCConfiguration from. If null, will use the Helios default MBeanServer. 
	 * @param counterPattern The pattern defining which traceable counters should be traced. All counters will be traced if null.
	 * @return a fresh instance of the GCConfiguration from the passed MBeanServerConnection
	 */
	@SuppressWarnings("unchecked")
	public static GCConfiguration getInstance(final MBeanServerConnection mbsc, final Pattern counterPattern) {
		final MBeanServerConnection _mbsc = mbsc==null ? JMXHelper.getHeliosMBeanServer() : mbsc;
		try {
			if(!JMXHelper.isRegistered(_mbsc, MBeanObserver.HOTSPOT_MEMORY_MBEAN.objectName)) {
				JMXHelper.registerHotspotInternal(_mbsc);
			}			
			return load(counterPattern, _mbsc, (List<Object>)JMXHelper.getAttribute(mbsc, MBeanObserver.HOTSPOT_MEMORY_MBEAN.objectName, "InternalMemoryCounters"));
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}		
	}
	
	
	private GCConfiguration(final Pattern counterPattern, final MBeanServerConnection mbsc, final Map<String, Map<String, String>> counterNameToTags, final Map<String, String> resolvedIndexNames, final String gcPolicy) {
		this.counterPattern = counterPattern!=null ? counterPattern : MATCH_ALL;
		this.mbsc = mbsc;
		final Map<String, String> tmpMetricNames = new HashMap<String, String>(counterNameToTags.size());
		for(Map.Entry<String,Map<String, String>> entry: counterNameToTags.entrySet()) {
			final String idx = entry.getValue().remove("idx");
			if(idx!=null) {
				tmpMetricNames.put(entry.getKey(), idx);
			} else {
				log("!!!!!   NO IDX FOR [%s]", entry.getKey());
			}
		}
		this.counterNameToMetricNames = Collections.unmodifiableMap(tmpMetricNames);
		this.counterNameToTags = Collections.unmodifiableMap(counterNameToTags);
		this.resolvedIndexNames = Collections.unmodifiableMap(resolvedIndexNames);
		this.gcPolicy = gcPolicy;
		Set<String> tmpColls = new LinkedHashSet<String>();
		Map<String, Set<String>> tmpGens = new LinkedHashMap<String, Set<String>>();
		
		for(Map.Entry<String,String> entry: this.resolvedIndexNames.entrySet()) {
			final String key = entry.getKey();
			final String name = entry.getValue();
			if(key.startsWith("collector.")) {
				tmpColls.add(entry.getValue());
			} else if(key.startsWith("generation.")) {
				final String[] keys = key.split("\\.");
				if(keys.length==2) {
					if(!tmpGens.containsKey(name)) {
						tmpGens.put(name, new LinkedHashSet<String>());
					}
				} else {
					tmpGens.get(resolvedIndexNames.get(keys[0] + "." + keys[1])).add(name);
				}
			}
		}
		collectorNames = Collections.unmodifiableSet(new LinkedHashSet<String>(tmpColls));
		Map<String, Set<String>> tmpGens2 = new LinkedHashMap<String, Set<String>>(tmpGens.size());
		for(Map.Entry<String, Set<String>> entry: tmpGens.entrySet()) {
			tmpGens2.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<String>(entry.getValue())));
		}
		generations = Collections.unmodifiableMap(tmpGens2);
		setCounterPattern(this.counterPattern);
	}
	
	/**
	 * Format out logger
	 * @param fmt The format
	 * @param args The token values
	 */
	public static void log(final Object fmt, final Object...args) {
		System.out.println(String.format(fmt.toString(), args));
	}

	
	private static GCConfiguration load(final Pattern counterPattern, final MBeanServerConnection mbsc, final List<Object> counters) {		
		final Map<String, Counter> allCountersByName = new HashMap<String, Counter>(counters.size());
		final Map<String, Counter> allNameCountersByName = new HashMap<String, Counter>(16);
		final Map<String, Map<String, String>> counterNameToTags = new HashMap<String, Map<String, String>>();
		final Map<String, String> resolvedIndexNames = new  TreeMap<String, String>();
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
//		log("Indexed [%s] Counters By Name and [%s] Name Counters", allCountersByName.size(), allNameCountersByName.size());
		gcPolicy = allNameCountersByName.remove("sun.gc.policy.name").getValue().toString();
//		log("GC Policy: %s", gcPolicy);
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
		for(Map.Entry<String, Counter> ent: allCountersByName.entrySet()) {
			final Counter ctr = ent.getValue();
			if(!(TRACE_COUNTER.isInstance(ctr)) || "Constant".equals(ctr.getVariability().toString())) continue;
			final LinkedHashMap<String, String> tags = new LinkedHashMap<String, String>(4);
			counterNameToTags.put(ent.getKey(), tags);
			tags.putAll(getTagsFor(ctr, resolvedIndexNames));
		}
		// clean out any remaining constants
		for(String name: new HashSet<String>(counterNameToTags.keySet())) {
			Counter ctr = allCountersByName.get(name);
			if(ctr==null) continue;
			if("Constant".equals(ctr.getVariability().toString())) {
				counterNameToTags.remove(name);
			}
		}
//		printMap("counterNameToTags:", counterNameToTags);
		return new GCConfiguration(counterPattern, mbsc, counterNameToTags, resolvedIndexNames, gcPolicy);
	}
	
	@SuppressWarnings("unused")
	private static final Comparator<Object> arrSort = new Comparator<Object>() {
		@Override
		public int compare(final Object ob1, final Object ob2) {
			final String o1 = ob1.toString();
			final String o2 = ob2.toString();
			final int o1ArrLen = DOT_SPLIT.split(o1).length;
			final int o2ArrLen = DOT_SPLIT.split(o2).length;
			if(o1ArrLen == o2ArrLen) {
				return o1.compareTo(o2);
			}
			return o1ArrLen < o2ArrLen ? -1 : +1;
		}
	};

	
	private static int getIndexCount(final String counterName) {
		Matcher m = INDEX_PATTERN.matcher(counterName);
		if(!m.matches()) return 0;
		int count = 0;
		m = INT_PATTERN.matcher(counterName);
		while(m.find()) count++;
		return count;		
	}
	
	private static Map<String, Counter> extractIndexedNames(final Map<String, Counter> nameCounters, final int indexCount) {
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
	
	private static Map<String, String> getTagsFor(final Counter ctr, final Map<String, String> resolvedIndexNames) {
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

		final String idxValue = getIdxTag(ctr.getName());
		tags.put("idx", idxValue);		
		String units = ctr.getUnits().toString();
		if("Ticks".equals(units)) units = "ms";
		else if("None".equals(units)) {
			if(idxValue.toLowerCase().contains("slope")) {
				units = "slope";
			} else {
				units = null;
			}
//			log("'None' Unit Counter: %s, value:[%s], var:[%s]", ctr, ctr.getValue(), ctr.getVariability());
		}
		if(units!=null) {
			tags.put("unit", units.toLowerCase());
		}
		return tags;
	}
	
	private static String getIdxTag(final String name) {
		Matcher m = IDX_PATTERN.matcher(name);
		if(m.matches()) {
			return m.group(2);
		} else {
			return name.replace("sun.", "");
		}
	}
	
	private static String extractNameIndex(final Pattern p, final String name, int indexCount, final Set<String> plurals) {
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
									
	private static int toInt(final String v) {
		try {
			return Integer.parseInt(v);
		} catch (Exception ex) {
			return -1;
		}
	}

	
	private static Pattern extractNameIndexPatternBuilder(final int indexCount, final String suffix) {
		StringBuilder b = new StringBuilder(".*");
		for(int i = 0; i < indexCount; i++) {
			b.append("\\.(.*)?\\.(\\d)");
		}
		if(suffix!=null) {
			b.append(suffix);
		}
		return Pattern.compile(b.toString());
	}
	
	private static void printMap(final String title, final Map<?, ?> map) {
//		log("====== %s ======", title);
//		TreeMap<Object, Object> treeMap = new TreeMap<Object, Object>(arrSort);
//		treeMap.putAll(map);
//		for(Map.Entry<?, ?> entry: treeMap.entrySet()) {
//			log("\t[%s]  :  [%s]", entry.getKey(), entry.getValue());
//		}
	}
	
	@SuppressWarnings("unused")
	private static void printSet(final String title, final Set<?> set) {
//		log("====== %s ======", title);
//		for(Object o: set) {
//			log("\t[%s]", o);
//		}
	}
	



}
