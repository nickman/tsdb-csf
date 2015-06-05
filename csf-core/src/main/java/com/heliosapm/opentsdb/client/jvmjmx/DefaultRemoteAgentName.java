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

package com.heliosapm.opentsdb.client.jvmjmx;

import com.heliosapm.opentsdb.client.name.AgentName;
import com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection;

/**
 * <p>Title: DefaultRemoteAgentName</p>
 * <p>Description: Uses the AgentName remote finders to get the remote host and app</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.DefaultRemoteAgentName</code></p>
 */

public class DefaultRemoteAgentName implements RemoteAgentName {
	/**
	 * Creates a new DefaultRemoteAgentName
	 */
	public DefaultRemoteAgentName() {
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.RemoteAgentName#getHostName(com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection)
	 */
	@Override
	public String getHostName(final RuntimeMBeanServerConnection remote) {
		return AgentName.remoteHostName(remote);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.RemoteAgentName#getAppName(com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection)
	 */
	@Override
	public String getAppName(final RuntimeMBeanServerConnection remote) {
		return AgentName.remoteAppName(remote);
	}

}
