/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2007, Helios Development Group and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org. 
 *
 */
package com.heliosapm.opentsdb.client.opentsdb.jvm;

import java.lang.management.CompilationMXBean;
import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;

import org.cliffc.high_scale_lib.NonBlockingHashMap;
import org.cliffc.high_scale_lib.NonBlockingHashSet;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;

/**
 * <p>Title: BaseMBeanObserver</p>
 * <p>Description: Base abstract class for MBeanObservers.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.BaseMBeanObserver</code></p>
 */

public abstract class BaseMBeanObserver implements MetricSet, NotificationListener, NotificationFilter {
	/** The MBeanServerConnection to the MBeanServer to observe */
	protected RuntimeMBeanServerConnection mbs = null;
	/** The metrics created by this observer */
	protected final NonBlockingHashMap<String, Metric> metrics = new NonBlockingHashMap<String, Metric>(16); 
	/** The jmx ObjectName pattern to collect from */
	protected final ObjectName objectName;
	/** The resolved ObjectNames to collect from */
	protected final Set<ObjectName> objectNames = new NonBlockingHashSet<ObjectName>();
	/** A long delta tracker */
	protected final NonBlockingHashMap<String, long[]> longDeltas = new NonBlockingHashMap<String, long[]>(); 
	
	/**
	 * Creates a new BaseMBeanObserver
	 * @param connection The MBeanServerConnection to the MBeanServer to observe
	 * @param objectName The jmx ObjectName pattern to collect from
	 */
	protected BaseMBeanObserver(final MBeanServerConnection connection, final ObjectName objectName) {
		mbs = RuntimeMBeanServerConnection.newInstance(connection);
		this.objectName = objectName;
	}
	
	/**
	 * Creates a new BaseMBeanObserver
	 * @param jmxConnector The JMXConnector that supplies the MBeanServerConnection to the MBeanServer to observe
	 * @param objectName The jmx ObjectName pattern to collect from
	 */
	protected BaseMBeanObserver(final JMXConnector jmxConnector, final ObjectName objectName) {
		mbs = RuntimeMBeanServerConnection.newInstance(jmxConnector);
		this.objectName = objectName;
	}
	
	/**
	 * Creates a new BaseMBeanObserver. This is a convenience overload to avoid concretes
	 * from having to implement multiple ctors. Either connection or jmxConnector is expected to 
	 * be not null, and the other null.
	 * @param connection The MBeanServerConnection to the MBeanServer to observe
	 * @param jmxConnector The JMXConnector that supplies the MBeanServerConnection to the MBeanServer to observe
	 * @param objectName The jmx ObjectName pattern to collect from
	 */
	protected BaseMBeanObserver(final MBeanServerConnection connection,final JMXConnector jmxConnector, final ObjectName objectName) {
		mbs = connection==null ? RuntimeMBeanServerConnection.newInstance(jmxConnector) : RuntimeMBeanServerConnection.newInstance(connection); 
		this.objectName = objectName;
	}
	
	
	/**
	 * Collects from the observed MBean
	 */
	protected void collect() {
		try {
			
		} catch (Exception ex) {
			
		}
	}
	
	/**
	 * The collect procedure imple for Concrete observers
	 */
	protected abstract void doCollect();
	
	/**
	 * Computes a delta between the named passed sample and the prior sample for the same name.
	 * @param name The name of the delta
	 * @param sample The new sample
	 * @param defaultValue The value to return if there was no prior sample
	 * @return the computed delta which could be null
	 */
	protected Long delta(final String name, final long sample, final Long defaultValue) {
		long[] state = longDeltas.putIfAbsent(name, new long[]{sample});
		if(state==null) return defaultValue;
		return sample - state[0];
	}
	
	/**
	 * Computes a delta between the named passed sample and the prior sample for the same name.
	 * @param name The name of the delta
	 * @param sample The new sample
	 * @return the computed delta which could be null
	 */
	protected Long delta(final String name, final long sample) {
		return delta(name, sample, null);
	}

	/**
	 * Creates an MXBean proxy for the delegate
	 * @param objectName the name of a platform MXBean
	 * @param type the MXBean interface to be implemented by the proxy
	 * @return the MXBean proxy
	 */
	protected <T> T newPlatformMXBeanProxy(final String objectName, Class<T> type) {
		try {
			return ManagementFactory.newPlatformMXBeanProxy(mbs, objectName, type);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to create MXProxy for [" + objectName + "]", ex);
		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.codahale.metrics.MetricSet#getMetrics()
	 */
	@Override
	public Map<String, Metric> getMetrics() {
		return metrics;
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
	 * @see javax.management.NotificationFilter#isNotificationEnabled(javax.management.Notification)
	 */
	@Override
	public boolean isNotificationEnabled(final Notification notification) {
		return true;
	}

}
