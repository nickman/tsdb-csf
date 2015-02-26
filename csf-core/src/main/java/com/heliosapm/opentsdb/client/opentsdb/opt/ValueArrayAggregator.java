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

import java.util.EnumMap;
import java.util.Map;

import com.codahale.metrics.Metric;

/**
 * <p>Title: ValueArrayAggregator</p>
 * <p>Description: Aggregates series of value arrays</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.opt.ValueArrayAggregator</code></p>
 */

public class ValueArrayAggregator {

	/**
	 * @param valueArray
	 * @param aggregate
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static final Map<Measurement, Metric> aggregate(final long[] valueArray, final Map<Measurement, Metric> aggregate) {
		if(valueArray==null || valueArray.length < Measurement.VALUEBUFFER_HEADER_SIZE+1) return aggregate;
		final int mask = (int)valueArray[0];
		final Measurement[] measurements = Measurement.getEnabled(mask);
		final Map<Measurement, Metric> metricMap;
		if(aggregate==null) {
			metricMap = new EnumMap<Measurement, Metric>(Measurement.class);
			for(Measurement m: measurements) {
				metricMap.put(m, m.chMetric.createNewMetric());
			}
		} else {
			metricMap = aggregate;
		}

		int index = Measurement.VALUEBUFFER_HEADER_SIZE-1;
		for(Measurement m: Measurement.getEnabled(mask)) {
			m.chMetric.metricWriter.writeValue(valueArray[index], metricMap.get(m));
			index++;
		}
		return aggregate;
	}
	
	/**
	 * Calcs a double average incorporating a new value
	 * using <b><code>(prev_avg*cnt + newval)/(cnt+1)</code></b>
	 * @param prev_avg The pre-average
	 * @param cnt The pre-count
	 * @param newval The new value
	 * @return the average
	 */
	public static double avgd(double prev_avg, double cnt, double newval) {		
		return (prev_avg*cnt + newval)/(cnt+1);
	}	
	
	
	private ValueArrayAggregator() {}
}
