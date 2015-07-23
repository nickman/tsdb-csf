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
package com.heliosapm.hub;

import com.heliosapm.shorthand.attach.vm.VirtualMachine;

/**
 * <p>Title: JVMMatcher</p>
 * <p>Description: Defines a jvm matcher which selects the correct target jvm to attach to</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.hub.JVMMatcher</code></p>
 */

public interface JVMMatcher {
	/**
	 * Insoects the passed display name and VM and determines if they are a match for the target JVM
	 * @param displayName The JVM display name
	 * @param vm The attach api vm
	 * @param match The match value
	 * @param key The optional match key
	 * @return true for a match, false otherwise
	 */
	public boolean match(String displayName, VirtualMachine vm, String match, String key);
	
	//<mountjvm match="GroovyStarter" matcher="displayName" matchkey="">
}
