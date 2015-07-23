/**
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
 */
package com.heliosapm.hub.jvmmatchers;

import java.util.Properties;

import com.heliosapm.hub.JVMMatcher;
import com.heliosapm.shorthand.attach.vm.VirtualMachine;
import com.heliosapm.utils.lang.StringHelper;

/**
 * <p>Title: AgentPropertyMatcher</p>
 * <p>Description: JVM matcher that matches against the presence or value of a vm's agent property</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.hub.jvmmatchers.AgentPropertyMatcher</code></p>
 */

public class AgentPropertyMatcher implements JVMMatcher {
	/** Reusable static instance */
	public static final JVMMatcher INSTANCE = new AgentPropertyMatcher();

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.hub.JVMMatcher#match(java.lang.String, com.heliosapm.shorthand.attach.vm.VirtualMachine, java.lang.String, java.lang.String)
	 */
	@Override
	public boolean match(final String displayName, final VirtualMachine vm, final String match, final String key) {
		final Properties p = vm.getAgentProperties();
		if(match==null || match.trim().isEmpty()) {
			if(key==null || key.trim().isEmpty()) {
				throw new IllegalArgumentException("Both the match and the matchkey were empty or null");
			}
			return p.containsKey(key.trim());
		} else {
			if(key==null || key.trim().isEmpty()) {
				throw new IllegalArgumentException("Match provided with no matchkey");				
			}
			final String v = p.getProperty(key.trim());
			return StringHelper.wildmatch(v, match.trim());
		}
	}

}
