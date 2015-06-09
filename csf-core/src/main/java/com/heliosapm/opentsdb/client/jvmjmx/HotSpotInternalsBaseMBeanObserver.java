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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;

import sun.management.counter.Counter;

import com.heliosapm.opentsdb.client.opentsdb.MetricBuilder;
import com.heliosapm.opentsdb.client.opentsdb.OTMetric;
import com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection;
import com.heliosapm.utils.jmx.JMXHelper;

/**
 * <p>Title: HotSpotInternalsBaseMBeanObserver</p>
 * <p>Description: Base HotSpot internals JMX collector</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.HotSpotInternalsBaseMBeanObserver</code></p>
 */
@SuppressWarnings("restriction")
public class HotSpotInternalsBaseMBeanObserver extends BaseMBeanObserver {
	
	/** Indicates if the hotspot internals MBean has been registered */
	protected final AtomicBoolean hotspotRegistered = new AtomicBoolean(false);
	
	/** The target hotspot MBean ObjectName */
	protected final ObjectName hotspotMBean;
	/** The pattern defining the counters to trace */
	protected final Pattern counterPattern; 
	/** Indicates if the target MBean is accessible and there are counters to trace */
	protected final boolean available;
	/** The matching counter names */
	protected final Map<String, OTMetric> counterNames = new HashMap<String, OTMetric>();
	/** The OTMetric prefix from the ObjectName */
	protected final String domainPrefix;
	/** The OTMetric tags for the memory counters */
	protected final Map<String, String> compiledGCTags = new HashMap<String, String>();
	
	/** The counter providing attribute names keyed by the ObjectName */
	public static final Map<String, MBeanObserver> HOTSPOT_MBEAN_OBSERVERS;
	
	static {
		final MBeanObserver[] hotspotObservers = MBeanObserver.getHotSpotObservers();
		Map<String, MBeanObserver> tmp = new HashMap<String, MBeanObserver>(hotspotObservers.length);
		for(MBeanObserver mbo : hotspotObservers) {
			String name = mbo.objectName.getKeyProperty("type").replace("Hotspot", "").trim().toLowerCase();
			tmp.put(name, mbo);
		}		
		HOTSPOT_MBEAN_OBSERVERS = Collections.unmodifiableMap(new HashMap<String, MBeanObserver>(tmp));
	}
	
	/**  */
	private static final long serialVersionUID = -6216924637150318633L;

	/**
	 * Creates a new HotSpotInternalsBaseMBeanObserver
	 * @param jmxConnector The JMXConnector that will supply an MBeanServerConnection
	 * @param publishObserverMBean If true, an observer management MBean will be registered
	 * @param hotspotMBean The target hotspot MBean ObjectName
	 * @param counterPattern The pattern defining the counters to trace
	 */
	public HotSpotInternalsBaseMBeanObserver(final JMXConnector jmxConnector, final boolean publishObserverMBean, final Map<String, String> tags, final String hotspotMBean, final String counterPattern) {
		this(RuntimeMBeanServerConnection.newInstance(jmxConnector), publishObserverMBean, tags, hotspotMBean, counterPattern);
		
	}
	

	/**
	 * Creates a new HotSpotInternalsBaseMBeanObserver
	 * @param mbeanServerConn The MBeanServerConnection to monitor
	 * @param publishObserverMBean If true, an observer management MBean will be registered
	 * @param hotspotMBean The target hotspot MBean ObjectName
	 * @param counterPattern The pattern defining the counters to trace
	 */
	public HotSpotInternalsBaseMBeanObserver(final MBeanServerConnection mbeanServerConn, final boolean publishObserverMBean, final Map<String, String> tags, final String hotspotMBean, final String counterPattern) {
		super(mbeanServerConn, HOTSPOT_MBEAN_OBSERVERS.get(hotspotMBean.trim().toLowerCase()), tags, publishObserverMBean);
		this.hotspotMBean = HOTSPOT_MBEAN_OBSERVERS.get(hotspotMBean.trim().toLowerCase()).objectName;
		this.domainPrefix = hotspotMBean.replace("sun.management:type=Hotspot", "").toLowerCase().trim();
		this.counterPattern = Pattern.compile(counterPattern);
		if(hotspotRegistered.compareAndSet(false, true)) {
			if(!JMXHelper.registerHotspotInternal(mbeanServerConn)) {
				hotspotRegistered.set(false);
			}
		}
		available = ready();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.BaseMBeanObserver#accept(java.util.Map, long, long)
	 */
	@Override
	protected boolean accept(Map<ObjectName, Map<String, Object>> data, long currentTime, long elapsedTime) {
		Map<String, Object> myMap = data.get(mbeanObserver.objectName);
		if(myMap!=null) {
			for(Object obj : myMap.values()) {
				if(!(obj instanceof List)) continue;
				List<Object> objList = (List<Object>)obj;
				if(objList.isEmpty()) continue;
				final Object first = objList.iterator().next();
				if(first==null) continue;
				if(first instanceof Counter) {
					
					List<? extends Counter> ctrs = (List<? extends Counter>)obj; 
					doTheseCounters(ctrs);
				}
				
			}
		}
		return true;
	}
	
	/**
	 * Traces the counter list
	 * @param counters The list of counters retrieved from the hotspot mbean 
	 */
	protected void doTheseCounters(final List<? extends Counter> counters) {
		for(Counter ctr : counters) {
			if(ctr==null) continue;
			final String name = ctr.getName();
			OTMetric otm = counterNames.get(name);
			if(otm==null) continue;
			otm.trace(ctr.getValue());
		}
	}
	
	/**
	 * Introspects the data and gathers the target counter names
	 * @return true if successful and more than zero counters were introspected
	 */
	protected boolean ready() {
		try {
			String[] attributes = this.mbeanObserver.getAttributeNames();
			Map<String, Object> attrValues = this.mbs.getAttributeMap(hotspotMBean, attributes);
			for(Object obj: attrValues.values()) {				
				@SuppressWarnings("unchecked")
				List<Counter> counters = (List<Counter>)obj;
				for(Counter ctr: counters) {
					final Object value = ctr.getValue();
					if(!Number.class.isInstance(value)) continue;					
					final String name = ctr.getName();
					final Matcher matcher = counterPattern.matcher(name); 
					if(matcher.matches()) {
						final String metricName = extractCounterName(matcher);
						
						
						// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
						// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
						//   BUILD OTMETRIC HERE and put in counterNames
						counterNames.put(name, MetricBuilder.metric(domainPrefix + "." + metricName).tag("hotspot", hotspotMBean.getKeyProperty("type").toLowerCase().replace("hotspot", "")).tags(tags).build());
						// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
						// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
						if(metricName==null || metricName.isEmpty()) continue;
//						String units = ctr.getUnits().toString().toLowerCase();
//						if("ticks".equals(units)) {
//							units = "ms";
//						}
					} else {
						continue;
					}					
				}
				compileGCTags(counters);
				log.info("======================================");
				for(Counter ctr: counters) {
					String name = ctr.getName();
					if(name.contains(".name")) continue;
					int[] indx = extractNameIndexes(name);
					if(indx.length>0) {
						log.info("\t{} : {}", name, ctr.getValue());
					}
				}
				log.info("======================================");
				for(Counter ctr: counters) {
					String name = ctr.getName();
					if(name.contains(".name")) continue;
					int[] indx = extractNameIndexes(name);
					if(indx.length==0) {
						log.info("\t{} : {}", name, ctr.getValue());
					}
				}
				

			}
			
			return true;		
		} catch (Exception ex) {
			return false;
		}		
	}
	
	/**
	 * Command line counter printer
	 * @param args : <ul>
	 * 	<li><b>-m &lt:hotspot mbean short names&gt;</b>: The comma separated hotspot internal mbean short names</li>
	 *  <li><b>-c &lt:counter names&gt;</b>: The regex to filter in matching counter names</li>
	 *  <li><b>-x &lt:counter names&gt;</b>: The regex to filter out matching counter names</li>
	 * </ul>
	 */
	public static void main(String[] args) {
		final Set<MBeanObserver> mbeans = EnumSet.noneOf(MBeanObserver.class);
		final Set<Pattern> counterFilters = new HashSet<Pattern>();
		final Set<Pattern> excludeFilters = new HashSet<Pattern>();	
		final Set<String> uniqueCounterTypes = new HashSet<String>();
		for(int i = 0; i < args.length; i++) {
			try {
				if(args[i].equalsIgnoreCase("-m")) {
					i++;					
					mbeans.addAll(Arrays.asList(MBeanObserver.hotspotObservers(args[i])));					
				} else if(args[i].equalsIgnoreCase("-c")) {
					i++;
					counterFilters.add(Pattern.compile(args[i], Pattern.CASE_INSENSITIVE));
				} else if(args[i].equalsIgnoreCase("-x")) {
					i++;
					excludeFilters.add(Pattern.compile(args[i], Pattern.CASE_INSENSITIVE));
				}
			} catch (Exception x) {/* No Op */}
		}
		if(mbeans.isEmpty()) mbeans.addAll(Arrays.asList(MBeanObserver.getHotSpotObservers()));
		log("MBeans:");
		for(MBeanObserver mbo: mbeans) {
			log("\t%s", mbo.name());
		}
		
		if(counterFilters.isEmpty()) {
			log("Counters: All");
		} else {
			log("Counters:");
			for(Pattern p: counterFilters) {
				log("\t%s", p.pattern());
			}			
		}
		if(!excludeFilters.isEmpty()) {
			log("Exclude Counters:");
			for(Pattern p: excludeFilters) {
				log("\t%s", p.pattern());
			}			

		}
		
		JMXHelper.registerHotspotInternal();
		for(MBeanObserver mbo: mbeans) {
			log("\n\t===========================================================\n\tCounters for [%s]\n\t===========================================================", mbo.objectName);
			for(String attrName: mbo.getAttributeNames()) {
				List<Counter> counters = (List<Counter>)JMXHelper.getAttribute(mbo.objectName, attrName);
				for(Counter ctr: counters) {
					uniqueCounterTypes.add(ctr.getClass().getSimpleName());
					if(counterFilters.isEmpty() && !isExcluded(excludeFilters, ctr.getName())) {
						log("\t[%s] value:%s, type:%s, unit:%s, var:%s", ctr.getName(), ctr.getValue(), ctr.getClass().getSimpleName(), ctr.getUnits().toString(), ctr.getVariability().toString());
					} else {
						final String name = ctr.getName();
						for(Pattern p: counterFilters) {
							if(p.matcher(name).matches() && !isExcluded(excludeFilters, ctr.getName())) {
								log("\t[%s] value:%s, type:%s, unit:%s, var:%s", ctr.getName(), ctr.getValue(), ctr.getClass().getSimpleName(), ctr.getUnits().toString(), ctr.getVariability().toString());
							}
						}
					}
				}
			}
		}
		log("\n\n\t======================\n\tUnique Counter Types\n\t======================");
		for(String s: uniqueCounterTypes) {
			log("\t%s", s);
		}
		log("\n");
	}
	
	private static boolean isExcluded(final Set<Pattern> excludes, final String value) {
		if(excludes.isEmpty()) return false;
		for(Pattern p: excludes) {
			if(p.matcher(value).matches()) return true;
		}
		return false;
	}
	
	/**
	 * Format out logger
	 * @param fmt The format
	 * @param args The token values
	 */
	public static void log(final Object fmt, final Object...args) {
		System.out.println(String.format(fmt.toString(), args));
	}
	
	
	
	void compileGCTags(final List<? extends Counter> ctrs) {
		 
		if(mbeanObserver!=MBeanObserver.HOTSPOT_MEMORY_MBEAN || !compiledGCTags.isEmpty()) return;
		final List<? extends Counter> counters = new ArrayList<Counter>(ctrs);
		final Map<String, String> decodes = new HashMap<String, String>();
		
		for(Iterator<? extends Counter> citer = counters.iterator(); citer.hasNext();) {
			Counter ctr = citer.next();
			final String name = ctr.getName();
			if(!name.endsWith(".name")) {
				citer.remove();
				continue;
			}
			final Object value = ctr.getValue();
			if(value==null || !(value instanceof String) || value.toString().trim().isEmpty()) {
				citer.remove();
				continue;
			}
			log.info("\t{}", ctr.getName());
			final int[] indexes = extractNameIndexes(name);
			final String indexKey = str(indexes);
			if(indexes.length==0) {
				citer.remove();
				continue;
			}
			decodes.put(indexKey, value.toString());
		}
		for(Counter ctr: counters) {
			String name = ctr.getName();
			String[] frags = name.split("\\.");
			final int[] indexes = extractNameIndexes(name);
			final String indexKey = str(indexes);
			final String[] keys = getPrefixes(indexes, frags);
			final String[] values = new String[indexes.length];
			
			StringBuilder decodeLookup = new StringBuilder();
			for(int x = 0; x < indexes.length; x++) {
				decodeLookup.append(indexes[x]);
				values[x] = decodes.get(decodeLookup.toString());
			}
			
			StringBuilder tags = new StringBuilder();
			for(int x = 0; x < indexes.length; x++) {
				tags.append(keys[x]).append("=").append(values[x]).append(",");
			}
			tags.deleteCharAt(tags.length()-1);
			compiledGCTags.put(name, tags.toString());
			compiledGCTags.put(indexKey, tags.toString());
			log.info("Added tags [{}] for name [{}] and indexkey [{}]", tags, name, indexKey);
		}
		
	}
	
	
	static String[] getPrefixes(final int[] indexes, final String[] frags) {
		String[] names = new String[indexes.length];
		String pref = null;
		int focus = 0;
		for(int x = 0; x < frags.length; x++) {
			String strf = "" + indexes[focus];
			if(strf.equals(frags[x])) {
				names[focus] = pref;
				focus++;
				if(focus==indexes.length) break;
			}
			pref = frags[x];			
		}
		return names;
				
	}
	
	private static final int[] EMPTY_INT_ARR = {};
	
	static int[] extractNameIndexes(final String counterName) {
		if(counterName==null || counterName.trim().isEmpty()) return EMPTY_INT_ARR;
		final String[] frags = counterName.replace(" ", "").split("\\.");
		int icounts = 0;
		for(String v: frags) {
			if(isInt(v) != -1) icounts++;
		}
		if(icounts==0) return EMPTY_INT_ARR;
		final int[] arr = new int[icounts];
		icounts = 0;
		for(String v: frags) {
			int x = isInt(v);
			if(x==-1) continue;
			arr[icounts] = x;
			icounts++;
		}
		return arr;
	}
	
	static String str(int...ints) {
		if(ints==null || ints.length==0) return "";
		StringBuilder b = new StringBuilder(ints.length);
		for(int x: ints) {
			b.append(x);
		}
		return b.toString();
	}
	
	static int isInt(final String v) {
		try {
			return Integer.parseInt(v);
		} catch (Exception x) {
			return -1;
		}
	}
	
	/**
	 * Extracts the metric name from the counter name 
	 * @param m The matching matcher
	 * @return the extracted name or null if no capturing groups were defined.
	 */
	protected String extractCounterName(final Matcher m) {
		final int groups = m.groupCount();
		if(groups < 1) {
			log.warn("The counter name matching pattern [{}] has no capturing groups", m.pattern().pattern());
			return null;
		}
		if(groups == 1) return m.group(1); 
		StringBuilder b = new StringBuilder();
		for(int i = 1; i <= groups; i++) {
			b.append(m.group(i)).append(".");
		}
		return b.deleteCharAt(b.length()-1).toString();		
	}
	
	

}
