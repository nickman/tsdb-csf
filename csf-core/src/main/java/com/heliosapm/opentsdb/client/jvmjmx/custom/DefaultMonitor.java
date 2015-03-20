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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import javax.management.MBeanInfo;
import javax.management.ObjectName;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.netty.util.Timeout;
import org.w3c.dom.Node;

import com.heliosapm.opentsdb.client.opentsdb.OTMetric;
import com.heliosapm.opentsdb.client.util.XMLHelper;

/**
 * <p>Title: DefaultMonitor</p>
 * <p>Description: The default custom mbean monitor implementation</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.custom.DefaultMonitor</code></p>
 */

public class DefaultMonitor {
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

	
	String objectNamePrefix = null;
	String objectNameSuffix = null;
	ObjectName tagetObjectName = null;
	
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
	
	
	final Map<ObjectName, MBeanInfo> beanInfos = new ConcurrentHashMap<ObjectName, MBeanInfo>();
	final Map<ObjectName, Map<String, OTMetric>> otMetrics = new ConcurrentHashMap<ObjectName, Map<String, OTMetric>>();

	/** The polling schedule handle */
	Timeout scheduleHandle = null;
	
	// MetricBuilder.metric(on).ext("classloading.loaded").tags(tags).build();
	
	/**
	 * Creates a new DefaultMonitor
	 * @param configNode The configuration node
	 * @param defaultPeriod The default polling period in seconds
	 * @param prefix The default object name prefix
	 */
	public DefaultMonitor(final Node configNode, final int defaultPeriod, final String prefix) {
		if(configNode==null) throw new IllegalArgumentException("The passed configuration node was null");
		objectNamePrefix = prefix;
		this.defaultPeriod = defaultPeriod;
		configure(configNode, true);

	}
	
	/**
	 * Introduces a new configuration
	 * @param configNode The XML configuration node 
	 * @param init true if this is the first call, false if it is a refresh
	 */
	void configure(final Node configNode, final boolean init) {
		if(configNode==null) throw new IllegalArgumentException("The passed configuration node was null");
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

		
	}
	
	
	
}
