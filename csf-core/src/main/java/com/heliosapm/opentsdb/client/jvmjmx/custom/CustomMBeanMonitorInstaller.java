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
package com.heliosapm.opentsdb.client.jvmjmx.custom;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.w3c.dom.Node;

import com.heliosapm.opentsdb.client.util.XMLHelper;

/**
 * <p>Title: CustomMBeanMonitorInstaller</p>
 * <p>Description: Configures and installs custom MBean monitors based on the XML config from the agent config.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.custom.CustomMBeanMonitorInstaller</code></p>
 */

public class CustomMBeanMonitorInstaller {
/*
	<customjmx freq="5">
		<mbeanserver domain="DefaultDomain" prefix="Hadoop:service=HBase">
			<monitor objectName="name=*">
				<attributes include="" exclude="" numericsonly="true" />
				<keys pattern="tag\.(.*)?" delim="" include="" exclude=""/>
			</monitor>
		</mbeanserver>
	</customjmx> 
 */
	
	/** The singleton instance */
	private static volatile CustomMBeanMonitorInstaller instance = null;
	/** The singleton ctor */
	private static final Object lock = new Object();
	
	/** The default frequency in seconds */
	private int defaultFrequency = 15;
	/** The map of monitored mbean servers keyed by the default domain of the mbeanserver */
	private Map<String, MonitoredMBeanServer> mbeanServers = new ConcurrentHashMap<String, MonitoredMBeanServer>(8);
	
	/**
	 * Returns the CustomMBeanMonitorInstaller singleton instance
	 * @param configNode the configuration node which is only required on the first call.
	 * @return the CustomMBeanMonitorInstaller singleton instance
	 */
	public static CustomMBeanMonitorInstaller getInstance(final Node configNode) {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					if(configNode==null) throw new IllegalArgumentException("The passed configuration node was null");
					instance = new CustomMBeanMonitorInstaller(configNode);
				}
			}
		}
		return instance;
	}
	
	private CustomMBeanMonitorInstaller(final Node configNode) {
		if(configNode==null) throw new IllegalArgumentException("The passed configuration node was null");
		defaultFrequency = XMLHelper.getAttributeByName(configNode, "customjmx", 15);
		final List<Node> mbeanServers = XMLHelper.getChildNodesByName(configNode, "mbeanserver", false);
		for(Node mbsNode: mbeanServers) {
			String dd = XMLHelper.getAttributeByName(mbsNode, "domain", null);
		}
	}
	
}
