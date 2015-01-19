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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.management.ObjectName;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: SenderMetric</p>
 * <p>Description: A compound metric for tracking stats related to a metric sender</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.sender.SenderMetric</code></p>
 */

public class SenderMetric implements MetricSet, SenderMetricMXBean {
	/** The cummulative count of items sent */
	protected final Counter sendCounter;
	/** The cummulative count of send failures */
	protected final Counter failedSendCounter;
	/** The cummulative count of bad content sends */
	protected final Counter badContentCounter;
	
	/** The elapsed time tracker for senders */
	protected final Timer timer;
	/** The JMX ObjectName for this metric */
	protected final ObjectName objectName;
	/** The metrics map */
	protected final Map<String, Metric> metricMap = new LinkedHashMap<String, Metric>(2);
	/** The time cached timer snapshot */
	protected final AtomicReference<Snapshot> snapshot;
	/** The timestamped snapshot */
	protected final AtomicLong snapshotTimestamp = new AtomicLong();
	
//	Snapshot snap = null;
	
	/** The snapshot expiry in ms. */
	public static final long SNAPSHOT_EXPIRY = 10000;
	
	/**
	 * Creates a new SenderMetric
	 * @param metric The sender metric
	 * @param tags The metric tags 
	 */
	public SenderMetric(final String metric, final String...tags) {
		objectName = Util.objectName(metric, tags);
		sendCounter = new Counter();
		failedSendCounter = new Counter();
		badContentCounter = new Counter();
		timer = new Timer();
		snapshot = new AtomicReference<Snapshot>(timer.getSnapshot());
		String mnPrefix = objectName.toString().replace(':', '.').replace(',', '.');		
		metricMap.put(mnPrefix + ".cmtype=Timer", timer);
		metricMap.put(mnPrefix + ".cmtype=Counter.attr=Sends", sendCounter);
		metricMap.put(mnPrefix + ".cmtype=Counter.attr=FailedSends", failedSendCounter);
		metricMap.put(mnPrefix + ".cmtype=Counter.attr=BadContent", badContentCounter);
//		OpenTsdb.getInstance().getMetricRegistry().registerAll(this);
		snapshot.set(timer.getSnapshot());
		snapshotTimestamp.set(System.currentTimeMillis() + SNAPSHOT_EXPIRY);
		Util.registerMBean(this, objectName);
	}
	
	/**
	 * Updates the snapshot if it is expired
	 * @return the current snapshot
	 */
	protected Snapshot snap() {
		final long now = System.currentTimeMillis();
		if(snapshotTimestamp.get() <= now) {
			snapshotTimestamp.set(now);
			snapshot.set(timer.getSnapshot());
		}
		return snapshot.get();
	}

	/**
	 * {@inheritDoc}
	 * @see com.codahale.metrics.MetricSet#getMetrics()
	 */
	@Override
	public Map<String, Metric> getMetrics() {
		return metricMap;
	}

	// =======================================================================================
	//		Incrementors
	// =======================================================================================
	
	/**
	 * Increments the number of sent metrics
	 * @param n The amount to increment by
	 */
	public void incSent(final long n) {
		sendCounter.inc(n);
	}
	
	
	/**
	 * Increments the number of bad content sends 
	 * @param n The amount to increment by
	 */
	public void incBadContent(final long n) {
		badContentCounter.inc(n);
	}
		
	
	/**
	 * Increments the number of failed sends
	 * @param n The amount to increment by
	 */
	public void incFailedSends(final long n) {
		failedSendCounter.inc(n);
	}
	
	// =======================================================================================
	//		Count Accessors
	// =======================================================================================
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.sender.SenderMetricMXBean#getFailedSendCount()
	 */
	@Override
	public long getFailedSendCount() {
		return failedSendCounter.getCount();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.sender.SenderMetricMXBean#getBadContentCount()
	 */
	@Override
	public long getBadContentCount() {
		return badContentCounter.getCount();
	}	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.sender.SenderMetricMXBean#getSendCount()
	 */
	@Override
	public long getSendCount() {
		return timer.getCount();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.sender.SenderMetricMXBean#getSentCount()
	 */
	@Override
	public long getSentCount() {
		return sendCounter.getCount();
	}
	
	// =======================================================================================
	//		Sender time updates
	// =======================================================================================
	
	/**
	 * Updates the sender metric with a new reading
	 * @param elapsed The elapsed time of the op
	 * @param unit The unit of the elapsed time
	 * @param sentItems The number of sent items
	 */
	public void update(final long elapsed, final TimeUnit unit, final long sentItems) {
		timer.update(elapsed, unit);
		sendCounter.inc(sentItems);
	}
	

	/**
	 * Updates the sender metric with a new reading
	 * @param elapsed The elapsed time of the op
	 * @param unit The unit of the elapsed time
	 * @see com.codahale.metrics.Timer#update(long, java.util.concurrent.TimeUnit)
	 */
	public void update(final long elapsed, final TimeUnit unit) {
		timer.update(elapsed, unit);
	}

	// =======================================================================================
	//   Send rate statistics
	// =======================================================================================

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.sender.SenderMetricMXBean#getFifteenMinuteRate()
	 */
	@Override
	public double getFifteenMinuteRate() {
		return timer.getFifteenMinuteRate();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.sender.SenderMetricMXBean#getFiveMinuteRate()
	 */
	@Override
	public double getFiveMinuteRate() {
		return timer.getFiveMinuteRate();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.sender.SenderMetricMXBean#getMeanRate()
	 */
	@Override
	public double getMeanRate() {
		return timer.getMeanRate();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.sender.SenderMetricMXBean#getOneMinuteRate()
	 */
	@Override
	public double getOneMinuteRate() {
		return timer.getOneMinuteRate();
	}
	
	// =======================================================================================
	//   Send time statistics
	// =======================================================================================
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.sender.SenderMetricMXBean#getMedian()
	 */
	@Override
	public double getMedian() {
		return snap().getMedian();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.codahale.metrics.JmxReporter.JmxTimerMBean#get50thPercentile()
	 */
	@Override
	public double get50thPercentile() {
		return snap().getValue(0.50);
	}
	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.sender.SenderMetricMXBean#get75thPercentile()
	 */
	@Override
	public double get75thPercentile() {
		return snap().get75thPercentile();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.sender.SenderMetricMXBean#get95thPercentile()
	 */
	@Override
	public double get95thPercentile() {
		return snap().get95thPercentile();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.sender.SenderMetricMXBean#get98thPercentile()
	 */
	@Override
	public double get98thPercentile() {
		return snap().get98thPercentile();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.sender.SenderMetricMXBean#get99thPercentile()
	 */
	@Override
	public double get99thPercentile() {
		return snap().get99thPercentile();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.sender.SenderMetricMXBean#get999thPercentile()
	 */
	@Override
	public double get999thPercentile() {
		return snap().get999thPercentile();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.sender.SenderMetricMXBean#getMax()
	 */
	@Override
	public double getMax() {
		return snap().getMax();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.sender.SenderMetricMXBean#getMean()
	 */
	@Override
	public double getMean() {
		return snap().getMean();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.sender.SenderMetricMXBean#getMin()
	 */
	@Override
	public double getMin() {
		return snap().getMin();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.sender.SenderMetricMXBean#getStdDev()
	 */
	@Override
	public double getStdDev() {
		return snap().getStdDev();
	}
	


	// =======================================================================================
	//   Sender data ops
	// =======================================================================================
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.sender.SenderMetricMXBean#getValue(double)
	 */
	@Override
	public double getValue(double quantile) {
		return snap().getValue(quantile);
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.codahale.metrics.JmxReporter.JmxTimerMBean#values()
	 */
	@Override
	public long[] values() {
		return snap().getValues();
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.sender.SenderMetricMXBean#size()
	 */
	@Override
	public int size() {
		return snap().size();
	}
	

	/**
	 * Returns a snapshot of the send time stats
	 * @return a snapshot of the send time stats
	 * @see com.codahale.metrics.Timer#getSnapshot()
	 */
	public Snapshot getSnapshot() {
		return timer.getSnapshot();
	}


	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return timer.toString();
	}



	/**
	 * {@inheritDoc}
	 * @see com.codahale.metrics.JmxReporter.JmxTimerMBean#getDurationUnit()
	 */
	@Override
	public String getDurationUnit() {
		return "second";
	}

	/**
	 * {@inheritDoc}
	 * @see com.codahale.metrics.JmxReporter.JmxMeterMBean#getRateUnit()
	 */
	@Override
	public String getRateUnit() {
		return "sends";
	}


}
