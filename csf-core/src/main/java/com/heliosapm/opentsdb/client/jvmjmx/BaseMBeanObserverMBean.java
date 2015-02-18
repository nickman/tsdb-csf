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

/**
 * <p>Title: BaseMBeanObserverMBean</p>
 * <p>Description: JMX MBean interface for {@link BaseMBeanObserver}</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.BaseMBeanObserverMBean</code></p>
 */

public interface BaseMBeanObserverMBean {
	/**
	 * Returns the target host tag value
	 * @return the target host tag value
	 */
	public String getTargetHost();
	/**
	 * Returns the target host tag value
	 * @return the target host tag value
	 */
	public String getTargetApp();
	
	/**
	 * Returns the MBeanObserver's enabled attribute names
	 * @return the MBeanObserver's enabled attribute names
	 */
	public Set<String> getEnabledAttributeNames();
	
	/**
	 * Returns the MBeanObserver's one time (immutable) attribute names
	 * @return the MBeanObserver's one time attribute names
	 */
	public Set<String> getOneTimeAttributeNames();
	
	/**
	 * Returns the cummulative number of JMX collections against this observer
	 * @return the cummulative number of JMX collections 
	 */
	public long getCount();

	/**
	 * Returns the median collection time of JMX collections against this observer
	 * @return the median collection time of JMX collections 
	 */
	public double getMedian();

	/**
	 * Returns the collection time at the 99th percentile of JMX collections against this observer
	 * @return the collection time at the 99th percentile of JMX collections 
	 */
	public double get99thPercentile();

	/**
	 * Returns the maximum collection time of JMX collections against this observer
	 * @return the maximum  collection time of JMX collections 
	 */
	public long getMax();

	/**
	 * Returns the mean collection time of JMX collections against this observer
	 * @return the mean collection time of JMX collections 
	 */
	public double getMean();

	/**
	 * Returns the minimum collection time of JMX collections against this observer
	 * @return the minimum collection time of JMX collections 
	 */
	public long getMin();

	
}
