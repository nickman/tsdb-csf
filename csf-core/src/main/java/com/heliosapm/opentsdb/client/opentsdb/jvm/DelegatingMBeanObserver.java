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

package com.heliosapm.opentsdb.client.opentsdb.jvm;

import java.util.Map;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;

/**
 * <p>Title: DelegatingMBeanObserver</p>
 * <p>Description: An MBeanObserver which delegates to an underlying {@link MetricSet}</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.DelegatingMBeanObserver</code></p>
 */

public class DelegatingMBeanObserver extends BaseMBeanObserver {
	
	final MetricSet metricSet;
	DelegatingMBeanObserver(final MBeanObserverBuilder builder) throws Exception {
		super(builder);
		metricSet = builder.getMetricSetImpl().newInstance();
	}

	
	/**
	 * {@inheritDoc}
	 * @see com.codahale.metrics.MetricSet#getMetrics()
	 */
	@Override
	public Map<String, Metric> getMetrics() {
		// TODO Auto-generated method stub
		return null;
	}

}
