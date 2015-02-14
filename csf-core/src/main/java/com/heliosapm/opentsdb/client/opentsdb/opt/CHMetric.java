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

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Timer;

/**
 * <p>Title: CHMetric</p>
 * <p>Description: Enumerates the codahale metric types</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.CHMetric</code></p>
 */

public enum CHMetric {
	/** Not a CHMetric */
	NOTAMETRIC(null, 0, EnumSet.noneOf(SubMetric.class)),
	/** The {@link Gauge} metric */
	GAUGE(Gauge.class, SubMetric.GAUGE_SUBMETRIC_MASK, SubMetric.GAUGE_SUBMETRICS),
	/** The {@link Timer} metric */
	TIMER(Timer.class, SubMetric.TIMER_SUBMETRIC_MASK, SubMetric.TIMER_SUBMETRICS),
	/** The {@link Meter} metric */
	METER(Meter.class, SubMetric.METER_SUBMETRIC_MASK, SubMetric.METER_SUBMETRICS),
	/** The {@link Histogram} metric */
	HISTOGRAM(Histogram.class, SubMetric.HISTOGRAM_SUBMETRIC_MASK, SubMetric.HISTOGRAM_SUBMETRICS),
	/** The {@link Counter} metric */
	COUNTER(Counter.class, SubMetric.COUNTER_SUBMETRIC_MASK, SubMetric.COUNTER_SUBMETRICS);
	
	private CHMetric(final Class<? extends Metric> type, final int subMetricMask, final Set<SubMetric> subMetrics) {
		this.type = type;
		this.subMetricMask = subMetricMask;
		this.bordinal = (byte)ordinal();		
		this.subMetrics = Collections.unmodifiableSet(subMetrics);
	}
	
	/** The maximum bordinal */
	private static final byte MAX_BORDINAL = values()[values().length].bordinal;
	
	/** The metric type */
	public final Class<? extends Metric> type;
	/** The byte ordinal used to flag an OTMetric */
	public final byte bordinal;
	/** The bit mask of the default submetrics */
	public final int subMetricMask;
	
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
		if(bordinal < 0 || bordinal > MAX_BORDINAL) throw new IllegalArgumentException("Invalid byte ordinal:" + bordinal);
		return values()[bordinal];
	}
	
	private static <T extends Metric> CHMetric decode(final Class<T> metricType) {
		for(CHMetric chm: posValues) {
			if(chm.type.isAssignableFrom(metricType)) return chm;
		}
		throw new RuntimeException("The type [" + metricType + "] does not map to any defined CHMetric");
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
}
