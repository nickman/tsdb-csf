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

import static com.heliosapm.opentsdb.client.jvmjmx.MBeanObserver.OPERATING_SYSTEM_MXBEAN;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;

import com.heliosapm.opentsdb.client.jvmjmx.MBeanObserver.OperatingSystemAttribute;
import com.heliosapm.opentsdb.client.opentsdb.MetricBuilder;
import com.heliosapm.opentsdb.client.opentsdb.OTMetric;
import com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection;

/**
 * <p>Title: OperatingSystemCollectorMBeanObserver</p>
 * <p>Description: MBeanObserver for the  OperatingSystem MXBean</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.OperatingSystemCollectorMBeanObserver</code></p>
 */

public class OperatingSystemCollectorMBeanObserver extends BaseMBeanObserver {
	
	/** Indicates if the OS is Windows */
	protected final boolean isWin;
	
	/**  */
	private static final long serialVersionUID = -1267019120073737688L;
	/** A map of the core OS merics keyed by the attribute name */
	protected final Map<String, OTMetric> otMetrics;
	/** A map of the derived OS merics keyed by the attribute name */
	protected final Map<String, OTMetric> otDerivedMetrics;
	
	/** The number of processors available to the target JVM */
	protected int processorCount = 1;
	
	/** A map of all the values keyed by the OS attribute enum member */
	protected final Map<OperatingSystemAttribute, Number> attributeValues = new EnumMap<OperatingSystemAttribute, Number>(OperatingSystemAttribute.class);
	
	/** A map of the one-timer values keyed by the OS attribute enum member */
	protected final Map<OperatingSystemAttribute, Long> oneTimerValues = new EnumMap<OperatingSystemAttribute, Long>(OperatingSystemAttribute.class);
	
	/** The enabled one-timer attributes */
	protected final EnumSet<OperatingSystemAttribute> enabledOneTimers;
	
	
	/** A map of derived metrics where the key is the attribute name of the derived metric and the value is a set of dependencies of attributes
	 * that must be available to calculate the derived value */
	protected static final Map<String, Set<OperatingSystemAttribute>> DERIVED_OT_METRICS;
	
	static {
		Map<String, Set<OperatingSystemAttribute>> tmp = new HashMap<String, Set<OperatingSystemAttribute>>();
		tmp.put("UsedPhysicalMemorySize", EnumSet.of(OperatingSystemAttribute.FREE_PHYSICAL_MEMORY_SIZE, OperatingSystemAttribute.TOTAL_PHYSICAL_MEMORY_SIZE));
		tmp.put("PctUsedPhysicalMemory", EnumSet.of(OperatingSystemAttribute.FREE_PHYSICAL_MEMORY_SIZE, OperatingSystemAttribute.TOTAL_PHYSICAL_MEMORY_SIZE));
		tmp.put("PctFreePhysicalMemory", EnumSet.of(OperatingSystemAttribute.FREE_PHYSICAL_MEMORY_SIZE, OperatingSystemAttribute.TOTAL_PHYSICAL_MEMORY_SIZE));
		tmp.put("UsedSwapSpace", EnumSet.of(OperatingSystemAttribute.FREE_SWAP_SPACE_SIZE, OperatingSystemAttribute.TOTAL_SWAP_SPACE_SIZE));
		tmp.put("PctUsedSwap", EnumSet.of(OperatingSystemAttribute.FREE_SWAP_SPACE_SIZE, OperatingSystemAttribute.TOTAL_SWAP_SPACE_SIZE));
		tmp.put("PctFreeSwap", EnumSet.of(OperatingSystemAttribute.FREE_SWAP_SPACE_SIZE, OperatingSystemAttribute.TOTAL_SWAP_SPACE_SIZE));
		tmp.put("PctFDCapacity", EnumSet.of(OperatingSystemAttribute.OPEN_FILE_DESCRIPTOR_COUNT, OperatingSystemAttribute.MAX_FILE_DESCRIPTOR_COUNT));
		tmp.put("PctProcessCpuLoad", EnumSet.of(OperatingSystemAttribute.PROCESS_CPU_LOAD, OperatingSystemAttribute.SYSTEM_CPU_LOAD));
		tmp.put("PctProcessCpuTime", EnumSet.of(OperatingSystemAttribute.PROCESS_CPU_TIME));
		
		DERIVED_OT_METRICS = Collections.unmodifiableMap(tmp);
	}

	/**
	 * Creates a new OperatingSystemCollectorMBeanObserver
	 * @param jmxConnector The JMXConnector that will supply an MBeanServerConnection
	 * @param tags The tags common to all metrics submitted from this observer
	 * @param publishObserverMBean If true, an observer management MBean will be registered
	 */
	public OperatingSystemCollectorMBeanObserver(final JMXConnector jmxConnector, final Map<String, String> tags, final boolean publishObserverMBean) {
		this(RuntimeMBeanServerConnection.newInstance(jmxConnector), tags, publishObserverMBean);
	}

	/**
	 * Creates a new MemoryCollectorMBeanObserver
	 * @param mbeanServerConn The MBeanServerConnection to monitor
	 * @param tags The tags common to all metrics submitted from this observer
	 * @param publishObserverMBean If true, an observer management MBean will be registered
	 */
	public OperatingSystemCollectorMBeanObserver(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags, final boolean publishObserverMBean) {
		super(mbeanServerConn, OPERATING_SYSTEM_MXBEAN, tags, publishObserverMBean);		
		final EnumSet<OperatingSystemAttribute> enabled = OperatingSystemAttribute.getEnabledNonOneTimers(attributeNames);		
		final String osName = (String)mbs.getAttribute(OPERATING_SYSTEM_MXBEAN.objectName, "Name");
		isWin = (osName!=null && osName.toLowerCase().contains("windows"));
		processorCount = (Integer)mbs.getAttribute(OPERATING_SYSTEM_MXBEAN.objectName, "AvailableProcessors");
		enabledOneTimers = OperatingSystemAttribute.getEnabledOneTimers(attributeNames);
		final EnumSet<OperatingSystemAttribute> allEnabled = EnumSet.copyOf(enabled);
		allEnabled.addAll(enabledOneTimers);
		
		for(OperatingSystemAttribute oneTimer: enabledOneTimers) {
			oneTimerValues.put(oneTimer, 0L);
		}
		otMetrics = new HashMap<String, OTMetric>(enabled.size());
		otDerivedMetrics = new HashMap<String, OTMetric>(DERIVED_OT_METRICS.size());
		for(OperatingSystemAttribute attr: enabled) {
			OTMetric otm = MetricBuilder.metric(OPERATING_SYSTEM_MXBEAN.objectName).ext("os." + attr.attributeName).tags(this.tags).build();
			otMetrics.put(attr.attributeName, otm);
			log.info("Built Metric: [{}]", otm.toString());
		}
		if(isWin) {
			otMetrics.remove("SystemLoadAverage");
			otMetrics.remove("ProcessCpuLoad");		
			otDerivedMetrics.remove("PctProcessCpuLoad");
			otDerivedMetrics.remove("PctProcessCpuTime");
			
		}
		for(Map.Entry<String, Set<OperatingSystemAttribute>> entry: DERIVED_OT_METRICS.entrySet()) {
			if(allEnabled.containsAll(entry.getValue())) {
				otDerivedMetrics.put(entry.getKey(), MetricBuilder.metric(OPERATING_SYSTEM_MXBEAN.objectName).tags(this.tags).ext("os." + entry.getKey()).build());
			}
		}
		traceOneTimes();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.BaseMBeanObserverMBean#getOneTimeAttributeNames()
	 */
	@Override
	public Set<String> getOneTimeAttributeNames() {		
		return Collections.emptySet();
	}

	
	/**
	 * Records and traces the one-time attribute values
	 */
	protected void traceOneTimes() {
		final long currentTime = clock.getTime();
		Map<String, Object> values = mbs.getAttributeMap(OPERATING_SYSTEM_MXBEAN.objectName, OperatingSystemAttribute.getAttributeNameArr(oneTimerValues.keySet()));
		for(OperatingSystemAttribute osa: oneTimerValues.keySet()) {
			final long value = (Long)values.get(osa.attributeName);
			oneTimerValues.put(osa, value);
			MetricBuilder.metric(OPERATING_SYSTEM_MXBEAN.objectName).ext("os." + osa.attributeName).tags(this.tags).build().trace(currentTime, value);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.BaseMBeanObserver#accept(java.util.Map, long, long)
	 */
	@Override
	protected boolean accept(final Map<ObjectName, Map<String, Object>> data, final long currentTime, final long elapsedTime) {
		final Map<String, Object> osValues = data.get(OPERATING_SYSTEM_MXBEAN.objectName);
		log.trace("OS MBean Attrs: {}", osValues.keySet());
		attributeValues.clear();
		attributeValues.putAll(oneTimerValues);
		for(Map.Entry<String, Object> entry: osValues.entrySet()) {			
			attributeValues.put(OperatingSystemAttribute.getEnum(entry.getKey()), (Number)entry.getValue());
		}
		log.trace("OS MBean Metric Keys: {}", otMetrics.keySet());
		for(Map.Entry<String, OTMetric> entry: otMetrics.entrySet()) {
			OTMetric otm = entry.getValue();
			Object value = osValues.get(entry.getKey());
			log.debug("Tracing [{}] for metric [{}]  with key [{}]", value, otm, entry.getKey());
			entry.getValue().trace(currentTime, osValues.get(entry.getKey()));
		}
		try {
			// calc the supported derived metrics
			if(otDerivedMetrics.containsKey("UsedPhysicalMemorySize")) {
				final long totalPhys = attributeValues.get(OperatingSystemAttribute.TOTAL_PHYSICAL_MEMORY_SIZE).longValue();
				final long freePhys = attributeValues.get(OperatingSystemAttribute.FREE_PHYSICAL_MEMORY_SIZE).longValue();
				final long usedPhys = totalPhys - freePhys;
				otDerivedMetrics.get("UsedPhysicalMemorySize").trace(currentTime, usedPhys);
				otDerivedMetrics.get("PctUsedPhysicalMemory").trace(currentTime, percent(usedPhys, totalPhys));
				otDerivedMetrics.get("PctFreePhysicalMemory").trace(currentTime,percent(freePhys, totalPhys));
			}
			if(otDerivedMetrics.containsKey("UsedSwapSpace")) {
				final long totalSwap = attributeValues.get(OperatingSystemAttribute.TOTAL_SWAP_SPACE_SIZE).longValue();
				final long freeSwap = attributeValues.get(OperatingSystemAttribute.FREE_SWAP_SPACE_SIZE).longValue();
				final long usedSwap = totalSwap - freeSwap;
				otDerivedMetrics.get("UsedSwapSpace").trace(currentTime, usedSwap);
				otDerivedMetrics.get("PctUsedSwap").trace(currentTime, percent(usedSwap, totalSwap));
				otDerivedMetrics.get("PctFreeSwap").trace(currentTime,percent(freeSwap, totalSwap));
			}
			if(otDerivedMetrics.containsKey("PctFDCapacity")) {
				final long openFds = attributeValues.get(OperatingSystemAttribute.OPEN_FILE_DESCRIPTOR_COUNT).longValue();
				final long maxFds = attributeValues.get(OperatingSystemAttribute.MAX_FILE_DESCRIPTOR_COUNT).longValue();
				otDerivedMetrics.get("PctFDCapacity").trace(currentTime,percent(openFds, maxFds));
			}
			if(!isWin && otDerivedMetrics.containsKey("PctProcessCpuLoad")) {
				final double processCpu = attributeValues.get(OperatingSystemAttribute.PROCESS_CPU_LOAD).doubleValue();
				final double systemCpu = attributeValues.get(OperatingSystemAttribute.SYSTEM_CPU_LOAD).doubleValue();
				otDerivedMetrics.get("PctProcessCpuLoad").trace(currentTime,percent(processCpu, systemCpu));
			} 
			if(!isWin && otDerivedMetrics.containsKey("PctProcessCpuTime")) {
				final long processCpuTime = attributeValues.get(OperatingSystemAttribute.PROCESS_CPU_TIME).longValue();			
				otDerivedMetrics.get("PctProcessCpuTime").trace(currentTime,percent(processCpuTime, elapsedTime));
			}
		} catch (Exception ex) {
			log.warn("OS MBean Collection Failure", ex);
		}
		
		return true;
	}

}
