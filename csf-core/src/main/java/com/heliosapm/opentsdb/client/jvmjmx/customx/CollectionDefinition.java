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

import static com.heliosapm.opentsdb.client.jvmjmx.customx.CollectorStatus.*;
import static com.heliosapm.opentsdb.client.logging.LogLevel.INFO;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
import com.heliosapm.opentsdb.client.logging.LogLevel;
import com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection;
import com.heliosapm.utils.jmx.JMXHelper;
import com.heliosapm.utils.lang.StringHelper;
import com.heliosapm.utils.unsafe.collections.ConcurrentLongSlidingWindow;
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
	/** The attribute names to retrieve from each polled MBean */
	protected final Set<String> polledAttributeNames = new HashSet<String>();
	/** The ObjectNames of known MBeans so far with the MBeanInfo for each */
	protected ConcurrentHashMap<ObjectName, Map<MBeanFeature, Map<String, MBeanFeatureInfo>>> knownMBeans = new ConcurrentHashMap<ObjectName, Map<MBeanFeature, Map<String, MBeanFeatureInfo>>>();
	/** The base tags to apply to all metrics traced by these collections */
	protected final Map<String, String> baseTags = Collections.synchronizedMap(new TreeMap<String, String>());	
	
	
	/** The status of this CollectionDefinition */
	protected final AtomicReference<CollectorStatus> status = new AtomicReference<CollectorStatus>(CREATED);
	/** The logging level of this CollectionDefinition */
	protected final AtomicReference<LogLevel> logLevel = new AtomicReference<LogLevel>(INFO);

	
	/** The last collected attribute values, keyed by the composite attribute name within a map keyed by ObjectName */
	protected final Map<ObjectName, Map<String, Object>> attributeValues = new LinkedHashMap<ObjectName, Map<String, Object>>();	
	/** The cummulative count of successful collections */
	private final AtomicLong successfulCollections = new AtomicLong(0L);
	/** The count of consecutive successful collections */
	private final AtomicLong consecutiveCollections = new AtomicLong(0L);
	/** The cummulative count of failed collections */
	private final AtomicLong failures = new AtomicLong(0L);
	/** The count of consecutive failed collections */
	private final AtomicLong consecutiveFailures = new AtomicLong(0L);
	/** The timestamp of the last successful collection */
	private final AtomicLong lastCollectionDate = new AtomicLong(0L);
	/** The timestamp of the last failed collection */
	private final AtomicLong lastFailedDate = new AtomicLong(0L);
	/** A sliding window of elapsed collection times */
	private final ConcurrentLongSlidingWindow collectionTimes = new ConcurrentLongSlidingWindow(100);
	
/*
			<collect id="" pattern="" query="" attrs="">  <!-- optionally override period="" metric-prefix="" metric-suffix="" tags="" -->
				<es>
					<mp v="" />   			<!-- metric name prepend -->
					<ma v="" />					<!-- metric name append -->
					<tag k="" v="" />		<!-- add tag -->
					<ftag k="" v="" />	<!-- force override tag -->
					<value v="">				<!-- return numeric -->
				</es>
			</collect>
	
 */
	
	

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
		} else if(XMLHelper.hasAttribute(jmxCollection, "atags")) {
			this.baseTags.putAll(sd.getBaseTags());
			this.baseTags.putAll(StringHelper.splitKeyValues(XMLHelper.getAttributeByName(jmxCollection, "atags", "")));			
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
		
		for(ObjectName on: this.server.queryNames(targetObjectName, query)) {
			final Set<String> attributeNames = ingest(on);
			if(attributeNames!=null && !attributeNames.isEmpty()) {
				polledAttributeNames.addAll(attributeNames);
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
		if(status.compareAndSet(SCHEDULED, COLLECTING)) {
			try {
				final Set<ObjectName> queriedObjectNames = server.queryNames(targetObjectName, query);
				final Map<ObjectName, Map<String, Object>> queriedData = new LinkedHashMap<ObjectName, Map<String, Object>>();
				final CollectionContextImpl cci = new CollectionContextImpl(queriedData);
				for(final ObjectName on: server.queryNames(targetObjectName, query)) {
					final Map<String, Object> attrMap = JMXHelper.getAttributes(on, server, polledAttributeNames.toArray(new String[polledAttributeNames.size()]));
					log.info("AttrMap: [{}]", attrMap);
					focus(server, on, attrMap);
//					for(Map.Entry<String, Object> entry: attrMap.entrySet()) {
//						focus(server, on, entry.getKey(), entry.getValue());
//					}
				}
				
			} catch (Exception ex) {
				log.warn("Collection failed", ex);
			} finally {
				status.set(SCHEDULED);
			}
		} else {
			log.warn("CollectionDefinition skipped. Status was not [{}], but was [{}]", SCHEDULED, status.get());
		}		
	}
	
	/**
	 * <p>Title: CollectionContextImpl</p>
	 * <p>Description: The CollectionContext impl.</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionDefinition.CollectionContextImpl</code></p>
	 */
	class CollectionContextImpl implements CollectionContext {
		/** The queried data */
		final Map<ObjectName, Map<String, Object>> queriedData;
		
		/** The collector expression supplied traces */
		final Map<String, Number> traces = new HashMap<String, Number>();
		
		final StringBuilder metricNameBuilder = new StringBuilder();
		
		/**
		 * Creates a new CollectionContextImpl
		 * @param queriedData The looked up results
		 */
		CollectionContextImpl(final Map<ObjectName, Map<String, Object>> queriedData) {
			this.queriedData = queriedData;
		}
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionContext#name()
		 */
		@Override
		public String name() {
			// TODO Auto-generated method stub
			return null;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionContext#value()
		 */
		@Override
		public Object value() {
			// TODO Auto-generated method stub
			return null;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionContext#objectName()
		 */
		@Override
		public ObjectName objectName() {
			// TODO Auto-generated method stub
			return null;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionContext#mbeanServer()
		 */
		@Override
		public RuntimeMBeanServerConnection mbeanServer() {
			// TODO Auto-generated method stub
			return null;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionContext#values()
		 */
		@Override
		public Map<String, Object> values() {
			// TODO Auto-generated method stub
			return null;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionContext#metaData()
		 */
		@Override
		public Map<MBeanFeature, Map<String, MBeanFeatureInfo>> metaData() {
			// TODO Auto-generated method stub
			return null;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionContext#allValues()
		 */
		@Override
		public Map<ObjectName, Map<String, Object>> allValues() {
			// TODO Auto-generated method stub
			return null;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionContext#allMetaData()
		 */
		@Override
		public Map<ObjectName, Map<MBeanFeature, Map<String, MBeanFeatureInfo>>> allMetaData() {
			// TODO Auto-generated method stub
			return null;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionContext#appendMetric(java.lang.String)
		 */
		@Override
		public void appendMetric(final String suffix) {
			if(suffix!=null) {
				final String s = suffix.trim();
				if(!s.isEmpty()) {
					metricNameBuilder.append(s);
				}
			}
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionContext#prependMetric(java.lang.String)
		 */
		@Override
		public void prependMetric(final String prefix) {
			if(prefix!=null) {
				final String s = prefix.trim();
				if(!s.isEmpty()) {
					metricNameBuilder.insert(0, s);
				}
			}			
		}
		

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionContext#tags()
		 */
		@Override
		public Map<String, String> tags() {
			// TODO Auto-generated method stub
			return null;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionContext#tag(java.lang.String, java.lang.String)
		 */
		@Override
		public Map<String, String> tag(String key, String value) {
			// TODO Auto-generated method stub
			return null;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionContext#forceTag(java.lang.String, java.lang.String)
		 */
		@Override
		public Map<String, String> forceTag(String key, String value) {
			// TODO Auto-generated method stub
			return null;
		}
		
	}
	
	/**
	 * Sets the focus of the data context
	 * @param rmbs The mbean server 
	 * @param objectName The attribute's parent ObjectName
	 * @param attrMap The attribute map of value keyed by the attribute name
	 * @return this data context
	 */
	public CollectionDefinition focus(final RuntimeMBeanServerConnection rmbs, final ObjectName objectName, final Map<String, Object> attrMap) {
		
		return this;
	}
	
	
	
	/**
	 * Ingests and indexes the MBeanInfo for the MBean with the passed objectName
	 * @param actualTarget The objectname of the MBean to ingest
	 * @return The attribute names
	 */
	protected Set<String> ingest(final ObjectName actualTarget) {
		Set<String> attributeNames = Collections.emptySet();
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
				if(mbf==MBeanFeature.ATTRIBUTE) {
					attributeNames = f2Map.keySet();
				}
			}
		}
		return attributeNames;
	}
	

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
