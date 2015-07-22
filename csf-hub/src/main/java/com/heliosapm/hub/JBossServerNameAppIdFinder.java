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

import java.util.Properties;

/**
 * <p>Title: JBossServerNameAppIdFinder</p>
 * <p>Description: AppId finder for jboss servers. Gets the appid name 
 * from the first segment of the value in the system property <b><code>jboss.server.name</code></b></p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.hub.JBossServerNameAppIdFinder</code></p>
 */

public class JBossServerNameAppIdFinder implements AppIdFinder {
	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.hub.AppIdFinder#getAppId(com.heliosapm.hub.MountedJVM)
	 */
	@Override
	public String getAppId(final MountedJVM jvm) {		
		final Properties p = jvm.getVm().getSystemProperties();
		final String sname = p.getProperty("jboss.server.name", "").trim();
		if(sname.isEmpty()) return null;
		final int index = sname.indexOf('.');
		if(index<1) return sname.replace(".", "");		
		return sname.substring(0, index);
	}

}
