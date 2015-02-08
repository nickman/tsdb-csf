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

import static com.heliosapm.opentsdb.client.jvmjmx.MBeanObserver.GARBAGE_COLLECTOR_MXBEAN;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;

import com.heliosapm.opentsdb.client.jvmjmx.MBeanObserver.GarbageCollectorAttribute;
import com.heliosapm.opentsdb.client.opentsdb.MetricBuilder;
import com.heliosapm.opentsdb.client.opentsdb.OTMetric;
import com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: GarbageCollectorMBeanObserver</p>
 * <p>Description: MBeanObserver for the Garbage Collection MXBeans</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.GarbageCollectorMBeanObserver</code></p>
 */

public class GarbageCollectorMBeanObserver extends BaseMBeanObserver {
	/** The garbage collector MBeans */
	protected final Set<ObjectName> garbageCollectors;
	/** The total collection time metric */
	protected final Map<String, OTMetric> totalCollectionTimes;
	/** The total collection count metric */
	protected final Map<String, OTMetric> totalCollectionCounts;
	/** The collection time rate metric */
	protected final Map<String, OTMetric> collectionTimeRates;
	/** The collection count rate metric */
	protected final Map<String, OTMetric> collectionCountRates;
	/** The duration of the last GC event */
	protected final Map<String, OTMetric> lastDurations;
	/** The delta of the used memory for each pool */
	protected final Map<String, Map<String, OTMetric>> lastUsedDelta;
	/** The delta of the committed memory for each pool */
	protected final Map<String, Map<String, OTMetric>> lastCommittedDelta;
	/** The percent of elapsed CPU time spent in GC */
	protected final OTMetric percentageCPUTimeInGC;
	/** The total delta in used memory for the last GC */
	protected final OTMetric totalUsedDeltaOnLastGC;
	/** The total delta in committed memory for the last GC */
	protected final OTMetric totalCommittedDeltaOnLastGC;
	
	/** The names of the memory pools maintained by these garbage collectors */
	protected final String[] poolNames;
	
	/** Indicates if we can get the process cpu time from the OperatingSystem MXBean ObjectName */
	protected final boolean hasProcessCpuTime;
	/** The number of processors available to the target JVM */
	protected final int processorCount;
	
	/** The OperatingSystem MXBean ObjectName */
	protected static final ObjectName OS_OBJECT_NAME = Util.objectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);
	/** The base metric name */
	protected static final String BASEMN = "java.lang.gc";
	/** The GC Name tag key */
	protected static final String GCNAME = "gcname";
	/** The Mem Pool Name tag key */
	protected static final String POOLNAME = "pool";
	/** The Mem Type tag key */
	protected static final String MEM_TYPE = "type";
	/** Used memory tag value */
	protected static final String MEM_USED = "used";
	/** Committed memory tag value */
	protected static final String MEM_COMM = "committed";
	
	
	/**
	 * Creates a new GarbageCollectorMBeanObserver
	 * @param jmxConnector The JMXConnector that will supply an MBeanServerConnection
	 * @param tags The tags common to all metrics submitted from this observer
	 */
	public GarbageCollectorMBeanObserver(final JMXConnector jmxConnector, final Map<String, String> tags) {
		this(RuntimeMBeanServerConnection.newInstance(jmxConnector), tags);
		
	}

	/**
	 * Creates a new GarbageCollectorMBeanObserver
	 * @param mbeanServerConn The MBeanServerConnection to monitor
	 * @param tags The tags common to all metrics submitted from this observer
	 */
	public GarbageCollectorMBeanObserver(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags) {
		super(mbeanServerConn, GARBAGE_COLLECTOR_MXBEAN, tags);
		garbageCollectors = objectNamesAttrs.keySet();
		boolean tmp = false;
		try {
			delta(deltaKey("ProcessCPUTime"), (Long)mbs.getAttribute(OS_OBJECT_NAME, "ProcessCpuTime"));
			tmp = true;
		} catch (Exception ex) {/* No Op */}		
		hasProcessCpuTime = tmp;
		processorCount = getProcessorCount();
		poolNames = getPoolNames();
		final String[] gcNames = getGCNames();
		final int poolCount = poolNames.length;
		final int gcCount = garbageCollectors.size();
		final String groupName = getDefaultGroupName();
		totalCollectionTimes = new LinkedHashMap<String, OTMetric>(gcCount);
		totalCollectionCounts = new LinkedHashMap<String, OTMetric>(gcCount);
		collectionTimeRates = new LinkedHashMap<String, OTMetric>(gcCount);
		collectionCountRates = new LinkedHashMap<String, OTMetric>(gcCount);
		lastDurations = new LinkedHashMap<String, OTMetric>(gcCount);
		lastUsedDelta = new LinkedHashMap<String, Map<String, OTMetric>>(poolCount);
		lastCommittedDelta = new LinkedHashMap<String, Map<String, OTMetric>>(poolCount);
		percentageCPUTimeInGC = MetricBuilder.metric(BASEMN).ext("pctCPUTimeInGC").tags(tags).build(groupName);
		totalUsedDeltaOnLastGC = MetricBuilder.metric(BASEMN).ext("totalFreedOnLastGC").tag(MEM_TYPE, MEM_USED).tags(tags).build(groupName);
		totalCommittedDeltaOnLastGC = MetricBuilder.metric(BASEMN).ext("totalFreedOnLastGC").tag(MEM_TYPE, MEM_COMM).tags(tags).build(groupName);
		for(String poolName: poolNames) {
			Map<String, OTMetric> lud = new LinkedHashMap<String, OTMetric>(gcCount);
			Map<String, OTMetric> lcd = new LinkedHashMap<String, OTMetric>(gcCount);
			for(String gcName: gcNames) {
				lud.put(gcName, MetricBuilder.metric(BASEMN).ext("freedOnLastGC").tag(GCNAME, gcName).tag(POOLNAME, poolName).tag(MEM_TYPE, MEM_USED).tags(tags).build(groupName));				
				lud.put(gcName, MetricBuilder.metric(BASEMN).ext("freedOnLastGC").tag(GCNAME, gcName).tag(POOLNAME, poolName).tag(MEM_TYPE, MEM_COMM).tags(tags).build(groupName));
			}
			lastUsedDelta.put(poolName, lud);
			lastCommittedDelta.put(poolName, lcd);			
		}
		for(String gcName: gcNames) {
			totalCollectionTimes.put(gcName, MetricBuilder.metric(BASEMN).ext("collectionTime").tag(GCNAME, gcName).tags(tags).build(groupName));
			totalCollectionCounts.put(gcName, MetricBuilder.metric(BASEMN).ext("collectionCount").tag(GCNAME, gcName).tags(tags).build(groupName));
			collectionTimeRates.put(gcName, MetricBuilder.metric(BASEMN).ext("collectionTimeRate").tag(GCNAME, gcName).tags(tags).build(groupName));
			collectionCountRates.put(gcName, MetricBuilder.metric(BASEMN).ext("collectionCountRate").tag(GCNAME, gcName).tags(tags).build(groupName));
			lastDurations.put(gcName, MetricBuilder.metric(BASEMN).ext("lastGCDuration").tag(GCNAME, gcName).tags(tags).build(groupName));
		}
		groupMetrics.addAll(MetricBuilder.getGroupSet(groupName));
		log.info("Group Metrics: {}", groupMetrics.size());
	}
	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.BaseMBeanObserver#accept(java.util.Map, long, long)
	 */
	@SuppressWarnings("unchecked")
	@Override
	protected boolean accept(final Map<ObjectName, Map<String, Object>> data, final long currentTime, final long elapsedTime) {
		try {
			final AtomicLong totalCollectTime = new AtomicLong(-1L);
			final Set<Map<MUsage, Long>> memUsages = new HashSet<Map<MUsage, Long>>();
			for(ObjectName on: garbageCollectors) {
				final Map<String, Object> values = data.get(on);
				final String gcName = on.getKeyProperty("name");
				// ======================================
				// Basic times and counts
				// ======================================				
				gcCollectTimesAndCounts(currentTime, totalCollectTime, values, gcName, true); // FIXME: make traceRates configurable
				// ======================================
				// Last GC Event
				// ======================================				
				gcCollectLastGCEvent(currentTime, memUsages, values, gcName, true); // FIXME: make tracePoolGC configurable			
			}
			if(!memUsages.isEmpty()) {
				Map<MUsage, Long> totalDelta = sum(memUsages.toArray(new Map[memUsages.size()]));
				totalUsedDeltaOnLastGC.trace(currentTime, totalDelta.get(MUsage.used));
				totalCommittedDeltaOnLastGC.trace(currentTime, totalDelta.get(MUsage.committed));
			}
			long tct = totalCollectTime.get();
			if(tct > -1 && elapsedTime > -1) {
				tct++;
				if(tct==0) {
					percentageCPUTimeInGC.trace(currentTime, 0);
				} else {
					long cpuTime = hasProcessCpuTime ? getProcessCPUTime() : (processorCount * elapsedTime);
					percentageCPUTimeInGC.trace(currentTime, percent(tct, cpuTime));
				}			
			}
			int zeroTraced = 0;
			for(OTMetric otm: groupMetrics) {
				if(otm.getLastTraceTime() < currentTime) {
					log.info("Tracing Default Zero for [{}]", otm);
					otm.trace(currentTime, 0);
					zeroTraced++;
				}
			}
			log.info("Zero Traced {} metrics out of {}", zeroTraced, groupMetrics.size());
			return true;
		} catch (Exception ex) {
			log.error("Collection Failed", ex);
			return false;
		}
	}

	/**
	 * Collects and traces the most recent GC Event for the named Garbage Collector
	 * @param currentTime The current time
	 * @param memUsages The mem usage accumulator for collected memory pools
	 * @param values The collected values keyed by attribute name
	 * @param gcName The name of the garbage collector
	 * @param tracePoolGC true to trace the freed memory in each memory pool for the named GC, false otherwise
	 */
	@SuppressWarnings("unchecked")
	protected void gcCollectLastGCEvent(final long currentTime, final Set<Map<MUsage, Long>> memUsages, final Map<String, Object> values, final String gcName, final boolean tracePoolGC) {
		final CompositeData lgi = (CompositeData)values.get(GarbageCollectorAttribute.LAST_GC_INFO_INIT.attributeName);
		if(lgi!=null) {
			try {
				long duration = (Long)lgi.get("duration");
				lastDurations.get(gcName).trace(currentTime, duration);
			} catch (Exception x) {/* No Op */}
			// memoryUsageAfterGc
			try {
				
				Long gcIdDelta = delta(deltaKey(gcName, "LastGCID"), (Long)lgi.get("id"));
				if(gcIdDelta!=null && gcIdDelta.longValue()>0) {
					
					// FIXME:  Should we trace all zeros if there has not been a new GC ?
					final Map<Set<String>, CompositeData> usageBeforeGc = (Map<Set<String>, CompositeData>)lgi.get("memoryUsageBeforeGc");
					final Map<Set<String>, CompositeData> usageAfterGc = (Map<Set<String>, CompositeData>)lgi.get("memoryUsageAfterGc");					
					if(usageBeforeGc!=null && usageAfterGc!=null) {
						for(String poolName: poolNames) {
							gcCollectLastGCEventByPool(currentTime, memUsages, gcName, poolName, usageBeforeGc, usageAfterGc, tracePoolGC);
						}
					}
				}
			} catch (Exception x) {
				/* No Op */
				log.error("Failed to process MemPool MemoryUsages", x);
			}
		}
	}

	/**
	 * Collects and traces the used and committed freed memory pool for the named Garbage Collector and Memory Pool
	 * @param currentTime The current time
	 * @param memUsages The mem usage aggregator
	 * @param gcName The Garbage Collector name
	 * @param poolName The Memory Pool Na,e
	 * @param usageBeforeGc The collected before-GC memory usage composite data instance
	 * @param usageAfterGc The collected after-GC memory usage composite data instance
	 * @param tracePoolGC true to trace the freed memory in each memory pool for the named GC, false otherwise
	 */
	protected void gcCollectLastGCEventByPool(final long currentTime,
			final Set<Map<MUsage, Long>> memUsages, final String gcName, final String poolName,			
			final Map<Set<String>, CompositeData> usageBeforeGc,
			final Map<Set<String>, CompositeData> usageAfterGc, final boolean tracePoolGC) {
		CompositeData pBefore = (CompositeData)usageBeforeGc.get(new Object[]{poolName}).get("value");
		CompositeData pAfter = (CompositeData)usageAfterGc.get(new Object[]{poolName}).get("value");
		if(pBefore!=null && pAfter!=null) {
			Map<MUsage, Long> delta = diff(pBefore, pAfter);
			log.debug("Mem Delta for Pool [{}/{}] :  {}", gcName, poolName, delta);
			memUsages.add(delta);
			if(tracePoolGC) {
				try { lastUsedDelta.get(poolName).get(gcName).trace(currentTime, delta.get(MUsage.used)); } catch (Exception x) {/* No Op */}
				try { lastCommittedDelta.get(poolName).get(gcName).trace(currentTime, delta.get(MUsage.committed)); } catch (Exception x) {/* No Op */}
			}
		}
	}

	/**
	 * Collects the basic GC collect times and counts for the last period
	 * @param currentTime The current time
	 * @param totalCollectTime The total collection time accumulator 
	 * @param values The collected values keyed by attribute name
	 * @param gcName The garbage collector name
	 * @param traceRates true to trace collection time and rates, false to trace absolutes only
	 */
	protected void gcCollectTimesAndCounts(final long currentTime, final AtomicLong totalCollectTime, final Map<String, Object> values, final String gcName, final boolean traceRates) {
		final long time = (Long)values.get(GarbageCollectorAttribute.COLLECTION_TIME.attributeName);			
		final long count = (Long)values.get(GarbageCollectorAttribute.COLLECTION_COUNT.attributeName);
		final Long timeElapsed = delta(deltaKey(gcName, "collectionTime"), time);
		totalCollectionTimes.get(gcName).trace(currentTime, time);
		totalCollectionCounts.get(gcName).trace(currentTime, count);
		if(timeElapsed != null && timeElapsed > -1) {
			totalCollectTime.addAndGet(timeElapsed);
			if(traceRates) collectionTimeRates.get(gcName).trace(currentTime, timeElapsed);
		} else {
			if(traceRates) collectionTimeRates.get(gcName).trace(currentTime, 0);
		}
		if(traceRates) {
			final Long countElapsed = delta(deltaKey(gcName, "collectionCount"), count);
			if(countElapsed!=null && countElapsed > -1) {
				collectionCountRates.get(gcName).trace(currentTime, countElapsed);
			} else {
				collectionCountRates.get(gcName).trace(currentTime, 0);
			}
		}
	}
	
	
	/**
	 * Formats the passed memory usage
	 * @param mu the memory usage to format
	 * @return the formatted string
	 */
	public static String format(final MemoryUsage mu) {
		if(mu==null) return "";
		if(mu.getInit()==-1L) return String.format("MemoryUsage:[used:%s, committed:%s]", mu.getUsed(), mu.getCommitted()); 
		return String.format("MemoryUsage:[init:%s, used:%s, committed:%s, max:%s]", mu.getInit(), mu.getUsed(), mu.getCommitted(), mu.getMax());		
	}
	
	/**
	 * Returns an array of the memory pool names
	 * @return an array of the memory pool names
	 */
	protected String[] getPoolNames() {
		Set<ObjectName> poolObjectNames = new LinkedHashSet<ObjectName>(mbs.queryNames(Util.objectName(ManagementFactory.MEMORY_POOL_MXBEAN_DOMAIN_TYPE + ",*"), null));
		Set<String> poolNames = new LinkedHashSet<String>(poolObjectNames.size());
		for(ObjectName on: poolObjectNames) {
			poolNames.add(on.getKeyProperty("name"));
		}		
		return poolNames.toArray(new String[poolNames.size()]);
	}
	
	/**
	 * Returns an array of the garbage collector names
	 * @return an array of the garbage collector names
	 */
	protected String[] getGCNames() {		
		Set<String> gcNames = new LinkedHashSet<String>(garbageCollectors.size());
		for(ObjectName on: garbageCollectors) {
			gcNames.add(on.getKeyProperty("name"));
		}		
		return gcNames.toArray(new String[gcNames.size()]);
	}
	
	/**
	 * Computes the delta in memory usage between two instances
	 * @param before The memory usage before a GC event
	 * @param after The memory usage after a GC event
	 * @return a map of MemoryUsage keys and values instance representing the delta in the Used and Committed memory.
	 * (Init and Max are -1L since they don't change).
	 */
	protected static Map<MUsage, Long> diff(final CompositeData before, final CompositeData after) {
		final MemoryUsage _before = MemoryUsage.from((CompositeData) before);
		final MemoryUsage _after = MemoryUsage.from((CompositeData) after);
		Map<MUsage, Long> diff = new EnumMap<MUsage, Long>(MUsage.class);
		diff.put(MUsage.used, _before.getUsed()-_after.getUsed());
		diff.put(MUsage.committed, _before.getCommitted()-_after.getCommitted());
		return diff;
//		System.err.println("BEFORE: " + format(_before));
//		System.err.println("AFTER: " + format(_after));
//		return new MemoryUsage(-1L, _before.getUsed()-_after.getUsed(), _before.getCommitted()-_after.getCommitted(), -1L);		
	}
	
	
	/**
	 * <p>Title: MUsage</p>
	 * <p>Description: Enuemerates the mem usage keys</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.GarbageCollectorMBeanObserver.MUsage</code></p>
	 */
	public static enum MUsage {
		/** Initial allocation */
		init,
		/** Currently used */
		used,
		/** Currently committed */
		committed,
		/** Maximum committed */
		max;
	}
	
	/**
	 * Returns the process cpu time
	 * @return the process cpu time
	 */
	protected long getProcessCPUTime() {
		return (Long)mbs.getAttribute(OS_OBJECT_NAME, "ProcessCpuTime");
	}
	
	protected int getProcessorCount() {
		return (Integer)mbs.getAttribute(OS_OBJECT_NAME, "AvailableProcessors");
	}
	
	/**
	 * Computes the sum of the used and committed deltas in the passed memory ussage deltas array
	 * @param usages The delta memory usages 
	 * @return a MemoryUsage instance representing the sum of the deltas in the Used and Committed memory.
	 * (Init and Max are -1L since they don't change).
	 */
	public static Map<MUsage, Long> sum(final Map<MUsage, Long>...usages) {
		long used = 0L, committed = 0L;
		for(Map<MUsage, Long> em: usages) {
			used += em.get(MUsage.used);
			committed += em.get(MUsage.committed);
		}
		final Map<MUsage, Long> map = new EnumMap<MUsage, Long>(MUsage.class);
		map.put(MUsage.used, used);
		map.put(MUsage.committed, committed);
		System.err.println(String.format("TOTAL: used:%s, committed:%s, all:%s", used, committed, used + committed));
		return map;
	}

}
