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
package com.heliosapm.hub.appidfinders;

import javax.management.ObjectName;

import com.heliosapm.hub.AppIdFinder;
import com.heliosapm.hub.MountedJVM;
import com.heliosapm.utils.jmx.JMXHelper;

/**
 * <p>Title: CoherenceAppIdFinder</p>
 * <p>Description: Figures out the appid from the node id of the member entry in the coherence cluster for the mounted jvm's pid</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.hub.appidfinders.CoherenceAppIdFinder</code></p>
 */

public class CoherenceAppIdFinder implements AppIdFinder {
	private static final ObjectName CLUSTER_OBJECT_NAME = JMXHelper.objectName("Coherence:type=Cluster");
	

	// Member(Id=41, Timestamp=2015-07-22 10:12:08.096, Address=10.5.202.30:8096, MachineId=23752, Location=site:,machine:pdk-pt-cepas-01,process:10584, Role=CpexPtmsCacheStorageVMController)
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.hub.AppIdFinder#getAppId(com.heliosapm.hub.MountedJVM)
	 */
	@Override
	public String getAppId(final MountedJVM jvm) {
		final String pid = jvm.getId();
		final String match = ",process:" + pid + ",";
		final String[] members = (String[])jvm.getMbeanServer().getAttribute(CLUSTER_OBJECT_NAME, "Members");
		for(String s: members) {
			if(s.contains(match)) {
				s = s.replace("MachineId", "");
				final int index = s.indexOf("Id=");
				if(index!=0) {
					final int nextIndex = s.indexOf(",", index);
					if(nextIndex!=0) {
						return "CoherenceNode#" + s.substring(index+3, nextIndex);
					}
				}
			}
		}
		
		return null;
	}

}
