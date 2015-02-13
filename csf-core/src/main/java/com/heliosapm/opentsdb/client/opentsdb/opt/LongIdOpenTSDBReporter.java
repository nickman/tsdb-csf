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

import static com.heliosapm.opentsdb.client.opentsdb.Constants.UTF8;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.util.Timeout;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metered;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Reporter;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.heliosapm.opentsdb.client.opentsdb.ConfigurationReader;
import com.heliosapm.opentsdb.client.opentsdb.Constants;
import com.heliosapm.opentsdb.client.opentsdb.EpochClock;
import com.heliosapm.opentsdb.client.opentsdb.OTMetric;
import com.heliosapm.opentsdb.client.opentsdb.OTMetricCache;
import com.heliosapm.opentsdb.client.opentsdb.OpenTSDBReporter;
import com.heliosapm.opentsdb.client.opentsdb.OpenTsdb;
import com.heliosapm.opentsdb.client.opentsdb.Threading;
import com.heliosapm.opentsdb.client.util.DynamicByteBufferBackedChannelBuffer;
import com.heliosapm.opentsdb.client.util.DynamicByteBufferBackedChannelBufferFactory;

/**
 * <p>Title: LongIdOpenTSDBReporter</p>
 * <p>Description: The optimized metric reporter</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.opt.LongIdOpenTSDBReporter</code></p>
 */

public class LongIdOpenTSDBReporter implements Reporter, Closeable {
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
	protected final LongIdMetricRegistry registry;
	/** A map of tags to add to all metrics reported out of this reporter */
	protected final Map<String, String> tags;
    /** An optional metric filter to be selective about which metrics are reported */
    protected final OTMetricFilter metricFilter;
    /** The rate conversion unit */
    protected final TimeUnit rateUnit;
    /** The duration conversion unit */
    protected final TimeUnit durationUnit;
    /** The duration modifier factor */
    protected final double durationFactor;
    /** The rate modifier factor */
    protected final double rateFactor;
    /** The snapshot sub-metric mask */
    protected final int snapshotMask;
    /** The metered sub-metric mask */
    protected final int meteredMask;
    
    /** The initial capacity of collection buffers */
    protected int initialCapacity = 1024;
    
    /** The collection buffer factory */
    protected final DynamicByteBufferBackedChannelBufferFactory bufferFactory = new DynamicByteBufferBackedChannelBufferFactory();
    /** The OTMetric cache */
    protected final LongIdOTMetricCache otMetricCache = LongIdOTMetricCache.getInstance(); 
	
    /**
     * Returns a new {@link Builder} for {@link OpenTSDBReporter}.
     * @param registry The metric registry to be wrapped and registered
     * @return the reporter builder
     */
    public static Builder forRegistry(final LongIdMetricRegistry registry) {
    	if(registry==null) throw new IllegalArgumentException("The passed registry was null");
    	return new Builder(registry);
    }
    

	
	/**
	 * Creates a new LongIdOpenTSDBReporter
	 * @param registry The metric registry this reporter is reporting from
	 * @param prefix An optional prefix to add to all metrics reported out of this reporter
	 * @param rateUnit The rate conversion unit
	 * @param durationUnit The duration conversion unit
	 * @param filter An optional metric filter to be selective about which metrics are reported
	 * @param extraTags Optional extra tags to all submissions made from this reporter
	 * @param snapshotMask The snapshot sub-metric mask
	 * @param meteredMask The metered sub-metric mask
	 */
	public LongIdOpenTSDBReporter(final LongIdMetricRegistry registry, final String prefix, final TimeUnit rateUnit, final TimeUnit durationUnit, final OTMetricFilter filter, final Map<String, String> extraTags, final int snapshotMask, final int meteredMask) {
        this.tags = extraTags;
        this.clock = ConfigurationReader.confBool(Constants.PROP_TIME_IN_SEC, Constants.DEFAULT_TIME_IN_SEC) ? EpochClock.INSTANCE : Clock.defaultClock();
        this.prefix = prefix;
        this.registry = registry;
        this.metricFilter = filter;
        this.rateUnit = rateUnit;
        this.durationUnit = durationUnit;
        this.durationFactor = 1.0 / durationUnit.toNanos(1);
        this.rateFactor = rateUnit.toSeconds(1);
        this.snapshotMask = snapshotMask;
        this.meteredMask = meteredMask;
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
	                    log.error("RuntimeException thrown from {}#report. Exception was suppressed.", LongIdOpenTSDBReporter.this.getClass().getSimpleName(), ex);
	                }
	            }
	        };
	        scheduleHandle = Threading.getInstance().schedule(r, period, unit);
    	}
    }
    
    /**
     * Stops the reporter
     */
    public void stop() {
    	if(started.compareAndSet(true, false)) {
	    	if(scheduleHandle!=null) {
	    		scheduleHandle.cancel();
	    		log.debug("Stopped scheduled task for opt Registry");
	    	}
    	}    	
    }
	
	
	/**
	 * Gathers and submits all metrics for the underlying metric registry
	 */
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
    public void report(final Map<OTMetric, Gauge> gauges, final Map<OTMetric, Counter> counters, final Map<OTMetric, Histogram> histograms, final Map<OTMetric, Meter> meters, final Map<OTMetric, Timer> timers) {
    	final DynamicByteBufferBackedChannelBuffer buffer = bufferFactory.getBuffer(initialCapacity);
    	buffer.writeBytes(ARR_OPENER);
        final long timestamp = clock.getTime();
        int metricCount = 0;
        for (Map.Entry<OTMetric, Gauge> g : gauges.entrySet()) {
        	final Object value = g.getValue().getValue(); 
            metricCount += build(g.getKey(), value, timestamp, buffer);
        }
        
        for (Map.Entry<OTMetric, Counter> entry : counters.entrySet()) {
        	metricCount += build(entry.getKey(), entry.getValue(), timestamp, buffer);
        }

        for (Map.Entry<OTMetric, Histogram> entry : histograms.entrySet()) {
        	metricCount += build(entry.getKey(), entry.getValue(), timestamp, buffer);
        }

        for (Map.Entry<OTMetric, Meter> entry : meters.entrySet()) {
        	metricCount += build(entry.getKey(), entry.getValue(), timestamp, buffer);
        }

        for (Map.Entry<OTMetric, Timer> entry : timers.entrySet()) {
        	metricCount += build(entry.getKey(), entry.getValue(), timestamp, buffer);
        }
        if(metricCount>0) {
        	buffer.writerIndex(buffer.writerIndex()-1);
        	buffer.writeBytes(ARR_CLOSER);
        	
//       	try {
//    		JSONArray j = new JSONArray(buffer.toString(UTF8));
//    		System.out.println(j.toString(2));    		
//    	} catch (Exception ex) {
//    		System.out.println(buffer.toString(UTF8));
//    	}
        	
        	
        	opentsdb.send(buffer, metricCount);
        }
    }
    
    /**
     * Traces a value for the passed OTMetric provided by the passed Metric
     * @param otm The OTMetric defining the metric name
     * @param gaugeValue The value to trace
     * @param timestamp The timestamp of the metric
     * @param chBuff The buffer to write to
     * @return the number of metrics traced
     */
    protected int build(final OTMetric otm, final Object gaugeValue, final long timestamp, final ChannelBuffer chBuff) {
    	final String value = gaugeValue.toString();
    	otm.toJSON(timestamp, value, chBuff, true);
    	return 1;
    }
    
    /**
     * Traces a value for the passed OTMetric provided by the passed Metric
     * @param otm The OTMetric defining the metric name
     * @param counter The counter to trace the value from
     * @param timestamp The timestamp of the metric
     * @param chBuff The buffer to write to
     * @return the number of metrics traced
     */
    protected int build(final OTMetric otm, final Counter counter, final long timestamp, final ChannelBuffer chBuff) {
    	otm.toJSON(timestamp, counter.getCount(), chBuff, true);
    	return 1;
    }
    
    protected int build(final OTMetric otm, final Histogram histogram, final long timestamp, final ChannelBuffer chBuff) {
    	registry.register(otm, histogram, "hcount").toJSON(timestamp, histogram.getCount(), chBuff, true);
    	return 1 + build(otm, histogram, histogram.getSnapshot(), timestamp, chBuff, false);
    }
    
    protected int build(final OTMetric otm, final Meter meter, final long timestamp, final ChannelBuffer chBuff) {
    	registry.register(otm, meter, "mcount").toJSON(timestamp, meter.getCount(), chBuff, true);
    	return 1 + build(otm, meter, timestamp, chBuff, true);
    }
    
    protected int build(final OTMetric otm, final Timer timer, final long timestamp, final ChannelBuffer chBuff) {
    	registry.register(otm, timer, "tcount").toJSON(timestamp, timer.getCount(), chBuff, true);
    	return 1 + build(otm, timer, timestamp, chBuff, true);
    }
    
    protected int build(final OTMetric otm, final Metric parentMetric, final Snapshot snapshot, final long timestamp, final ChannelBuffer chBuff, final boolean conv) {
    	final OTSnapshot[] subs = OTSnapshot.getEnabled(snapshotMask);
    	for(OTSnapshot snap: subs) {
    		registry.register(otm, parentMetric, snap.name()).toJSON(timestamp, snap.get(snapshot), chBuff, true);
    	}
    	return subs.length;
    }
    
    protected int build(final OTMetric otm, final Metered meter, final long timestamp, final ChannelBuffer chBuff, final boolean conv) {
    	final OTMetered[] mets = OTMetered.getEnabled(meteredMask & ~OTMetered.mcount.mask);
    	for(OTMetered met: mets) {
    		registry.register(otm, meter, met.name()).toJSON(timestamp, met.get(meter), chBuff, true);
    	}
    	return mets.length;
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
        private final LongIdMetricRegistry registry;        
        private String prefix;
        private TimeUnit rateUnit;
        private TimeUnit durationUnit;
        private OTMetricFilter filter;
        private Map<String, String> tags;
        private int snapshotMask = OTSnapshot.ALL;
        private int meteredMask = OTMetered.ALL;

        private Builder(final LongIdMetricRegistry registry) {
            this.registry = registry;
            this.prefix = null;
            this.rateUnit = TimeUnit.SECONDS;
            this.durationUnit = TimeUnit.MILLISECONDS;
            this.filter = OTMetricFilter.ALL;            
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
         * @param filter a {@link OTMetricFilter}
         * @return {@code this}
         */
        public Builder filter(OTMetricFilter filter) {
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
         * Sets the snapshot sub-metric mask
         * @param mask the snapshot sub-metric mask
         * @return this builder
         */
        public Builder withSnapshotMask(final int mask) {
        	this.snapshotMask = mask;
        	return this;
        }
        
        /**
         * Sets the snapshot sub-metric mask
         * @param options the snapshot members to enable
         * @return this builder
         */
        public Builder withSnapshotMask(final OTSnapshot...options) {
        	this.snapshotMask = OTSnapshot.getMaskFor(options);
        	return this;
        }
        
        /**
         * Sets the snapshot sub-metric mask
         * @param options the snapshot member names to enable
         * @return this builder
         */
        public Builder withSnapshotMask(final String...options) {
        	this.snapshotMask = OTSnapshot.getMaskFor(options);
        	return this;
        }

        /**
         * Sets the metered sub-metric mask
         * @param mask the metered sub-metric mask
         * @return this builder
         */
        public Builder withMeteredMask(final int mask) {
        	this.meteredMask = mask;
        	return this;
        }
        
        /**
         * Sets the metered sub-metric mask
         * @param options the metered members to enable
         * @return this builder
         */
        public Builder withMeteredMask(final OTMetered...options) {
        	this.meteredMask = OTMetered.getMaskFor(options);
        	return this;
        }
        
        /**
         * Sets the metered sub-metric mask
         * @param options the metered member names to enable
         * @return this builder
         */
        public Builder withMeteredMask(final String...options) {
        	this.meteredMask = OTMetered.getMaskFor(options);
        	return this;
        }
 
        /**
         * Builds a {@link LongIdOpenTSDBReporter} with the given properties, sending metrics using the
         * given {@link com.heliosapm.opentsdb.client.opentsdb.OpenTsdb} client.
         *
         * @return a {@link LongIdOpenTSDBReporter}
         */
        public LongIdOpenTSDBReporter build() {            
            return new LongIdOpenTSDBReporter(registry,                    
                    prefix,
                    rateUnit,
                    durationUnit,
                    filter,
                    tags,
                    snapshotMask,
                    meteredMask);
        }
    }





	/**
	 * Returns the 
	 * @return the registry
	 */
	public LongIdMetricRegistry getRegistry() {
		return registry;
	}
	

}
