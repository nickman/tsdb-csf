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

import static com.heliosapm.opentsdb.client.jvmjmx.MBeanObserver.MEMORY_POOL_MXBEAN;

import java.lang.management.MemoryUsage;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;

import com.heliosapm.opentsdb.client.opentsdb.MetricBuilder;
import com.heliosapm.opentsdb.client.opentsdb.OTMetric;
import com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: MemoryPoolsCollectorMBeanObserver</p>
 * <p>Description: MBeanObserver for the Memory Pools MXBeans</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.MemoryPoolsCollectorMBeanObserver</code></p>
 */

public class MemoryPoolsCollectorMBeanObserver extends BaseMBeanObserver {
	
	/**  */
	private static final long serialVersionUID = 6809115571686500213L;
	/** Sets of metrics keyed by the pool name */
	protected final Map<String, Map<MUsage, OTMetric>> poolMetrics;
	/** The max sizes keyed by the pool name */
	protected final Map<String, Long> poolMaxes;

	/** The Mem Type metric name extension */
	protected static final String MEM_EXT = "memory";
	/** The pool name key */
	protected static final String POOL = "pool";
	
	/** The Mem Type tag key */
	protected static final String MEM_TYPE = "type";
	/** The Mem Alloc key */
	protected static final String MEM_ALLOC = "alloc";
	
	/**
	 * Creates a new MemoryPoolsCollectorMBeanObserver
	 * @param jmxConnector The JMXConnector that will supply an MBeanServerConnection
	 * @param tags The tags common to all metrics submitted from this observer
	 * @param publishObserverMBean If true, an observer management MBean will be registered
	 */
	public MemoryPoolsCollectorMBeanObserver(final JMXConnector jmxConnector, final Map<String, String> tags, final boolean publishObserverMBean) {
		this(RuntimeMBeanServerConnection.newInstance(jmxConnector), tags, publishObserverMBean);
	}

	/**
	 * Creates a new MemoryPoolsCollectorMBeanObserver
	 * @param mbeanServerConn The MBeanServerConnection to monitor
	 * @param tags The tags common to all metrics submitted from this observer
	 * @param publishObserverMBean If true, an observer management MBean will be registered
	 */
	public MemoryPoolsCollectorMBeanObserver(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags, final boolean publishObserverMBean) {
		super(mbeanServerConn, MEMORY_POOL_MXBEAN, tags, publishObserverMBean);
		poolMetrics = new HashMap<String, Map<MUsage, OTMetric>>(objectNamesAttrs.size());
		poolMaxes = new HashMap<String, Long>(objectNamesAttrs.size());
		for(ObjectName on: objectNamesAttrs.keySet()) {
			Map<MUsage, OTMetric> otMetrics = new EnumMap<MUsage, OTMetric>(MUsage.class);
			final String poolName = on.getKeyProperty("name");
			final String cleanedPoolName = Util.clean(poolName);
			poolMetrics.put(poolName, otMetrics);
			for(MUsage mu: MUsage.getNonOneTimes()) {
				otMetrics.put(mu, MetricBuilder.metric("java.lang").ext("mempool").tags(tags).tag(MEM_ALLOC, mu).tag(POOL, cleanedPoolName).build());
			}
			traceOneTimers(on);
		}
		
	}
	
	/**
	 * Traces immutable memory metrics once.
	 * @param on The ObjectName
	 */
	protected void traceOneTimers(final ObjectName on) {
		try {
			final long timestamp = clock.getTime();
			final String poolName = on.getKeyProperty("name");
			final MemoryUsage usage = MemoryUsage.from((CompositeData)mbs.getAttribute(on, "Usage"));
			poolMaxes.put(poolName, usage.getMax());
			for(MUsage mu: MUsage.getOneTimes()) {
				MetricBuilder.metric("java.lang").ext("mempool").tags(tags).tag(MEM_ALLOC, mu).tag(POOL, Util.clean(poolName)).build().trace(timestamp, mu.get(usage));
			}
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
		for(ObjectName on: objectNamesAttrs.keySet()) {
			final String poolName = on.getKeyProperty("name");
			final Map<MUsage, OTMetric> otMetrics = poolMetrics.get(poolName);
			final MemoryUsage usage = MemoryUsage.from((CompositeData)data.get(on).get("Usage"));
			for(MUsage mu: MUsage.getNonOneTimes()) {
				otMetrics.get(mu).trace(currentTime, mu.get(usage));
			}
		}
		return true;
	}
	
}
