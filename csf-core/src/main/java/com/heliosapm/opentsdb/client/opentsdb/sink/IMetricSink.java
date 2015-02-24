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

package com.heliosapm.opentsdb.client.opentsdb.sink;

import java.util.concurrent.atomic.AtomicInteger;


/**
 * <p>Title: IMetricSink</p>
 * <p>Description: The generalized interface exposed to callers submitting generic metrics. 
 * Reduces the footprint of the classes the caller needs to know about.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.sink.IMetricSink</code></p>
 */

public interface IMetricSink {
	/**
	 * Accepts a metric submission
	 * @param measurements The opt metrc to enqueue
	 */
	public void submit(long[] measurements);
	
	/**
	 * Returns the concurrency counter for the passed parent metric id
	 * @param parentMetricId The parent metric id for the concurrency counter
	 * @return the concurrency counter
	 */
	public AtomicInteger getConcurrencyCounter(final long parentMetricId);
	

}
