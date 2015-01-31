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

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.util.Timeout;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Reporter;
import com.codahale.metrics.Timer;

/**
 * <p>Title: OpenTSDBReporter</p>
 * <p>Description: Metrics reporter optimized for OpenTSDB OTMetrics.</p> 
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
	 * @param clock A clock for providing time stamps
	 * @param prefix An optional prefix to add to all metrics reported out of this reporter
	 * @param rateUnit The rate conversion unit
	 * @param durationUnit The duration conversion unit
	 * @param filter An optional metric filter to be selective about which metrics are reported
	 */
	public OpenTSDBReporter(final MetricRegistry registry, final Clock clock, final String prefix, final TimeUnit rateUnit, final TimeUnit durationUnit, final MetricFilter filter) {
        tags = new TreeMap<String, String>();
        this.clock = clock;
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
		
	}
	
    /**
     * Collects from all the metrics passed and posts to OpenTSDB
     * @param gauges A map of gauges keyed by the metric name
     * @param counters A map of counters keyed by the metric name
     * @param histograms A map of histograms keyed by the metric name
     * @param meters A map of meters keyed by the metric name
     * @param timers A map of timers keyed by the metric name
     */
    public void report(final SortedMap<String, Gauge> gauges, final SortedMap<String, Counter> counters, final SortedMap<String, Histogram> histograms, final SortedMap<String, Meter> meters, final SortedMap<String, Timer> timers) {
        final long timestamp = clock.getTime() / 1000;
        //final Set<OTMetric> metrics = new HashSet<OTMetric>();
        final ChannelBuffer buff = ChannelBuffers.dynamicBuffer();
        for (Map.Entry<String, Gauge> g : gauges.entrySet()) {
            if(g.getValue().getValue() instanceof Collection && ((Collection)g.getValue().getValue()).isEmpty()) {
                continue;
            }
            metrics.add(buildGauge(g.getKey(), g.getValue(), timestamp, buff));
        }

        for (Map.Entry<String, Counter> entry : counters.entrySet()) {
            metrics.add(buildCounter(entry.getKey(), entry.getValue(), timestamp));
        }

        for (Map.Entry<String, Histogram> entry : histograms.entrySet()) {
            metrics.addAll(buildHistograms(entry.getKey(), entry.getValue(), timestamp));
        }

        for (Map.Entry<String, Meter> entry : meters.entrySet()) {
            metrics.addAll(buildMeters(entry.getKey(), entry.getValue(), timestamp));
        }

        for (Map.Entry<String, Timer> entry : timers.entrySet()) {
            metrics.addAll(buildTimers(entry.getKey(), entry.getValue(), timestamp));
        }

//        opentsdb.send(metrics);
    }
    
    private OTMetric buildGauge(String name, Gauge gauge, long timestamp) {    	
    	
        return OpenTsdbMetric.tsdbName(name, "value")
                .withValue(gauge.getValue()) 
                .withTimestamp(timestamp)
                .withTags(tags)
                .build();
    }

	
	
    /**
     * A builder for {@link OpenTSDBReporter} instances. Defaults to not using a prefix, using the
     * default clock, converting rates to events/second, converting durations to milliseconds, and
     * not filtering metrics.
     */
    public static class Builder {
        private final MetricRegistry registry;
        private Clock clock;
        private String prefix;
        private TimeUnit rateUnit;
        private TimeUnit durationUnit;
        private MetricFilter filter;
        private Map<String, String> tags;

        private Builder(final MetricRegistry registry) {
            this.registry = registry;
            this.clock = Clock.defaultClock();
            this.prefix = null;
            this.rateUnit = TimeUnit.SECONDS;
            this.durationUnit = TimeUnit.MILLISECONDS;
            this.filter = MetricFilter.ALL;            
        }

        /**
         * Use the given {@link Clock} instance for the time.
         *
         * @param clock a {@link Clock} instance
         * @return {@code this}
         */
        public Builder withClock(Clock clock) {
            this.clock = clock;
            return this;
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
                    clock,
                    prefix,
                    rateUnit,
                    durationUnit,
                    filter);
        }
    }
	

}
