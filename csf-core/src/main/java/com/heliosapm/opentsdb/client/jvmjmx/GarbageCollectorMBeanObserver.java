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

import com.heliosapm.opentsdb.client.jvmjmx.MBeanObserver.GarbageCollectorAttribute;

import java.util.Map;
import java.util.Set;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;

import com.heliosapm.opentsdb.client.opentsdb.MetricBuilder;
import com.heliosapm.opentsdb.client.opentsdb.OTMetric;
import com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection;

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
	final Map<String, OTMetric> totalCollectionTimes;
	/** The total collection count metric */
	final Map<String, OTMetric> totalCollectionCount;
	/** The collection time rate metric */
	final Map<String, OTMetric> collectionTimeRates;
	/** The collection count rate metric */
	final Map<String, OTMetric> collectionCountRates;
	
//	LAST_GC_INFO_COMMITTED("LastGcInfo", "committed", CompositeData.class, long.class),
//	/**  */
//	LAST_GC_INFO_INIT("LastGcInfo", "init", CompositeData.class, long.class),
//	/**  */
//	LAST_GC_INFO_MAX("LastGcInfo", "max", CompositeData.class, long.class),
//	/**  */
//	LAST_GC_INFO_USED("LastGcInfo", "used", CompositeData.class, long.class),		
	

	
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
		totalCompileTime = MetricBuilder.metric(objectName).ext("compiler.time").tags(tags).build();
		compileRate = MetricBuilder.metric(objectName).ext("compiler.rate").tags(tags).build();
	}
	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.BaseMBeanObserver#accept(java.util.Map, long)
	 */
	@Override
	protected boolean accept(Map<ObjectName, Map<String, Object>> data,
			long currentTime) {
		// TODO Auto-generated method stub
		return false;
	}

}
