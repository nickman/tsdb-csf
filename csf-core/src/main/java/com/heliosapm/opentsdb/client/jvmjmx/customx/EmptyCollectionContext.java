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
 * <p>Title: EmptyCollectionContext</p>
 * <p>Description: An empty {@link CollectionContext} impl, useful for extending, mostly for testing, but in some cases, might be useful for extended impls. </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.EmptyCollectionContext</code></p>
 */

public class EmptyCollectionContext implements CollectionContext {

	/**
	 * Creates a new EmptyCollectionContext
	 */
	public EmptyCollectionContext() {
		// TODO Auto-generated constructor stub
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
	public void appendMetric(String suffix) {
		// TODO Auto-generated method stub

	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionContext#prependMetric(java.lang.String)
	 */
	@Override
	public void prependMetric(String prefix) {
		// TODO Auto-generated method stub

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
