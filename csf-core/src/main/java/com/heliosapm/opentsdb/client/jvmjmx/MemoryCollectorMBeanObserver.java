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

import static com.heliosapm.opentsdb.client.jvmjmx.MBeanObserver.MEMORY_MXBEAN;

import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.EnumMap;
import java.util.Map;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;

import com.heliosapm.opentsdb.client.jvmjmx.MBeanObserver.MemoryAttribute;
import com.heliosapm.opentsdb.client.opentsdb.MetricBuilder;
import com.heliosapm.opentsdb.client.opentsdb.OTMetric;
import com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection;


/**
 * <p>Title: MemoryCollectorMBeanObserver</p>
 * <p>Description: MBeanObserver for the Memory (Heap and NonHeap summary) MXBean</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.MemoryCollectorMBeanObserver</code></p>
 */

public class MemoryCollectorMBeanObserver extends BaseMBeanObserver {
	/**  */
	private static final long serialVersionUID = -6369906554383026767L;
	/** The heap used metric */
	protected final Map<MemoryType, Map<MUsage, OTMetric>> memMetrics = new EnumMap<MemoryType, Map<MUsage, OTMetric>>(MemoryType.class);
	/** The pending finalizers count metric */
	protected final OTMetric pendingFinalizers;
	
	/** The max heap size */
	protected long maxHeap = -1L;
	/** The max non-heap size */
	protected long maxNonHeap = -1L;

	/** The Mem Type metric name extension */
	protected static final String MEM_EXT = "memory";
	
	/** The Mem Type tag key */
	protected static final String MEM_TYPE = "type";
	/** The Mem Alloc key */
	protected static final String MEM_ALLOC = "alloc";

	/** The memory MXBean object name */
	final ObjectName objectName;

	/**
	 * Creates a new MemoryCollectorMBeanObserver
	 * @param jmxConnector The JMXConnector that will supply an MBeanServerConnection
	 * @param tags The tags common to all metrics submitted from this observer
	 */
	public MemoryCollectorMBeanObserver(final JMXConnector jmxConnector, final Map<String, String> tags) {
		this(RuntimeMBeanServerConnection.newInstance(jmxConnector), tags);
	}

	/**
	 * Creates a new MemoryCollectorMBeanObserver
	 * @param mbeanServerConn The MBeanServerConnection to monitor
	 * @param tags The tags common to all metrics submitted from this observer
	 */
	public MemoryCollectorMBeanObserver(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags) {
		super(mbeanServerConn, MEMORY_MXBEAN, tags);
		objectName = objectNamesAttrs.keySet().iterator().next();
		for(MemoryType mt: MemoryType.values()) {
			final Map<MUsage, OTMetric> otmap = new EnumMap<MUsage, OTMetric>(MUsage.class);
			for(MUsage mu: MUsage.getNonOneTimes()) {
				otmap.put(mu, MetricBuilder.metric(objectName).ext(MEM_EXT).tags(tags).tag(MEM_ALLOC, mu).tag(MEM_TYPE, mt.name().toLowerCase()).build());
			}
		}
		pendingFinalizers = MetricBuilder.metric(objectName).ext("pendingFinalizers").tags(tags).build();
		traceOneTimers(objectName);
	}
	
	/**
	 * Traces immutable memory metrics once.
	 * @param on The ObjectName
	 */
	protected void traceOneTimers(final ObjectName on) {
		try {
			final long timestamp = clock.getTime();
			final Map<String, Object> initial = mbs.getAttributeMap(on, MemoryAttribute.HEAP_MEMORY_USAGE_INIT.attributeName, MemoryAttribute.NON_HEAP_MEMORY_USAGE_INIT.attributeName);
			final MemoryUsage heap = MemoryUsage.from((CompositeData)initial.get(MemoryAttribute.HEAP_MEMORY_USAGE_INIT.attributeName));
			maxHeap = heap.getMax();
			final MemoryUsage nonHeap = MemoryUsage.from((CompositeData)initial.get(MemoryAttribute.NON_HEAP_MEMORY_USAGE_INIT.attributeName));
			maxNonHeap = nonHeap.getMax();
			MetricBuilder.metric(on).ext(MEM_EXT).tags(tags).tag(MEM_ALLOC, MUsage.init).tag(MEM_TYPE, "heap").build().trace(timestamp, heap.getInit());
			MetricBuilder.metric(on).ext(MEM_EXT).tags(tags).tag(MEM_ALLOC, MUsage.max).tag(MEM_TYPE, "heap").build().trace(timestamp, heap.getMax());
			MetricBuilder.metric(on).ext(MEM_EXT).tags(tags).tag(MEM_ALLOC, MUsage.init).tag(MEM_TYPE, "nonheap").build().trace(timestamp, nonHeap.getInit());
			MetricBuilder.metric(on).ext(MEM_EXT).tags(tags).tag(MEM_ALLOC, MUsage.max).tag(MEM_TYPE, "nonheap").build().trace(timestamp, nonHeap.getMax());
		} catch (Exception ex) {
			log.error("Failed to trace init/max for [{}]", on, ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.BaseMBeanObserver#accept(java.util.Map, long, long)
	 */
	@Override
	protected boolean accept(final Map<ObjectName, Map<String, Object>> data, final long currentTime, final long elapsedTime) {
		final Map<String, Object> memData = data.get(objectName);
		final Map<MemoryType, MemoryUsage> usages = new EnumMap<MemoryType, MemoryUsage>(MemoryType.class);
		usages.put(MemoryType.HEAP, MemoryUsage.from((CompositeData)memData.get(MemoryAttribute.HEAP_MEMORY_USAGE_INIT.attributeName)));
		usages.put(MemoryType.NON_HEAP, MemoryUsage.from((CompositeData)memData.get(MemoryAttribute.NON_HEAP_MEMORY_USAGE_INIT.attributeName)));
		for(MemoryType mt: MemoryType.values()) {
			for(MUsage mu: MUsage.getNonOneTimes()) {
				memMetrics.get(mt).get(mu).trace(currentTime, mu.get(usages.get(mt)));
			}
		}
		pendingFinalizers.trace(currentTime, memData.get(MemoryAttribute.OBJECT_PENDING_FINALIZATION_COUNT.attributeName));
		return true;
	}

}
