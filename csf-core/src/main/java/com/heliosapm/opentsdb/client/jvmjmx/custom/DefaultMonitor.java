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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanFeatureInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.netty.util.Timeout;
import org.w3c.dom.Node;

import com.heliosapm.opentsdb.client.opentsdb.OTMetric;
import com.heliosapm.opentsdb.client.opentsdb.Threading;
import com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection;
import com.heliosapm.opentsdb.client.util.Util;
import com.heliosapm.utils.jmx.JMXHelper;
import com.heliosapm.utils.xml.XMLHelper;

/**
 * <p>Title: DefaultMonitor</p>
 * <p>Description: The default custom mbean monitor implementation</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.custom.DefaultMonitor</code></p>
 */

public class DefaultMonitor implements Runnable {
/*
			<monitor objectName="name=*">
				<attributes include="" exclude="" numericsonly="true" />
				<keys pattern="tag\.(.*)?" delim="" include="" exclude=""/>
			</monitor>

 */
	/** Instance logger */
	protected final Logger log = LogManager.getLogger(getClass());
	
	/** The collection period in seconds */
	final AtomicInteger collectionPeriod = new AtomicInteger(15);
	/** The mbeanserver where the mbeans this monitor will poll are registered */
	final RuntimeMBeanServerConnection rmbs;
	final MBeanServer mbs;
	
	
	String objectNamePrefix = null;
	String objectNameSuffix = null;
	ObjectName targetObjectName = null;
	
	boolean numericsOnly = true;
	String attrInclude = null;
	Pattern attrIncludePattern = null;
	String attrExclude = null;
	Pattern attrExcludePattern = null;
	
	String keyInclude = null;
	Pattern keyIncludePattern = null;
	String keyExclude = null;
	Pattern keyExcludePattern = null;
	String keyDelim = null;
	
	int defaultPeriod = 15;
	
	/** The ObjectNames to be polled  */
	final Set<ObjectName> polledObjectNames = new CopyOnWriteArraySet<ObjectName>();
	/** The attribute names to be polled for */
	final Set<String> polledAttributeNames = new CopyOnWriteArraySet<String>();
	
	final List<AttributeTracer> tracers = new ArrayList<AttributeTracer>();
	
	/** The map of attribute values that polling collects into, pre-created to avoid excess object creation */
	final Map<ObjectName, Map<String, Object>> attrValueMap = new HashMap<ObjectName, Map<String, Object>>();
	
	
	final Map<ObjectName, Map<MBeanFeature, Map<String, MBeanFeatureInfo>>> metaData = new ConcurrentHashMap<ObjectName, Map<MBeanFeature, Map<String, MBeanFeatureInfo>>>();
	
	final Map<ObjectName, MBeanInfo> beanInfos = new ConcurrentHashMap<ObjectName, MBeanInfo>();
	final Map<ObjectName, Map<String, OTMetric>> otMetrics = new ConcurrentHashMap<ObjectName, Map<String, OTMetric>>();
	
	/** Our data context */
	final ExpressionDataContext dataContext = new ExpressionDataContextImpl(); 
	

	/** The polling schedule handle */
	Timeout scheduleHandle = null;
	/** The long hash code for the monitor configuration node */
	private long configHashCode = -1L;
	
	public static final Pattern TOKEN_PATTERN = Pattern.compile("\\$(.*)?\\{(.*?)\\((\\d+([,-]\\d+)*)\\)\\}");
	
	/** The known numeric class names */
	static final Set<String> KNOWN_NUMERICS = new CopyOnWriteArraySet<String>(Arrays.asList(
			Byte.class.getName(), Byte.TYPE.getName(),
			Short.class.getName(), Short.TYPE.getName(),
			Integer.class.getName(), Integer.TYPE.getName(),
			Long.class.getName(), Long.TYPE.getName(),
			Float.class.getName(), Float.TYPE.getName(),
			Double.class.getName(), Double.TYPE.getName(),
			BigInteger.class.getName(), BigDecimal.class.getName(),
			AtomicInteger.class.getName(), AtomicLong.class.getName() 
	));
	
	/**
	 * Determines if the passed class is a numeric
	 * @param clazz The class to test
	 * @return true if the passed class is a numeric, false otherwise
	 */
	public static boolean isNumber(final Class<?> clazz) {
		if(clazz==null) return false;
		if(KNOWN_NUMERICS.contains(clazz.getName())) return true;
		if(Number.class.isAssignableFrom(clazz)) {
			KNOWN_NUMERICS.add(clazz.getName());
			return true;
		}
		return false;
	}

	/**
	 * Determines if the passed object is a numeric
	 * @param obj The object to test
	 * @return true if the passed object is a numeric, false otherwise
	 */
	public static boolean isNumber(final Object obj) {
		if(obj==null) return false;
		return isNumber(obj.getClass());
	}

	
	// MetricBuilder.metric(on).ext("classloading.loaded").tags(tags).build();
	
	/**
	 * Creates a new DefaultMonitor
	 * @param mbs The mbeanserver containing the mbeans this monitor will poll
	 * @param configNode The configuration node
	 * @param defaultPeriod The default polling period in seconds
	 * @param prefix The default object name prefix
	 */
	public DefaultMonitor(final MBeanServer mbs, final Node configNode, final int defaultPeriod, final String prefix) {
		if(configNode==null) throw new IllegalArgumentException("The passed configuration node was null");
		objectNamePrefix = prefix;
		this.mbs = mbs;
		this.rmbs = RuntimeMBeanServerConnection.newInstance(mbs);
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
			tracers.clear();
		}
		// =================== ObjectName suffix
		objectNameSuffix = XMLHelper.getAttributeByName(configNode, "objectName", null);
		
		// =================== Collection Period
		if(XMLHelper.hasAttribute(configNode, "freq")) {
			collectionPeriod.set(XMLHelper.getAttributeByName(configNode, "freq", defaultPeriod));
		}
		// =================== Attr Conf
		Node attrNode = XMLHelper.getChildNodeByName(configNode, "attributes");		
		if(attrNode!=null) {
			attrInclude = XMLHelper.getAttributeByName(attrNode, "include", ".*");
			attrExclude = XMLHelper.getAttributeByName(attrNode, "exclude", null);
			numericsOnly = XMLHelper.getAttributeByName(attrNode, "numericsonly", true);
		} else {
			attrInclude = ".*";
			attrExclude = null;
			numericsOnly = true;			
		}
		attrIncludePattern = Pattern.compile(attrInclude);
		attrExcludePattern = attrExclude==null ? null : Pattern.compile(attrExclude);
		// =================== Keys Conf
		// <keys pattern="tag\.(.*)?" delim="" include="" exclude=""/>
		Node keysNode = XMLHelper.getChildNodeByName(configNode, "keys");		
		if(keysNode!=null) {
			keyInclude = XMLHelper.getAttributeByName(keysNode, "include", ".*");
			keyExclude = XMLHelper.getAttributeByName(keysNode, "exclude", null);
		} else {
			keyInclude = ".*";
			keyExclude = null;
		}
		keyIncludePattern = Pattern.compile(keyInclude);
		keyExcludePattern = keyExclude==null ? null : Pattern.compile(keyExclude);
		// =================== Build ObjectName Pattern
		targetObjectName = buildObjectName(configNode);
		// =================== Get MBeanInfo
		updateMBeanMeta(configNode);
		//attrValueMap = new HashMap<ObjectName, Map<String, Object>>(metaData.size());
		
		// =================== Calc final attr list to get
		polledAttributeNames.addAll(getAttributeNames());
		// =================== Register for MBeanInfo change notif
		// TODO
		// =================== Compile metric names
		
		// =================== Build OTMetrics
		// =================== Update the hash code
		if(!init && evalHashCode!= -1L) {			
			configHashCode = evalHashCode;
		}
		// =================== Build the tracers
		for(Node tracerNode: XMLHelper.getChildNodesByName(configNode, "tracer", false)) {
			tracers.add(new AttributeTracer(tracerNode, this.dataContext));
		}
		
		// =================== Schedule
		scheduleHandle = Threading.getInstance().schedule(this, 1, collectionPeriod.get(), TimeUnit.SECONDS);
		log.info("Scheduled [{}] for a collection period of [{}] secs.");
	}
	
	/**
	 * Polls the target MBeans for the configured attribute values
	 */
	void poll() {
		for(ObjectName objectName: polledObjectNames) {
			Map<String, Object> values = JMXHelper.getAttributes(objectName, polledAttributeNames);
			attrValueMap.get(objectName).clear();
			attrValueMap.get(objectName).putAll(values);
		}
		for(ObjectName objectName: polledObjectNames) {
			
		}
		
	}
	
	
	/**
	 * Updates the MBean meta-data catalog
	 * @param configNode The monitor configuration node
	 */
	void updateMBeanMeta(final Node configNode) {
		for(final ObjectName on: rmbs.queryNames(targetObjectName, null)) {
			polledObjectNames.add(on);
			attrValueMap.put(on, new HashMap<String, Object>());
			final MBeanInfo info = rmbs.getMBeanInfo(on);
			beanInfos.put(on, info);
			Map<MBeanFeature, Map<String, MBeanFeatureInfo>> map = new EnumMap<MBeanFeature, Map<String, MBeanFeatureInfo>>(MBeanFeature.class);
			for(MBeanFeature mbf: MBeanFeature.values()) {
				final MBeanFeatureInfo[] finfos = mbf.getFeatures(info);
				final Map<String, MBeanFeatureInfo> featureInfos = new HashMap<String, MBeanFeatureInfo>();
				for(MBeanFeatureInfo finfo: finfos) {
					featureInfos.put(finfo.getName(), finfo);
				}
				map.put(mbf, featureInfos);
			}
			metaData.put(on, map);
		}
	}
	
	
	/**
	 * Builds the target ObjectName from the default suffix and configured ObjectName for the monitor
	 * @param configNode the configuration node The monitor configuration node
	 * @return the built ObjectName
	 */
	ObjectName buildObjectName(final Node configNode) {		
		if((objectNameSuffix==null || objectNameSuffix.isEmpty()) && (objectNamePrefix==null || objectNamePrefix.isEmpty())) {
			throw new RuntimeException("Failed to build ObjectName. Neither objectName or prefix was defined");
		}
		try {
			String pre = objectNamePrefix==null ? "" : objectNamePrefix;
			String suf = objectNameSuffix==null ? "" : objectNameSuffix;
			return new ObjectName(pre + suf);  // FIXME:  need to do some more defensive massaging here
		} catch (Exception ex) {
			throw new RuntimeException("Failed to build ObjectName. prefix:[" + objectNamePrefix + "], suffix:[" + objectNameSuffix + "]", ex);
		}
	}
	
	/**
	 * Returns the attribute names that should be polled for 
	 * @return a set of attribute names
	 */
	Set<String> getAttributeNames() {
		Set<String> attrNames = new HashSet<String>();
		for(Map.Entry<ObjectName, Map<MBeanFeature, Map<String, MBeanFeatureInfo>>> entry: metaData.entrySet()) {
			final ObjectName objectName = entry.getKey();
			Map<String, MBeanFeatureInfo> minfos = entry.getValue().get(MBeanFeature.ATTRIBUTE);
			for(Map.Entry<String, MBeanFeatureInfo> mentry: minfos.entrySet()) {
				String name = mentry.getKey();
				MBeanAttributeInfo minfo = (MBeanAttributeInfo)mentry.getValue();
				if(shouldIncludeAttribute(name, minfo.getType(), objectName)) {
					attrNames.add(name);
				}
			}
		}
		return attrNames;
	}
	
	/**
	 * Determines if the passed attribute name should be included in the MBean poll
	 * @param attrName The attribute's name
	 * @param className The attribute's type
	 * @param objectName The attribute's ObjectName
	 * @return true to include, false otherwise
	 */
	public boolean shouldIncludeAttribute(final String attrName, final String className, final ObjectName objectName) {
		final boolean isNumeric = !numericsOnly ? true : isNumeric(className, objectName, rmbs);
		final boolean matchesAttr = matchesAttr(attrName);
		final boolean matchesKeys = matchesKeys(attrName);
		return (matchesAttr && isNumeric) || matchesKeys;
	}
	
	/**
	 * Determines if the passed name matches the attribute include/exclude patterns
	 * @param name The name to test
	 * @return true for match, false otherwise
	 */
	public boolean matchesAttr(final String name) {
		return attrIncludePattern.matcher(name).matches()
				&& (attrExcludePattern==null || !attrExcludePattern.matcher(name).matches());
	}
	
	
	
	/**
	 * Determines if the passed name matches the key include/exclude patterns
	 * @param name The name to test
	 * @return true for match, false otherwise
	 */
	public boolean matchesKeys(final String name) {
		return (keyIncludePattern!=null && !keyIncludePattern.matcher(name).matches())
				&& (keyExcludePattern!=null && !keyExcludePattern.matcher(name).matches());
	}
	
	/**
	 * Determines if the named class is assignable as a numeric
	 * @param className The class name
	 * @param objectName The ObjectName in case we need the classloader
	 * @param mbs The MBeanServer in case we need the classloader
	 * @return true if a numeric type, false if not or indeterminate
	 */
	public static boolean isNumeric(final String className, final ObjectName objectName, final RuntimeMBeanServerConnection mbs) {
		if(KNOWN_NUMERICS.contains(className)) return true;
		Class<?> clazz = null;
		try {
			clazz = Class.forName(className);
		} catch (Exception x) {/* No Op */}
		if(clazz == null) {
			try {
				clazz = Class.forName(className, true, mbs.getClassLoaderFor(objectName));
			} catch (Exception x) {
				return false;
			}
		}
		final boolean isNum = Number.class.isAssignableFrom(clazz);
		if(isNum) {
			KNOWN_NUMERICS.add(className);
		}
		return isNum;
	}
	
	
		/**
		 * {@inheritDoc}
		 * @see java.lang.Runnable#run()
		 */
		@Override
		public void run() {
			// TODO Auto-generated method stub
			
		}
	
		/**
		 * Stops this monitor
		 */
		public void shutdown() {
			
		}
		
		
	
		/**
		 * <p>Title: ExpressionDataContextImpl</p>
		 * <p>Description: The expression data context for this monitor impl</p> 
		 * <p>Company: Helios Development Group LLC</p>
		 * @author Whitehead (nwhitehead AT heliosdev DOT org)
		 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.custom.DefaultMonitor.ExpressionDataContextImpl</code></p>
		 */
		private class ExpressionDataContextImpl implements ExpressionDataContext {
			/** The accumulated tags */
			protected final Map<String, String> tags = new TreeMap<String, String>();

			/** The current focused ObjectName */
			protected RuntimeMBeanServerConnection focusedMBeanServer = null;			
			/** The current focused ObjectName */
			protected ObjectName focusedObjectName = null;
			/** The current focused attribute name */
			protected String focusedAttributeName = null;
			/** The current focused attribute value */
			protected Object focusedAttributeValue = null;
			
			/**
			 * {@inheritDoc}
			 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext#focus(com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection, javax.management.ObjectName, java.lang.String, java.lang.Object)
			 */
			@Override
			public ExpressionDataContext focus(final RuntimeMBeanServerConnection rmbs, final ObjectName objectName, final String attributeName, final Object attributeValue) {
				focusedMBeanServer = rmbs;
				focusedObjectName = objectName;
				focusedAttributeName = attributeName;
				focusedAttributeValue = attributeValue;
				return this;
			}
			
			/**
			 * {@inheritDoc}
			 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext#focusedObjectName()
			 */
			@Override
			public ObjectName focusedObjectName() {
				return focusedObjectName;
			}
			
			/**
			 * {@inheritDoc}
			 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext#focusedAttributeName()
			 */
			@Override
			public String focusedAttributeName() {				
				return focusedAttributeName;
			}
			
			/**
			 * {@inheritDoc}
			 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext#focusedAttributeValue()
			 */
			@Override
			public Object focusedAttributeValue() {				
				return focusedAttributeValue;
			}

			/**
			 * {@inheritDoc}
			 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext#focusedMBeanServer()
			 */
			@Override
			public RuntimeMBeanServerConnection focusedMBeanServer() {
				return focusedMBeanServer !=null ? focusedMBeanServer : rmbs; 
			}

			/**
			 * {@inheritDoc}
			 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext#attributeValues()
			 */
			@Override
			public Map<ObjectName, Map<String, Object>> attributeValues() {
				return attrValueMap;
			}

			/**
			 * {@inheritDoc}
			 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext#metaData()
			 */
			@Override
			public Map<ObjectName, Map<MBeanFeature, Map<String, MBeanFeatureInfo>>> metaData() {
				return metaData;
			}

			/**
			 * {@inheritDoc}
			 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext#keyIncludePattern()
			 */
			@Override
			public Pattern keyIncludePattern() {
				return keyIncludePattern;
			}

			/**
			 * {@inheritDoc}
			 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext#keyExcludePattern()
			 */
			@Override
			public Pattern keyExcludePattern() {
				return keyExcludePattern;
			}
			
			/**
			 * {@inheritDoc}
			 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext#getKeyDelim()
			 */
			@Override
			public String getKeyDelim() {
				return keyDelim;
			}

			/**
			 * {@inheritDoc}
			 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext#tags()
			 */
			@Override
			public Map<String, String> tags() {
				return tags;
			}

			/**
			 * {@inheritDoc}
			 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext#tag(java.lang.String, java.lang.String)
			 */
			@Override
			public Map<String, String> tag(final String key, final String value) {
				if(key==null || key.trim().isEmpty()) throw new IllegalArgumentException("The passed key was null");
				String cleanedKey = Util.clean(key);
				if(!tags.containsKey(cleanedKey)) {
					tags.put(cleanedKey, Util.clean(value));
				}
				return tags;
			}
			
			/**
			 * {@inheritDoc}
			 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext#forceTag(java.lang.String, java.lang.String)
			 */
			@Override
			public Map<String, String> forceTag(String key, String value) {
				if(key==null || key.trim().isEmpty()) throw new IllegalArgumentException("The passed key was null");
				String cleanedKey = Util.clean(key);
				tags.put(cleanedKey, Util.clean(value));
				return tags;
			}
			
		}
	
}
