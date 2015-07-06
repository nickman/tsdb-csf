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
package com.heliosapm.opentsdb.client.jvmjmx.customx;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Node;

import com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection;
import com.heliosapm.opentsdb.client.scripts.ScriptManager;
import com.heliosapm.utils.io.CloseableService;
import com.heliosapm.utils.jmx.JMXHelper;
import com.heliosapm.utils.jmx.JMXManagedScheduler;
import com.heliosapm.utils.jmx.JMXManagedThreadPool;
import com.heliosapm.utils.jmx.notifcations.ConnectionNotification;
import com.heliosapm.utils.lang.StringHelper;
import com.heliosapm.utils.xml.XMLHelper;

/**
 * <p>Title: CollectionManager</p>
 * <p>Description: Creates and configures JMX collectors</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>configs.jmxcollect.CollectionManager</code></p>
 */

public class CollectionManager implements NotificationListener, CollectionManagerMBean {
	/** The singleton instance */
	private static volatile CollectionManager instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();

	/** Instance logger */
	private final Logger log = LogManager.getLogger(getClass());
	
	/** A map of server definitions keyed by the server name */
	final Map<String, JMXServerDefinition> serverDefinitions = new ConcurrentHashMap<String, JMXServerDefinition>();
	/** The collection scheduler */
	protected final JMXManagedScheduler scheduler;
	/** The collection executor */
	protected final JMXManagedThreadPool executor;
	
	/** The script manager */
	protected final ScriptManager sm;
	/** The JMX query manager */ 
	protected final QueryManager qm;

	
	/** The CollectionManager JMX ObjectName  */
	public static final ObjectName OBJECT_NAME = JMXHelper.objectName(CollectionManager.class);
	/** The CollectionManager's scheduler JMX ObjectName  */
	public static final ObjectName SCHEDULER_OBJECT_NAME = JMXHelper.objectName(OBJECT_NAME + ",threadpool=Scheduler");
	/** The CollectionManager's executor JMX ObjectName  */
	public static final ObjectName EXECUTOR_OBJECT_NAME = JMXHelper.objectName(OBJECT_NAME + ",threadpool=Executor");

	

	/**
	 * Acquires the CollectionManager singleton instance
	 * @return the CollectionManager singleton instance
	 */
	public static CollectionManager getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new CollectionManager(); 
				}
			}
		}
		return instance;
	}
	
	/**
	 * Creates a new CollectionManager
	 */
	private CollectionManager() {
		scheduler = new JMXManagedScheduler(SCHEDULER_OBJECT_NAME, "JMXCollectionScheduler", 1, false);
		JMXHelper.registerAutoCloseMBean(scheduler, SCHEDULER_OBJECT_NAME, "stop");
		executor = new JMXManagedThreadPool(EXECUTOR_OBJECT_NAME, "JMXCollectionExecutor", 2, 4, 128, 60000, 100, 99, false);
		executor.prestartAllCoreThreads();
		JMXHelper.registerAutoCloseMBean(executor, EXECUTOR_OBJECT_NAME, "stop");
		JMXHelper.registerAutoCloseMBean(this, OBJECT_NAME, "stop");
		sm = ScriptManager.getInstance();
		qm = QueryManager.getInstance();
	}
	
/*
	<customjmx>
		<scripts>
			<script name="foo" src="URL" ext="js" classpath="">
		</scripts>
	
		<queryexps>
		</queryexps>
		<mbeanserver server="" period="" metric-prefix="" metric-suffix="" tags="">
			<collect id="" pattern="" query="">  <!-- optionally override period="" metric-prefix="" metric-suffix="" tags="" -->
			
			</collect>
		</mbeanserver>
 	
 */
	/**
	 * Loads an XML collection configuration
	 * @param config The configuration node
	 */
	public void load(final Node config) {
		if(config==null) throw new IllegalArgumentException("The passed configuration node was null");
		executor.execute(new Runnable(){
			@Override
			public void run() {
				log.info("Loading CollectionManager...");
				final long start = System.currentTimeMillis();
				_load(config);
				final long elapsed = System.currentTimeMillis() - start;
				log.info("CollectionManager loaded in [{}] ms.", elapsed);
				
			}
		});
	}

	
	/**
	 * Loads an XML collection configuration
	 * @param config The configuration node
	 */
	private void _load(final Node config) {
		int scnt = 0;
		for(Node snode : XMLHelper.getChildNodesByName(config, "scripts", false)) {
			scnt += sm.load(snode);
		}
		log.info("Loaded [{}] Scripts", scnt);
		
		int qcnt = 0;
		for(Node qnode : XMLHelper.getChildNodesByName(config, "queryexps", false)) {
			try { 
				qcnt += qm.load(qnode);
			} catch (Exception ex) {
				log.error("Failed to load queries from QueryExp node [{}]", XMLHelper.renderNode(qnode), ex);
			}
		}
		log.info("Loaded [{}] QueryExp expressions", qcnt);
		// load collections
		qcnt = 0;
		for(Node mnode : XMLHelper.getChildNodesByName(config, "mbeanserver", false)) {
			JMXServerDefinition def = new JMXServerDefinition();
			try {
				def.server = XMLHelper.getAttributeByName(mnode, "server", "").trim();
				if(def.server.isEmpty()) throw new Exception("No server attribute defined");
				if(def.server.startsWith("service:jmx:")) {
					final String user = XMLHelper.getAttributeByName(mnode, "user", "").trim();
					final String password = XMLHelper.getAttributeByName(mnode, "password", "").trim();
					final Map<String, Object> env = new HashMap<String, Object>();
					if(!user.isEmpty()) {
						env.put(JMXConnector.CREDENTIALS, new String[]{user, password});						
					}
					final JMXServiceURL surl = new JMXServiceURL(def.server);
					def.connector = JMXConnectorFactory.newJMXConnector(surl, env);
					def.connector.addConnectionNotificationListener(this, null, def.server);
					log.info("Acquired connector for [{}]", def.server);
					final MBeanServerConnection msc = def.connector.getMBeanServerConnection();
					def.runtimeMBeanServer = RuntimeMBeanServerConnection.newInstance(msc);
				} else {
					MBeanServerConnection msc = JMXHelper.getLocalMBeanServer(def.server);
					def.runtimeMBeanServer = RuntimeMBeanServerConnection.newInstance(msc);					
				}
				def.collectionPeriod = XMLHelper.getAttributeByName(mnode, "period", 15);
				def.metricPrefix = XMLHelper.getAttributeByName(mnode, "metric-prefix", "");
				def.metricSuffix = XMLHelper.getAttributeByName(mnode, "metric-suffix", "");
				if(XMLHelper.hasAttribute(mnode, "tags")) {
					final Map<String, String> tmpTags = StringHelper.splitKeyValues(XMLHelper.getAttributeByName(mnode, "tags", ""));
					if(tmpTags!=null && !tmpTags.isEmpty()) {						
						def.baseTags= tmpTags;
					}				
				}
				serverDefinitions.put(def.server, def);
				for(Node collectNode: XMLHelper.getChildNodesByName(mnode, "collect", false)) {
					final CollectionDefinition cd = new CollectionDefinition(def, collectNode);
					def.collectionDefinitions.put(cd.id, cd);
				}
				log.info("Created Server Definition for [{}]", def.server);
			} catch (Exception ex) {
				log.error("Failed to create mbeanserver configuration [{}]", XMLHelper.renderNode(mnode), ex);				
			}
		}
	}
	
	/**
	 * Stops this service, deallocating all resources
	 */
	public void stop() {
		// TODO
	}
	
	/**
	 * <p>Title: JMXServerDefinition</p>
	 * <p>Description: Contains the definition of an MBeanServer connection and default JMX collection parameters</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionManager.JMXServerDefinition</code></p>
	 */
	public class JMXServerDefinition {
		/** The server name */
		private String server = null;
		/** The MBeanServer */
		private RuntimeMBeanServerConnection runtimeMBeanServer = null;
		/** The JMXConnector used to get the MBeanServer connection */
		private JMXConnector connector = null;
		/** The collection period in seconds */
		private int collectionPeriod = 15;
		/** The metric prefix prepended to all trace metric names */
		private String metricPrefix = "";
		/** The metric suffox appended to all trace metric names */
		private String metricSuffix = "";
		/** The default base metric tags */
		private Map<String, String> baseTags = Collections.emptyMap();
		/** A map of the collection definitions for this server */
		private final Map<String, CollectionDefinition> collectionDefinitions = new ConcurrentHashMap<String, CollectionDefinition>();
		/**
		 * Returns the server name
		 * @return the server
		 */
		public String getServer() {
			return server;
		}
		/**
		 * Returns the MBeanServer
		 * @return the runtimeMBeanServer
		 */
		public RuntimeMBeanServerConnection getRuntimeMBeanServer() {
			return runtimeMBeanServer;
		}
		/**
		 * Returns the connector
		 * @return the connector
		 */
		public JMXConnector getConnector() {
			return connector;
		}
		/**
		 * Returns the collection period in seconds
		 * @return the collectionPeriod
		 */
		public int getCollectionPeriod() {
			return collectionPeriod;
		}
		/**
		 * Returns the metric prefix
		 * @return the metricPrefix
		 */
		public String getMetricPrefix() {
			return metricPrefix;
		}
		/**
		 * Returns the metric suffix
		 * @return the metricSuffix
		 */
		public String getMetricSuffix() {
			return metricSuffix;
		}
		/**
		 * Returns 
		 * @return the baseTags
		 */
		public Map<String, String> getBaseTags() {
			return baseTags;
		}
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.NotificationListener#handleNotification(javax.management.Notification, java.lang.Object)
	 */
	@Override
	public void handleNotification(final Notification notif, final Object handback) {
		final ConnectionNotification cn = ConnectionNotification.TYPE2ENUM.get(notif.getType());
		switch(cn) {
		case CLOSED:
			break;
		case FAILED:
			break;
		case NOTIFS_LOST:
			break;
		case OPENED:
			break;
		default:
			break;
			
		}
		
	}

}
