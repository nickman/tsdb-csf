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
import com.heliosapm.opentsdb.client.opentsdb.MetricBuilder;
import com.heliosapm.opentsdb.client.opentsdb.OTMetric;

/**
 * <p>Title: OTMetricFilter</p>
 * <p>Description: THe analog of a metric filter for the opt cache</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.opt.OTMetricFilter</code></p>
 */

public interface OTMetricFilter {
    /**
     * Matches all metrics, regardless of type or name.
     */
	OTMetricFilter ALL = new OTMetricFilter() {
        @Override
        public boolean matches(final String name, final Metric metric) {
            return true;
        }

		@Override
		public boolean matches(final OTMetric otMetric, final Metric metric) {
			return true;
		}
    };
    
    /**
     * Base OTMetric filter to extend
     */
    public static abstract class BaseOTMetricFilter implements OTMetricFilter {
        @Override
        public boolean matches(final String name, final Metric metric) {
            return matches(MetricBuilder.metric(name).chMetric(CHMetric.getCHMetricType(metric)).buildNoCache(), metric);
        }
    	
    }

    /**
     * Returns {@code true} if the metric matches the filter; {@code false} otherwise.
     *
     * @param name      the metric's name
     * @param metric    the metric
     * @return {@code true} if the metric matches the filter
     */
    boolean matches(String name, Metric metric);
    
    /**
     * Returns {@code true} if the metric matches the filter; {@code false} otherwise.
     *
     * @param otMetric  the OTMetric
     * @param metric    the metric
     * @return {@code true} if the metric matches the filter
     */
    boolean matches(OTMetric otMetric, Metric metric);
    
}

