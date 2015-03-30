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

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Node;

import com.heliosapm.opentsdb.client.boot.XMLLoader;
import com.heliosapm.opentsdb.client.util.XMLHelper;

/**
 * <p>Title: CustomMBeanMonitorInstaller</p>
 * <p>Description: Configures and installs custom MBean monitors based on the XML config from the agent config.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.custom.CustomMBeanMonitorInstaller</code></p>
 */

public class CustomMBeanMonitorInstaller {
	
	
	/** The singleton instance */
	private static volatile CustomMBeanMonitorInstaller instance = null;
	/** The singleton ctor */
	private static final Object lock = new Object();
	
	/** The default period in seconds */
	private final AtomicInteger defaultPeriod = new AtomicInteger(15);
	/** The long hash code for the installer configuration node */
	private long configHashCode = -1L;
	/** The map of monitored mbean servers keyed by the default domain of the mbeanserver */
	private Map<String, MonitoredMBeanServer> mbeanServers = new ConcurrentHashMap<String, MonitoredMBeanServer>(8);
	/** Instance logger */
	protected final Logger log = LogManager.getLogger(getClass());
	

	public static void main(String[] args) {
		log("Basic XML Direct Test");
		try {
			File xmlConfig = new File("./src/test/resources/configs/LocalTest.xml");
			Node rootNode = XMLHelper.parseXML(xmlConfig).getDocumentElement();
			XMLLoader.boot("./src/test/resources/configs/LocalTest.xml");
//			getInstance(rootNode);
			log("Booted......");
			Thread.currentThread().join();
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
			Runtime.getRuntime().halt(-1);
		}
	}
	
	/**
	 * System out format logger
	 * @param fmt The message format
	 * @param args The message args
	 */
	public static void log(final Object fmt, final Object...args) {
		System.out.println(String.format(fmt.toString(), args));
	}

	
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
	
	/**
	 * Creates a new CustomMBeanMonitorInstaller
	 * @param configNode The XML configuration node 
	 */
	private CustomMBeanMonitorInstaller(final Node configNode) {
		configHashCode = XMLHelper.longHashCode(configNode);
		configure(configNode, true);
		log.info("CustomMBeanMonitorInstaller Started. Installed [{}] MBeanServers and [{}] monitors", mbeanServers.size(), 0);  // FIXME
	}
	
	/**
	 * Introduces a new configuration
	 * @param configNode The XML configuration node 
	 * @param init true if the first call, false if a refresh
	 */
	void configure(final Node configNode, final boolean init) {
		if(configNode==null) throw new IllegalArgumentException("The passed configuration node was null");
		long evalHashCode = -1L;
		if(!init) {
			// If we're refreshing the config, but the node hash code is unchanged, we eject.
			evalHashCode = XMLHelper.longHashCode(configNode);
			if(evalHashCode == configHashCode) return;
		}
		final Map<String, MonitoredMBeanServer> copyOfMBeanServers = new HashMap<String, MonitoredMBeanServer>(mbeanServers);
		
		
		defaultPeriod.set(XMLHelper.getAttributeByName(configNode, "freq", 15));  // TODO:  Do this last ?
		final List<Node> mbeanServers = XMLHelper.getChildNodesByName(configNode, "mbeanserver", false);
		for(Node mbsNode: mbeanServers) {
			final String dd = XMLHelper.getAttributeByName(mbsNode, "domain", "").trim();
			if(dd.isEmpty()) { 
				log.warn("Invalid domain specified. Cannot be null or empty");
				continue;
			}
			MonitoredMBeanServer mmbs = copyOfMBeanServers.remove(dd);
			try {
				if(mmbs==null) {
					this.mbeanServers.put(dd, new MonitoredMBeanServer(mbsNode, defaultPeriod.get()));
				} else {
					mmbs.configure(mbsNode, false);
				}
			} catch (Exception ex) {
				log.error("Invalid custom jmx mbeanserver monitor configuration node [{}]", XMLHelper.renderNode(mbsNode), ex);
				continue;
			}
		}		
		// clear out left over mbean servers
		for(final String dd: copyOfMBeanServers.keySet()) {
			MonitoredMBeanServer mmbs = this.mbeanServers.remove(dd);
			if(mmbs!=null) {
				mmbs.shutdown();
				log.info("Stopped monitoring for MBeanServer[{}]", dd);
			}
		}
		copyOfMBeanServers.clear();
		if(!init && evalHashCode!= -1L) {			
			configHashCode = evalHashCode;
		}
	}
	
	
	
	/**
	 * Returns the default polling period in seconds
	 * @return the default period
	 */
	public int getDefaultPeriod() {
		return defaultPeriod.get();
	}

	/**
	 * Sets the default polling period in seconds
	 * @param defaultPeriod the default period to set
	 */
	public void setDefaultPeriod(final int  defaultPeriod) {
		if(defaultPeriod<1) throw new IllegalArgumentException("Invalid default period [" + defaultPeriod + "]");		
		final int prior = this.defaultPeriod.getAndSet(defaultPeriod);
		if(prior!=defaultPeriod) {
			fireDefaultPerodChanged(defaultPeriod);
		}
	}

	private void fireDefaultPerodChanged(final int newPeriod) {
		// TODO:
	}
	
	
}
