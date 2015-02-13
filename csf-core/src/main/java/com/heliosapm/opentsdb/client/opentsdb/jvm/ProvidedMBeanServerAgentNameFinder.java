/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.heliosapm.opentsdb.client.opentsdb.jvm;

import javax.management.MBeanServerConnection;

/**
 * <p>Title: ProvidedMBeanServerAgentNameFinder</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.ProvidedMBeanServerAgentNameFinder</code></p>
 */

public class ProvidedMBeanServerAgentNameFinder implements RemoteMBeanServerAgentNameFinder {
	/** The provided host name */
	protected String hostName = null;
	/** The provided app name */
	protected String appName = null;
	
	/**
	 * Creates a new ProvidedMBeanServerAgentNameFinder
	 * @param hostName The provided host name 
	 * @param appName The provided app name
	 */
	public ProvidedMBeanServerAgentNameFinder(final String hostName, final String appName ) {
		this.hostName = hostName;
		this.appName = appName;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.RemoteMBeanServerAgentNameFinder#getRemoteAgentName(javax.management.MBeanServerConnection)
	 */
	@Override
	public String[] getRemoteAgentName(MBeanServerConnection mbs) {
		return new String[]{hostName, appName};
	}

}
