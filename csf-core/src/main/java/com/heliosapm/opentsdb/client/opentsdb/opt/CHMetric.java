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

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Timer;
import com.codahale.metrics.UniformReservoir;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: CHMetric</p>
 * <p>Description: Enumerates the codahale metric types</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.CHMetric</code></p>
 */

public enum CHMetric implements CHMetricFactory {
	/** Not a CHMetric */
	NOTAMETRIC(null, null, 0, Collections.unmodifiableSet(EnumSet.noneOf(SubMetric.class)), 0, Collections.unmodifiableSet(EnumSet.noneOf(SubMetric.class))){@Override public Metric createNewMetric() {return null;}},
	/** The {@link Gauge} metric */
	GAUGE(Gauge.class, new GaugeMetricWriter(), SubMetric.GAUGE_SUBMETRIC_MASK, SubMetric.GAUGE_SUBMETRICS, SubMetric.DEFAULT_GAUGE_SUBMETRIC_MASK, SubMetric.DEFAULT_GAUGE_SUBMETRICS){@Override public Metric createNewMetric() {return new UpdateableLongGauge();}},
	/** The {@link Timer} metric */
	TIMER(Timer.class, new TimerMetricWriter(), SubMetric.TIMER_SUBMETRIC_MASK, SubMetric.TIMER_SUBMETRICS, SubMetric.DEFAULT_TIMER_SUBMETRIC_MASK, SubMetric.DEFAULT_TIMER_SUBMETRICS){@Override public Timer createNewMetric() {return new Timer();}},
	/** The {@link Meter} metric */
	METER(Meter.class, new MeterMetricWriter(), SubMetric.METER_SUBMETRIC_MASK, SubMetric.METER_SUBMETRICS, SubMetric.DEFAULT_METER_SUBMETRIC_MASK, SubMetric.DEFAULT_METER_SUBMETRICS){@Override public Meter createNewMetric() {return new Meter();}},
	/** The {@link Histogram} metric */
	HISTOGRAM(Histogram.class, new HistogramMetricWriter(), SubMetric.HISTOGRAM_SUBMETRIC_MASK, SubMetric.HISTOGRAM_SUBMETRICS, SubMetric.DEFAULT_HISTOGRAM_SUBMETRIC_MASK, SubMetric.DEFAULT_HISTOGRAM_SUBMETRICS){@Override public Histogram createNewMetric() {return new Histogram(new UniformReservoir());}},
	/** The {@link Counter} metric */
	COUNTER(Counter.class, new CounterMetricWriter(), SubMetric.COUNTER_SUBMETRIC_MASK, SubMetric.COUNTER_SUBMETRICS, SubMetric.DEFAULT_COUNTER_SUBMETRIC_MASK, SubMetric.DEFAULT_COUNTER_SUBMETRICS){@Override public Counter createNewMetric() {return new Counter();}};
	
	private CHMetric(final Class<? extends Metric> type, final MetricWriter metricWriter, final int subMetricMask, final Set<SubMetric> subMetrics, final int defaultSubMetricMask, final Set<SubMetric> defaultSubMetrics) {
		this.type = type;
		this.subMetricMask = subMetricMask;
		this.bordinal = Util.pow2ByteIndex(ordinal());
		this.subMetrics = subMetrics;
		this.defaultSubMetricMask = defaultSubMetricMask;
		this.defaultSubMetrics = defaultSubMetrics;
		this.metricWriter = metricWriter;
	}
	
	public static void main(String[] args) {
		for(CHMetric c: values()) {
			System.out.println(c);
		}
	}
	
	
	/** The metric type */
	public final Class<? extends Metric> type;
	/** The byte ordinal used to flag an OTMetric */
	public final byte bordinal;
	/** The bit mask of the default submetrics */
	public final int subMetricMask;
	/** The default sub-metric mask */
	final int defaultSubMetricMask;
	/** The default sub-metrics */
	final Set<SubMetric> defaultSubMetrics;	
	/** The metric writer for this metric type */
	public final MetricWriter metricWriter;
	/** A set of the submetrics used by this CHMetric */
	public final Set<SubMetric> subMetrics;
	
	private static final Map<Class<? extends Metric>, CHMetric> typeDecodeCache = Collections.synchronizedMap(new WeakHashMap<Class<? extends Metric>, CHMetric>(128));
	/** An array of all the CHMetrics types except {@link CHMetric#NOTAMETRIC} */
	private static final CHMetric[] posValues = new CHMetric[values().length-1];
	
	static {
		System.arraycopy(values(), 1, posValues, 0, values().length-1);
	}
	
	/**
	 * Decodes the value of the passed byte ordinal to the corresponding CHMetric
	 * @param bordinal the byte ordinal to decode
	 * @return the CHMetric
	 */
	public static CHMetric valueOf(final byte bordinal) {
		if(bordinal < 0 || bordinal > values().length) throw new IllegalArgumentException("Invalid byte ordinal:" + bordinal);
		return values()[bordinal];
	}
	
	private static <T extends Metric> CHMetric decode(final Class<T> metricType) {
		for(CHMetric chm: posValues) {
			if(chm.type.isAssignableFrom(metricType)) return chm;
		}
		throw new RuntimeException("The type [" + metricType + "] does not map to any defined CHMetric");
	}
	
	public static class UpdateableLongGauge implements Gauge<Long> {
		/** The current value */
		protected final AtomicLong value = new AtomicLong(0L);
		
		/**
		 * {@inheritDoc}
		 * @see com.codahale.metrics.Gauge#getValue()
		 */
		@Override
		public Long getValue() {
			return value.get();
		}
		
		/**
		 * Sets the current gauge value
		 * @param v the value to set
		 */
		public void setLong(final long v) {
			value.set(v);
		}
		
		/**
		 * {@inheritDoc}
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return "GaugeValue:" + value.get();
		}
	}
	
	/**
	 * Decodes the passed {@link Metric} type to the corresponding CHMetric
	 * @param metricType the {@link Metric} type to decode
	 * @return the corresponding CHMetric
	 */
	public static <T extends Metric> CHMetric getCHMetricType(final Class<T> metricType) {
		if(metricType==null) throw new IllegalArgumentException("The passed metric type was null");
		CHMetric chMetric = typeDecodeCache.get(metricType);
		if(chMetric==null) {
			synchronized(typeDecodeCache) {
				chMetric = typeDecodeCache.get(metricType);
				if(chMetric==null) {
					chMetric = decode(metricType);
					typeDecodeCache.put(metricType, chMetric);
				}
			}
		}
		return chMetric;
	}
	
	/**
	 * Decodes the passed {@link Metric} instance to the corresponding CHMetric
	 * @param metric the {@link Metric} instance to decode
	 * @return the corresponding CHMetric
	 */
	public static <T extends Metric> CHMetric getCHMetricType(final T metric) {
		if(metric==null) throw new IllegalArgumentException("The passed metric was null");
		return getCHMetricType(metric.getClass());
	}
	
	/**
	 * Returns an array of the CHMetrics enabled by the passed mask 
	 * @param mask The mask
	 * @return an array of CHMetrics
	 */
	public static CHMetric[] getEnabled(final int mask) {
		final EnumSet<CHMetric> set = EnumSet.noneOf(CHMetric.class);
		for(CHMetric ch: values()) {
			if((mask & ch.bordinal) != 0) set.add(ch);
		}
		return set.toArray(new CHMetric[set.size()]);
	}
	
	/**
	 * Returns a bitmask enabled for all the passed CHMetric members
	 * @param chMetrics the CHMetric members to get a mask for
	 * @return the mask
	 */
	public static byte getMaskFor(final CHMetric...chMetrics) {
		if(chMetrics==null || chMetrics.length==0) return 0;
		byte bitMask = 0;
		for(CHMetric ch: chMetrics) {
			if(chMetrics==null) continue;
			bitMask = (byte) (bitMask | ch.bordinal);
		}
		return bitMask;
	}	
	
	public static class CounterMetricWriter implements MetricWriter<Counter> {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.opt.MetricWriter#writeValue(long, com.codahale.metrics.Metric)
		 */
		@Override
		public void writeValue(final long value, final Counter metric) {
			if(metric!=null) {
				metric.inc(value);
			}
		}
	}
	
	public static class HistogramMetricWriter implements MetricWriter<Histogram> {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.opt.MetricWriter#writeValue(long, com.codahale.metrics.Metric)
		 */
		@Override
		public void writeValue(final long value, final Histogram metric) {
			if(metric!=null) {
				metric.update(value);
			}			
		}
	}
	
	public static class MeterMetricWriter implements MetricWriter<Meter> {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.opt.MetricWriter#writeValue(long, com.codahale.metrics.Metric)
		 */
		@Override
		public void writeValue(final long value, final Meter metric) {
			if(metric!=null) {
				metric.mark(value);
			}			
		}
	}
	
	public static class TimerMetricWriter implements MetricWriter<Timer> {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.opt.MetricWriter#writeValue(long, com.codahale.metrics.Metric)
		 */
		@Override
		public void writeValue(final long value, final Timer metric) {
			if(metric!=null) {
				metric.update(value, TimeUnit.NANOSECONDS);
			}			
		}
	}
	
	
	public static class GaugeMetricWriter implements MetricWriter<Gauge> {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.opt.MetricWriter#writeValue(long, com.codahale.metrics.Metric)
		 */
		@Override
		public void writeValue(final long value, final Gauge metric) {
			if(metric!=null && (metric instanceof UpdateableLongGauge)) {
				((UpdateableLongGauge)metric).setLong(value);
			}
			
		}
	}
	

}
