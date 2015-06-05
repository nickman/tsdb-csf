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

/**
 * <p>Title: ObserverFactories</p>
 * <p>Description: MBean observer factories</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.ObserverFactories</code></p>
 */

public class ObserverFactories {

	/**
	 * <p>Title: ObserverFactory</p>
	 * <p>Description: Abstracted BaseMBeanObserver factory</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.ObserverFactories.ObserverFactory</code></p>
	 */
	public static interface ObserverFactory {
		/**
		 * Creates a new BaseMBeanObserver
		 * @param mbeanServerConn The MBeanServerConnection to monitor
		 * @param tags The tags common to all metrics submitted from this observer
		 * @param publishObserverMBean If true, an observer management MBean will be registered
		 * @param args Optional additional args for observers needing extra config
		 * @return the created BaseMBeanObserver
		 */
		public BaseMBeanObserver build(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags, final boolean publishObserverMBean, final String...args);
		// HotSpotInternalsBaseMBeanObserver(final MBeanServerConnection mbeanServerConn, final boolean publishObserverMBean, final Map<String, String> tags, final String hotspotMBean, final String counterPattern) {
	}
	
	
	private ObserverFactories() {}

}
