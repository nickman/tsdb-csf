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
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerDelegate;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.remote.JMXAddressable;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;

import org.cliffc.high_scale_lib.NonBlockingHashMap;

import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: RuntimeMBeanServerConnection</p>
 * <p>Description: An extended {@link MBeanServerConnection} wrapper to ditch the existing exception signatures
 * and replace them with runtime exceptions.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection</code></p>
 * TODO:
 * 	Handle connect/disconnect/error/dropped notifs events
 * 	Handle future listeners  (listen on new RuntimeMBeanServerConnection, connectivity events and direct/indirect listeners on MBeans)
 */

public class RuntimeMBeanServerConnection implements MBeanServerConnection, NotificationListener, NotificationFilter {
	/**  */
	private static final long serialVersionUID = -1629208561266062776L;

	/** A cache of RuntimeMBeanServerConnections keyed by MBeanServerId */
	private static final Map<String, RuntimeMBeanServerConnection> connections = new NonBlockingHashMap<String, RuntimeMBeanServerConnection>(32);
    /** Listeners to be registered on future MBeanServerConnection instances */
    private static final List<ListenerInfo> futureListeners = new CopyOnWriteArrayList<ListenerInfo>();

	
	/** The delegate MBeanServerConnection */
	protected final MBeanServerConnection delegate;
	/** The delegate MBeanServerConnection's JMXConnector */
	protected final JMXConnector jmxConnector;
	/** The JMXConnector's JMXServiceURL */
	protected final JMXServiceURL jmxServiceUrl;
	/** Listeners registered on this MBeanServer instance */
	protected final List<ListenerInfo> myListeners = new CopyOnWriteArrayList<ListenerInfo>();	
	/** The mbeanserver id */
	protected final String mbeanServerId;
	/** The mbeanserver's pid */
	protected final int pid;
	/** The mbeanserver's host from the RuntimeMXBean */
	protected final String hostName;
	/** Indicates if the MBeanServerConnection is actually the platform MBeanServer */
	protected final boolean localPlatform;
	
	
	
	
	
	
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
	
	
	/**
	 * Returns the MBeanServerId for the passed MBeanServerConnection
	 * @param delegate the delegate MBeanServerConnection
	 * @return the MBeanServerId 
	 */
	private static String getMBeanServerId(final MBeanServerConnection delegate) {
		if(delegate==null) throw new IllegalArgumentException("The passed MBeanServerConnection was null");
		try {
			return (String)delegate.getAttribute(MBeanServerDelegate.DELEGATE_NAME, "MBeanServerId");
		} catch (Exception x) {
			throw new RuntimeException("Failed to get MBeanServerId for [" + delegate + "]");
		}
	}
	
	/**
	 * Returns the Runtime Name for the passed MBeanServerConnection
	 * @param delegate the delegate MBeanServerConnection
	 * @return the Runtime Name 
	 */
	private static String getRuntimeName(final MBeanServerConnection delegate) {
		if(delegate==null) throw new IllegalArgumentException("The passed MBeanServerConnection was null");
		try {
			return (String)delegate.getAttribute(Util.objectName(ManagementFactory.RUNTIME_MXBEAN_NAME), "Name");
		} catch (Exception x) {
			throw new RuntimeException("Failed to get Runtime Name for [" + delegate + "]");
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
		localPlatform = ManagementFactory.getPlatformMBeanServer() == delegate;
		this.mbeanServerId = getMBeanServerId(delegate);
		String[] rtsplit = getRuntimeName(delegate).split("@");
		pid = Integer.parseInt(rtsplit[0]);
		hostName = rtsplit[1];
		this.jmxConnector = jmxConnector;
		if(this.jmxConnector != null) {
			this.jmxConnector.addConnectionNotificationListener(this, this, null);
			if(this.jmxConnector instanceof JMXAddressable) {
				jmxServiceUrl = ((JMXAddressable)this.jmxConnector).getAddress();
			} else {
				jmxServiceUrl = null;
			}
		} else {
			jmxServiceUrl = null;
		}
	}
	

	/**
	 * Indicates if the delegate is actually the platform MBeanServer
	 * @return true if the delegate is the platform MBeanServer, false otherwise
	 */
	public boolean isLocalPlatform() {
		return localPlatform;
	}
	
	/**
	 * Determines if the delegate is in this JVM
	 * @return true if the delegate is in this JVM, false otherwise
	 */
	public boolean isInVM() {
		return localPlatform || MBeanObserverBuilder.isSameJVM(delegate);
	}

	
	/**
	 * {@inheritDoc}
	 * @see javax.management.NotificationListener#handleNotification(javax.management.Notification, java.lang.Object)
	 */
	@Override
	public void handleNotification(final Notification notif, final Object handback) {
		// handle connect, disconnect, error and dropped notifs
		notifyFutures(notif, handback);
	}
	
	/**
	 * Propagates received notifications to future listeners
	 * @param notif The notification
	 * @param handback The handback
	 */
	protected void notifyFutures(final Notification notif, final Object handback) {
//		if(!listenerList.isEmpty()) {
//			for(final ListenerInfo li: listenerList) {
//				if(li.filter!=null && li.filter.isNotificationEnabled(notif)) {
//					Threading.getInstance().async(new Runnable(){
//						@Override
//						public void run() {
//							li.listener.handleNotification(notif, li.handback);
//						}
//					});
//				}
//			}			
//		}
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
	
	/**
	 * Returns the delegate MBeanServerConnection
	 * @return the delegate MBeanServerConnection
	 */
	public MBeanServerConnection getDelegate() {
		return delegate;
	}

	/**
	 * Returns the JMXConnector that provided the delegate MBeanServerConnection, or null if we don't know about the JMXConnector 
	 * @return the delegate's jmxConnector or null
	 */
	public JMXConnector getJmxConnector() {
		return jmxConnector;
	}

	/**
	 * Returns the JMXServiceURL of the JMXConnector that provided the delegate MBeanServerConnection, or null if we don't know about the JMXConnector
	 * @return the jmxServiceUrl of the delegate's jmxConnector or null
	 */
	public JMXServiceURL getJmxServiceUrl() {
		return jmxServiceUrl;
	}

	/**
	 * Returns the delegate's MBeanServerId
	 * @return the delegate's MBeanServerId
	 */
	public String getMBeanServerId() {
		return mbeanServerId;
	}
    
	/**
	 * Returns the delegate's Runtime host name
	 * @return the delegate's Runtime host name
	 */
	public String getHostName() {
		return hostName;
	}
	
	/**
	 * Returns the delegate's Runtime expressed PID
	 * @return the delegate's PID
	 */
	public int getPID() {
		return pid;
	}

	// ==============================================================================
	//   The wrapper methods
	// ==============================================================================
	
	
	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#createMBean(java.lang.String, javax.management.ObjectName)
	 */
	@Override
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
	@Override
	public ObjectInstance createMBean(final String className, final ObjectName name, final ObjectName loaderName) {
		try {
			return delegate.createMBean(className, name, loaderName);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#createMBean(java.lang.String, javax.management.ObjectName, java.lang.Object[], java.lang.String[])
	 */
	@Override
	public ObjectInstance createMBean(final String className, final ObjectName name, final Object[] params, final String[] signature) {
		try {
			return delegate.createMBean(className, name, params, signature);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#createMBean(java.lang.String, javax.management.ObjectName, javax.management.ObjectName, java.lang.Object[], java.lang.String[])
	 */
	@Override
	public ObjectInstance createMBean(final String className, final ObjectName name, final ObjectName loaderName, final Object[] params, final String[] signature) {
		try {
			return delegate.createMBean(className, name, loaderName, params, signature);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#unregisterMBean(javax.management.ObjectName)
	 */
	@Override
	public void unregisterMBean(final ObjectName name) {
		try {
			delegate.unregisterMBean(name);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
			
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#getObjectInstance(javax.management.ObjectName)
	 */
	@Override
	public ObjectInstance getObjectInstance(final ObjectName name) {
		try {
			return delegate.getObjectInstance(name);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#queryMBeans(javax.management.ObjectName, javax.management.QueryExp)
	 */
	@Override
	public Set<ObjectInstance> queryMBeans(final ObjectName name, final QueryExp query) throws IOException {
		try {
			return delegate.queryMBeans(name, query);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#queryNames(javax.management.ObjectName, javax.management.QueryExp)
	 */
	@Override
	public Set<ObjectName> queryNames(final ObjectName name, final QueryExp query) {
		try {
			return delegate.queryNames(name, query);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#isRegistered(javax.management.ObjectName)
	 */
	@Override
	public boolean isRegistered(final ObjectName name) {
		try {
			return delegate.isRegistered(name);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#getMBeanCount()
	 */
	@Override
	public Integer getMBeanCount() {
		try {
			return delegate.getMBeanCount();
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#getAttribute(javax.management.ObjectName, java.lang.String)
	 */
	@Override
	public Object getAttribute(final ObjectName name, final String attribute) {
		try {
			return delegate.getAttribute(name, attribute);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#getAttributes(javax.management.ObjectName, java.lang.String[])
	 */
	@Override
	public AttributeList getAttributes(final ObjectName name, final String[] attributes) {
		try {
			return delegate.getAttributes(name, attributes);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
	/**
	 * Returns the named attributes from the MBean registered under the passed ObjectName
	 * as a map of values keyed by the attribute name
	 * @param name The ObjectName of the target MBean
	 * @param attributes The attribute names to retrieve
	 * @return a map of attribute values
	 */
	public Map<String, Object> getAttributeMap(final ObjectName name, final String...attributes) {
		final AttributeList attrList = getAttributes(name, attributes);
		final Map<String, Object> map = new HashMap<String, Object>(attrList.size());
		for(Attribute attr: attrList.asList()) {
			map.put(attr.getName(), attr.getValue());
		}
		return map;
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#setAttribute(javax.management.ObjectName, javax.management.Attribute)
	 */
	@Override
	public void setAttribute(final ObjectName name, final Attribute attribute) {
		try {
			delegate.setAttribute(name, attribute);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#setAttributes(javax.management.ObjectName, javax.management.AttributeList)
	 */
	@Override
	public AttributeList setAttributes(final ObjectName name, final AttributeList attributes) {
		try {
			return delegate.setAttributes(name, attributes);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#invoke(javax.management.ObjectName, java.lang.String, java.lang.Object[], java.lang.String[])
	 */
	@Override
	public Object invoke(final ObjectName name, final String operationName, final Object[] params, final String[] signature) {
		try {
			return delegate.invoke(name, operationName, params, signature);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#getDefaultDomain()
	 */
	@Override
	public String getDefaultDomain() {
		try {
			return delegate.getDefaultDomain();
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#getDomains()
	 */
	@Override
	public String[] getDomains() {
		try {
			return delegate.getDomains();
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#addNotificationListener(javax.management.ObjectName, javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
	 */
	@Override
	public void addNotificationListener(final ObjectName name, final NotificationListener listener, final NotificationFilter filter, final Object handback) {
		try {
			delegate.addNotificationListener(name, listener, filter, handback);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#addNotificationListener(javax.management.ObjectName, javax.management.ObjectName, javax.management.NotificationFilter, java.lang.Object)
	 */
	@Override
	public void addNotificationListener(final ObjectName name, final ObjectName listener, final NotificationFilter filter, final Object handback) {
		try {
			delegate.addNotificationListener(name, listener, filter, handback);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#removeNotificationListener(javax.management.ObjectName, javax.management.ObjectName)
	 */
	@Override
	public void removeNotificationListener(final ObjectName name, final ObjectName listener) {
		try {
			delegate.removeNotificationListener(name, listener);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#removeNotificationListener(javax.management.ObjectName, javax.management.ObjectName, javax.management.NotificationFilter, java.lang.Object)
	 */
	@Override
	public void removeNotificationListener(final ObjectName name, final ObjectName listener, final NotificationFilter filter, final Object handback) {
		try {
			delegate.removeNotificationListener(name, listener, filter, handback);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#removeNotificationListener(javax.management.ObjectName, javax.management.NotificationListener)
	 */
	@Override
	public void removeNotificationListener(final ObjectName name, final NotificationListener listener) {
		try {
			delegate.removeNotificationListener(name, listener);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#removeNotificationListener(javax.management.ObjectName, javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
	 */
	@Override
	public void removeNotificationListener(final ObjectName name, final NotificationListener listener, final NotificationFilter filter, final Object handback) {
		try {
			delegate.removeNotificationListener(name, listener, filter, handback);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#getMBeanInfo(javax.management.ObjectName)
	 */
	@Override
	public MBeanInfo getMBeanInfo(final ObjectName name) {
		try {
			return delegate.getMBeanInfo(name);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanServerConnection#isInstanceOf(javax.management.ObjectName, java.lang.String)
	 */
	@Override
	public boolean isInstanceOf(final ObjectName name, final String className) {
		try {
			return delegate.isInstanceOf(name, className);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
	/**
	 * Removes all registered future listeners
	 */
	public static void removeAllFutureListeners() {
		futureListeners.clear();
	}

    /**
     * <p>Title: ListenerInfo</p>
     * <p>Description: Copied from {@link NotificationBroadcasterSupport} to provide static future listener registrations</p>
     * @author The original authors of {@link NotificationBroadcasterSupport}
     * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection.ListenerInfo</code></p>
     */
    static class ListenerInfo {
        NotificationListener listener;
        NotificationFilter filter;
        Object handback;

        ListenerInfo(NotificationListener listener,
                     NotificationFilter filter,
                     Object handback) {
            this.listener = listener;
            this.filter = filter;
            this.handback = handback;
        }

        @Override
		public boolean equals(Object o) {
            if (!(o instanceof ListenerInfo))
                return false;
            ListenerInfo li = (ListenerInfo) o;
            if (li instanceof WildcardListenerInfo)
                return (li.listener == listener);
            else
                return (li.listener == listener && li.filter == filter
                        && li.handback == handback);
        }
    }

	
    /**
     * <p>Title: WildcardListenerInfo</p>
     * <p>Description: Copied from {@link NotificationBroadcasterSupport} to provide static future listener registrations</p>
     * @author The original authors of {@link NotificationBroadcasterSupport}
     * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection.WildcardListenerInfo</code></p>
     */
    private static class WildcardListenerInfo extends ListenerInfo {
        WildcardListenerInfo(NotificationListener listener) {
            super(listener, null, null);
        }

        @Override
		public boolean equals(Object o) {
            assert (!(o instanceof WildcardListenerInfo));
            return o.equals(this);
        }
    }



    /**
     * Registers a notification listener to be registered on all future new RuntimeMBeanServerConnections
     * @param listeners The listener list to add to 
     * @param listener The listener to receive notifications.
     * @param filter The filter object. If filter is null, no
     * filtering will be performed before handling notifications.
     * @param handback An opaque object to be sent back to the
     * listener when a notification is emitted. This object cannot be
     * used by the Notification broadcaster object. It should be
     * resent unchanged with the notification to the listener.
     */
    public static void addNotificationListener(final List<ListenerInfo> listeners, final NotificationListener listener, final NotificationFilter filter, final Object handback) {
    	if (listener == null) {
    		throw new IllegalArgumentException ("Listener can't be null") ;
    	}
    	listeners.add(new ListenerInfo(listener, filter, handback));
    }

    /**
     * Removes all future listener registrations for the passed listener
     * @param listeners The listener list to add to 
     * @param listener The future listener to remove
     * @throws ListenerNotFoundException if the listener was not found
     */
    public static void removeNotificationListener(final List<ListenerInfo> listeners, final NotificationListener listener) throws ListenerNotFoundException {
    	ListenerInfo wildcard = new WildcardListenerInfo(listener);
    	boolean removed =
    			listeners.removeAll(Collections.singleton(wildcard));
    	if (!removed)
    		throw new ListenerNotFoundException("Listener not registered");
    }

    /**
     * Removes all future listener registrations for the passed listener and filter combo
     * @param listeners The listener list to remove from 
     * @param listener The listener to remove
     * @param filter The filter registered with the listener to remove 
     * @param handback The handback registered with the listener to remove
     * @throws ListenerNotFoundException thrown if the combination of listener, filter and handback are not found
     */
    public static void removeNotificationListener(final List<ListenerInfo> listeners, final NotificationListener listener,  final NotificationFilter filter,  final Object handback) throws ListenerNotFoundException {
    	ListenerInfo li = new ListenerInfo(listener, filter, handback);
    	boolean removed = listeners.remove(li);
    	if (!removed) {
    		throw new ListenerNotFoundException("Listener not registered " +
    				"(with this filter and " +
    				"handback)");
    		// or perhaps not registered at all
    	}
    }


}
