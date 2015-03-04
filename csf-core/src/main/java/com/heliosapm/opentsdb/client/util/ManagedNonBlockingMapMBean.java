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
package com.heliosapm.opentsdb.client.util;

import javax.management.ObjectName;

/**
 * <p>Title: ManagedNonBlockingMapMBean</p>
 * <p>Description: JMX interface to expose metrics on a NonBlockingHashMapLong or ManagedNonBlockingMap</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.util.ManagedNonBlockingMapMBean</code></p>
 */

public interface ManagedNonBlockingMapMBean {
	/**
	 * Returns the hash map application designated name
	 * @return the hash map application designated name
	 */
	public String getName();
	/**
	 * Returns the JMX MBean ObjectName
	 * @return the JMX MBean ObjectName
	 */
	public ObjectName getObjectName();
	/**
	 * Returns the number of key-value mappings in this map
	 * @return the number of key-value mappings in this map
	 */
	public int getSize();
	/**
	 * Removes all of the mappings from this map
	 */
	public void clear();
	/**
	 * Returns the total number of reprobes that have occured in this map
	 * @return the total number of reprobes that have occured in this map
	 */
	public long getTotalReprobeCount();
	/**
	 * Returns the last number of reprobes that occured in this map
	 * @return the last number of reprobes that occured in this map
	 */
	public long getLastReprobeCount();
}
