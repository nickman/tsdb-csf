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
import java.util.regex.Pattern;

import javax.management.MBeanServerConnection;

import com.heliosapm.opentsdb.client.opentsdb.ConfigurationReader;
import com.heliosapm.opentsdb.client.opentsdb.Constants;

/**
 * <p>Title: MBeanObserverFactory</p>
 * <p>Description: Defines a factory that creates new MBeanObserver instances</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.MBeanObserverFactory</code></p>
 */

public class MBeanObserverFactory {
	
	/**
	 * Returns a BaseMBeanObserver for the passed observer type
	 * @param type the observer type to create
	 * @param mbeanServerConn The MBeanServer where the observed MBean is registered
	 * @param tags The tags that the metrics generated will include
	 * @param publishObserverMBean true to create a management interface for the observer
	 * @return a BaseMBeanObserver for the passed observer type
	 */
	public static BaseMBeanObserver getMBeanObserver(final MBeanObserver type, final MBeanServerConnection mbeanServerConn, final Map<String, String> tags, final boolean publishObserverMBean) {
		 switch(type) {
			case CLASSLOADING_MXBEAN:
				return new ClassLoadingMBeanObserver(mbeanServerConn, tags, publishObserverMBean);
			case COMPILATION_MXBEAN:
				return new CompilationMBeanObserver(mbeanServerConn, tags, publishObserverMBean);
			case GARBAGE_COLLECTOR_MXBEAN:
				return new GarbageCollectorMBeanObserver(mbeanServerConn, tags, publishObserverMBean);
			case HOTSPOT_CLASSLOADING_MBEAN:
				return new HotSpotInternalsBaseMBeanObserver(mbeanServerConn, publishObserverMBean, tags, "classloading", ConfigurationReader.conf(Constants.PROP_JMX_HOTSPOT_CLASSLOADING, Constants.DEFAULT_JMX_HOTSPOT_CLASSLOADING));
			case HOTSPOT_COMPILATION_MBEAN:
				return new HotSpotInternalsBaseMBeanObserver(mbeanServerConn, publishObserverMBean, tags, "compilation", ConfigurationReader.conf(Constants.PROP_JMX_HOTSPOT_COMPILATION, Constants.DEFAULT_JMX_HOTSPOT_COMPILATION));
			case HOTSPOT_MEMORY_MBEAN:
				return GCConfiguration.getInstance(mbeanServerConn, Pattern.compile(
						ConfigurationReader.conf(Constants.PROP_JMX_HOTSPOT_MEMORY, Constants.DEFAULT_JMX_HOTSPOT_MEMORY)
				));
			case HOTSPOT_RUNTIME_MBEAN:
				return new HotSpotInternalsBaseMBeanObserver(mbeanServerConn, publishObserverMBean, tags, "runtime", ConfigurationReader.conf(Constants.PROP_JMX_HOTSPOT_RUNTIME, Constants.DEFAULT_JMX_HOTSPOT_RUNTIME));
			case HOTSPOT_THREADING_MBEAN:
				return new HotSpotInternalsBaseMBeanObserver(mbeanServerConn, publishObserverMBean, tags, "threading", ConfigurationReader.conf(Constants.PROP_JMX_HOTSPOT_THREADING, Constants.DEFAULT_JMX_HOTSPOT_THREADING));
			case MEMORY_MXBEAN:
				return new MemoryCollectorMBeanObserver(mbeanServerConn, tags, publishObserverMBean);
			case MEMORY_POOL_MXBEAN:
					return new MemoryPoolsCollectorMBeanObserver(mbeanServerConn, tags, publishObserverMBean);			
			case OPERATING_SYSTEM_MXBEAN:
					return new OperatingSystemCollectorMBeanObserver(mbeanServerConn, tags, publishObserverMBean);
			case RUNTIME_MXBEAN:
					return null; //new RuntimeMBeanObserver(mbeanServerConn, tags, publishObserverMBean);
			case THREAD_MXBEAN:
					return new ThreadingCollectorMBeanObserver(mbeanServerConn, tags, publishObserverMBean);
			default:
				return null;
			 
			 }
	}
	

}
