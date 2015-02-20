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
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Timer;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: MetricValueReader</p>
 * <p>Description: Functional enumeration of the CHMetric's submetrics and their metric readers available for each known metric</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.opt.SubMetric</code></p>
 * FIXME: We have 4 different count sub-metrics:  count, hcount, tcount, mcount. If multiple are in effect, need to pick the most optimal.
 */

@SuppressWarnings("unchecked")
public enum SubMetric implements IMetricValueReader<Metric> {
	/** A {@link Gauge} value */
	gauge(""){@SuppressWarnings("rawtypes")	@Override public double get(final Metric metric) {return ((Number)((Gauge)metric).getValue()).doubleValue();}},
	/** A {@link Counter} count */
	count("count"){@Override public double get(final Metric metric) {return ((Number)((Counter)metric).getCount()).doubleValue();}},
	/** A {@link Histogram} count */
	hcount("count"){@Override public double get(final Metric metric) {return ((CachedSnapshot)metric).getCount();}},
	/** the lowest value in the snapshot of a  {@link Histogram}. */
	min{@Override	public double get(final Metric metric) { return ((CachedSnapshot)metric).getMin(); }},
	/** the highest value in the snapshot of a  {@link Histogram}. */
	max{@Override	public double get(final Metric metric) { return ((CachedSnapshot)metric).getMax(); }},
	/** the arithmetic mean of the values in the snapshot of a  {@link Histogram} */
	mean{@Override	public double get(final Metric metric) { return ((CachedSnapshot)metric).getMean(); }},
	/** the standard deviation of the values in the snapshot of a  {@link Histogram}. */
	stddev{@Override	public double get(final Metric metric) { return ((CachedSnapshot)metric).getStdDev(); }},
	/** the median value in the distribution. */
	median{@Override	public double get(final Metric metric) { return ((CachedSnapshot)metric).getMedian(); }},
	/** The value at the 75th percentile in the distribution. */
	p75{@Override	public double get(final Metric metric) { return ((CachedSnapshot)metric).get75thPercentile(); }},
	/** The value at the 95th percentile in the distribution. */
	p95{@Override	public double get(final Metric metric) { return ((CachedSnapshot)metric).get95thPercentile(); }},
	/** The value at the 98th percentile in the distribution. */
	p98{@Override	public double get(final Metric metric) { return ((CachedSnapshot)metric).get98thPercentile(); }},
	/** The value at the 99th percentile in the distribution. */
	p99{@Override	public double get(final Metric metric) { return ((CachedSnapshot)metric).get99thPercentile(); }},
	/** The value at the 99.9th percentile in the distribution. */
	p999{@Override public double get(final Metric metric) { return ((CachedSnapshot)metric).get999thPercentile(); }},
	/** A {@link Timer} count */
	tcount("count"){@Override public double get(final Metric metric) {return ((Timer)metric).getCount();}},
	/** The number of events which have been marked in a {@link Meter} */
	mcount("count"){@Override public double get(final Metric metric) { return ((Meter)metric).getCount(); }},
	/** The mean rate at which events have occurred in a {@link Meter} since the meter was created */
	mean_rate{@Override public double get(final Metric metric) { return ((Meter)metric).getMeanRate(); }},
	/** The one-minute exponentially-weighted moving average rate at which events have occurred in a {@link Meter} since the meter was created */
	m1{@Override public double get(final Metric metric) { return ((Meter)metric).getOneMinuteRate(); }},
	/** The five-minute exponentially-weighted moving average rate at which events have occurred in a {@link Meter} since the meter was created */
	m5{@Override public double get(final Metric metric) { return ((Meter)metric).getFiveMinuteRate(); }},
	/** The fifteen-minute exponentially-weighted moving average rate at which events have occurred in a {@link Meter} since the meter was created */
	m15{@Override public double get(final Metric metric) { return ((Meter)metric).getFifteenMinuteRate(); }};
	
	
	/** The ordered set of count subMetrics. Since we only need one count per instrumentation definition, these are in order of precedence */
	public static final SortedSet<SubMetric> countFilter = Collections.unmodifiableSortedSet(new TreeSet<SubMetric>(Arrays.asList(count, hcount, tcount, mcount)));

	/** An empty SubMetric array const */
	public static final SubMetric[] EMPTY_SUBMETRIC_ARR = {};
	/** A comma splitter pattern */
	public static final Pattern COMMA_SPLITTER = Pattern.compile(",");
	

	
	/** The {@link Gauge} SubMetrics */
	public static final Set<SubMetric> GAUGE_SUBMETRICS = Collections.unmodifiableSet(EnumSet.of(gauge));
	/** The default {@link Gauge} SubMetrics */
	public static final Set<SubMetric> DEFAULT_GAUGE_SUBMETRICS = GAUGE_SUBMETRICS;
	/** The {@link Counter} SubMetrics */
	public static final Set<SubMetric> COUNTER_SUBMETRICS = Collections.unmodifiableSet(EnumSet.of(count));
	/** The default {@link Counter} SubMetrics */
	public static final Set<SubMetric> DEFAULT_COUNTER_SUBMETRICS = COUNTER_SUBMETRICS;	
	/** The {@link Meter} SubMetrics */
	public static final Set<SubMetric> METER_SUBMETRICS = Collections.unmodifiableSet(EnumSet.of(mcount, mean_rate, m1, m5, m15));
	/** The default {@link Meter} SubMetrics */
	public static final Set<SubMetric> DEFAULT_METER_SUBMETRICS = Collections.unmodifiableSet(EnumSet.of(mcount, mean_rate));	
	/** The {@link Timer} SubMetrics */
	public static final Set<SubMetric> TIMER_SUBMETRICS = Collections.unmodifiableSet(EnumSet.of(tcount, mean_rate, m1, m5, m15, min, max, mean, stddev, median, p75, p95, p98, p99, p999));
	/** The default {@link Timer} SubMetrics */
	public static final Set<SubMetric> DEFAULT_TIMER_SUBMETRICS = Collections.unmodifiableSet(EnumSet.of(tcount, mean_rate, median));
	/** The {@link Histogram} SubMetrics */
	public static final Set<SubMetric> HISTOGRAM_SUBMETRICS = Collections.unmodifiableSet(EnumSet.of(hcount, min, max, mean, stddev, median, p75, p95, p98, p99, p999));
	/** The default {@link Histogram} SubMetrics */
	public static final Set<SubMetric> DEFAULT_HISTOGRAM_SUBMETRICS = Collections.unmodifiableSet(EnumSet.of(hcount, median));
	
	/** The {@link Gauge} SubMetrics Mask */
	public static final int GAUGE_SUBMETRIC_MASK = getMaskFor(gauge);
	/** The default {@link Gauge} SubMetrics Mask */
	public static final int DEFAULT_GAUGE_SUBMETRIC_MASK = GAUGE_SUBMETRIC_MASK;
	/** The {@link Counter} SubMetrics Mask */
	public static final int COUNTER_SUBMETRIC_MASK = getMaskFor(count);
	/** The default {@link Counter} SubMetrics Mask */
	public static final int DEFAULT_COUNTER_SUBMETRIC_MASK = COUNTER_SUBMETRIC_MASK;	
	/** The {@link Meter} SubMetrics Mask */
	public static final int METER_SUBMETRIC_MASK = getMaskFor(METER_SUBMETRICS);
	/** The default {@link Meter} SubMetrics Mask */
	public static final int DEFAULT_METER_SUBMETRIC_MASK = getMaskFor(DEFAULT_METER_SUBMETRICS);	
	/** The {@link Timer} SubMetrics Mask */
	public static final int TIMER_SUBMETRIC_MASK = getMaskFor(TIMER_SUBMETRICS);
	/** The default {@link Timer} SubMetrics Mask */
	public static final int DEFAULT_TIMER_SUBMETRIC_MASK = getMaskFor(DEFAULT_TIMER_SUBMETRICS);	
	/** The {@link Histogram} SubMetrics Mask */
	public static final int HISTOGRAM_SUBMETRIC_MASK = getMaskFor(HISTOGRAM_SUBMETRICS);
	/** The default {@link Histogram} SubMetrics Mask */
	public static final int DEFAULT_HISTOGRAM_SUBMETRIC_MASK = getMaskFor(DEFAULT_HISTOGRAM_SUBMETRICS);
	
	/** All the SubMetrics mask */
	public static final int ALL_SUBMETRICS_MASK = getMaskFor(values());
	/** The default SubMetrics mask */
	public static final int DEFAULT_SUBMETRICS_MASK = (DEFAULT_GAUGE_SUBMETRIC_MASK | DEFAULT_COUNTER_SUBMETRIC_MASK | DEFAULT_METER_SUBMETRIC_MASK | DEFAULT_HISTOGRAM_SUBMETRIC_MASK);

	/** Maps the member bitmask to the member */
	public static final Map<Integer, SubMetric> MASK2ENUM;
	
	static {
		final SubMetric[] values = values();
		Map<Integer, SubMetric> tmp = new HashMap<Integer, SubMetric>(values.length);
		for(SubMetric s: values) {
			tmp.put(s.mask, s);
		}
		MASK2ENUM = Collections.unmodifiableMap(tmp);		
	}
	

	private SubMetric() {
		this(null, null);
	}

	private <T extends Metric> SubMetric(final Class<T>...types) {
		this(null, types);
	}

	
	private <T extends Metric> SubMetric(final String ext, final Class<T>...types) {
		this.mask = Util.pow2Index(ordinal());
		this.ext = ext==null ? name() : ext;		
		this.types = new HashSet<Class<Metric>>();
		if(types!=null) {
			for(Class<T> ct: types) {
				this.types.add((Class<Metric>) ct);
			}
		}
	}
	
	public static void main(String[] args) {
		for(SubMetric m: SubMetric.values()) {
			//System.out.println("Name:" + m.name() + ", Reader:" + (m.reader==null ? "<None>" : m.reader.getClass().getSimpleName()));
//			System.out.println(String.format("\t<option value=\"%s\" selected=\"selected\" >%s</option>", m.mask, m.name()));
			System.out.println(m);
			
		}
	}
	
	
	
	/** The bit mask value for this SubMetric member */
	public final int mask;
	/** The default metric extension used when rendered by a reporter */
	public final String ext;
	/** The metric types this sub-metric applies to */
	public final Set<Class<Metric>> types;
	
	/**
	 * Returns a bitmask enabled for all the passed subMetric members
	 * @param ots the subMetric members to get a mask for
	 * @return the mask
	 */
	public static int getMaskFor(final SubMetric...ots) {
		if(ots==null || ots.length==0) return 0;
		int bitMask = 0;
		for(SubMetric ot: ots) {
			if(ot==null) continue;
			bitMask = bitMask | ot.mask;
		}
		return bitMask;
	}
	
	/**
	 * Returns a bitmask enabled for all the passed subMetric members
	 * @param ots the subMetric members to get a mask for
	 * @return the mask
	 */
	public static int getMaskFor(final Set<SubMetric> ots) {
		if(ots==null) return 0;
		return getMaskFor(ots.toArray(new SubMetric[ots.size()]));
	}
	
	/**
	 * Decodes the passed expression into an array of SubMetrics.
	 * Is expected to be a comma separated list of values where each value 
	 * can be either the name of a SubMetric member or a SubMetric mask.
	 * Normally there would only be one mask, but multiple are supported. 
	 * The expression can contain both representations. 
	 * @param throwIfInvalid If true, will throw an exception if a value cannot be decoded
	 * @param expr The expression to decode
	 * @return an array of represented SubMetric members.
	 */
	public static SubMetric[] decode(final boolean throwIfInvalid, final CharSequence expr) {
		if(expr==null) return EMPTY_SUBMETRIC_ARR;
		final String sexpr = expr.toString().trim().toUpperCase();
		if(sexpr.isEmpty()) return EMPTY_SUBMETRIC_ARR;
		final EnumSet<SubMetric> set = EnumSet.noneOf(SubMetric.class);
		final String[] exprFields = COMMA_SPLITTER.split(sexpr);
		for(String s: exprFields) {
			if(isSubMetricName(s)) {
				set.add(valueOf(s.trim()));
				continue;
			}
			try {
				int mask = new Double(s).intValue();
				SubMetric sm = MASK2ENUM.get(mask);
				if(sm!=null) {
					set.add(sm);
					continue;
				} else {
					if(!throwIfInvalid) continue;
				}
			} catch (Exception ex) {
				if(!throwIfInvalid) continue;
			}
			throw new RuntimeException("Failed to decode SubMetric value [" + s + "]");
		}
		return set.toArray(new SubMetric[set.size()]);
	}
	
	/**
	 * Decodes the passed expression into an array of SubMetrics, ignoring any non-decoded values.
	 * Is expected to be a comma separated list of values where each value 
	 * can be either the name of a SubMetric member or a SubMetric mask (individual or bitmasked).
	 * Normally there would only be one mask, but multiple are supported. 
	 * The expression can contain both representations. 
	 * @param expr The expression to decode
	 * @return an array of represented SubMetric members.
	 */
	public static SubMetric[] decode(final CharSequence expr) {
		return decode(false, expr);
	}
	
	/**
	 * Decodes the passed expression into a bitmask representing enabled SubMetric, ignoring any non-decoded values.
	 * Is expected to be a comma separated list of values where each value 
	 * can be either the name of a SubMetric member or a SubMetric mask.
	 * Normally there would only be one mask, but multiple are supported. 
	 * The expression can contain both representations. 
	 * @param expr The expression to decode
	 * @return the bit mask of the enabled SubMetric members
	 */
	public static int decodeToMask(final CharSequence expr) {
		return getMaskFor(decode(expr));
	}
	
	/**
	 * Decodes the passed expression into a bitmask representing enabled SubMetrics
	 * Is expected to be a comma separated list of values where each value 
	 * can be either the name of a SubMetric member or a SubMetric mask.
	 * Normally there would only be one mask, but multiple are supported. 
	 * The expression can contain both representations. 
	 * @param throwIfInvalid If true, will throw an exception if a value cannot be decoded
	 * @param expr The expression to decode
	 * @return the bit mask of the enabled SubMetric members
	 */
	public static int decodeToMask(final boolean throwIfInvalid, final CharSequence expr) {
		return getMaskFor(decode(throwIfInvalid, expr));
	}
	
	
	
	/**
	 * Indicates if the passed symbol is a valid SubMetric member name.
	 * The passed value is trimmed and upper-cased.
	 * @param symbol The symbol to test
	 * @return true if the passed symbol is a valid SubMetric member name, false otherwise
	 */
	public static boolean isSubMetricName(final CharSequence symbol) {
		if(symbol==null) return false;
		final String name = symbol.toString().trim().toLowerCase();
		if(name.isEmpty()) return false;
		try {
			valueOf(name);
			return true;
		} catch (Exception ex) {
			return false;
		}
	}
	


	/**
	 * Returns a bitmask enabled for all the passed subMetric member names
	 * @param ignoreInvalids If true, ignore any invalid names, otherwise throws.
	 * @param names the subMetric member names to get a mask for
	 * @return the mask
	 */
	public static int getMaskFor(final boolean ignoreInvalids, final String...names) {
		if(names==null || names.length==0) return 0;
		int bitMask = 0;
		for(int i = 0; i < names.length; i++) {
			String name = names[i];
			if((name==null || name.trim().isEmpty())) {
				if(ignoreInvalids) continue;
				throw new RuntimeException("Invalid name at index [" + i + "] in " + Arrays.toString(names));
			}
			SubMetric ot = null;
			try { ot = SubMetric.valueOf(name.toLowerCase().trim()); } catch (Exception ex) {}
			if(ot==null) {
				if(ignoreInvalids) continue;
				throw new RuntimeException("Invalid name at index [" + i + "] in " + Arrays.toString(names));				
			}
			bitMask = bitMask | ot.mask;
		}
		return bitMask;
	}
	
	/**
	 * Returns a bitmask enabled for all the passed subMetric member names, ignoring any invalid values
	 * @param names the subMetric member names to get a mask for
	 * @return the mask
	 */
	public static int getMaskFor(final String...names) {
		return getMaskFor(true, names);
	}
	
	/**
	 * Returns an array of the SubMetrics enabled by the passed mask 
	 * @param mask The mask
	 * @return an array of SubMetrics
	 */
	public static SubMetric[] getEnabled(final int mask) {
		final EnumSet<SubMetric> set = EnumSet.noneOf(SubMetric.class);
		for(SubMetric ot: values()) {
			if((mask & ot.mask) != 0) set.add(ot);
		}
		return set.toArray(new SubMetric[set.size()]);
	}
	


}
