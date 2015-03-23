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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.MBeanServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Node;

import com.heliosapm.opentsdb.client.util.JMXHelper;
import com.heliosapm.opentsdb.client.util.XMLHelper;

/**
 * <p>Title: MonitoredMBeanServer</p>
 * <p>Description: Monitoring and management representation of an in-vm mbeanserver.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.custom.MonitoredMBeanServer</code></p>
 */

public class MonitoredMBeanServer {
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
	/** Instance logger */
	protected final Logger log = LogManager.getLogger(getClass());	
	/** The monitored mbeanserver */
	final MBeanServer server;
	/** The monitored mbeanserver's default domain name */
	final String defaultDomain;
	/** The default object name prefix for mbean monitors operating against this mbean server */
	String defaultPrefix = "";
	/** The installer defined default period */
	int defaultPeriod = 15;
	/** The long hash code for the server configuration node */
	private long configHashCode = -1L;
	
	/** A map of monitors that run against this MBeanServer keyed by the monitor's object name */
	final Map<String, DefaultMonitor> monitors = new ConcurrentHashMap<String, DefaultMonitor>();

	/**
	 * Creates a new MonitoredMBeanServer
	 * @param configNode The XML configuration node
	 * @param defaultPeriod The installer defined default period
	 * @param customPrefix The installer defined object name prefix
	 */
	MonitoredMBeanServer(final Node configNode, final int defaultPeriod) {	
		if(configNode==null) throw new IllegalArgumentException("The passed configuration node was null");
		defaultDomain = XMLHelper.getAttributeByName(configNode, "domain", "DefaultDomain");
		server = JMXHelper.getLocalMBeanServer(defaultDomain, false);
		this.defaultPeriod = defaultPeriod;
		configHashCode = XMLHelper.longHashCode(configNode);
		configure(configNode, true);
	}
	
	
	
	/**
	 * Introduces a new configuration
	 * @param configNode The XML configuration node 
	 * @param init true if this is the first call, false if it is a refresh
	 */
	void configure(final Node configNode, final boolean init) {
		if(configNode==null) throw new IllegalArgumentException("The passed configuration node was null");
		long evalHashCode = -1L;
		if(!init) {
			// If we're refreshing the config, but the node hash code is unchanged, we eject.
			evalHashCode = XMLHelper.longHashCode(configNode);
			if(evalHashCode == configHashCode) return;
		}
		
		defaultPrefix = XMLHelper.getAttributeByName(configNode, "prefix", "");
		
		for(Node monitorNode: XMLHelper.getChildNodesByName(configNode, "monitor", false)) {
			DefaultMonitor monitor = null;
			final String objectNamePattern = XMLHelper.getAttributeByName(monitorNode, "objectName", null);
			if(objectNamePattern != null && !objectNamePattern.trim().isEmpty()) {
				monitor = monitors.get(objectNamePattern);
				if(monitor!=null) {
					monitor.configure(monitorNode, false);
					continue;				
				}
				monitor = new DefaultMonitor(server, monitorNode, defaultPeriod, defaultPrefix);
				monitors.put(objectNamePattern, monitor);
			} else {
				log.info("Skipping invalid monitor definition with undefined or blank objectName attribute: {}", XMLHelper.renderNode(monitorNode));
			}
		}
		if(!init && evalHashCode!= -1L) {			
			configHashCode = evalHashCode;
		}
	}

	
	/**
	 * Called when this monitored mbean server is no longer needed, which can be at shutdown, agent removal or a refreshed
	 * configuration which did not configure this targetted mbeanserver.
	 */
	void shutdown() {
		// TODO:
	}
	
}
