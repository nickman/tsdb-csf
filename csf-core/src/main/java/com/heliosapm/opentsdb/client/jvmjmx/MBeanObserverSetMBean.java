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

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * <p>Title: MBeanObserverSetMBean</p>
 * <p>Description: The JMX MBean interface for {@link MBeanObserverSet} instances</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.MBeanObserverSetMBean</code></p>
 */

public interface MBeanObserverSetMBean {
	/**
	 * Returns the fully qualified names of the metrics being traced by this observer
	 * @return the fully qualified names of the metrics being traced by this observer
	 */
	public Set<String> getEnabledOTMetrics();
	/**
	 * Indicates if the MBeanObserver is collecting
	 * @return true if the MBeanObserver is collecting, false otherwise
	 */
	public boolean isActive();
	
	/**
	 * Returns the collection period
	 * @return the collection period
	 */
	public long getCollectionPeriod();
	
	/**
	 * Returns the collection period unit
	 * @return the collection period unit
	 */
	public TimeUnit getCollectionPeriodUnit();
	
	/**
	 * Returns the enabled observer names
	 * @return the enabled observer names
	 */
	public Set<String> getEnabledObservers();
	/**
	 * Returns the average collection time in ms.
	 * @return the average collection time in ms.
	 */
	public long getAverageCollectTime();
	
	/**
	 * Starts this MBeanObserver's collection scheduler 
	 */
	public void start();
	
	/**
	 * Stops this MBeanObserver's collection scheduler 
	 */
	public void stop();	
	
	
}
