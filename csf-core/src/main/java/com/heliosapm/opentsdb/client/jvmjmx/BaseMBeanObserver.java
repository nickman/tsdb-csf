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

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;

import com.heliosapm.opentsdb.client.opentsdb.Constants;
import com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: BaseMBeanObserver</p>
 * <p>Description: The base MBean monitor implementation</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.BaseMBeanObserver</code></p>
 */

public class BaseMBeanObserver implements Closeable, NotificationListener, NotificationFilter, Runnable {
	/** The MBeanObserver defining what we're collecting */
	protected final MBeanObserver mbeanObserver;
	/** The MBeanServer we're connecting from */
	protected final RuntimeMBeanServerConnection mbs;
	/** The object names being monitored */
	protected final Map<ObjectName, String[]> objectNamesAttrs;
	/** The tags common to all metrics submitted from this observer */
	protected final Map<String, String> tags = new LinkedHashMap<String, String>();
	
	/** The attribute mask */
	protected int attributeMask = -1;
	/** The attribute names we're collecting */
	protected String[] attributeNames = null;
	
	/**
	 * Creates a new BaseMBeanObserver
	 * @param jmxConnector The JMXConnector that will supply an MBeanServerConnection
	 * @param mbeanObserver The MBeanObserver defining what we're collecting
	 * @param tags The tags common to all metrics submitted from this observer
	 */
	protected BaseMBeanObserver(final JMXConnector jmxConnector, final MBeanObserver mbeanObserver, final Map<String, String> tags) {
		this(RuntimeMBeanServerConnection.newInstance(jmxConnector), mbeanObserver, tags);
	}
	
	/**
	 * Creates a new BaseMBeanObserver
	 * @param mbeanServerConn The MBeanServerConnection to monitor
	 * @param mbeanObserver The MBeanObserver defining what we're collecting
	 * @param tags The tags common to all metrics submitted from this observer
	 */
	protected BaseMBeanObserver(final MBeanServerConnection mbeanServerConn, final MBeanObserver mbeanObserver, final Map<String, String> tags) {
		mbs = RuntimeMBeanServerConnection.newInstance(mbeanServerConn);
		this.mbeanObserver = mbeanObserver;
		if(tags!=null && !tags.isEmpty()) {
			for(Map.Entry<String, String> entry: tags.entrySet()) {
				String key = Util.clean(entry.getKey());
				String value = Util.clean(entry.getValue());
				if(key!=null && !key.isEmpty() && value!=null && !value.isEmpty()) {
					this.tags.put(key, value);
				}
			}
		}
		Set<ObjectName> objectNames = mbs.queryNames(mbeanObserver.objectName, null);
		objectNamesAttrs = new HashMap<ObjectName, String[]>(objectNames.size());
		for(ObjectName on: objectNames) {
			int attributeMask = mbeanObserver.getMaskFor(mbs.getMBeanInfo(on).getAttributes());
			objectNamesAttrs.put(on, mbeanObserver.getAttributeNames(attributeMask));
		}
		initializeAgentName();
	}
	
	/**
	 * If the target MBeanServer is not in-VM and either of the agent name tags has not been supplied,
	 * this is where we try to figure out what the missing tags should be to represent the remote endpoint.
	 */
	void initializeAgentName() {
		if(!mbs.isInVM() && (!tags.containsKey(Constants.APP_TAG) || !tags.containsKey(Constants.HOST_TAG))) {
			
		}		
	}
	
	/**
	 * {@inheritDoc}
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {

	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.NotificationFilter#isNotificationEnabled(javax.management.Notification)
	 */
	@Override
	public boolean isNotificationEnabled(final Notification notification) {
		return false;
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.NotificationListener#handleNotification(javax.management.Notification, java.lang.Object)
	 */
	@Override
	public void handleNotification(final Notification notification, final Object handback) {

	}

	/**
	 * {@inheritDoc}
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() throws IOException {

	}

}
