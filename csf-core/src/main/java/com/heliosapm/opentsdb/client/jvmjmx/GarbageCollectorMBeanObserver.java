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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
			delta("ProcessCPUTime", (Long)mbs.getAttribute(OS_OBJECT_NAME, "ProcessCpuTime"));
			tmp = true;
		} catch (Exception ex) {/* No Op */}		
		hasProcessCpuTime = tmp;
		processorCount = getProcessorCount();
		poolNames = getPoolNames();
		final String[] gcNames = getGCNames();
		final int poolCount = poolNames.length;
		final int gcCount = garbageCollectors.size();
		totalCollectionTimes = new LinkedHashMap<String, OTMetric>(gcCount);
		totalCollectionCounts = new LinkedHashMap<String, OTMetric>(gcCount);
		collectionTimeRates = new LinkedHashMap<String, OTMetric>(gcCount);
		collectionCountRates = new LinkedHashMap<String, OTMetric>(gcCount);
		lastDurations = new LinkedHashMap<String, OTMetric>(gcCount);
		lastUsedDelta = new LinkedHashMap<String, Map<String, OTMetric>>(poolCount);
		lastCommittedDelta = new LinkedHashMap<String, Map<String, OTMetric>>(poolCount);
		percentageCPUTimeInGC = MetricBuilder.metric("java.lang.gc").ext("pctCPUTimeInGC").tags(tags).build();
		totalUsedDeltaOnLastGC = MetricBuilder.metric("java.lang.gc").ext("usedFreeOnLastGC").tags(tags).build();
		totalCommittedDeltaOnLastGC = MetricBuilder.metric("java.lang.gc").ext("committedFreeOnLastGC").tags(tags).build();
		for(String poolName: poolNames) {
			Map<String, OTMetric> lud = new LinkedHashMap<String, OTMetric>(gcCount);
			Map<String, OTMetric> lcd = new LinkedHashMap<String, OTMetric>(gcCount);
			for(String gcName: gcNames) {
				lud.put(gcName, MetricBuilder.metric("java.lang.gc").ext("lastGCUsedFreed").tag("gcname", gcName).tag("pool", poolName).tags(tags).build());				
				lud.put(gcName, MetricBuilder.metric("java.lang.gc").ext("lastGCCommittedFreed").tag("gcname", gcName).tag("pool", poolName).tags(tags).build());
			}
			lastUsedDelta.put(poolName, lud);
			lastCommittedDelta.put(poolName, lcd);			
		}
		for(String gcName: gcNames) {
			totalCollectionTimes.put(gcName, MetricBuilder.metric("java.lang.gc").ext("collectionTime").tag("gcname", gcName).tags(tags).build());
			totalCollectionCounts.put(gcName, MetricBuilder.metric("java.lang.gc").ext("collectionCount").tag("gcname", gcName).tags(tags).build());
			collectionTimeRates.put(gcName, MetricBuilder.metric("java.lang.gc").ext("collectionTimeRate").tag("gcname", gcName).tags(tags).build());
			collectionCountRates.put(gcName, MetricBuilder.metric("java.lang.gc").ext("collectionCountRate").tag("gcname", gcName).tags(tags).build());
			lastDurations.put(gcName, MetricBuilder.metric("java.lang.gc").ext("lastGCDuration").tag("gcname", gcName).tags(tags).build());
		}
	}
	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.BaseMBeanObserver#accept(java.util.Map, long, long)
	 */
	@SuppressWarnings("unchecked")
	@Override
	protected boolean accept(final Map<ObjectName, Map<String, Object>> data, final long currentTime, final long elapsedTime) {
		try {
			log.error("Accepting DataMap: " + data);
			long totalCollectTime = -1L;
			final Set<MemoryUsage> memUsages = new HashSet<MemoryUsage>();
			for(ObjectName on: garbageCollectors) {
				final Map<String, Object> values = data.get(on);
				final String gcName = on.getKeyProperty("name");
				// ======================================
				// Basic times and counts
				// ======================================
				
				final long time = (Long)values.get(GarbageCollectorAttribute.COLLECTION_TIME.attributeName);			
				final long count = (Long)values.get(GarbageCollectorAttribute.COLLECTION_COUNT.attributeName);
				final Long timeElapsed = delta("collectionTime", time);
				final Long countElapsed = delta("collectionCount", count);
				
				totalCollectionTimes.get(gcName).trace(currentTime, time);
				totalCollectionCounts.get(gcName).trace(currentTime, count);
				if(timeElapsed != null && timeElapsed > -1) {
					totalCollectTime += timeElapsed;
					collectionTimeRates.get(gcName).trace(currentTime, timeElapsed);
				} else {
					collectionTimeRates.get(gcName).trace(currentTime, 0);
				}
				if(countElapsed!=null && countElapsed > -1) {
					collectionCountRates.get(gcName).trace(currentTime, countElapsed);
				} else {
					collectionCountRates.get(gcName).trace(currentTime, 0);
				}
				// =============
				final CompositeData lgi = (CompositeData)values.get(GarbageCollectorAttribute.LAST_GC_INFO_INIT.attributeName);
				if(lgi!=null) {
					try {
						long duration = (Long)lgi.get("duration");
						lastDurations.get(gcName).trace(currentTime, duration);
					} catch (Exception x) {/* No Op */}
					// memoryUsageAfterGc
					try {
						final Map<Set<String>, CompositeData> usageBeforeGc = (Map<Set<String>, CompositeData>)lgi.get("memoryUsageBeforeGc");
						final Map<Set<String>, CompositeData> usageAfterGc = (Map<Set<String>, CompositeData>)lgi.get("memoryUsageAfterGc");					
						if(usageBeforeGc!=null && usageAfterGc!=null) {
							for(String poolName: poolNames) {
								CompositeData pBefore = usageBeforeGc.get(new Object[]{poolName});
								CompositeData pAfter = usageAfterGc.get(new Object[]{poolName});
								if(pBefore!=null && pAfter!=null) {
									MemoryUsage delta = diff(pBefore, pAfter);
									memUsages.add(delta);
									lastUsedDelta.get(poolName).get(gcName).trace(currentTime, delta.getUsed());
									lastCommittedDelta.get(poolName).get(gcName).trace(currentTime, delta.getCommitted());
								}
							}
						}
					} catch (Exception x) {
						/* No Op */
						log.error("Failed to process MemPool MemoryUsages", x);
					}
				}			
			}
			if(!memUsages.isEmpty()) {
				MemoryUsage totalDelta = sum(memUsages.toArray(new MemoryUsage[memUsages.size()]));
				totalUsedDeltaOnLastGC.trace(currentTime, totalDelta.getUsed());
				totalCommittedDeltaOnLastGC.trace(currentTime, totalDelta.getCommitted());
			}
			if(totalCollectTime > -1 && elapsedTime > -1) {
				totalCollectTime++;
				if(totalCollectTime==0) {
					percentageCPUTimeInGC.trace(currentTime, 0);
				} else {
					long cpuTime = hasProcessCpuTime ? getProcessCPUTime() : (processorCount * elapsedTime);
					percentageCPUTimeInGC.trace(currentTime, percent(totalCollectTime, cpuTime));
				}			
			}
			return true;
		} catch (Exception ex) {
			log.error("Collection Failed", ex);
			return false;
		}
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
	 * @return a MemoryUsage instance representing the delta in the Used and Committed memory.
	 * (Init and Max are -1L since they don't change).
	 */
	protected static MemoryUsage diff(final CompositeData before, final CompositeData after) {
		final MemoryUsage _before = MemoryUsage.from(before);
		final MemoryUsage _after = MemoryUsage.from(after);
		return new MemoryUsage(-1L, _before.getUsed()-_after.getUsed(), _before.getCommitted()-_after.getCommitted(), -1L);		
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
	protected static MemoryUsage sum(final MemoryUsage...usages) {
		long used = 0L, committed = 0L;
		for(MemoryUsage m: usages) {
			used += m.getUsed();
			committed += m.getCommitted();
		}
		return new MemoryUsage(-1L, used, committed, -1L);
	}

}
