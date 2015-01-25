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

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.JMX;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.Notification;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;

import org.cliffc.high_scale_lib.NonBlockingHashMap;
import org.cliffc.high_scale_lib.NonBlockingHashSet;
import org.jboss.netty.util.Timeout;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Timer;
import com.codahale.metrics.Timer.Context;
import com.heliosapm.opentsdb.client.name.AgentName;
import com.heliosapm.opentsdb.client.opentsdb.Threading;

/**
 * <p>Title: BaseMBeanObserver</p>
 * <p>Description: Base abstract class for MBeanObservers.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.BaseMBeanObserver</code></p>
 */

public abstract class BaseMBeanObserver implements MetricSet, NotificationListener, NotificationFilter, Runnable {
	/** The MBeanServerConnection to the MBeanServer to observe */
	protected RuntimeMBeanServerConnection mbs = null;
	/** The metrics created by this observer */
	protected final NonBlockingHashMap<String, Metric> metrics = new NonBlockingHashMap<String, Metric>(); 
	/** The jmx ObjectName pattern to collect from */
	protected final ObjectName objectName;
	/** The resolved ObjectNames to collect from */
	protected final Set<ObjectName> objectNames = new NonBlockingHashSet<ObjectName>();
	/** A long delta tracker */
	protected final NonBlockingHashMap<String, long[]> longDeltas = new NonBlockingHashMap<String, long[]>();
	/** The remote MBeanServer agent name finder */
	protected RemoteMBeanServerAgentNameFinder agentNameFinder = null;
	/** The iface to the remote MBean to generate a proxy with */
	private Class<?> mBeanIface = null;	
	/** The agent host name and app name */
	protected final String[] names = new String[2];
	/** The number of collection failures */
	protected final AtomicLong collectionFailures = new AtomicLong(0);
	/** A timer to gather collection times */
	protected final Timer timer = new Timer();	
	/** The refresh task scheduler handle */
	protected Timeout timerHandle = null;
	
	/** The laych to prevent metrics from the same collector to be sampled with different period readings */
	protected final ResettingCountDownLatch latch;
	
	/** The frequency of the observer observations in ms. */
	private long period = 15000;
	/** The timeout on observer refreshes in ms. */
	private long timeout = 2000;
	/** The attribute names to collect */
	private final String[] collectionAttrNames;
	/** Empty metric map const  */
	protected static final Map<String, Metric> EMPTY_METRIC_MAP = Collections.unmodifiableMap(new HashMap<String, Metric>(0));

	
	
	BaseMBeanObserver(final MBeanObserverBuilder builder, final String...collectionAttrNames) {
		MBeanServerConnection tmpMbs = builder.getMBeanServerConnection();
		JMXConnector tmpJmxConn = builder.getJmxConnector();
		mbs = tmpMbs==null ? RuntimeMBeanServerConnection.newInstance(tmpJmxConn) : RuntimeMBeanServerConnection.newInstance(tmpMbs);		
		this.objectName = builder.getTarget();
		if(this.objectName.isPattern()) {
			objectNames.addAll(mbs.queryNames(this.objectName, null));
		} else {
			objectNames.add(this.objectName);
		}
		this.collectionAttrNames = collectionAttrNames;
		period = TimeUnit.MILLISECONDS.convert(builder.getPeriod(), builder.getUnit());
		timeout = TimeUnit.MILLISECONDS.convert(builder.getTimeout(), builder.getTimeoutUnit());
		latch = ResettingCountDownLatch.newInstance(collectionAttrNames.length);
		agentNameFinder = builder.getNameFinder();
		setAgentNameTags();
		if(period > 0) {
			timerHandle = Threading.getInstance().schedule(this, period);
		}
		
	}
	
	public void run() {
		final Context ctx = this.timer.time();
		try {
			// implement timeout here.
			refresh();
			ctx.stop();			
		} catch (Exception ex) {
			collectionFailures.incrementAndGet();
		}
	}
	
	/**
	 * Refreshes the observation.
	 * Override to implement a different observation refresh.
	 * 
	 */
	protected void refresh() {
		Map<ObjectName, Map<String, Object>> attrMaps = new HashMap<ObjectName, Map<String, Object>>(objectNames.size());
		for(ObjectName on: objectNames) {
			attrMaps.put(on, getAttributes(on, mbs, collectionAttrNames));
		}
		try {
			latch.await(500, TimeUnit.MILLISECONDS);
			acceptData(attrMaps);
		} catch (Exception ex) {
			// what to do here ?
		}
	}
	

	protected abstract void acceptData(final Map<ObjectName, Map<String, Object>> attrMaps);
	
	
	/**
	 * Attempts to assign a host and app name from the delegate MBeanServerConnection.
	 * If the delegate is local, this has already been done and the names can be acquired from {@link AgentName}.
	 * Otherwise, we use some of the same techniques {@link AgentName} does, but remotely.
	 * @return The agent tag string in the format <b><code>host=[host-name],app=[app-name]</code></b>.
	 */
	protected String getAgentNameTags() {		
		return "host=" + names[0] + ",app=" + names[1];  
	}
	
	/**
	 * Sets the agent name tags 
	 */
	protected void setAgentNameTags() {
		final String[] names = agentNameFinder.getRemoteAgentName(mbs);
		this.names[0] = names[0];
		this.names[1] = names[1];
	}
	
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
	 * Builds an MBean invocation proxy
	 * @param objectName The target ObjectName
	 * @return the proxy
	 */
	protected <T> T newProxy(final ObjectName objectName) {
		if(mBeanIface==null) throw new IllegalStateException("Cannot generate a proxy. No MBeanIface has been provided");
		@SuppressWarnings("unchecked")
		final Class<T> clazz = (Class<T>)mBeanIface;
		final boolean notificationBroadcaster = mbs.isInstanceOf(objectName, NotificationBroadcaster.class.getName());
		if(JMX.isMXBeanInterface(mBeanIface)) {			
			return JMX.newMXBeanProxy(mbs, objectName, clazz, notificationBroadcaster);
		} else {
			return MBeanServerInvocationHandler.newProxyInstance(mbs, objectName, clazz, notificationBroadcaster); 					
		}
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
	
	/**
	 * {@inheritDoc}
	 * @see com.codahale.metrics.MetricSet#getMetrics()
	 */
	@Override
	public Map<String, Metric> getMetrics() {
		return metrics;
	}
	
	/**
	 * Returns an array of the names of the attributes for the passed ObjectName reached through the passed mbean server connection
	 * @param objectName The mbean to get the attribute names for
	 * @param connection The connection to reach the mbean through. If null, uses the helios mbean server
	 * @return an array of strings
	 */
	public static String[] getAttributeNames(final ObjectName objectName, final MBeanServerConnection connection) {
		if(objectName==null) throw new IllegalArgumentException("The passed objectname was null", new Throwable());
		try {
			MBeanAttributeInfo[] infos = connection.getMBeanInfo(objectName).getAttributes();
			String[] names = new String[infos.length];
			for(int i = 0; i < infos.length; i++) {
				names[i] = infos[i].getName();
			}
			return names;
		} catch (Exception ex) {
			return new String[0];
		}
	}
	
	
	/**
	 * Returns a String->Object Map of the named attributes from the Mbean.
	 * @param on The object name of the MBean.
	 * @param server The MBeanServerConnection the MBean is registered in. If this is null, uses the helios mbean server
	 * @param attributes An array of attribute names to retrieve. If this is null or empty, retrieves all the names
	 * @return A name value map of the requested attributes.
	 */
	public static Map<String, Object> getAttributes(ObjectName on, MBeanServerConnection server, String...attributes) {
		try {
			if(attributes==null || attributes.length<1) {
				attributes = getAttributeNames(on, server);				
			}
			Map<String, Object> attrs = new HashMap<String, Object>(attributes.length);
			AttributeList attributeList = server.getAttributes(on, attributes);
			
			
			for(int i = 0; i < attributeList.size(); i++) {
				Attribute at = (Attribute)attributeList.get(i);
				attrs.put(at.getName(), at.getValue());
			}
			return attrs;
		} catch (Exception e) {
			throw new RuntimeException("Failed to getAttributes on [" + on + "]", e);
		}
	}
	

}
