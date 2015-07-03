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
package com.heliosapm.opentsdb.client.jvmjmx.customx;

/**
 * <p>Title: ScheduledMonitor</p>
 * <p>Description: A monitor that periodically executes custom JMX data collection definitions
 * and traces the results</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.ScheduledMonitor</code></p>
 */

public class ScheduledMonitor {
	/** The collection period expression. Defaults to <b><code>15s</code></b>. */
	protected String period = "15s";
	/**
	 * Creates a new ScheduledMonitor
	 */
	public ScheduledMonitor() {
		// TODO Auto-generated constructor stub
	}

}
