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

package com.heliosapm.opentsdb.client.jvmjmx;

import java.util.Map;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;

/**
 * <p>Title: ThreadingCollectorMBeanObserver</p>
 * <p>Description: MBeanObserver for the  Threading MXBean</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.ThreadingCollectorMBeanObserver</code></p>
 */

public class ThreadingCollectorMBeanObserver extends BaseMBeanObserver {

	/**
	 * Creates a new ThreadingCollectorMBeanObserver
	 * @param jmxConnector
	 * @param mbeanObserver
	 * @param tags
	 */
	public ThreadingCollectorMBeanObserver(JMXConnector jmxConnector,
			MBeanObserver mbeanObserver, Map<String, String> tags) {
		super(jmxConnector, mbeanObserver, tags);
		// TODO Auto-generated constructor stub
	}

	/**
	 * Creates a new ThreadingCollectorMBeanObserver
	 * @param mbeanServerConn
	 * @param mbeanObserver
	 * @param tags
	 */
	public ThreadingCollectorMBeanObserver(
			MBeanServerConnection mbeanServerConn, MBeanObserver mbeanObserver,
			Map<String, String> tags) {
		super(mbeanServerConn, mbeanObserver, tags);
		// TODO Auto-generated constructor stub
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.BaseMBeanObserver#accept(java.util.Map, long, long)
	 */
	@Override
	protected boolean accept(Map<ObjectName, Map<String, Object>> data,
			long currentTime, long elapsedTime) {
		// TODO Auto-generated method stub
		return false;
	}

}
