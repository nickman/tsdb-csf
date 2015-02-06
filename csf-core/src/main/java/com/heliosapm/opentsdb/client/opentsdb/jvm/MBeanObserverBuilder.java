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
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerFactory;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.remote.JMXAddressable;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.cliffc.high_scale_lib.NonBlockingHashMap;

import com.codahale.metrics.MetricSet;
import com.heliosapm.opentsdb.client.opentsdb.jmx.AddressableJMXConnector;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: MBeanObserverBuilder</p>
 * <p>Description: Fluent style builder for building MBeanObserver</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.MBeanObserverBuilder</code></p>
 */

public class MBeanObserverBuilder {
	/** The target MBeanServerConnection */
	private final MBeanServerConnection mbs;
	/** The JMXConnector that will supply the target MBeanServerConnection */
	private final JMXConnector jmxConnector;
	/** The JMXServiceURL used to create the JMXConnector that will supply the target MBeanServerConnection */
	private final JMXServiceURL jmxServiceURL;	
	/** The underlying  {@link MetricSet} implementation */
	private final Class<? extends MetricSet> metricSetImpl;
	
	/** The ObjectName of the target MBean to observe */
	private final ObjectName target;
	/** The provided host name to use */
	private String hostName = null;
	/** The provided app name to use */
	private String appName = null;	
	/** An agent name finder that will use the mbean server connection to discover the AgentName */
	private RemoteMBeanServerAgentNameFinder nameFinder = null;	
	/** The interface mapping to an MBean  */
	private Class<?> mBeanIface = null;
	
	/** The frequency of the observer observations */
	private long period = 15;
	/** The unit of the frequency of the observer observations */
	private TimeUnit unit = TimeUnit.SECONDS;
	/** The timeout on observer refreshes */
	private long timeout = 2000;
	/** The unit of the timeout on observer refreshes */
	private TimeUnit timeoutUnit = TimeUnit.MILLISECONDS;
	
	
	/** The known and default base mbean observer classes keyed by the ObjectName */
	private static Map<ObjectName, Class<? extends BaseMBeanObserver>> KNOWN_IMPLS = new NonBlockingHashMap<ObjectName, Class<? extends BaseMBeanObserver>>();;
	/** A map of BaseMBeanObserver implementation constructors keyed by the class */
	private static final Map<Class<? extends BaseMBeanObserver>, Constructor<? extends BaseMBeanObserver>> observerCtors = new ConcurrentHashMap<Class<? extends BaseMBeanObserver>, Constructor<? extends BaseMBeanObserver>>();

	
	static {
		KNOWN_IMPLS.put(Util.objectName(ManagementFactory.COMPILATION_MXBEAN_NAME), CompilationMBeanObserver.class);
	}
	
	/**
	 * Creates a new observer builder
	 * @param mbs The target MBeanServerConnection
	 * @param target The ObjectName of the target MBean to observe
	 * @param metricSetImpl The MetricSet provider class
	 * @return the initialized builder
	 */
	public static MBeanObserverBuilder newBuilder(final MBeanServerConnection mbs, final ObjectName target, final Class<? extends MetricSet> metricSetImpl) {
		if(mbs==null) throw new IllegalArgumentException("The passed MBeanServerConnection was null");
		if(target==null) throw new IllegalArgumentException("The passed target ObjectName was null");
		if(metricSetImpl==null) new IllegalArgumentException("The passed MetricSet class was null");
		return new MBeanObserverBuilder(mbs, target, metricSetImpl);
	}
	
	/**
	 * Creates a new observer builder
	 * @param jmxConnector The JMXConnector that will supply the target MBeanServerConnection
	 * @param target The ObjectName of the target MBean to observe
	 * @param metricSetImpl The MetricSet provider class
	 * @return the initialized builder
	 */
	public static MBeanObserverBuilder newBuilder(final JMXConnector jmxConnector, final ObjectName target, final Class<? extends MetricSet> metricSetImpl) {
		if(jmxConnector==null) throw new IllegalArgumentException("The passed JMXConnector was null");
		if(target==null) throw new IllegalArgumentException("The passed target ObjectName was null");
		if(metricSetImpl==null) new IllegalArgumentException("The passed MetricSet class was null");
		return new MBeanObserverBuilder(jmxConnector, target, metricSetImpl);
	}
	
	/**
	 * Creates a new observer builder
	 * @param jmxServiceURL The JMXServiceURL used to create the JMXConnector that will supply the target MBeanServerConnection
	 * @param target The ObjectName of the target MBean to observe
	 * @param metricSetImpl The MetricSet provider class
	 * @return the initialized builder
	 */
	public static MBeanObserverBuilder newBuilder(final JMXServiceURL jmxServiceURL, final ObjectName target, final Class<? extends MetricSet> metricSetImpl) {
		if(jmxServiceURL==null) throw new IllegalArgumentException("The passed JMXServiceURL was null");
		if(target==null) throw new IllegalArgumentException("The passed target ObjectName was null");
		if(metricSetImpl==null) new IllegalArgumentException("The passed MetricSet class was null");
		return new MBeanObserverBuilder(jmxServiceURL, target, metricSetImpl);
	}
	
	/**
	 * Creates a new MBeanObserverBuilder
	 * @param jmxServiceURL The JMXServiceURL used to create the JMXConnector that will supply the target MBeanServerConnection
	 * @param target The ObjectName of the target MBean to observe
	 * @param metricSetImpl The MetricSet provider class
	 * @param metricSetImpl 
	 */
	private MBeanObserverBuilder(final JMXServiceURL jmxServiceURL, final ObjectName target, final Class<? extends MetricSet> metricSetImpl) {
		this.jmxServiceURL = jmxServiceURL;
		try {
			JMXConnector tmpConnector = JMXConnectorFactory.newJMXConnector(jmxServiceURL, null);
			if(!JMXAddressable.class.isInstance(tmpConnector)) {
				jmxConnector = new AddressableJMXConnector(tmpConnector, jmxServiceURL);
			} else {
				jmxConnector = tmpConnector;
			}			
		} catch (Exception ex) {
			throw new RuntimeException("Failed to create JMXConnector from [" + jmxServiceURL + "]", ex);
		}
		mbs = null;
		this.target = target;
		this.metricSetImpl = metricSetImpl; 
	}
	
	
	/**
	 * Creates a new MBeanObserverBuilder
	 * @param mbs The target MBeanServerConnection
	 * @param target The ObjectName of the target MBean to observe
	 * @param metricSetImpl The MetricSet provider class
	 */
	private MBeanObserverBuilder(final MBeanServerConnection mbs, final ObjectName target, final Class<? extends MetricSet> metricSetImpl) {		
		this.mbs = mbs;
		jmxConnector = null;
		jmxServiceURL = null;
		this.target = target;
		this.metricSetImpl = metricSetImpl; 
	}
	
	/**
	 * Creates a new MBeanObserverBuilder
	 * @param jmxConnector The JMXConnector that will supply the target MBeanServerConnection
	 * @param target The ObjectName of the target MBean to observe
	 * @param metricSetImpl The MetricSet provider class
	 */
	private MBeanObserverBuilder(final JMXConnector jmxConnector, final ObjectName target, final Class<? extends MetricSet> metricSetImpl) {
		this.jmxConnector = jmxConnector;
		jmxServiceURL = JMXAddressable.class.isInstance(jmxConnector) ? ((JMXAddressable)jmxConnector).getAddress() : null;
		mbs = null;		
		this.target = target;
		this.metricSetImpl = metricSetImpl; 
	}
	
	
	public BaseMBeanObserver build() {
		/*
		 * Must have jmxConnector or MBeanServerConnection
		 * Must provide a host/app pair, or a nameFinder if remote. If not remote, will fall back on AgentName
		 * Must have metricSetImpl. If not a BaseMBeanObserver, then will use a DelegatingBaseMBeanObserver.
		 *   // ==========================
		 * MBeanIFace will allow seamless MBean access proxies to be generated.
		 */
		if(jmxConnector == null && mbs == null && jmxServiceURL==null) throw new RuntimeException("Neither a JMXConnector, a JMXServiceURL or a MBeanServerConnection was provided");
		if(hostName==null && appName==null) {
			if(nameFinder==null) {
				try {
					if(!isSameJVM(mbs!=null ? mbs : jmxConnector.getMBeanServerConnection())) {
						throw new RuntimeException("No host/app name pair or RemoteMBeanServerAgentNameFinder provided and target MBeanServer is remote");
					} else {
						nameFinder = DefaultMBeanServerAgentNameFinder.INSTANCE;
					}
				} catch (Exception ex) {
					throw new RuntimeException("Failed to test remote MBeanServerConnection", ex);					
				}
			}
		} else {
			nameFinder = new ProvidedMBeanServerAgentNameFinder(hostName, appName);
		}
		if(BaseMBeanObserver.class.isAssignableFrom(metricSetImpl)) {
			@SuppressWarnings("unchecked")
			Class<? extends BaseMBeanObserver> clazz = (Class<? extends BaseMBeanObserver>)metricSetImpl;
			Constructor<? extends BaseMBeanObserver> ctor = observerCtors.get(clazz);
			if(ctor==null) {
				synchronized(observerCtors) {
					ctor = observerCtors.get(clazz);
					if(ctor==null) {
						try {
							ctor = clazz.getDeclaredConstructor(MBeanObserverBuilder.class);
							observerCtors.put(clazz, ctor);
						} catch (Exception ex) {
							throw new RuntimeException("Failed to find ctor for [" + clazz.getSimpleName() + "]", ex);
						}
					}
				}
			}
			try {
				return ctor.newInstance(this);
			} catch (Exception ex) {
				throw new RuntimeException("Failed to instantiate an instance of [" + clazz.getSimpleName() + "]", ex);
			}
		} else {
			// FIXME: Need to figure out how delegating MetricSet will know what to do ....
			return null;
		}		
	}
	
	
	/**
	 * Determines if the passed MBeanServerConnection is attached to an in-jvm MBeanServer, or a remote MBeanServer.
	 * @param mbsc the MBeanServerConnection to test
	 * @return true if the passed MBeanServerConnection is attached to an in-jvm MBeanServer, false otherwise
	 */
	public static boolean isSameJVM(final MBeanServerConnection mbsc) {
		if(mbsc==null) throw new IllegalArgumentException("The passed MBeanServerConnection was null");
		final MBeanServer platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
		if(mbsc==platformMBeanServer) return true;
		final String UID = UUID.randomUUID().toString();
		final NotificationListener testNotifListener = new NotificationListener() {
			@Override
			public void handleNotification(Notification notification, Object handback) {				
			}
		};
		try {
			mbsc.addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, testNotifListener, null, UID);
			for(MBeanServer srvr: MBeanServerFactory.findMBeanServer(null)) {
				try {
					srvr.removeNotificationListener(MBeanServerDelegate.DELEGATE_NAME, testNotifListener, null, UID);
					return true;
				} catch (ListenerNotFoundException lnfe) {}
			}
			try {
				mbsc.removeNotificationListener(MBeanServerDelegate.DELEGATE_NAME, testNotifListener, null, UID);
			} catch (Exception ex) {}
			return false;
		} catch (Exception ex) {
			return false;
		}
	}
	

	/**
	 * Sets the provided host name
	 * @param hostName the hostName to set
	 * @return this builder
	 */
	public MBeanObserverBuilder hostName(final String hostName) {
		this.hostName = hostName;
		return this;
	}

	/**
	 * Sets the provided app name
	 * @param appName the appName to set
	 * @return this builder
	 */
	public MBeanObserverBuilder appName(final String appName) {
		this.appName = appName;
		return this;
	}

	/**
	 * Sets the AgentName finder instance
	 * @param nameFinder the nameFinder to set
	 * @return this builder
	 */
	public MBeanObserverBuilder nameFinder(final RemoteMBeanServerAgentNameFinder nameFinder) {
		this.nameFinder = nameFinder;
		return this;
	}

	/**
	 * Sets the target MBean local interface to create a proxy stub
	 * @param mBeanIface the mBeanIface to set
	 * @return this builder
	 */
	public MBeanObserverBuilder mBeanIface(final Class<?> mBeanIface) {
		this.mBeanIface = mBeanIface;
		return this;
	}


	/**
	 * Returns the provided MBeanServerConnection
	 * @return the provided MBeanServerConnection
	 */
	public MBeanServerConnection getMBeanServerConnection() {
		return mbs;
	}

	/**
	 * Returns the JMXConnector that will provide the MBeanServerConnection
	 * @return the jmxConnector
	 */
	public JMXConnector getJmxConnector() {
		return jmxConnector;
	}

	/**
	 * Returns the ObjectName of the target MBean
	 * @return the ObjectName of the target MBean
	 */
	public ObjectName getTarget() {
		return target;
	}

	/**
	 * Returns the provided host name of the remote MBean's AgentName
	 * @return the provided host name
	 */
	public String getHostName() {
		return hostName;
	}

	/**
	 * Returns the provided app name of the remote MBean's AgentName
	 * @return the provided app name
	 */
	public String getAppName() {
		return appName;
	}

	/**
	 * Returns the RemoteMBeanServerAgentNameFinder that will be used to find the AgentName of a remote MBeanServer
	 * @return the nameFinder the RemoteMBeanServerAgentNameFinder
	 */
	public RemoteMBeanServerAgentNameFinder getNameFinder() {
		return nameFinder;
	}

	/**
	 * Returns the interface that maps to the target MBean
	 * @return the mBeanIface
	 */
	public Class<?> getMBeanIface() {
		return mBeanIface;
	}

	/**
	 * Returns the underlying {@link MetricSet}
	 * @return the metricSetImpl
	 */
	public Class<? extends MetricSet> getMetricSetImpl() {
		return metricSetImpl;
	}

	/**
	 * Returns the observation period which is the fequency at which observers are refreshed
	 * @return the observation period
	 */
	public long getPeriod() {
		return period;
	}

	/**
	 * Sets the observation period which is the fequency at which observers are refreshed.
	 * If the period is < 1, no refresh will be called.
	 * @param period the period to set
	 * @return this builder
	 */
	public MBeanObserverBuilder period(final long period) {
		this.period = period;
		return this;
	}

	/**
	 * Returns the unit of the observation period
	 * @return the unit of the observation period
	 */
	public TimeUnit getUnit() {
		return unit;
	}

	/**
	 * Sets the unit of the observation period
	 * @param unit the unit to set
	 * @return this builder
	 */
	public MBeanObserverBuilder unit(final TimeUnit unit) {
		if(unit==null) throw new IllegalArgumentException("The passed unit was null");
		this.unit = unit;
		return this;
	}

	/**
	 * Returns the timeout on observer refreshes
	 * @return timeout on observer refreshes
	 */
	public long getTimeout() {
		return timeout;
	}

	/**
	 * Sets the timeout on observer refreshes
	 * @param timeout the timeout to set
	 * @return this builder
	 */
	public MBeanObserverBuilder timeout(final long timeout) {
		if(timeout < 1L) throw new IllegalArgumentException("The passed timeout [" + timeout + "] was invalid");
		this.timeout = timeout;
		return this;
	}

	/**
	 * Returns the unit of the timeout on observer refreshes
	 * @return the timeout unit
	 */
	public TimeUnit getTimeoutUnit() {
		return timeoutUnit;
	}

	/**
	 * Sets the unit of the timeout on observer refreshes
	 * @param timeoutUnit the timeout Unit to set
	 */
	public MBeanObserverBuilder timeoutUnit(TimeUnit timeoutUnit) {
		if(timeoutUnit==null) throw new IllegalArgumentException("The passed timeout unit was null");
		this.timeoutUnit = timeoutUnit;
		return this;
	}
	

}
