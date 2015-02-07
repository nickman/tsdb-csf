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
	final OTMetric loadedClassCount;
	 /** The total loaded class count metric */
	final OTMetric totalLoadedClassCount;
	 /** The total unloaded class count metric */
	final OTMetric unloadedClassCount;
	 /** The class unload rate metric */
	final OTMetric classUnloadRate;
	 /** The class load rate metric */
	final OTMetric classLoadRate;
	 
	/**
	 * Creates a new ClassLoadingMBeanObserver
	 * @param jmxConnector The JMXConnector that will supply an MBeanServerConnection
	 * @param tags The tags common to all metrics submitted from this observer
	 */
	public ClassLoadingMBeanObserver(final JMXConnector jmxConnector, final Map<String, String> tags) {
		this(RuntimeMBeanServerConnection.newInstance(jmxConnector), tags);
	}

	/**
	 * Creates a new ClassLoadingMBeanObserver
	 * @param mbeanServerConn The MBeanServerConnection to monitor
	 * @param tags The tags common to all metrics submitted from this observer
	 */
	public ClassLoadingMBeanObserver(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags) {
		super(mbeanServerConn, CLASSLOADING_MXBEAN, tags);
		final ObjectName on = objectNamesAttrs.keySet().iterator().next();
		loadedClassCount = MetricBuilder.metric(on).ext("classloading.loaded").tags(tags).build();
		totalLoadedClassCount = MetricBuilder.metric(on).ext("classloading.totalLoaded").tags(tags).build();
		unloadedClassCount = MetricBuilder.metric(on).ext("classloading.totalUnloaded").tags(tags).build();
		classUnloadRate = MetricBuilder.metric(on).ext("classloading.unloadRate").tags(tags).build();
		classLoadRate = MetricBuilder.metric(on).ext("classloading.loadRate").tags(tags).build();

	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.BaseMBeanObserver#accept(java.util.Map, long)
	 */
	@Override
	protected boolean accept(Map<ObjectName, Map<String, Object>> data, final long currentTime) {
		Map<String, Object> values = data.values().iterator().next();
		final long totalLoaded = (Long)values.get(ClassLoadingAttribute.TOTAL_LOADED_CLASS_COUNT);
		final long unLoaded = (Long)values.get(ClassLoadingAttribute.UNLOADED_CLASS_COUNT);
		final Long loadRate = delta("loadRate", totalLoaded);
		final Long unloadRate = delta("unloadRate", unLoaded);
		loadedClassCount.trace(currentTime, values.get(ClassLoadingAttribute.LOADED_CLASS_COUNT));
		totalLoadedClassCount.trace(currentTime, totalLoaded);
		unloadedClassCount.trace(currentTime, unLoaded);
		classUnloadRate.trace(currentTime, unloadRate);
		classLoadRate.trace(currentTime, loadRate);		
		return true;
	}

}
