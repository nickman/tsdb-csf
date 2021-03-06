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

import java.util.Map;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;

import static com.heliosapm.opentsdb.client.jvmjmx.MBeanObserver.CLASSLOADING_MXBEAN;
import com.heliosapm.opentsdb.client.jvmjmx.MBeanObserver.ClassLoadingAttribute;
import com.heliosapm.opentsdb.client.opentsdb.MetricBuilder;
import com.heliosapm.opentsdb.client.opentsdb.OTMetric;
import com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection;

/**
 * <p>Title: ClassLoadingMBeanObserver</p>
 * <p>Description: ClassLoading MXBean MBean Observer </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.ClassLoadingMBeanObserver</code></p>
 */

public class ClassLoadingMBeanObserver extends BaseMBeanObserver {
	 /**  */
	private static final long serialVersionUID = -6556007382355868989L;
	/** The currently loaded class count metric */
	protected final OTMetric loadedClassCount;
	 /** The total loaded class count metric */
	protected final OTMetric totalLoadedClassCount;
	 /** The total unloaded class count metric */
	protected final OTMetric unloadedClassCount;
	 /** The class unload rate metric */
	protected final OTMetric classUnloadRate;
	 /** The class load rate metric */
	protected final OTMetric classLoadRate;
	 
	/**
	 * Creates a new ClassLoadingMBeanObserver
	 * @param jmxConnector The JMXConnector that will supply an MBeanServerConnection
	 * @param tags The tags common to all metrics submitted from this observer
	 * @param publishObserverMBean If true, an observer management MBean will be registered
	 */
	public ClassLoadingMBeanObserver(final JMXConnector jmxConnector, final Map<String, String> tags, final boolean publishObserverMBean) {
		this(RuntimeMBeanServerConnection.newInstance(jmxConnector), tags, publishObserverMBean);
	}

	/**
	 * Creates a new ClassLoadingMBeanObserver
	 * @param mbeanServerConn The MBeanServerConnection to monitor
	 * @param tags The tags common to all metrics submitted from this observer
	 * @param publishObserverMBean If true, an observer management MBean will be registered
	 */
	public ClassLoadingMBeanObserver(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags, final boolean publishObserverMBean) {
		super(mbeanServerConn, CLASSLOADING_MXBEAN, tags, publishObserverMBean);
		final ObjectName on = objectNamesAttrs.keySet().iterator().next();
		loadedClassCount = MetricBuilder.metric(on).ext("classloading.loaded").tags(tags).build();
		totalLoadedClassCount = MetricBuilder.metric(on).ext("classloading.totalLoaded").tags(tags).build();
		unloadedClassCount = MetricBuilder.metric(on).ext("classloading.totalUnloaded").tags(tags).build();
		classUnloadRate = MetricBuilder.metric(on).ext("classloading.unloadRate").tags(tags).build();
		classLoadRate = MetricBuilder.metric(on).ext("classloading.loadRate").tags(tags).build();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.BaseMBeanObserver#accept(java.util.Map, long, long)
	 */
	@Override
	protected boolean accept(Map<ObjectName, Map<String, Object>> data, final long currentTime, final long elapsedTime) {
		Map<String, Object> values = data.values().iterator().next();
		final long totalLoaded = (Long)values.get(ClassLoadingAttribute.TOTAL_LOADED_CLASS_COUNT.attributeName);
		final long unLoaded = (Long)values.get(ClassLoadingAttribute.UNLOADED_CLASS_COUNT.attributeName);
		final Long loadRate = delta(deltaKey("loadRate"), totalLoaded);
		final Long unloadRate = delta(deltaKey("unloadRate"), unLoaded);
		loadedClassCount.trace(currentTime, values.get(ClassLoadingAttribute.LOADED_CLASS_COUNT.attributeName));
		totalLoadedClassCount.trace(currentTime, totalLoaded);
		unloadedClassCount.trace(currentTime, unLoaded);
		classUnloadRate.trace(currentTime, unloadRate);
		classLoadRate.trace(currentTime, loadRate);		
		return true;
	}

}
