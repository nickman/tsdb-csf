/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2015, Helios Development Group and individual contributors
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
package com.heliosapm.hub;

import java.io.Closeable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.management.InstanceAlreadyExistsException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;

import org.w3c.dom.Node;

import com.heliosapm.hub.JVMMatch.JMatch;
import com.heliosapm.opentsdb.client.jvmjmx.MBeanObserverSet;
import com.heliosapm.opentsdb.client.name.AgentName;
import com.heliosapm.opentsdb.client.opentsdb.Constants;
import com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection;
import com.heliosapm.shorthand.attach.vm.VirtualMachine;
import com.heliosapm.shorthand.attach.vm.VirtualMachineDescriptor;
import com.heliosapm.utils.collections.FluentMap;
import com.heliosapm.utils.io.CloseableService;
import com.heliosapm.utils.jmx.JMXHelper;
import com.sun.jdmk.remote.cascading.CascadingService;

/**
 * <p>Title: MountedJVM</p>
 * <p>Description: Represents the critical components supporting a mounted remote JVM</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.hub.MountedJVM</code></p>
 */

public class MountedJVM implements Closeable, NotificationListener, NotificationFilter, MountedJVMMBean {
	private final VirtualMachineDescriptor vmd;
	private final VirtualMachine vm;
	private final JMXConnector jmxConnector;
	private final JMXServiceURL jmxUrl;
	private final String id;
	private final String displayName;
	private final MBeanServerConnection mbeanServerConnection;
	private final RuntimeMBeanServerConnection mbeanServer;
	private final String mbeanServerId;
	private final Set<String> mountPointIds = new HashSet<String>();
	private final CascadingService cascade;
	private final Map<String, MountedJVM> mounted;
	private String hostName;
	private String appId;
	private final LinkedHashSet<AppIdFinder> appIdFinders = new LinkedHashSet<AppIdFinder>(); 
	private MBeanObserverSet observerSet = null;
	private final Node platformConfigNode;
	private final JMatch jmatch;
	private ObjectName objectName = null;
	public static final String DEFAULT_MOUNT_PREFIX = "/local/";
	
	
	/**
	 * Creates a new MountedJVM
	 * @param vmd The virtual machine descriptor for the target mounted JVM
	 * @param cascade The cascading service
	 * @param mounted The mountpoint map to remove ourselves from 
	 * @param jmatch The configured jmatcher 
	 * @throws Exception thrown on any error 
	 */
	public MountedJVM(final VirtualMachineDescriptor vmd, final CascadingService cascade, final Map<String, MountedJVM> mounted, final JMatch jmatch) throws Exception {
		this.vmd = vmd;
		this.jmatch = jmatch;
		this.displayName = vmd.displayName();
		this.cascade = cascade;
		this.id = vmd.id();
		this.vm = vmd.provider().attachVirtualMachine(vmd);
		this.jmxUrl = vm.getJMXServiceURL();
		this.jmxConnector = vm.getJMXConnector();
		this.mbeanServerConnection = this.jmxConnector.getMBeanServerConnection();	
		this.platformConfigNode = jmatch.getPlatformNode();
		mbeanServer = RuntimeMBeanServerConnection.newInstance(this.mbeanServerConnection);
		mbeanServerId = mbeanServer.getMBeanServerId();
		this.mounted = mounted;
		hostName = AgentName.getInstance().getHostName();
		CloseableService.getInstance().register(this);
		Collections.addAll(this.appIdFinders, jmatch.getAppIdFinders());	
		mount(DEFAULT_MOUNT_PREFIX, id, false);
		findAppId();
	}
	
	/**
	 * Indicates if we're ready to schedule platform collection
	 * @return true if we're ready to schedule platform collection, false otherwise
	 */
	public boolean readyForPlatform() {
		if(appId==null) findAppId();
		return appId != null && platformConfigNode != null && objectName != null;
	}
	
	/**
	 * Mounts all mountpoints defined in the JMatch
	 * @param prefix The mountpoint prefix
	 * @param name The JVM name to use (hint: use ID to start with, then switch to the app id)
	 * @param remount true if remounting, false otherwise
	 */
	public void mount(final String prefix, final String name, final boolean remount) {
		if(remount) {
			for(String mp: mountPointIds) {
				try {
					cascade.unmount(mp);
				} catch (Exception ex) {/* No Op */}
			}
			mountPointIds.clear();
		}
		for(String mp: jmatch.getMountPoints()) {
			try {				
				addMountPoint(cascade.mount(jmxUrl, null, JMXHelper.objectName(mp), prefix + name));
			} catch (InstanceAlreadyExistsException iex) {
				/* No Op */
			} catch (Exception ex) {
				throw new RuntimeException("Failed to mount [" + mp + "] for VM [" + jmxUrl + "]", ex);
			}
		}
	}
	
	
	
	/**
	 * Attempts to find the target app id
	 * @return the app id or null if one was not found
	 */
	public String findAppId() {
		if(appId!=null) return null;
		for(AppIdFinder finder: appIdFinders) {
			try {
				final String a = finder.getAppId(this);
				if(a!=null) {
					appId = a.trim();
					mount(DEFAULT_MOUNT_PREFIX, appId, true);
					objectName = JMXHelper.objectName(getClass().getPackage().getName(), new Hashtable<String, String>(FluentMap.newMap(String.class, String.class)
							.fput("service", getClass().getSimpleName())
							.fput(Constants.HOST_TAG, hostName)
							.fput(Constants.APP_TAG, appId))
							
					);					
				}
			} catch (Exception x) {
				x.printStackTrace(System.err);
			}
		}
		return null;
	}
	
	/**
	 * Starts the collectors for this mounted JVM
	 * @return true if started, false otherwise
	 */
	public boolean enableCollectors() {
		if(appId==null || objectName==null) return false;
		if(observerSet != null ) {
			return true;
		}
		Map<String, String> tags = FluentMap.newMap(String.class, String.class)
				.fput(Constants.APP_TAG, appId)
				.fput(Constants.HOST_TAG, AgentName.getInstance().getHostName());
//				.fput("pid", id);
		observerSet =  MBeanObserverSet.build(mbeanServer, platformConfigNode, tags);
		JMXHelper.registerMBean(this, objectName);
		observerSet.start();
		log("----------------> Start [%s:%s]", hostName, appId);
		return true;
	}
	
	/**
	 * {@inheritDoc}
	 * @see javax.management.NotificationFilter#isNotificationEnabled(javax.management.Notification)
	 */
	@Override
	public boolean isNotificationEnabled(final Notification n) {
		return (n!=null && (n instanceof JMXConnectionNotification));
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.NotificationListener#handleNotification(javax.management.Notification, java.lang.Object)
	 */
	@Override
	public void handleNotification(Notification n, Object handback) {
		final String type = n.getType();
		if(JMXConnectionNotification.CLOSED.equals(type) || JMXConnectionNotification.FAILED.equals(type)) {
			this.close();
		}
		
	}
	
	
	public static void log(final Object fmt, final Object...args) {
		if(args.length==0) {
			System.out.println("[HubMain]" + fmt.toString());
		} else {
			System.out.printf("[HubMain]" + fmt.toString() + "\n", args);
		}
	}
	
	public static void loge(final Object fmt, final Object...args) {
		if(args.length==0) {
			System.err.println("[HubMain]" + fmt.toString());
		} else {
			System.err.printf("[HubMain]" + fmt.toString() + "\n", args);
		}
	}
	
	
	
	/**
	 * {@inheritDoc}
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() {
		log("Closing [%s]", id);
		if(observerSet!=null) {
			try { observerSet.stop(); } catch (Exception x) { /* No Op */}
		}
		final Iterator<String> iter = mountPointIds.iterator();
		while(iter.hasNext()) {
			final String mp = iter.next();
			try { cascade.unmount(mp); } catch (Exception ex) {/* No Op */}
			iter.remove();
		}
		try { jmxConnector.close(); } catch (Exception x) { /* No Op */ }
		try { vm.detach(); } catch (Exception x) { /* No Op */ }
		mounted.remove(id);
		if(objectName!=null) try { JMXHelper.unregisterMBean(objectName); } catch (Exception x) {/* No Op */}
		objectName = null;
		log("Closed [%s]", id);
	}
	
	
	
	
	/**
	 * @param listener
	 * @param filter
	 * @param handback
	 * @see javax.management.remote.JMXConnector#addConnectionNotificationListener(javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
	 */
	public void addConnectionNotificationListener(
			NotificationListener listener, NotificationFilter filter,
			Object handback) {
		jmxConnector.addConnectionNotificationListener(listener, filter,
				handback);
	}

	/**
	 * @param listener
	 * @throws ListenerNotFoundException
	 * @see javax.management.remote.JMXConnector#removeConnectionNotificationListener(javax.management.NotificationListener)
	 */
	public void removeConnectionNotificationListener(
			NotificationListener listener) throws ListenerNotFoundException {
		jmxConnector.removeConnectionNotificationListener(listener);
	}

	/**
	 * @param l
	 * @param f
	 * @param handback
	 * @throws ListenerNotFoundException
	 * @see javax.management.remote.JMXConnector#removeConnectionNotificationListener(javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
	 */
	public void removeConnectionNotificationListener(NotificationListener l,
			NotificationFilter f, Object handback)
			throws ListenerNotFoundException {
		jmxConnector.removeConnectionNotificationListener(l, f, handback);
	}


	/**
	 * Returns the 
	 * @return the vmd
	 */
	public VirtualMachineDescriptor getVmd() {
		return vmd;
	}


	/**
	 * Returns the 
	 * @return the vm
	 */
	public VirtualMachine getVm() {
		return vm;
	}


	/**
	 * Returns the 
	 * @return the jmxConnector
	 */
	public JMXConnector getJmxConnector() {
		return jmxConnector;
	}


	/**
	 * Returns the 
	 * @return the jmxUrl
	 */
	public JMXServiceURL getJmxUrl() {
		return jmxUrl;
	}


	/**
	 * Returns the 
	 * @return the id
	 */
	public String getId() {
		return id;
	}


	/**
	 * Returns the 
	 * @return the displayName
	 */
	public String getDisplayName() {
		return displayName;
	}


	/**
	 * Returns the RuntimeMBeanServerConnection
	 * @return the mbeanServer
	 */
	public RuntimeMBeanServerConnection getMbeanServer() {
		return mbeanServer;
	}

	/**
	 * @return
	 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection#getMBeanServerId()
	 */
	public String getMBeanServerId() {
		return mbeanServerId;
	}

	/**
	 * @return
	 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection#getDefaultDomain()
	 */
	public String getDefaultDomain() {
		return mbeanServer.getDefaultDomain();
	}

	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result
				+ ((mbeanServerId == null) ? 0 : mbeanServerId.hashCode());
		return result;
	}

	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof MountedJVM))
			return false;
		MountedJVM other = (MountedJVM) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (mbeanServerId == null) {
			if (other.mbeanServerId != null)
				return false;
		} else if (!mbeanServerId.equals(other.mbeanServerId))
			return false;
		return true;
	}

	/**
	 * Returns the 
	 * @return the mountPointId
	 */
	public String[] getMountPointIds() {
		return mountPointIds.toArray(new String[mountPointIds.size()]);
	}

	/**
	 * Sets the 
	 * @param mountPointId the mountPointId to set
	 */
	public void addMountPoint(final String mountPointId) {
		mountPointIds.add(mountPointId);
	}

	/**
	 * Returns the 
	 * @return the hostName
	 */
	public String getHostName() {
		return hostName;
	}

	/**
	 * Sets the 
	 * @param hostName the hostName to set
	 */
	public void setHostName(String hostName) {
		this.hostName = hostName;
	}

	/**
	 * Returns the 
	 * @return the appId
	 */
	public String getAppId() {
		return appId;
	}

	/**
	 * Sets the 
	 * @param appId the appId to set
	 */
	public void setAppId(String appId) {
		this.appId = appId;
	}

	/**
	 * Returns the 
	 * @return the mbeanServerId
	 */
	public String getMbeanServerId() {
		return mbeanServerId;
	}

	/**
	 * @return
	 * @see com.heliosapm.opentsdb.client.jvmjmx.MBeanObserverSetMBean#getEnabledOTMetrics()
	 */
	public Set<String> getEnabledOTMetrics() {
		return observerSet.getEnabledOTMetrics();
	}

	/**
	 * @return
	 * @see com.heliosapm.opentsdb.client.jvmjmx.MBeanObserverSetMBean#isActive()
	 */
	public boolean isActive() {
		return observerSet.isActive();
	}

	/**
	 * @return
	 * @see com.heliosapm.opentsdb.client.jvmjmx.MBeanObserverSetMBean#getCollectionPeriod()
	 */
	public long getCollectionPeriod() {
		return observerSet.getCollectionPeriod();
	}

	/**
	 * @return
	 * @see com.heliosapm.opentsdb.client.jvmjmx.MBeanObserverSetMBean#getCollectionPeriodUnit()
	 */
	public TimeUnit getCollectionPeriodUnit() {
		return observerSet.getCollectionPeriodUnit();
	}

	/**
	 * @return
	 * @see com.heliosapm.opentsdb.client.jvmjmx.MBeanObserverSetMBean#getEnabledObservers()
	 */
	public Set<String> getEnabledObservers() {
		return observerSet.getEnabledObservers();
	}

	/**
	 * @return
	 * @see com.heliosapm.opentsdb.client.jvmjmx.MBeanObserverSetMBean#getAverageCollectTime()
	 */
	public long getAverageCollectTime() {
		return observerSet.getAverageCollectTime();
	}

	/**
	 * 
	 * @see com.heliosapm.opentsdb.client.jvmjmx.MBeanObserverSetMBean#start()
	 */
	public void start() {
		observerSet.start();
	}

	/**
	 * 
	 * @see com.heliosapm.opentsdb.client.jvmjmx.MBeanObserverSetMBean#stop()
	 */
	public void stop() {
		observerSet.stop();
	}


}
