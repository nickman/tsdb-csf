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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import jsr166e.LongAdder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cliffc.high_scale_lib.NonBlockingHashMapLong;
import org.cliffc.high_scale_lib.NonBlockingHashMapLong.IteratorLong;
import org.jboss.netty.buffer.ChannelBuffer;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.heliosapm.opentsdb.client.opentsdb.ConfigurationReader;
import com.heliosapm.opentsdb.client.opentsdb.Constants;
import com.heliosapm.opentsdb.client.opentsdb.MetricBuilder;
import com.heliosapm.opentsdb.client.opentsdb.OTMetric;
import com.heliosapm.opentsdb.client.opentsdb.opt.LongIdMetricRegistry;
import com.heliosapm.opentsdb.client.opentsdb.opt.LongIdOTMetricCache;
import com.heliosapm.opentsdb.client.opentsdb.opt.LongIdOpenTSDBReporter;
import com.heliosapm.opentsdb.client.opentsdb.opt.Measurement;
import com.heliosapm.opentsdb.client.opentsdb.opt.ValueArrayAggregator;
import com.heliosapm.opentsdb.client.util.DynamicByteBufferBackedChannelBufferFactory;
import com.heliosapm.opentsdb.client.util.JMXHelper;

/**
 * <p>Title: MetricSink</p>
 * <p>Description: The metric ingestion sink.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.sink.MetricSink</code></p>
 */

public class MetricSink implements Runnable, IMetricSink, MetricSinkMBean {
	/** The metric sink singleton instance */
	private static volatile MetricSink instance = null;
	/** The metric sink singleton instance ctor lock */
	private static final Object lock = new Object();
	
	/** The buffer factory for allocating buffers to stream metrics out to the tracer */
    private final DynamicByteBufferBackedChannelBufferFactory bufferFactory = new DynamicByteBufferBackedChannelBufferFactory(128);
    /** Instance logger */
    protected final Logger log = LogManager.getLogger(getClass());
	/** Keeps the count of the number of metrics buffered pending a flush */
    protected final AtomicInteger counter = new AtomicInteger(0);
	/** The maximum number of metrics appended before a flush */
	protected int sizeThreshold;
	/** The maximum elapsed time in ms. before a flush */
	protected long timeThreshold;
	/** The size of the input queue */
	protected final int inputQueueSize;
	/** The fairness of the input queue */
	protected final boolean inputQueueFair;
	/** The input queue */
	protected final BlockingQueue<long[]> inputQueue;
	/** The input queue processor thread */
	protected Thread inputProcessorThread = null;
	/** The metric registry */
	protected final LongIdMetricRegistry registry = LongIdMetricRegistry.getInstance();
	/** The metric reporter */   // FIXME:  add options for reporter
	protected final LongIdOpenTSDBReporter reporter = LongIdOpenTSDBReporter.forRegistry(registry).build();
	/** The opt cache */
	protected final LongIdOTMetricCache otCache = LongIdOTMetricCache.getInstance(); 
	/** The input queue processor thread */
	protected final Thread inputProcessor;
	
	
	
	// ==================================================================================================
	//		Temp Dev Constructs
	// ==================================================================================================
	/** The aggregate metrics */
	protected final NonBlockingHashMapLong<Map<Measurement, Metric>> aggregateMetrics = new NonBlockingHashMapLong<Map<Measurement, Metric>>();
	/** Keeps references for metrics in play */
	protected final NonBlockingHashMapLong<OTMetric> refKeeper = new NonBlockingHashMapLong<OTMetric>();

	// ==================================================================================================
	
	/** The buffer metrics are appended to */
	protected ChannelBuffer metricBuffer = bufferFactory.getBuffer(4096);
	/** The last flush time */
	protected final AtomicLong lastFlush = new AtomicLong(0);
	/** The flush in progress flag */
	protected final AtomicBoolean flushInProgress = new AtomicBoolean(false);
	/** The input Q in progress flag */
	protected final AtomicBoolean inputInProgress = new AtomicBoolean(true);

	/** A shared counter to track metric submissions dropped on account of a full input queue */
	protected final LongAdder fullQueueDrops = new LongAdder();
	/** A counter to track the number of processed metric submissions */
	protected final LongAdder processedMetrics = new LongAdder();
	
	/**
	 * Acquires the MetricSink singleton instance
	 * @return the MetricSink singleton instance
	 */
	public static MetricSink sink() {
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
		inputQueueSize = ConfigurationReader.confInt(Constants.PROP_SINK_INPUT_QSIZE, Constants.DEFAULT_SINK_INPUT_QSIZE);
		inputQueueFair = ConfigurationReader.confBool(Constants.PROP_SINK_INPUT_QFAIR, Constants.DEFAULT_SINK_INPUT_QFAIR);
		inputQueue = new ArrayBlockingQueue<long[]>(inputQueueSize, inputQueueFair); 
		inputProcessor = new Thread(getQProcessorTask(), "InputQProcessor");
		inputProcessor.setDaemon(true);
		inputProcessor.start();
		try {
			JMXHelper.registerMBean(this, OBJECT_NAME);
		} catch (Exception ex) {
			log.warn("Failed to register MetricSink JMX interface. Will continue without:" + ex);
		}
	}
	
	/**
	 * Creates the input queue processor task
	 * @return the input queue processor task
	 */
	protected Runnable getQProcessorTask() {
		return new Runnable() {			
			final Set<long[]> submissions = new HashSet<long[]>(); 
			final int maxDrain = sizeThreshold - 1;
			public void run() {
				while(true) {
					try {
						submissions.add(inputQueue.take());
						final int qsize = inputQueue.size();
						if(qsize>0) {
							inputQueue.drainTo(submissions, maxDrain);
						}
						processedMetrics.add(submissions.size());
						
						for(long[] valueArr: submissions) {
							final long metricId = valueArr[1];
							final OTMetric otMetric = otCache.getOTMetric(metricId);
							if(refKeeper.putIfAbsent(metricId, otMetric)==null) {
								for(Measurement m: otMetric.getMeasurements()) {
									final OTMetric subMetric = MetricBuilder.metric(otMetric, true).tag("submetric", m.shortName).measurement(m).optBuild();
									refKeeper.put(subMetric.longHashCode(), subMetric);
								}
							}
							Map<Measurement, Metric> metricMap = aggregateMetrics.get(metricId);
							if(metricMap==null) {
								metricMap = ValueArrayAggregator.aggregate(valueArr, metricMap);
								aggregateMetrics.put(metricId, metricMap);
							} else {
								ValueArrayAggregator.aggregate(valueArr, aggregateMetrics.get(metricId));
							}
						}
					} catch (InterruptedException iex) {
						if(inputInProgress.get()) {
							if(Thread.interrupted()) Thread.interrupted();							
						} else {
							break;
						}
					} catch (Exception ex) {
						log.error("MetricSink InputQ Processor Error", ex);
					} finally {
						submissions.clear();
					}
				}
			}
		};
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.sink.MetricSinkMBean#getInputQueueDepth()
	 */
	@Override
	public int getInputQueueDepth() {
		return inputQueue.size();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.sink.MetricSinkMBean#getInputQueueFree()
	 */
	@Override
	public int getInputQueueFree() {	
		return inputQueue.remainingCapacity();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.sink.MetricSinkMBean#getMetricMapCount()
	 */
	@Override
	public int getMetricMapCount() {
		return aggregateMetrics.size();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.sink.MetricSinkMBean#getProcessedArrays()
	 */
	@Override
	public long getProcessedArrays() {
		return processedMetrics.longValue();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.sink.MetricSinkMBean#getInputQueueDropCount()
	 */
	@Override
	public long getInputQueueDropCount() {	
		return fullQueueDrops.longValue();
	}
	
	/**
	 * Renders the current state of the aggregated metrics to a formated string
	 * @return the metric report
	 */
	public String renderMetrics() {
		final StringBuilder b = new StringBuilder("=====================================");
		//NonBlockingHashMapLong<Map<Measurement, Metric>> aggregateMetrics
		final IteratorLong il = (IteratorLong)aggregateMetrics.keySet().iterator();
		while(il.hasNext()) {
			final long metricId = il.nextLong();
			final Map<Measurement, Metric> metricMap = aggregateMetrics.get(metricId);
			final OTMetric metric = otCache.getOTMetric(metricId);
			b.append("\nMetric:").append(metric.toString());
			for(Map.Entry<Measurement, Metric> entry: metricMap.entrySet()) {
				b.append("\n\t").append(entry.getKey().shortName).append(":").append(print(entry.getValue()));
			}
		}
		return b.toString();
	}
	
	protected String print(final Metric metric) {
		final Map<String, Object> values = new HashMap<String, Object>();
		String type = "Unknown:";
		if(metric instanceof Gauge) {
			values.put("value", ((Gauge)metric).getValue());
			type = "Gauge:";
		} else if(metric instanceof Timer) {
			type = "Timer:";
			Timer timer = (Timer)metric;
			values.put("count", timer.getCount());
			values.put("15m", timer.getFifteenMinuteRate());
			values.put("5m", timer.getFiveMinuteRate());
			values.put("1m", timer.getOneMinuteRate());
			values.put("meanRate", timer.getMeanRate());
			Snapshot snap = timer.getSnapshot();
			values.put("max", snap.getMax());
			values.put("min", snap.getMin());
			values.put("med", snap.getMedian());
			values.put("mean", snap.getMean());
			values.put("stdev", snap.getStdDev());
			values.put("75pct", snap.get75thPercentile());
			values.put("95pct", snap.get95thPercentile());
			values.put("98pct", snap.get98thPercentile());
			values.put("99pct", snap.get99thPercentile());
			values.put("999pct", snap.get999thPercentile());			
		} else if(metric instanceof Counter) {
			type = "Counter:";
			Counter counter = (Counter)metric;
			values.put("count", counter.getCount());
		} else if(metric instanceof Meter) {
			type = "Meter:";
			Meter meter = (Meter)metric;
			values.put("count", meter.getCount());
			values.put("15m", meter.getFifteenMinuteRate());
			values.put("5m", meter.getFiveMinuteRate());
			values.put("1m", meter.getOneMinuteRate());
			values.put("meanRate", meter.getMeanRate());
		}				
		return type + values.toString();
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.sink.IMetricSink#getConcurrencyCounter(long)
	 */
	@Override
	public AtomicInteger getConcurrencyCounter(long parentMetricId) {		
		return LongIdOTMetricCache.getInstance().getCounter(parentMetricId);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.sink.IMetricSink#submit(long[])
	 */
	@Override
	public void submit(final long[] measurements) {
		if(!inputQueue.offer(measurements)) {
			fullQueueDrops.increment();
		}		
	}

	/**
	 * {@inheritDoc}
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		if(flushInProgress.compareAndSet(false, true)) {
			try {
				flush();
			} finally {
				flushInProgress.set(false);
			}
		}		
	}
	
	protected void flush() {
	}
	
//	public long instrument(final Method method, final String namingPattern, final int measurementMask, final int subMetricMask) {
//		
//	}
	
}
