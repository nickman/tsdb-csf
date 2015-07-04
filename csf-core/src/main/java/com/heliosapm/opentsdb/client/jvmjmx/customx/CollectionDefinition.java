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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.MBeanFeatureInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.NotificationFilterSupport;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.QueryExp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

import com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionManager.JMXServerDefinition;
import com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection;
import com.heliosapm.utils.jmx.JMXHelper;
import com.heliosapm.utils.lang.StringHelper;
import com.heliosapm.utils.xml.XMLHelper;

/**
 * <p>Title: CollectionDefinition</p>
 * <p>Description: Defines and manages a JMX collection</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionDefinition</code></p>
 */

public class CollectionDefinition implements Runnable, CollectionDefinitionMBean, NotificationListener {
	/** Instance logger */
	protected final Logger log;
	/** The collection id */
	protected final String id;
	/** The collection period in seconds */
	protected int collectionPeriod = 15;
	
	/** The schedule handle */
	protected ScheduledFuture<Object> scheduleHandle = null;
	/** The configured MBeanServer JMX domain to collect from */
	protected String domain = null;	
	/** The MBeanServer wrapper */
	protected RuntimeMBeanServerConnection server = null;
	
	/** The ObjectName or pattern specifying which MBeans to collect from */
	protected ObjectName targetObjectName = null;
	/** The JMX Query further specifying which MBeans to collect from */
	protected QueryExp query = null;
	/** The ObjectNames of known MBeans so far with the MBeanInfo for each */
	protected ConcurrentHashMap<ObjectName, Map<MBeanFeature, Map<String, MBeanFeatureInfo>>> knownMBeans = new ConcurrentHashMap<ObjectName, Map<MBeanFeature, Map<String, MBeanFeatureInfo>>>();
	/** The base tags to apply to all metrics traced by these collections */
	protected final Map<String, String> baseTags = Collections.synchronizedMap(new TreeMap<String, String>());	
	/** Indicates if a collection is currently running */
	protected final AtomicBoolean running = new AtomicBoolean(false);
	/** The last collected attribute values, keyed by the composite attribute name within a map keyed by ObjectName */
	protected final Map<ObjectName, Map<String, Object>> attributeValues = new LinkedHashMap<ObjectName, Map<String, Object>>();
	
	/** The MBeanServerNotification filter */
	private static final NotificationFilterSupport NOTIF_FILTER = new NotificationFilterSupport();
	
	static {
		NOTIF_FILTER.enableType(MBeanServerNotification.REGISTRATION_NOTIFICATION);
		NOTIF_FILTER.enableType(MBeanServerNotification.UNREGISTRATION_NOTIFICATION);
	}
	
	/** The metric prefix */
	protected String metricPrefix = null;
	/** The metric suffix */
	protected String metricSuffix = null;
	
	/**
	 * Creates a new CollectionDefinition from a configuration node
	 * @param sd The target server definition
	 * @param jmxCollection The collection definition xml node
	 */
	public CollectionDefinition(final JMXServerDefinition sd, final Node jmxCollection) {
		id = XMLHelper.getAttributeByName(jmxCollection, "id", getClass().getSimpleName() + "@" + System.identityHashCode(this));
		log = LoggerFactory.getLogger(getClass().getName() + "." + id);
		final String nodeText = XMLHelper.renderNode(jmxCollection, true);
		this.server = sd.getRuntimeMBeanServer();
		domain = this.server.getDefaultDomain();
		if(XMLHelper.hasAttribute(jmxCollection, "tags")) {
			final Map<String, String> tmpTags = StringHelper.splitKeyValues(XMLHelper.getAttributeByName(jmxCollection, "tags", ""));
			if(tmpTags==null || tmpTags.isEmpty()) {
				this.baseTags.putAll(sd.getBaseTags());
			} else {
				this.baseTags.putAll(tmpTags);
			}				
		}
		collectionPeriod = XMLHelper.getAttributeByName(jmxCollection, "period", sd.getCollectionPeriod());
		this.metricPrefix = XMLHelper.getAttributeByName(jmxCollection, "metric-prefix", sd.getMetricPrefix());
		this.metricSuffix = XMLHelper.getAttributeByName(jmxCollection, "metric-suffix", sd.getMetricSuffix());		
		try {			
			targetObjectName = JMXHelper.objectName(XMLHelper.getAttributeByName(jmxCollection, "pattern", null));
		} catch (Exception ex) {
			if(targetObjectName==null) {				
				log.error("Invalid pattern in 'collect' element [{}]", nodeText, ex);
			}
		}
		
		final String q = XMLHelper.getAttributeByName(jmxCollection, "query", "").trim();
		if(q!=null) {
			query = QueryManager.getInstance().getQuery(q);
			if(query==null) {
				log.error("Invalid query id [{}] in 'collect' element [{}]", q, nodeText);
				throw new RuntimeException("Invalid Query Id [" + q + "] in 'collect' element [" + nodeText + "]");
			}
		}
		
		
		
	}
	
	/**
	 * <p>Called by the scheduler when a collection should be run</p>
	 * {@inheritDoc}
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {		
		if(running.compareAndSet(false, true)) {
			try {
				// collect
				
			} finally {
				running.compareAndSet(true, false);
			}
		}
	}
	
	/**
	 * Ingests and indexes the MBeanInfo for the MBean with the passed objectName
	 * @param actualTarget The objectname of the MBean to ingest
	 */
	protected void ingest(final ObjectName actualTarget) {
		if(actualTarget!=null && !knownMBeans.containsKey(actualTarget)) {
			final MBeanInfo minfo = server.getMBeanInfo(actualTarget);
			final Map<MBeanFeature, Map<String, MBeanFeatureInfo>> fmap = new EnumMap<MBeanFeature, Map<String, MBeanFeatureInfo>>(MBeanFeature.class);
			knownMBeans.put(actualTarget, fmap);
			for(MBeanFeature mbf: MBeanFeature.getCollectionFeatures()) {
				final MBeanFeatureInfo[] features = mbf.getFeatures(minfo);
				final Map<String, MBeanFeatureInfo> f2Map = new HashMap<String, MBeanFeatureInfo>(features.length); 
				fmap.put(mbf, f2Map);
				for(MBeanFeatureInfo f: features) {
					f2Map.put(f.getName(), f);
				}
			}
		}
	}
	
//	/**
//	 * Returns the focused ObjectName, which may be a wildcard
//	 * @return the focused ObjectName
//	 */
//	public ObjectName focusedObjectName();
//	
//	/**
//	 * Returns the focused MBeanServer
//	 * @return the focused MBeanServer
//	 */
//	public RuntimeMBeanServerConnection focusedMBeanServer();
//	
//	/**
//	 * Returns a map of values keyed by the MBean attribute name, within a map keyed by the ObjectName of the MBean
//	 * @return the attribute value map
//	 */
//	public Map<ObjectName, Map<String, Object>> attributeValues();
//	
//	/**
//	 * Returns a map of MBeanFeatureInfos keyed by the feature name, within a map keyed by the MBeanFeature enum member, 
//	 * within a map keyed by the ObjectName of the MBean 
//	 * @return the meta data map
//	 */
//	public Map<ObjectName, Map<MBeanFeature, Map<String, MBeanFeatureInfo>>> metaData();
//	
//	/**
//	 * Returns the configured key pattern includer (never null)
//	 * @return the configured key pattern includer
//	 */
//	public Pattern keyIncludePattern();
//	
//	/**
//	 * Returns the configured key pattern excluder (can be null)
//	 * @return the configured key pattern excluder
//	 */
//	public Pattern keyExcludePattern();
//	
//	/**
//	 * Returns the delimeter for the key pattern
//	 * @return the delimeter for the key pattern
//	 */
//	public String getKeyDelim();
//	
//	/**
//	 * Returns the current tags
//	 * @return the current tags
//	 */
//	public Map<String, String> tags();
//	
//	/**
//	 * Adds a tag and returns the current tags. Ignored if key is already present.
//	 * @param key The tag key
//	 * @param value The tag value
//	 * @return the current tags
//	 */
//	public Map<String, String> tag(String key, String value);
//	
//	/**
//	 * Adds a tag and returns the current tags. Overwrites if key is already present.
//	 * @param key The tag key
//	 * @param value The tag value
//	 * @return the current tags
//	 */
//	public Map<String, String> forceTag(String key, String value);
//	
//	/**
//	 * Sets the focus of the data context
//	 * @param rmbs The mbean server 
//	 * @param objectName The attribute's parent ObjectName
//	 * @param attributeName The attribute name
//	 * @param attributeValue The attribute value
//	 * @return this data context
//	 */
//	public CollectionDefinition focus(final RuntimeMBeanServerConnection rmbs, final ObjectName objectName, final String attributeName, final Object attributeValue);
//	
//	/**
//	 * Returns the focused attribute name
//	 * @return the focused attribute name
//	 */
//	public String focusedAttributeName();
//	
//	/**
//	 * Returns the focused attribute value
//	 * @return the focused attribute value
//	 */
//	public Object focusedAttributeValue();
//

	/**
	 * {@inheritDoc}
	 * @see javax.management.NotificationListener#handleNotification(javax.management.Notification, java.lang.Object)
	 */
	@Override
	public void handleNotification(final Notification notif, final Object handback) {
		final MBeanServerNotification msn = (MBeanServerNotification)notif;
		final ObjectName event = msn.getMBeanName();
		if(MBeanServerNotification.REGISTRATION_NOTIFICATION.equals(msn.getType())) {			
			if(!knownMBeans.keySet().contains(event) &&  targetObjectName.apply(event)) {
				try {
					if(query!=null && query.apply(event)) {
						ingest(event);
						log.info("Added new target MBean [{}]", event);
					}
				} catch (Exception ex) {
					log.error("Failed to handle notification [{}]", notif, ex);
				}
			}
		} else {
			if(knownMBeans.remove(event)!=null) {
				log.info("Target MBean was unregistered [{}]", event);
			}
		}		
	}
	
	
	
}
