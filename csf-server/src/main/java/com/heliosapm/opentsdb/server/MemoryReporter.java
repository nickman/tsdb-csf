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
package com.heliosapm.opentsdb.server;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

/**
 * <p>Title: MemoryReporter</p>
 * <p>Description: Wakes up every <i>n</i> seconds and broadcasts the heap usage to all channels in the shared channel group</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.server.MemoryReporter</code></p>
 */
public class MemoryReporter extends Thread {
	/** The memory mx bean */
	public static final MemoryMXBean mxBean = ManagementFactory.getMemoryMXBean();
	/** The number of ms. to sleep */
	private final long sleepTime;
	/**
	 * Creates a new MemoryReporter
	 * @param frequency The frequency in seconds of the memory reporting
	 */
	public MemoryReporter(int frequency) {
		super("MemoryReporter-" + frequency);
		setDaemon(true);		
		sleepTime = TimeUnit.MILLISECONDS.convert(frequency, TimeUnit.SECONDS);
	}
	
	/**
	 * Broadcasts the heap memory usage every <code>sleepTime</code> ms.
	 * {@inheritDoc}
	 * @see java.lang.Thread#run()
	 */
	public void run() {
		while(true) {
			try { join(sleepTime); } catch (Exception e) {}
			MemoryUsage musage = mxBean.getHeapMemoryUsage();
			SharedChannelGroup.getInstance().write(new JSONObject(musage));
		}
	}
}
