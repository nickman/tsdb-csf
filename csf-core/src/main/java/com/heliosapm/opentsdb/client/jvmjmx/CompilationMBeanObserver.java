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

import static com.heliosapm.opentsdb.client.jvmjmx.MBeanObserver.COMPILATION_MXBEAN;

import java.lang.management.ManagementFactory;
import java.util.Map;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;

import com.heliosapm.opentsdb.client.jvmjmx.MBeanObserver.CompilationAttribute;
import com.heliosapm.opentsdb.client.opentsdb.MetricBuilder;
import com.heliosapm.opentsdb.client.opentsdb.OTMetric;
import com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection;
import com.heliosapm.utils.jmx.JMXHelper;

/**
 * <p>Title: CompilationMBeanObserver</p>
 * <p>Description: MBeanObserver for the compilation MXBean</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.CompilationMBeanObserver</code></p>
 */

public class CompilationMBeanObserver extends BaseMBeanObserver {
	/**  */
	private static final long serialVersionUID = 6501323051943930514L;
	/** The total compile time metric */
	protected final OTMetric totalCompileTime;
	 /** The compile rate metric */
	protected final OTMetric compileRate;
	
	/** The compiler MXBean ObjectName */
	final ObjectName objectName = JMXHelper.objectName(ManagementFactory.COMPILATION_MXBEAN_NAME);

	/**
	 * Creates a new CompilationMBeanObserver
	 * @param jmxConnector The JMXConnector that will supply an MBeanServerConnection
	 * @param tags The tags common to all metrics submitted from this observer
	 * @param publishObserverMBean If true, an observer management MBean will be registered 
	 */
	public CompilationMBeanObserver(final JMXConnector jmxConnector, final Map<String, String> tags, final boolean publishObserverMBean) {
		this(RuntimeMBeanServerConnection.newInstance(jmxConnector), tags, publishObserverMBean);
	}

	/**
	 * Creates a new CompilationMBeanObserver
	 * @param mbeanServerConn The MBeanServerConnection to monitor
	 * @param tags The tags common to all metrics submitted from this observer
	 * @param publishObserverMBean If true, an observer management MBean will be registered
	 */
	public CompilationMBeanObserver(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags, final boolean publishObserverMBean) {
		super(mbeanServerConn, COMPILATION_MXBEAN, tags, publishObserverMBean);	
		totalCompileTime = MetricBuilder.metric(objectName).ext("compiler.time").tags(tags).build();
		compileRate = MetricBuilder.metric(objectName).ext("compiler.rate").tags(tags).build();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.BaseMBeanObserver#initialize(com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection, java.util.Map)
	 */
	@Override
	protected boolean initialize(final RuntimeMBeanServerConnection mbs, final Map<ObjectName, String[]> objectNamesAttrs) {
		return (Boolean)mbs.getAttribute(objectName, "CompilationTimeMonitoringSupported");
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.BaseMBeanObserver#accept(java.util.Map, long, long)
	 */
	@Override
	protected boolean accept(final Map<ObjectName, Map<String, Object>> data, final long currentTime, final long elapsedTime) {
		Map<String, Object> values = data.values().iterator().next();
		totalCompileTime.trace(currentTime, values.get(CompilationAttribute.TOTAL_COMPILATION_TIME.attributeName));
		compileRate.trace(currentTime, delta(deltaKey("compileRate"), (Long)values.get(CompilationAttribute.TOTAL_COMPILATION_TIME.attributeName)));
		return true;
	}

}
