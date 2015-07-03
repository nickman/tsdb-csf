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
 * <p>Title: ExpressionDataContextImpl</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.ExpressionDataContextImpl</code></p>
 */

public class ExpressionDataContextImpl implements ExpressionDataContext {

	/**
	 * Creates a new ExpressionDataContextImpl
	 */
	public ExpressionDataContextImpl() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.ExpressionDataContext#focusedObjectName()
	 */
	@Override
	public ObjectName focusedObjectName() {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.ExpressionDataContext#focusedMBeanServer()
	 */
	@Override
	public RuntimeMBeanServerConnection focusedMBeanServer() {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.ExpressionDataContext#attributeValues()
	 */
	@Override
	public Map<ObjectName, Map<String, Object>> attributeValues() {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.ExpressionDataContext#metaData()
	 */
	@Override
	public Map<ObjectName, Map<MBeanFeature, Map<String, MBeanFeatureInfo>>> metaData() {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.ExpressionDataContext#keyIncludePattern()
	 */
	@Override
	public Pattern keyIncludePattern() {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.ExpressionDataContext#keyExcludePattern()
	 */
	@Override
	public Pattern keyExcludePattern() {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.ExpressionDataContext#getKeyDelim()
	 */
	@Override
	public String getKeyDelim() {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.ExpressionDataContext#tags()
	 */
	@Override
	public Map<String, String> tags() {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.ExpressionDataContext#tag(java.lang.String, java.lang.String)
	 */
	@Override
	public Map<String, String> tag(String key, String value) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.ExpressionDataContext#forceTag(java.lang.String, java.lang.String)
	 */
	@Override
	public Map<String, String> forceTag(String key, String value) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.ExpressionDataContext#focus(com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection, javax.management.ObjectName, java.lang.String, java.lang.Object)
	 */
	@Override
	public ExpressionDataContext focus(RuntimeMBeanServerConnection rmbs, ObjectName objectName, String attributeName,
			Object attributeValue) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.ExpressionDataContext#focusedAttributeName()
	 */
	@Override
	public String focusedAttributeName() {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.ExpressionDataContext#focusedAttributeValue()
	 */
	@Override
	public Object focusedAttributeValue() {
		// TODO Auto-generated method stub
		return null;
	}

}
