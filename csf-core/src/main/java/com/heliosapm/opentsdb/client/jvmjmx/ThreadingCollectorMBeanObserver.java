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

import static com.heliosapm.opentsdb.client.jvmjmx.MBeanObserver.THREAD_MXBEAN;

import java.lang.management.ThreadInfo;
import java.util.EnumMap;
import java.util.Map;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;

import com.heliosapm.opentsdb.client.jvmjmx.MBeanObserver.ThreadingAttribute;
import com.heliosapm.opentsdb.client.opentsdb.MetricBuilder;
import com.heliosapm.opentsdb.client.opentsdb.OTMetric;
import com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection;

/**
 * <p>Title: ThreadingCollectorMBeanObserver</p>
 * <p>Description: MBeanObserver for the  Threading MXBean</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.ThreadingCollectorMBeanObserver</code></p>
 */

public class ThreadingCollectorMBeanObserver extends BaseMBeanObserver {
	/**  */
	private static final long serialVersionUID = -4665443252849651807L;
	/** OTMetrics for all the enabled attributes */
	protected final EnumMap<ThreadingAttribute, OTMetric> otMetrics = new EnumMap<ThreadingAttribute, OTMetric>(ThreadingAttribute.class);
	/** OTMetrics for each thread state */
	protected final EnumMap<Thread.State, OTMetric> otThreadMetrics = new EnumMap<Thread.State, OTMetric>(Thread.State.class);
	
	/**
	 * Creates a new ThreadingCollectorMBeanObserver
	 * @param jmxConnector The JMXConnector that will supply an MBeanServerConnection
	 * @param tags The tags common to all metrics submitted from this observer
	 * @param publishObserverMBean If true, an observer management MBean will be registered
	 */
	public ThreadingCollectorMBeanObserver(final JMXConnector jmxConnector, final Map<String, String> tags, final boolean publishObserverMBean) {
		this(RuntimeMBeanServerConnection.newInstance(jmxConnector), tags, publishObserverMBean);
	}

	/**
	 * Creates a new ThreadingCollectorMBeanObserver
	 * @param mbeanServerConn The MBeanServerConnection to monitor
	 * @param tags The tags common to all metrics submitted from this observer
	 * @param publishObserverMBean If true, an observer management MBean will be registered
	 */
	public ThreadingCollectorMBeanObserver(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags, final boolean publishObserverMBean) {
		super(mbeanServerConn, THREAD_MXBEAN, tags, publishObserverMBean);
		for(ThreadingAttribute ta: ThreadingAttribute.values()) {
			otMetrics.put(ta, MetricBuilder.metric(THREAD_MXBEAN.objectName).tags(this.tags).ext("threading." + ta.attributeName).build());
		}
		for(Thread.State state: Thread.State.values()) {
			otThreadMetrics.put(state, MetricBuilder.metric(THREAD_MXBEAN.objectName).tags(this.tags).ext("threading.states").tag("state", state.name()).build());
		}
	}
	
	private static final String[] GET_THREAD_INFOS_SIG = new String[]{long[].class.getName()};

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.BaseMBeanObserver#accept(java.util.Map, long, long)
	 */
	@Override
	protected boolean accept(Map<ObjectName, Map<String, Object>> data, long currentTime, long elapsedTime) {
		try {
			final Map<String, Object> attrValues = data.get(THREAD_MXBEAN.objectName);
			for(Map.Entry<ThreadingAttribute, OTMetric> entry: otMetrics.entrySet()) {
				ThreadingAttribute ta = entry.getKey();
				if(ta.isPrimitive()) {
					entry.getValue().trace(currentTime, attrValues.get(ta.attributeName));
				}			
			}
			final long[] threadIDs = (long[])attrValues.get(ThreadingAttribute.ALL_THREAD_IDS.attributeName);
			final CompositeData[] threadDatas = (CompositeData[])mbs.invoke(THREAD_MXBEAN.objectName, "getThreadInfo", new Object[]{threadIDs}, GET_THREAD_INFOS_SIG);
			
			
			final EnumMap<Thread.State, int[]> accumulator = new EnumMap<Thread.State, int[]>(Thread.State.class);
			for(Thread.State st: Thread.State.values()) {
				accumulator.put(st, new int[]{0});
			}
			for(CompositeData threadData: threadDatas) {
				accumulator.get(ThreadInfo.from(threadData).getThreadState())[0]++;
			}
			for(Map.Entry<Thread.State, int[]> entry: accumulator.entrySet()) {
				final int count = entry.getValue()[0];
				final Thread.State state = entry.getKey();
				otThreadMetrics.get(state).trace(currentTime, count);
			}
		} catch (Exception ex) {
			log.error("Failed to collect", ex);
		}
		return true;
	}

}
