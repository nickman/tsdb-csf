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
import java.util.regex.Pattern;

import javax.management.MBeanFeatureInfo;
import javax.management.ObjectName;

import com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection;

/**
 * <p>Title: ExpressionDataContext</p>
 * <p>Description: The payload passed to token resolvers that they will lookup resolution values in.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.ExpressionDataContext</code></p>
 */

public interface ExpressionDataContext {
	/**
	 * Returns the focused ObjectName, which may be a wildcard
	 * @return the focused ObjectName
	 */
	public ObjectName focusedObjectName();
	
	/**
	 * Returns the focused MBeanServer
	 * @return the focused MBeanServer
	 */
	public RuntimeMBeanServerConnection focusedMBeanServer();
	
	/**
	 * Returns a map of values keyed by the MBean attribute name, within a map keyed by the ObjectName of the MBean
	 * @return the attribute value map
	 */
	public Map<ObjectName, Map<String, Object>> attributeValues();
	
	/**
	 * Returns a map of MBeanFeatureInfos keyed by the feature name, within a map keyed by the MBeanFeature enum member, 
	 * within a map keyed by the ObjectName of the MBean 
	 * @return the meta data map
	 */
	public Map<ObjectName, Map<MBeanFeature, Map<String, MBeanFeatureInfo>>> metaData();
	
	/**
	 * Returns the configured key pattern includer (never null)
	 * @return the configured key pattern includer
	 */
	public Pattern keyIncludePattern();
	
	/**
	 * Returns the configured key pattern excluder (can be null)
	 * @return the configured key pattern excluder
	 */
	public Pattern keyExcludePattern();
	
	/**
	 * Returns the delimeter for the key pattern
	 * @return the delimeter for the key pattern
	 */
	public String getKeyDelim();
	
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
	
	/**
	 * Sets the focus of the data context
	 * @param rmbs The mbean server 
	 * @param objectName The attribute's parent ObjectName
	 * @param attributeName The attribute name
	 * @param attributeValue The attribute value
	 * @return this data context
	 */
	public ExpressionDataContext focus(final RuntimeMBeanServerConnection rmbs, final ObjectName objectName, final String attributeName, final Object attributeValue);
	
	/**
	 * Returns the focused attribute name
	 * @return the focused attribute name
	 */
	public String focusedAttributeName();
	
	/**
	 * Returns the focused attribute value
	 * @return the focused attribute value
	 */
	public Object focusedAttributeValue();
	
	
	
}
