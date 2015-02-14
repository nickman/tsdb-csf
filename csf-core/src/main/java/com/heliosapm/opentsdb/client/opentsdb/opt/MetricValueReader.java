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

package com.heliosapm.opentsdb.client.opentsdb.opt;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: MetricValueReader</p>
 * <p>Description: Function enumeration of the metric readers available for each known metric</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.opt.MetricValueReader</code></p>
 */

public enum MetricValueReader implements IMetricValueReader<Metric> {
	/** A {@link Gauge} value */
	gauge{@Override public double get(final Metric metric) {return ((Number)((Gauge)metric).getValue()).doubleValue();}},
	/** A {@link Counter} count */
	counter{@Override public double get(final Metric metric) {return ((Number)((Counter)metric).getCount()).doubleValue();}},
	/** A {@link Histogram} count */
	hcount("hcount"){@Override public double get(final Metric metric) {return ((CachedSnapshot)metric).getCount();}},
	/** the lowest value in the snapshot of a  {@link Histogram}. */
	min(){@Override	public double get(final Metric metric) { return ((CachedSnapshot)metric).getMin(); }},
	/** the highest value in the snapshot of a  {@link Histogram}. */
	max(){@Override	public double get(final Metric metric) { return ((CachedSnapshot)metric).getMax(); }},
	/** the arithmetic mean of the values in the snapshot of a  {@link Histogram} */
	mean(){@Override	public double get(final Metric metric) { return ((CachedSnapshot)metric).getMean(); }},
	/** the standard deviation of the values in the snapshot of a  {@link Histogram}. */
	stddev(){@Override	public double get(final Metric metric) { return ((CachedSnapshot)metric).getStdDev(); }},
	/** the median value in the distribution. */
	median(){@Override	public double get(final Metric metric) { return ((CachedSnapshot)metric).getMedian(); }},
	/** The value at the 75th percentile in the distribution. */
	p75(){@Override	public double get(final Metric metric) { return ((CachedSnapshot)metric).get75thPercentile(); }},
	/** The value at the 95th percentile in the distribution. */
	p95(){@Override	public double get(final Metric metric) { return ((CachedSnapshot)metric).get95thPercentile(); }},
	/** The value at the 98th percentile in the distribution. */
	p98(){@Override	public double get(final Metric metric) { return ((CachedSnapshot)metric).get98thPercentile(); }},
	/** The value at the 99th percentile in the distribution. */
	p99(){@Override	public double get(final Metric metric) { return ((CachedSnapshot)metric).get99thPercentile(); }},
	/** The value at the 99.9th percentile in the distribution. */
	p999(){@Override public double get(final Metric metric) { return ((CachedSnapshot)metric).get999thPercentile(); }},
	/** The number of events which have been marked in a {@link Meter} */
	mcount(){@Override public double get(final Metric metric) { return ((Meter)metric).getCount(); }},
	/** The mean rate at which events have occurred in a {@link Meter} since the meter was created */
	mean_rate(){@Override public double get(final Metric metric) { return ((Meter)metric).getMeanRate(); }},
	/** The one-minute exponentially-weighted moving average rate at which events have occurred in a {@link Meter} since the meter was created */
	m1(){@Override public double get(final Metric metric) { return ((Meter)metric).getOneMinuteRate(); }},
	/** The five-minute exponentially-weighted moving average rate at which events have occurred in a {@link Meter} since the meter was created */
	m5(){@Override public double get(final Metric metric) { return ((Meter)metric).getFiveMinuteRate(); }},
	/** The fifteen-minute exponentially-weighted moving average rate at which events have occurred in a {@link Meter} since the meter was created */
	m15(){@Override public double get(final Metric metric) { return ((Meter)metric).getFifteenMinuteRate(); }};
	
	
	


	private MetricValueReader() {
		this(null);
	}
	
	private <T extends Metric> MetricValueReader(final String ext, Class<T>...types) {
		mask = Util.pow2Index(ordinal());
		this.ext = ext==null ? name() : ext;		
		this.types = new HashSet<Class<Metric>>();
		for(Class<T> ct: types) {
			this.types.add((Class<Metric>) ct);
		}
	}
	
	/** The bit mask value for this MetricValueReader member */
	public final int mask;
	/** The default metric extension used when rendered by a reporter */
	public final String ext;
	/** The metric types this reader applies to */
	public final Set<Class<Metric>> types;


}
