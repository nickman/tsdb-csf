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

import javax.management.MBeanServerConnection;

/**
 * <p>Title: RemoteMBeanServerAgentNameFinder</p>
 * <p>Description: Defines a class that gets the AgentName (host and app) names from a remote MBeanServer</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.RemoteMBeanServerAgentNameFinder</code></p>
 */

public interface RemoteMBeanServerAgentNameFinder {
	/**
	 * Returns an array containing the AgentName host and app names for the passed MBeanServerConnection
	 * @param mbs The MBeanServerConnection to get the AgentName for
	 * @return An array with the host name [0] and app name [1].
	 */
	public String[] getRemoteAgentName(MBeanServerConnection mbs);
}
