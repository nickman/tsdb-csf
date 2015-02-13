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

import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.buffer.ChannelBuffer;

import com.heliosapm.opentsdb.client.opentsdb.ConfigurationReader;
import com.heliosapm.opentsdb.client.opentsdb.Constants;
import com.heliosapm.opentsdb.client.util.DynamicByteBufferBackedChannelBufferFactory;

/**
 * <p>Title: MetricSink</p>
 * <p>Description: The metric ingestion sink.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.opt.MetricSink</code></p>
 */

public class MetricSink {
	/** The metric sink singleton instance */
	private static volatile MetricSink instance = null;
	/** The metric sink singleton instance ctor lock */
	private static final Object lock = new Object();
	
	/** The buffer factory for allocating buffers to stream metrics out to the tracer */
    private final DynamicByteBufferBackedChannelBufferFactory bufferFactory = new DynamicByteBufferBackedChannelBufferFactory(128);

	/** Keeps the count of the number of metrics buffered pending a flush */
	protected final AtomicInteger counter = new AtomicInteger(0);
	/** The maximum number of metrics appended before a flush */
	protected int sizeThreshold;
	/** The maximum elapsed time in ms. before a flush */
	protected long timeThreshold;
	/** The buffer metrics are appended to */
	protected ChannelBuffer metricBuffer = bufferFactory.getBuffer(4096);
	/** The metric registry */
	protected final LongIdMetricRegistry registry = LongIdMetricRegistry.getInstance();
	/** The metric reporter */   // FIXME:  add options for reporter
	protected final LongIdOpenTSDBReporter reporter = LongIdOpenTSDBReporter.forRegistry(registry).build();
	/** The opt cache */
	protected final LongIdOTMetricCache otCache = LongIdOTMetricCache.getInstance(); 
	/**
	 * Acquires the MetricSink singleton instance
	 * @return the MetricSink singleton instance
	 */
	public static MetricSink hub() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new MetricSink();
				}
			}
		}
		return instance;
	}

	/**
	 * Creates a new IngestionHub
	 */
	private MetricSink() {
		sizeThreshold = ConfigurationReader.confInt(Constants.PROP_SINK_SIZE_TRIGGER, Constants.DEFAULT_SINK_SIZE_TRIGGER);
		timeThreshold = ConfigurationReader.confLong(Constants.PROP_SINK_TIME_TRIGGER, Constants.DEFAULT_SINK_TIME_TRIGGER);
	}

	
	
}
