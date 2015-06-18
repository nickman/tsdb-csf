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
package com.heliosapm.opentsdb.client.name;

import javax.management.AttributeChangeNotification;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;

import com.heliosapm.opentsdb.client.opentsdb.Constants;

/**
 * <p>Title: AgentNameMXBean</p>
 * <p>Description: MXBean interface for {@link AgentName}</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.name.AgentNameMXBean</code></p>
 */

public interface AgentNameMBean {
	
	/** Notification type for initial name and host assignment */
	public static final String NOTIF_ASSIGNED = "tsdb.agentname.assigned";
	/** Notification type for an AgentName app name change */
	public static final String NOTIF_APP_NAME_CHANGE = "tsdb.agentname.change.name";
	/** Notification type for an AgentName host change */
	public static final String NOTIF_HOST_NAME_CHANGE = "tsdb.agentname.change.host";
	/** Notification type for an AgentName app and host change */
	public static final String NOTIF_BOTH_NAME_CHANGE = "tsdb.agentname.change.both";
	
	final MBeanNotificationInfo[] NOTIF_INFOS = new MBeanNotificationInfo[]{
			new MBeanNotificationInfo(new String[]{NOTIF_ASSIGNED}, Notification.class.getName(), "Broadcast when the AgentName is initially assigned"),
			new MBeanNotificationInfo(new String[]{NOTIF_APP_NAME_CHANGE}, Notification.class.getName(), "Broadcast when the AgentName processes an app name change"),
			new MBeanNotificationInfo(new String[]{NOTIF_HOST_NAME_CHANGE}, Notification.class.getName(), "Broadcast when the AgentName processes a host change"),
			new MBeanNotificationInfo(new String[]{NOTIF_BOTH_NAME_CHANGE}, Notification.class.getName(), "Broadcast when the AgentName processes an app and host change")
	};

	
	/**
	 * Returns the current app name
	 * @return the current app name
	 */
	public String getAppName();

	/**
	 * Returns the current host name
	 * @return the current host name
	 */
	public String getHostName();
	
	/**
	 * Updates the AgentName's app name.
	 * If a new name is set, the system property {@link Constants#PROP_APP_NAME}
	 * will be updated and listeners will be notified
	 * @param newAppName The new app name. Ignored if null or empty.
	 */	
	public void resetAppName(String newAppName);
	
	/**
	 * Updates the AgentName's host name.
	 * If a new name is set, the system property {@link Constants#PROP_HOST_NAME}
	 * will be updated and listeners will be notified
	 * @param newHostName The new host name. Ignored if null or empty.
	 */	
	public void resetHostName(String newHostName);
	
	/**
	 * Resets the cached app and host names. If a new name is set, the corresponding
	 * system property {@link Constants#PROP_HOST_NAME} and/or {@link Constants#PROP_APP_NAME}
	 * will be updated. 
	 * @param newAppName The new app name to set. Ignored if null or empty.
	 * @param newHostName The new host name to set. Ignored if null or empty.
	 */
	public void resetNames(String newAppName, String newHostName);
}
