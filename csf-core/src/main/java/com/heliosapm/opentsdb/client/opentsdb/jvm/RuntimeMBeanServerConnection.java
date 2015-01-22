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

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.InvalidAttributeValueException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerDelegate;
import javax.management.NotCompliantMBeanException;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnector;

import org.cliffc.high_scale_lib.NonBlockingHashMap;

/**
 * <p>Title: RuntimeMBeanServerConnection</p>
 * <p>Description: An extended {@link MBeanServerConnection} wrapper to ditch the existing exception signatures
 * and replace them with runtime exceptions.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection</code></p>
 */

public class RuntimeMBeanServerConnection implements MBeanServerConnection, NotificationListener, NotificationFilter {
	/** A cache of RuntimeMBeanServerConnections keyed by MBeanServerId */
	private static final Map<String, RuntimeMBeanServerConnection> connections = new NonBlockingHashMap<String, RuntimeMBeanServerConnection>(32);
	
	/** The delegate MBeanServerConnection */
	protected final MBeanServerConnection delegate;
	/** The delegate MBeanServerConnection's JMXConnector */
	protected final JMXConnector jmxConnector;
	/** The mbeanserver id */
	protected final String mbeanServerId;
	
	
	/**
	 * Returns a RuntimeMBeanServerConnection for the passed MBeanServerConnection 
	 * @param delegate The delegate MBeanServerConnection
	 * @return the RuntimeMBeanServerConnection
	 */
	public static RuntimeMBeanServerConnection newInstance(final MBeanServerConnection delegate) {
		final String mbsId = getMBeanServerId(delegate);
		RuntimeMBeanServerConnection conn = connections.get(mbsId);
		if(conn==null) {
			synchronized(connections) {
				conn = connections.get(mbsId);
				if(conn==null) {
					conn = new RuntimeMBeanServerConnection(delegate);
					connections.put(mbsId, conn);
				}
			}
		}
		return conn;
	}
	
	/**
	 * Returns a RuntimeMBeanServerConnection for the MBeanServerConnection provided by the passed JMXConnector 
	 * @param jmxConnector The MBeanServerConnection provided by the passed JMXConnector
	 * @return the RuntimeMBeanServerConnection
	 */
	public static RuntimeMBeanServerConnection newInstance(final JMXConnector jmxConnector) {
		if(jmxConnector==null) throw new IllegalArgumentException("The passed jmxConnector was null");
		final MBeanServerConnection delegate;
		try {
			delegate = jmxConnector.getMBeanServerConnection();
			final String mbsId = getMBeanServerId(delegate);
			RuntimeMBeanServerConnection conn = connections.get(mbsId);
			if(conn==null) {
				synchronized(connections) {
					conn = connections.get(mbsId);
					if(conn==null) {
						conn = new RuntimeMBeanServerConnection(delegate, jmxConnector);
						connections.put(mbsId, conn);
					}
				}
			}
			return conn;			
		} catch (Exception ex) {
			throw new RuntimeException("Failed to get MBeanServerConnection from JMXConnector [" + jmxConnector + "]", ex);
		}
	}
	
	
	private static String getMBeanServerId(final MBeanServerConnection delegate) {
		if(delegate==null) throw new IllegalArgumentException("The passed MBeanServerConnection was null");
		try {
			return (String)delegate.getAttribute(MBeanServerDelegate.DELEGATE_NAME, "MBeanServerId");
		} catch (Exception x) {
			throw new RuntimeException("Failed to get MBeanServerId for [" + delegate + "]");
		}
	}
	
	/**
	 * Creates a new RuntimeMBeanServerConnection
	 * @param delegate The delegate MBeanServerConnection 
	 */
	private RuntimeMBeanServerConnection(final MBeanServerConnection delegate) {
		this(delegate, null);
	}
	
	/**
	 * Creates a new RuntimeMBeanServerConnection from a JMXConnector
	 * @param delegate The delegate MBeanServerConnection
	 * @param jmxConnector The JMXConnector that provided the MBeanServerConnection 
	 */
	private RuntimeMBeanServerConnection(final MBeanServerConnection delegate, final JMXConnector jmxConnector) {
		this.delegate = delegate;
		this.mbeanServerId = getMBeanServerId(delegate);
		this.jmxConnector = jmxConnector;
		if(this.jmxConnector != null) {
			this.jmxConnector.addConnectionNotificationListener(this, this, null);
		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see javax.management.NotificationListener#handleNotification(javax.management.Notification, java.lang.Object)
	 */
	@Override
	public void handleNotification(final Notification notif, final Object handback) {
		
	}
	
	/**
	 * {@inheritDoc}
	 * @see javax.management.NotificationFilter#isNotificationEnabled(javax.management.Notification)
	 */
	@Override
	public boolean isNotificationEnabled(final Notification notif) {	
		return (
			notif != null &&
			notif instanceof JMXConnectionNotification
		);
	}
	
	

	// ==============================================================================
	//   The wrapper methods
	// ==============================================================================
	
	
	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#createMBean(java.lang.String, javax.management.ObjectName)
	 */
	public ObjectInstance createMBean(final String className, final ObjectName name) {
		try {
			return delegate.createMBean(className, name);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#createMBean(java.lang.String, javax.management.ObjectName, javax.management.ObjectName)
	 */
	public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName) {
		try {
			return delegate.createMBean(className, name, loaderName);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public ObjectInstance createMBean(String className, ObjectName name,
			Object[] params, String[] signature) throws ReflectionException,
			InstanceAlreadyExistsException, MBeanRegistrationException,
			MBeanException, NotCompliantMBeanException, IOException {
		return delegate.createMBean(className, name, params, signature);
	}

	public ObjectInstance createMBean(String className, ObjectName name,
			ObjectName loaderName, Object[] params, String[] signature)
			throws ReflectionException, InstanceAlreadyExistsException,
			MBeanRegistrationException, MBeanException,
			NotCompliantMBeanException, InstanceNotFoundException, IOException {
		return delegate.createMBean(className, name, loaderName, params,
				signature);
	}

	public void unregisterMBean(ObjectName name)
			throws InstanceNotFoundException, MBeanRegistrationException,
			IOException {
		delegate.unregisterMBean(name);
	}

	public ObjectInstance getObjectInstance(ObjectName name)
			throws InstanceNotFoundException, IOException {
		return delegate.getObjectInstance(name);
	}

	public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query)
			throws IOException {
		return delegate.queryMBeans(name, query);
	}

	public Set<ObjectName> queryNames(ObjectName name, QueryExp query)
			throws IOException {
		return delegate.queryNames(name, query);
	}

	public boolean isRegistered(ObjectName name) throws IOException {
		return delegate.isRegistered(name);
	}

	public Integer getMBeanCount() throws IOException {
		return delegate.getMBeanCount();
	}

	public Object getAttribute(ObjectName name, String attribute)
			throws MBeanException, AttributeNotFoundException,
			InstanceNotFoundException, ReflectionException, IOException {
		return delegate.getAttribute(name, attribute);
	}

	public AttributeList getAttributes(ObjectName name, String[] attributes)
			throws InstanceNotFoundException, ReflectionException, IOException {
		return delegate.getAttributes(name, attributes);
	}

	public void setAttribute(ObjectName name, Attribute attribute)
			throws InstanceNotFoundException, AttributeNotFoundException,
			InvalidAttributeValueException, MBeanException,
			ReflectionException, IOException {
		delegate.setAttribute(name, attribute);
	}

	public AttributeList setAttributes(ObjectName name, AttributeList attributes)
			throws InstanceNotFoundException, ReflectionException, IOException {
		return delegate.setAttributes(name, attributes);
	}

	public Object invoke(ObjectName name, String operationName,
			Object[] params, String[] signature)
			throws InstanceNotFoundException, MBeanException,
			ReflectionException, IOException {
		return delegate.invoke(name, operationName, params, signature);
	}

	public String getDefaultDomain() throws IOException {
		return delegate.getDefaultDomain();
	}

	public String[] getDomains() throws IOException {
		return delegate.getDomains();
	}

	public void addNotificationListener(ObjectName name,
			NotificationListener listener, NotificationFilter filter,
			Object handback) throws InstanceNotFoundException, IOException {
		delegate.addNotificationListener(name, listener, filter, handback);
	}

	public void addNotificationListener(ObjectName name, ObjectName listener,
			NotificationFilter filter, Object handback)
			throws InstanceNotFoundException, IOException {
		delegate.addNotificationListener(name, listener, filter, handback);
	}

	public void removeNotificationListener(ObjectName name, ObjectName listener)
			throws InstanceNotFoundException, ListenerNotFoundException,
			IOException {
		delegate.removeNotificationListener(name, listener);
	}

	public void removeNotificationListener(ObjectName name,
			ObjectName listener, NotificationFilter filter, Object handback)
			throws InstanceNotFoundException, ListenerNotFoundException,
			IOException {
		delegate.removeNotificationListener(name, listener, filter, handback);
	}

	public void removeNotificationListener(ObjectName name,
			NotificationListener listener) throws InstanceNotFoundException,
			ListenerNotFoundException, IOException {
		delegate.removeNotificationListener(name, listener);
	}

	/**
	 * @param name
	 * @param listener
	 * @param filter
	 * @param handback
	 * @throws InstanceNotFoundException
	 * @throws ListenerNotFoundException
	 * @throws IOException
	 * @see javax.management.MBeanServerConnection#removeNotificationListener(javax.management.ObjectName, javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
	 */
	public void removeNotificationListener(ObjectName name,
			NotificationListener listener, NotificationFilter filter,
			Object handback) throws InstanceNotFoundException,
			ListenerNotFoundException, IOException {
		delegate.removeNotificationListener(name, listener, filter, handback);
	}

	/**
	 * @param name
	 * @return
	 * @throws InstanceNotFoundException
	 * @throws IntrospectionException
	 * @throws ReflectionException
	 * @throws IOException
	 * @see javax.management.MBeanServerConnection#getMBeanInfo(javax.management.ObjectName)
	 */
	public MBeanInfo getMBeanInfo(ObjectName name)
			throws InstanceNotFoundException, IntrospectionException,
			ReflectionException, IOException {
		return delegate.getMBeanInfo(name);
	}

	/**
	 * @param name
	 * @param className
	 * @return
	 * @throws InstanceNotFoundException
	 * @throws IOException
	 * @see javax.management.MBeanServerConnection#isInstanceOf(javax.management.ObjectName, java.lang.String)
	 */
	public boolean isInstanceOf(ObjectName name, String className)
			throws InstanceNotFoundException, IOException {
		return delegate.isInstanceOf(name, className);
	}


}
