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

package com.heliosapm.opentsdb.client.opentsdb;

import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

import com.codahale.metrics.Metric;

/**
 * <p>Title: LongIdMetricSet</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.LongIdMetricSet</code></p>
 */

public interface LongIdMetricSet {
	/**
     * A map of metric long hash codes to metrics.
     * @return the metrics
     */
    NonBlockingHashMapLong<Metric> getMetrics();
}
