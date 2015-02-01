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

import static com.heliosapm.opentsdb.client.opentsdb.Constants.UTF8;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.util.Timeout;
import org.json.JSONArray;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metered;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Reporter;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.heliosapm.opentsdb.client.util.DynamicByteBufferBackedChannelBuffer;
import com.heliosapm.opentsdb.client.util.DynamicByteBufferBackedChannelBufferFactory;

/**
 * <p>Title: OpenTSDBReporter</p>
 * <p>Description: Metrics reporter optimized for OpenTSDB OTMetrics.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.OpenTSDBReporter</code></p>
 */

/**
 * <p>Title: OpenTSDBReporter</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.OpenTSDBReporter</code></p>
 */
public class OpenTSDBReporter implements Reporter, Closeable {
	/** Instance logger */
	protected final Logger log = LogManager.getLogger(getClass());
	/** Started flag */
	protected final AtomicBoolean started = new AtomicBoolean(false);
	/** The OpenTsdb reference */
	protected final OpenTsdb opentsdb = OpenTsdb.getInstance();
	/** A clock for providing time stamps */
	protected final Clock clock;
	/** An optional prefix to add to all metrics reported out of this reporter */
	protected final String prefix;
	/** A handle to this reporter's scheduler */
	protected Timeout scheduleHandle = null;
	/** The metric registry this reporter is reporting from */
	protected final MetricRegistry registry;
	/** A map of tags to add to all metrics reported out of this reporter */
	protected final Map<String, String> tags;
    /** An optional metric filter to be selective about which metrics are reported */
    protected final MetricFilter metricFilter;
    /** The rate conversion unit */
    protected final TimeUnit rateUnit;
    /** The duration conversion unit */
    protected final TimeUnit durationUnit;
    /** The duration modifier factor */
    protected final double durationFactor;
    /** The rate modifier factor */
    protected final double rateFactor;
    
    /** The initial capacity of collection buffers */
    protected int initialCapacity = 1024;
    
    /** The collection buffer factory */
    protected final DynamicByteBufferBackedChannelBufferFactory bufferFactory = new DynamicByteBufferBackedChannelBufferFactory();
    /** The OTMetric cache */
    protected final OTMetricCache otMetricCache = OTMetricCache.getInstance(); 
	
    /**
     * Returns a new {@link Builder} for {@link OpenTSDBReporter}.
     * @param registry The metric registry to be wrapped and registered
     * @return the reporter builder
     */
    public static Builder forRegistry(final MetricRegistry registry) {
    	if(registry==null) throw new IllegalArgumentException("The passed registry was null");
    	return new Builder(registry);
    }
    

	
	/**
	 * Creates a new OpenTSDBReporter
	 * @param registry The metric registry this reporter is reporting from
	 * @param prefix An optional prefix to add to all metrics reported out of this reporter
	 * @param rateUnit The rate conversion unit
	 * @param durationUnit The duration conversion unit
	 * @param filter An optional metric filter to be selective about which metrics are reported
	 */
	public OpenTSDBReporter(final MetricRegistry registry, final String prefix, final TimeUnit rateUnit, final TimeUnit durationUnit, final MetricFilter filter, final Map<String, String> extraTags) {
        this.tags = extraTags;
        this.clock = ConfigurationReader.confBool(Constants.PROP_TIME_IN_SEC, Constants.DEFAULT_TIME_IN_SEC) ? EpochClock.INSTANCE : Clock.defaultClock();
        this.prefix = prefix;
        this.registry = registry;
        this.metricFilter = filter;
        this.rateUnit = rateUnit;
        this.durationUnit = durationUnit;
        this.durationFactor = 1.0 / durationUnit.toNanos(1);
        this.rateFactor = rateUnit.toSeconds(1);
    }

	/**
	 * {@inheritDoc}
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() throws IOException {
		stop();
	}
	
    /**
     * Starts the reporter on the specified period
     * @param period The time period the reporter should run on
     * @param unit The unit of the period
     */
    public void start(final long period, final TimeUnit unit) {
    	if(started.compareAndSet(false, true)) {
	    	final Runnable r = new Runnable() {
	            @Override
	            public void run() {
	                try {
	                    report();
	                } catch (RuntimeException ex) {
	                    log.error("RuntimeException thrown from {}#report. Exception was suppressed.", OpenTSDBReporter.this.getClass().getSimpleName(), ex);
	                }
	            }
	        };
	        scheduleHandle = Threading.getInstance().schedule(r, period, unit);
	        log.debug("Started scheduled task for Registry {}", registry.getNames().toString());
    	}
    }
    
    /**
     * Stops the reporter
     */
    public void stop() {
    	if(started.compareAndSet(true, false)) {
	    	if(scheduleHandle!=null) {
	    		scheduleHandle.cancel();
	    		log.debug("Stopped scheduled task for Registry {}", registry.getNames().toString());
	    	}
    	}    	
    }
	
	
	public void report() {
        synchronized (this) {
            report(registry.getGauges(metricFilter),
                    registry.getCounters(metricFilter),
                    registry.getHistograms(metricFilter),
                    registry.getMeters(metricFilter),
                    registry.getTimers(metricFilter));
        }		
	}
	
	
	private static final byte[] ARR_OPENER = "[".getBytes(UTF8);
	private static final byte[] ARR_CLOSER = "]".getBytes(UTF8);
	
    /**
     * Collects from all the metrics passed and posts to OpenTSDB
     * @param gauges A map of gauges keyed by the metric name
     * @param counters A map of counters keyed by the metric name
     * @param histograms A map of histograms keyed by the metric name
     * @param meters A map of meters keyed by the metric name
     * @param timers A map of timers keyed by the metric name
     */
    public void report(final SortedMap<String, Gauge> gauges, final SortedMap<String, Counter> counters, final SortedMap<String, Histogram> histograms, final SortedMap<String, Meter> meters, final SortedMap<String, Timer> timers) {
    	final DynamicByteBufferBackedChannelBuffer buffer = bufferFactory.getBuffer(initialCapacity);
    	buffer.writeBytes(ARR_OPENER);
        final long timestamp = clock.getTime();
        int metricCount = 0;
        for (Map.Entry<String, Gauge> g : gauges.entrySet()) {
            if(g.getValue().getValue() instanceof Collection && ((Collection<?>)g.getValue().getValue()).isEmpty()) {
                continue;
            }
            metricCount += build(g.getKey(), g.getValue(), timestamp, buffer);
        }
        
        for (Map.Entry<String, Counter> entry : counters.entrySet()) {
        	metricCount += build(entry.getKey(), entry.getValue(), timestamp, buffer);
        }

        for (Map.Entry<String, Histogram> entry : histograms.entrySet()) {
        	metricCount += build(entry.getKey(), entry.getValue(), timestamp, buffer);
        }

        for (Map.Entry<String, Meter> entry : meters.entrySet()) {
        	metricCount += build(entry.getKey(), entry.getValue(), timestamp, buffer);
        }

        for (Map.Entry<String, Timer> entry : timers.entrySet()) {
        	metricCount += build(entry.getKey(), entry.getValue(), timestamp, buffer);
        }
        if(metricCount>0) {
        	buffer.writerIndex(buffer.writerIndex()-1);
        	buffer.writeBytes(ARR_CLOSER);
        	
       	try {
    		JSONArray j = new JSONArray(buffer.toString(UTF8));
    		System.out.println(j.toString(2));
    		
    	} catch (Exception ex) {
    		System.out.println(buffer.toString(UTF8));
    	}
        	
        	
        	opentsdb.send(buffer, metricCount);
        }
    }
    
    protected int build(final String name, final Gauge<?> gauge, final long timestamp, final ChannelBuffer chBuff) {    	
    	otMetricCache.getOTMetric(name, prefix, null, tags)
    		.toJSON(timestamp, gauge.getValue(), chBuff, true);
    	return 1;
    }
    
    protected int build(final String name, final Counter counter, final long timestamp, final ChannelBuffer chBuff) {
    	otMetricCache.getOTMetric(name, prefix, null, tags)
		.toJSON(timestamp, counter.getCount(), chBuff, true);
    	return 1;
    }
    
    protected int build(final String name, final Histogram histogram, final long timestamp, final ChannelBuffer chBuff) {
    	otMetricCache.getOTMetric(name, prefix, "hcount", tags).toJSON(timestamp, histogram.getCount(), chBuff, true);    	
    	return 1 + build(name, histogram.getSnapshot(), timestamp, chBuff, false);
    }
    
    protected int build(final String name, final Meter meter, final long timestamp, final ChannelBuffer chBuff) {
    	otMetricCache.getOTMetric(name, prefix, "mcount", tags).toJSON(timestamp, meter.getCount(), chBuff, true);
    	return 1 + build(name, meter, timestamp, chBuff, true);
    }
    
    protected int build(final String name, final Timer timer, final long timestamp, final ChannelBuffer chBuff) {
    	otMetricCache.getOTMetric(name, prefix, "tcount", tags).toJSON(timestamp, timer.getCount(), chBuff, true);
    	int count = 1;
    	count += build(name, timer.getSnapshot(), timestamp, chBuff, true);
    	return count + build(name, timer, timestamp, chBuff, true);
    }
    
    protected int build(final String name, final Snapshot snapshot, final long timestamp, final ChannelBuffer chBuff, final boolean conv) {
    	otMetricCache.getOTMetric(name, prefix, "min", tags).toJSON(timestamp, cd(snapshot.getMin(), conv), chBuff, true);
    	otMetricCache.getOTMetric(name, prefix, "max", tags).toJSON(timestamp, cd(snapshot.getMax(), conv), chBuff, true);
    	otMetricCache.getOTMetric(name, prefix, "mean", tags).toJSON(timestamp, cd(snapshot.getMean(), conv), chBuff, true);
    	
    	otMetricCache.getOTMetric(name, prefix, "stddev", tags).toJSON(timestamp, cd(snapshot.getStdDev(), conv), chBuff, true);
    	otMetricCache.getOTMetric(name, prefix, "median", tags).toJSON(timestamp, cd(snapshot.getMedian(), conv), chBuff, true);
    	
    	otMetricCache.getOTMetric(name, prefix, "p75", tags).toJSON(timestamp, cd(snapshot.get75thPercentile(), conv), chBuff, true);
    	otMetricCache.getOTMetric(name, prefix, "p95", tags).toJSON(timestamp, cd(snapshot.get95thPercentile(), conv), chBuff, true);
    	otMetricCache.getOTMetric(name, prefix, "p98", tags).toJSON(timestamp, cd(snapshot.get98thPercentile(), conv), chBuff, true);
    	otMetricCache.getOTMetric(name, prefix, "p99", tags).toJSON(timestamp, cd(snapshot.get99thPercentile(), conv), chBuff, true);
    	otMetricCache.getOTMetric(name, prefix, "p999", tags).toJSON(timestamp, cd(snapshot.get999thPercentile(), conv), chBuff, true);
    	return 10;
    	
    }
    
    protected int build(final String name, final Metered meter, final long timestamp, final ChannelBuffer chBuff, final boolean conv) {
    	otMetricCache.getOTMetric(name, prefix, "mean_rate", tags).toJSON(timestamp, cr(meter.getMeanRate(), conv), chBuff, true);
    	otMetricCache.getOTMetric(name, prefix, "m1", tags).toJSON(timestamp, cr(meter.getOneMinuteRate(), conv), chBuff, true);
    	otMetricCache.getOTMetric(name, prefix, "m5", tags).toJSON(timestamp, cr(meter.getFiveMinuteRate(), conv), chBuff, true);
    	otMetricCache.getOTMetric(name, prefix, "m15", tags).toJSON(timestamp, cr(meter.getFifteenMinuteRate(), conv), chBuff, true);
    	return 4;
    	
    }

    protected double cd(final double duration, final boolean conv) {
        return conv ? duration * durationFactor : duration;
    }

    protected double cr(final double rate, final boolean conv) {
        return conv ? rate * rateFactor : rate;
    }
    


	
	
    /**
     * A builder for {@link OpenTSDBReporter} instances. Defaults to not using a prefix, using the
     * default clock, converting rates to events/second, converting durations to milliseconds, and
     * not filtering metrics.
     */
    public static class Builder {
        private final MetricRegistry registry;        
        private String prefix;
        private TimeUnit rateUnit;
        private TimeUnit durationUnit;
        private MetricFilter filter;
        private Map<String, String> tags;

        private Builder(final MetricRegistry registry) {
            this.registry = registry;
            this.prefix = null;
            this.rateUnit = TimeUnit.SECONDS;
            this.durationUnit = TimeUnit.MILLISECONDS;
            this.filter = MetricFilter.ALL;            
        }


        /**
         * Prefix all metric names with the given string.
         *
         * @param prefix the prefix for all metric names
         * @return {@code this}
         */
        public Builder prefixedWith(String prefix) {
            this.prefix = prefix;
            return this;
        }

        /**
         * Convert rates to the given time unit.
         *
         * @param rateUnit a unit of time
         * @return {@code this}
         */
        public Builder convertRatesTo(TimeUnit rateUnit) {
            this.rateUnit = rateUnit;
            return this;
        }

        /**
         * Convert durations to the given time unit.
         *
         * @param durationUnit a unit of time
         * @return {@code this}
         */
        public Builder convertDurationsTo(TimeUnit durationUnit) {
            this.durationUnit = durationUnit;
            return this;
        }

        /**
         * Only report metrics which match the given filter.
         *
         * @param filter a {@link MetricFilter}
         * @return {@code this}
         */
        public Builder filter(MetricFilter filter) {
            this.filter = filter;
            return this;
        }

        /**
         * Append tags to all reported metrics
         *
         * @param tags The tags to append
         * @return this builder
         */
        public Builder withTags(Map<String, String> tags) {
            this.tags = tags;
            return this;
        }

 
        /**
         * Builds a {@link OpenTSDBReporter} with the given properties, sending metrics using the
         * given {@link com.heliosapm.opentsdb.client.opentsdb.OpenTsdb} client.
         *
         * @return a {@link OpenTsdbReporter}
         */
        public OpenTSDBReporter build() {            
            return new OpenTSDBReporter(registry,                    
                    prefix,
                    rateUnit,
                    durationUnit,
                    filter,
                    tags);
        }
    }
	

}
