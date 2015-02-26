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

import com.codahale.metrics.Metric;

/**
 * <p>Title: MetricWriter</p>
 * <p>Description: Defines a class that knows how to write a value array member to a {@link CHMetric} type.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.opt.MetricWriter</code></p>
 * @param <T> The assumed metric type
 */

public interface MetricWriter<T extends Metric> {
	/**
	 * Writes the passed value to the passed metric
	 * @param value The value to write
	 * @param metric The metric to write to
	 */
	public void writeValue(final long value, final T metric);
}
