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

package com.heliosapm.opentsdb.client.sender;

import com.codahale.metrics.JmxReporter.JmxTimerMBean;

/**
 * <p>Title: SenderMetricMXBean</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.sender.SenderMetricMXBean</code></p>
 */

public interface SenderMetricMXBean {
	
	// =======================================================================================
	//  	Count Accessors
	// =======================================================================================
	
	/**
	 * Returns the number of items sent by the instrumented sender
	 * @return the number of items sent
	 * @see com.codahale.metrics.Counter#getCount()
	 */
	public long getSentCount(); 
	
	/**
	 * Returns the number of sends that have occured
	 * @return the number of sends that have occured
	 */
	public long getSendCount();
	
	/**
	 * Returns the number of failed sends
	 * @return the number of failed sends
	 */
	public long getFailedSendCount();
	
	/**
	 * Returns the number of bad content sends
	 * @return the number of bad content sends
	 */
	public long getBadContentCount();
	
	// =======================================================================================
	//   Send rate statistics
	// =======================================================================================

	/**
	 * Returns the 15 minute exponentially-weighted moving average rate of sends 
	 * @return the 15 minute exponentially-weighted moving average rate of sends
	 */
	public double getFifteenMinuteRate();

	/**
	 * Returns the 5 minute exponentially-weighted moving average rate of sends
	 * @return the 5 minute exponentially-weighted moving average rate of sends
	 */
	public double getFiveMinuteRate();

	/**
	 * Returns the mean rate at which sends have occured
	 * @return the mean rate at which sends have occured
	 */
	public double getMeanRate();

	/**
	 * Returns the 1 minute exponentially-weighted moving average rate of sends
	 * @return the 1 minute exponentially-weighted moving average rate of sends
	 */
	public double getOneMinuteRate();
	
	// =======================================================================================
	//   Send time statistics
	// =======================================================================================
	
	
	/**
	 * Returns the median send time
	 * @return the median send time
	 * @see com.codahale.metrics.Snapshot#getMedian()
	 */
	public double getMedian();
	
	/**
	 * Returns the 50th percentile send time
	 * @return the 50th percentile send time
	 */
	public double get50thPercentile();

	/**
	 * Returns the 75th percentile send time
	 * @return the 75th percentile send time
	 * @see com.codahale.metrics.Snapshot#get75thPercentile()
	 */
	public double get75thPercentile();

	/**
	 * Returns the 95th percentile send time
	 * @return the 95th percentile send time
	 * @see com.codahale.metrics.Snapshot#get95thPercentile()
	 */
	public double get95thPercentile();

	/**
	 * Returns the 98th percentile send time
	 * @return the 98th percentile send time
	 * @see com.codahale.metrics.Snapshot#get98thPercentile()
	 */
	public double get98thPercentile();

	/**
	 * Returns the 99th percentile send time
	 * @return the 99th percentile send time
	 * @see com.codahale.metrics.Snapshot#get99thPercentile()
	 */
	public double get99thPercentile();

	/**
	 * Returns the 999th percentile send time
	 * @return the 999th percentile send time
	 * @see com.codahale.metrics.Snapshot#get999thPercentile()
	 */
	public double get999thPercentile();

	/**
	 * Returns the max send time
	 * @return the max send time
	 * @see com.codahale.metrics.Snapshot#getMax()
	 */
	public double getMax();

	/**
	 * Returns the mean send time
	 * @return the mean send time
	 * @see com.codahale.metrics.Snapshot#getMean()
	 */
	public double getMean();

	/**
	 * Returns the min send time
	 * @return the min send time
	 * @see com.codahale.metrics.Snapshot#getMin()
	 */
	public double getMin();

	/**
	 * Returns the standard deviation of the recorded send times
	 * @return the standard deviation of the recorded send times
	 * @see com.codahale.metrics.Snapshot#getStdDev()
	 */
	public double getStdDev();
	
	// =======================================================================================
	//  Sender data functions
	// =======================================================================================
	
	/**
	 * Returns the send time at the given quantile
	 * @param quantile The quantile to get send times for
	 * @return the send time at the given quantile
	 * @see com.codahale.metrics.Snapshot#getValue(double)
	 */
	public double getValue(double quantile);	
	
	/**
	 * Returns the number of values currently in the window
	 * @return the number of values currently in the window
	 * @see com.codahale.metrics.Snapshot#size()
	 */
	public int size();

	
	// =======================================================================================
	

	/**
	 * Returns the underlying values in the send time samples
	 * @return the underlying values in the send time samples
	 */
	public long[] values();

	/**
	 * Returns the duration unit for send times
	 * @return the duration unit for send times
	 * @see com.codahale.metrics.JmxReporter.JmxTimerMBean#getDurationUnit()
	 */
	public String getDurationUnit();

	/**
	 * Returns the duration unit for rates
	 * @return the duration unit for rates
	 * @see com.codahale.metrics.JmxReporter.JmxMeterMBean#getRateUnit()
	 */
	public String getRateUnit();
	
	
}
