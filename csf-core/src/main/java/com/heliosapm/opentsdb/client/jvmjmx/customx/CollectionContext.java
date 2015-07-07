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

import java.util.Map;

import javax.management.MBeanFeatureInfo;
import javax.management.ObjectName;

import com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection;

/**
 * <p>Title: CollectionContext</p>
 * <p>Description: Interface to expose a collection definition to an external collector</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionContext</code></p>
 */

public interface CollectionContext {

	
	/**
	 * Returns the focused attribute name
	 * @return the focused attribute name
	 */
	public String name();
	
	/**
	 * Returns the focused attribute value
	 * @return the focused attribute value
	 */
	public Object value();
	

	/**
	 * Returns the focused ObjectName, which will not be a wildcard
	 * @return the focused ObjectName
	 */
	public ObjectName objectName();
	
	/**
	 * Returns the focused MBeanServer
	 * @return the focused MBeanServer
	 */
	public RuntimeMBeanServerConnection mbeanServer();
	
	/**
	 * The current MBean's attribute value map
	 * @return a map of attribute values keyed by the attribute name
	 */
	public Map<String, Object> values();
	
	/**
	 * Returns a map of the current MBean's MBeanFeatureInfos keyed by the feature name, within a map keyed by the MBeanFeature enum member
	 * @return the meta data map
	 */
	public Map<MBeanFeature, Map<String, MBeanFeatureInfo>> metaData();
	
	
	/**
	 * Returns a map of values keyed by the MBean attribute name, within a map keyed by the ObjectName of the MBean
	 * @return the attribute value map
	 */
	public Map<ObjectName, Map<String, Object>> allValues();
	
	/**
	 * Returns a map of MBeanFeatureInfos keyed by the feature name, within a map keyed by the MBeanFeature enum member, 
	 * within a map keyed by the ObjectName of the MBean 
	 * @return the meta data map
	 */
	public Map<ObjectName, Map<MBeanFeature, Map<String, MBeanFeatureInfo>>> allMetaData();
	
	/**
	 * Appends a suffix to the metric name
	 * @param suffix The metric name suffix to append
	 */
	public void appendMetric(final String suffix);
	
	/**
	 * Prepends a prefix to the metric name
	 * @param prefix The metric name prefix to prepend
	 */
	public void prependMetric(final String prefix);
	
	/**
	 * Returns the current tags
	 * @return the current tags
	 */
	public Map<String, String> tags();
	
	/**
	 * Adds a tag and returns the current tags. Ignored if key is already present.
	 * @param key The tag key
	 * @param value The tag value
	 * @return the current tags
	 */
	public Map<String, String> tag(String key, String value);
	
	/**
	 * Adds a tag and returns the current tags. Overwrites if key is already present.
	 * @param key The tag key
	 * @param value The tag value
	 * @return the current tags
	 */
	public Map<String, String> forceTag(String key, String value);
	


}
