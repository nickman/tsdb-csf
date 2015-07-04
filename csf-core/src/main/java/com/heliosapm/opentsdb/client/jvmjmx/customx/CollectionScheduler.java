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

import javax.management.ObjectName;

import com.heliosapm.utils.io.CloseableService;
import com.heliosapm.utils.jmx.JMXHelper;
import com.heliosapm.utils.jmx.JMXManagedScheduler;

/**
 * <p>Title: CollectionScheduler</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionScheduler</code></p>
 */

public class CollectionScheduler {
	/** The singleton instance */
	private static volatile CollectionScheduler instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	
	/** The CloseableService JMX ObjectName  */
	public static final ObjectName OBJECT_NAME = JMXHelper.objectName(CloseableService.class);
	
	/** The collection scheduler */
	protected final JMXManagedScheduler scheduler;

	
	/**
	 * Acquires the CollectionScheduler singleton instance
	 * @return the CollectionScheduler singleton instance
	 */
	public static CollectionScheduler getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new CollectionScheduler();
				}
			}
		}
		return instance;
	}


	/**
	 * Creates a new CollectionScheduler
	 */
	public CollectionScheduler() {
		scheduler = new JMXManagedScheduler(OBJECT_NAME, "JMXCollectionScheduler", 4, false);
		JMXHelper.registerAutoCloseMBean(scheduler, OBJECT_NAME, "stop");
	}

}
