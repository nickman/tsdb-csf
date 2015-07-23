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
package com.heliosapm.hub.appidfinders;

import com.heliosapm.hub.AppIdFinder;
import com.heliosapm.hub.MountedJVM;

/**
 * <p>Title: VMDisplayNameAppIdFinder</p>
 * <p>Description: App ID finder that uses the VM's display name as the app id</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.hub.appidfinders.VMDisplayNameAppIdFinder</code></p>
 */

public class VMDisplayNameAppIdFinder implements AppIdFinder {

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.hub.AppIdFinder#getAppId(com.heliosapm.hub.MountedJVM)
	 */
	@Override
	public String getAppId(final MountedJVM jvm) {
		String jvmName = jvm.getDisplayName();
		if(jvmName==null || jvmName.trim().isEmpty()) return "null-jname";
		jvmName = jvmName.trim();
		int index = jvmName.lastIndexOf('.');
		if(index==-1) return jvmName;
		return jvmName.substring(index+1);		
	}

}
