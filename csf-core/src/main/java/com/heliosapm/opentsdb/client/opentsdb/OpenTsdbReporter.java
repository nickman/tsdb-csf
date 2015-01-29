/*
 * Copyright 2014 the original author or authors.
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
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import javax.management.ObjectName;

import org.jboss.netty.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Reporter;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.heliosapm.opentsdb.client.logging.LoggingConfiguration;
import com.heliosapm.opentsdb.client.registry.IMetricRegistry;
import com.heliosapm.opentsdb.client.registry.IMetricRegistryFactory;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * A reporter which publishes metric values to a OpenTSDB server.
 *
 * @author Sean Scanlon <sean.scanlon@gmail.com>
 */
public class OpenTsdbReporter implements Closeable, Reporter {

    private final OpenTsdb opentsdb;
    private final Clock clock;
    private final String prefix;
    private Timeout scheduleHandle = null;
    private final IMetricRegistry registry;
    private final Map<String, String> tags;
    private static final Logger LOG;
    protected final MetricFilter metricFilter;
    protected final TimeUnit rateUnit;
    protected final TimeUnit durationUnit;
    protected final double durationFactor;
    protected final double rateFactor;
    protected final AtomicBoolean started = new AtomicBoolean(false);
    
    

    /**
     * Returns a new {@link Builder} for {@link OpenTsdbReporter}.
     *
     * @param registry the registry to report
     * @return a {@link Builder} instance for a {@link OpenTsdbReporter}
     */
    public static Builder forRegistry(final IMetricRegistry registry) {
    	if(registry==null) throw new IllegalArgumentException("The passed registry was null");
        return new Builder(registry);
    }
    
    /**
     * Returns a new {@link Builder} for {@link OpenTsdbReporter}.
     * @param registry The metric registry to be wrapped and registered
     * @return the reporter builder
     */
    public static Builder forRegistry(final MetricRegistry registry) {
    	if(registry==null) throw new IllegalArgumentException("The passed registry was null");
    	return new Builder(IMetricRegistryFactory.wrap(registry));
    }
    
    

    /**
     * A builder for {@link OpenTsdbReporter} instances. Defaults to not using a prefix, using the
     * default clock, converting rates to events/second, converting durations to milliseconds, and
     * not filtering metrics.
     */
    public static class Builder {
        private final IMetricRegistry registry;
        private Clock clock;
        private String prefix;
        private TimeUnit rateUnit;
        private TimeUnit durationUnit;
        private MetricFilter filter;
        private Map<String, String> tags;

        private Builder(IMetricRegistry registry) {
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
         * @param tags
         * @return
         */
        public Builder withTags(Map<String, String> tags) {
            this.tags = tags;
            return this;
        }

 
        /**
         * Builds a {@link OpenTsdbReporter} with the given properties, sending metrics using the
         * given {@link com.heliosapm.opentsdb.client.opentsdb.OpenTsdb} client.
         *
         * @param opentsdb a {@link OpenTsdb} client
         * @return a {@link OpenTsdbReporter}
         */
        public OpenTsdbReporter build(OpenTsdb opentsdb) {            
            return new OpenTsdbReporter(registry,
                    opentsdb,
                    clock,
                    prefix,
                    rateUnit,
                    durationUnit,
                    filter);
        }
    }

    private static class MetricsCollector {
        private final String prefix;
        private final Map<String, String> tags;
        private final long timestamp;
        private final Set<OpenTsdbMetric> metrics = new HashSet<OpenTsdbMetric>();

        private MetricsCollector(String prefix, Map<String, String> tags, long timestamp) {
        	final ObjectName on = Util.objectName(prefix);
            this.prefix = on.getDomain();
            this.tags = new LinkedHashMap<String, String>(on.getKeyPropertyList());
            this.timestamp = timestamp;
        }

        public static MetricsCollector createNew(String prefix, Map<String, String> tags, long timestamp) {
            return new MetricsCollector(prefix, tags, timestamp);
        }
        
        private static final Pattern DOT_SPLITTER = Pattern.compile("\\.");
        private static final Pattern EQ_SPLITTER = Pattern.compile("\\=");
        
        protected static ObjectName tsdbTag(String prefix, Map<String, String> tags) {
        	try {
        		final ObjectName on = new ObjectName(prefix);
        		final Map<String, String> newTags = new LinkedHashMap<String, String>();
        		newTags.putAll(tags);
        		newTags.putAll(on.getKeyPropertyList());        		
        		return new ObjectName(on.getDomain(), new Hashtable<String, String>(newTags));
        	} catch (Exception x) {throw new RuntimeException(x);}        	
        }
        
        protected static Map<String, String> tag(String prefix, Map<String, String> tags) {
        	final Map<String, String> newTags = new LinkedHashMap<String, String>();
        	for(String keyPairs: DOT_SPLITTER.split(prefix)) {
        		String[] keyPair = EQ_SPLITTER.split(keyPairs);
        		if(keyPair.length!=2) continue;
        		String key = Util.clean(keyPair[0]);
        		String value = Util.clean(keyPair[1]);
        		if(key!=null && !key.isEmpty() && value!=null && !value.isEmpty()) {
        			newTags.put(key, value);
        		}
        	}
        	if(tags!=null && !tags.isEmpty()) {
        		newTags.putAll(tags);
        	}
        	return newTags;
        }

        public MetricsCollector addMetric(String metricName, Object value) {
        	if(prefix!=null) {
        		metricName = prefix + "." + metricName;
        	}
            this.metrics.add(OpenTsdbMetric.named(metricName)
                    .withTimestamp(timestamp)
                    .withValue(value)
                    .withTags(tags).build());
            return this;
        }

        public Set<OpenTsdbMetric> build() {
            return metrics;
        }
    }

    
    private OpenTsdbReporter(IMetricRegistry registry, OpenTsdb opentsdb, Clock clock, String prefix, TimeUnit rateUnit, TimeUnit durationUnit, MetricFilter filter) {
        this.opentsdb = opentsdb;
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

    
    public void report(SortedMap<String, Gauge> gauges, SortedMap<String, Counter> counters, SortedMap<String, Histogram> histograms, SortedMap<String, Meter> meters, SortedMap<String, Timer> timers) {

        final long timestamp = clock.getTime() / 1000;

        final Set<OpenTsdbMetric> metrics = new HashSet<OpenTsdbMetric>();

        for (Map.Entry<String, Gauge> g : gauges.entrySet()) {
            if(g.getValue().getValue() instanceof Collection && ((Collection)g.getValue().getValue()).isEmpty()) {
                continue;
            }
            metrics.add(buildGauge(g.getKey(), g.getValue(), timestamp));
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

        opentsdb.send(metrics);
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
	                    LOG.error("RuntimeException thrown from {}#report. Exception was suppressed.", OpenTsdbReporter.this.getClass().getSimpleName(), ex);
	                }
	            }
	        };
	        scheduleHandle = Threading.getInstance().schedule(r, period, unit);
	        LOG.debug("Started scheduled task for Registry {}", registry.getNames().toString());
    	}
    }
    
    /**
     * Stops the reporter
     */
    public void stop() {
    	if(started.compareAndSet(true, false)) {
	    	if(scheduleHandle!=null) {
	    		scheduleHandle.cancel();
	    		LOG.debug("Stopped scheduled task for Registry {}", registry.getNames().toString());
	    	}
    	}    	
    }
    
    /**
     * Report the current values of all metrics in the registry.
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

    private Set<OpenTsdbMetric> buildTimers(String name, Timer timer, long timestamp) {
        final MetricsCollector collector = MetricsCollector.createNew(prefix(name), tags, timestamp);
        final Snapshot snapshot = timer.getSnapshot();

        return collector.addMetric("count", timer.getCount())
                //convert rate
                .addMetric("m15", convertRate(timer.getFifteenMinuteRate()))
                .addMetric("m5", convertRate(timer.getFiveMinuteRate()))
                .addMetric("m1", convertRate(timer.getOneMinuteRate()))
                .addMetric("mean_rate", convertRate(timer.getMeanRate()))
                // convert duration
                .addMetric("max", convertDuration(snapshot.getMax()))
                .addMetric("min", convertDuration(snapshot.getMin()))
                .addMetric("mean", convertDuration(snapshot.getMean()))
                .addMetric("stddev", convertDuration(snapshot.getStdDev()))
                .addMetric("median", convertDuration(snapshot.getMedian()))
                .addMetric("p75", convertDuration(snapshot.get75thPercentile()))
                .addMetric("p95", convertDuration(snapshot.get95thPercentile()))
                .addMetric("p98", convertDuration(snapshot.get98thPercentile()))
                .addMetric("p99", convertDuration(snapshot.get99thPercentile()))
                .addMetric("p999", convertDuration(snapshot.get999thPercentile()))
                .build();
    }

    private Set<OpenTsdbMetric> buildHistograms(String name, Histogram histogram, long timestamp) {

        final MetricsCollector collector = MetricsCollector.createNew(prefix(name), tags, timestamp);
        final Snapshot snapshot = histogram.getSnapshot();

        return collector.addMetric("count", histogram.getCount())
                .addMetric("max", snapshot.getMax())
                .addMetric("min", snapshot.getMin())
                .addMetric("mean", snapshot.getMean())
                .addMetric("stddev", snapshot.getStdDev())
                .addMetric("median", snapshot.getMedian())
                .addMetric("p75", snapshot.get75thPercentile())
                .addMetric("p95", snapshot.get95thPercentile())
                .addMetric("p98", snapshot.get98thPercentile())
                .addMetric("p99", snapshot.get99thPercentile())
                .addMetric("p999", snapshot.get999thPercentile())
                .build();
    }

    private Set<OpenTsdbMetric> buildMeters(String name, Meter meter, long timestamp) {

        final MetricsCollector collector = MetricsCollector.createNew(prefix(name), tags, timestamp);

        return collector.addMetric("count", meter.getCount())
                // convert rate
                .addMetric("mean_rate", convertRate(meter.getMeanRate()))
                .addMetric("m1", convertRate(meter.getOneMinuteRate()))
                .addMetric("m5", convertRate(meter.getFiveMinuteRate()))
                .addMetric("m15", convertRate(meter.getFifteenMinuteRate()))
                .build();
    }

    private OpenTsdbMetric buildCounter(String name, Counter counter, long timestamp) {
        return OpenTsdbMetric.tsdbName(name, "count")
                .withTimestamp(timestamp)
                .withValue(counter.getCount())
                .withTags(tags)
                .build();
    }


    private OpenTsdbMetric buildGauge(String name, Gauge gauge, long timestamp) {    	
        return OpenTsdbMetric.tsdbName(name, "value")
                .withValue(gauge.getValue()) 
                .withTimestamp(timestamp)
                .withTags(tags)
                .build();
    }

    private String prefix(String... components) {
        return OpenTsdbMetric.prefix(prefix, components);
    }
    

    
    
    static {
    	// init boot logging as early as possible
    	LoggingConfiguration.getInstance();
    	LOG = LoggerFactory.getLogger(OpenTsdbReporter.class);
    }


	/**
	 * {@inheritDoc}
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() throws IOException {
		stop();
	}
	
    protected String getRateUnit() {
        return durationUnit.toString().toLowerCase(Locale.US);
    }

    protected String getDurationUnit() {
        return calculateRateUnit(rateUnit);
    }

    protected double convertDuration(double duration) {
        return duration * durationFactor;
    }

    protected double convertRate(double rate) {
        return rate * rateFactor;
    }

    private String calculateRateUnit(TimeUnit unit) {
        final String s = unit.toString().toLowerCase(Locale.US);
        return s.substring(0, s.length() - 1);
    }
	

}
