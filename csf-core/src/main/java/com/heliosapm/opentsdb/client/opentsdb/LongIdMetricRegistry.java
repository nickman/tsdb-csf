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

import java.util.Map;

import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

import com.codahale.metrics.Counter;
import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Timer;

/**
 * <p>Title: LongIdMetricRegistry</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.LongIdMetricRegistry</code></p>
 */

public class LongIdMetricRegistry implements LongIdMetricSet {
	/** The singleton instance */
	private static volatile LongIdMetricRegistry instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();

	
	/** The metric cache */
	private final NonBlockingHashMapLong<Metric> metrics;
	
	/** The long id otmetric cache */
	private final LongIdOTMetricCache otCache;
	
	/** The initial size of the opt cache */
	final int initialSize;
	/** The space for speed option of the opt cache */
	final boolean space4Speed;

	/**
	 * Acquires the LongIdMetricRegistry singleton instance
	 * @return the LongIdMetricRegistry singleton instance
	 */
	public static LongIdMetricRegistry getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new LongIdMetricRegistry();
				}
			}
		}
		return instance;
	}
		
	
	/**
	 * Creates a new LongIdMetricRegistry
	 */
	private LongIdMetricRegistry() {
		initialSize = ConfigurationReader.confInt(Constants.PROP_OPT_CACHE_INIT_SIZE, Constants.DEFAULT_OPT_CACHE_INIT_SIZE);
		space4Speed = ConfigurationReader.confBool(Constants.PROP_OPT_CACHE_SPACE_FOR_SPEED, Constants.DEFAULT_OPT_CACHE_SPACE_FOR_SPEED);
		metrics = new NonBlockingHashMapLong<Metric>(initialSize, space4Speed);	
		otCache = LongIdOTMetricCache.getInstance();
	}
	
	
    /**
     * Given a {@link Metric}, registers it under the given name.
     *
     * @param name   the name of the metric
     * @param metric the metric
     * @param <T>    the type of the metric
     * @return {@code metric}
     * @throws IllegalArgumentException if the name is already registered
     */    
    public <T extends Metric> T register(final String name, final T metric) throws IllegalArgumentException {
        if (metric instanceof MetricSet) {
            registerAll(name, (MetricSet) metric);
        } else {
        	
        	final MetricBuilder mb = MetricBuilder.metric(name);
        	OTMetric otm = otCache.getOTMetric(mb.longHashCode());
        	if(otm==null) {
        		otm = otCache.getOTMetric(mb);
        		otm.setCHMetricType(CHMetric.getCHMetricType(metric));
        		onMetricAdded(name, metric);
        	} else {
        		throw new IllegalArgumentException("A metric named " + name + " already exists");
        	}
        }
        return metric;
    }
    
    /**
     * Given a {@link Metric}, registers it under the given {@link OTMetric}.
     *
     * @param otMetric   the name of the metric
     * @param metric the metric
     * @param <T>    the type of the metric
     * @return {@code metric}
     * @throws IllegalArgumentException if the name is already registered
     */    
    public <T extends Metric> T register(final OTMetric otMetric, final T metric) throws IllegalArgumentException {
        if (metric instanceof MetricSet) {
            registerAll(otMetric, (MetricSet) metric);
        } else {
        	OTMetric otm = otCache.getOTMetric(otMetric.longHashCode());
        	if(otm==null) {
        		otCache.put(otMetric);
        		otm = otCache.getOTMetric(mb);
        		otm.setCHMetricType(CHMetric.getCHMetricType(metric));
        		onMetricAdded(name, metric);
        	} else {
        		throw new IllegalArgumentException("A metric named " + name + " already exists");
        	}
        }
        return metric;
    
    }
    
    private void onMetricAdded(final String name, final Metric metric) {
    	/* No Op */
    }

    private void registerAll(String prefix, MetricSet metrics) throws IllegalArgumentException {
    	registerAll(MetricBuilder.metric(prefix).optBuild(), metrics);
    }
    
    private void registerAll(final OTMetric otMetric, final MetricSet metrics) throws IllegalArgumentException {
    	for (Map.Entry<String, Metric> entry : metrics.getMetrics().entrySet()) {
    		final OTMetric otm = MetricBuilder.metric(otMetric).pre(entry.getKey()).optBuild();
    		if(otm.equals(otMetric)) continue;
            if (entry.getValue() instanceof MetricSet) {            	
                registerAll(otMetric, (MetricSet) entry.getValue());
            } else {
                register(otMetric, entry.getValue());
            }
    	}
    }
    
    /**
     * Creates a new {@link Counter} and registers it under the given name.
     *
     * @param name the name of the metric
     * @return a new {@link Counter}
     */
    public Counter counter(String name) {
        return getOrAdd(name, CHMetricBuilder.COUNTERS);
    }
    
    
    
    private <T extends Metric> T getOrAdd(final OTMetric otMetric, CHMetricBuilder<T> builder) {
    	if(otMetric==null) throw new IllegalArgumentException("The passed OTMetric was null");
    	if(builder==null) throw new IllegalArgumentException("The passed CHMetricBuilder was null");
    	final Metric metric = metrics.get(otMetric.longHashCode());
        if (builder.isInstance(metric)) {
            return (T) metric;
        } else if (metric == null) {
            try {
                return register(otMetric, builder.newMetric());
            } catch (IllegalArgumentException e) {
                final Metric added = metrics.get(name);
                if (builder.isInstance(added)) {
                    return (T) added;
                }
            }
        }
        throw new IllegalArgumentException(name + " is already used for a different type of metric");
    	
    }
    
    private <T extends Metric> T getOrAdd(String name, CHMetricBuilder<T> builder) {
        final Metric metric = metrics.get(name);
        if (builder.isInstance(metric)) {
            return (T) metric;
        } else if (metric == null) {
            try {
                return register(name, builder.newMetric());
            } catch (IllegalArgumentException e) {
                final Metric added = metrics.get(name);
                if (builder.isInstance(added)) {
                    return (T) added;
                }
            }
        }
        throw new IllegalArgumentException(name + " is already used for a different type of metric");
    }

    


    


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.LongIdMetricSet#getMetrics()
	 */
	@Override
	public NonBlockingHashMapLong<Metric> getMetrics() {
		return metrics;
	}
	
	public int getInitialSize() {
		return initialSize;
	}

	public boolean isSpace4Speed() {
		return space4Speed;
	}	
	
    /**
     * A quick and easy way of capturing the notion of default metrics.
     */
    private interface CHMetricBuilder<T extends Metric> {
    	CHMetricBuilder<Counter> COUNTERS = new CHMetricBuilder<Counter>() {
            @Override
            public Counter newMetric() {
                return new Counter();
            }

            @Override
            public boolean isInstance(Metric metric) {
                return Counter.class.isInstance(metric);
            }
        };

        CHMetricBuilder<Histogram> HISTOGRAMS = new CHMetricBuilder<Histogram>() {
            @Override
            public Histogram newMetric() {
                return new Histogram(new ExponentiallyDecayingReservoir ());
            }

            @Override
            public boolean isInstance(Metric metric) {
                return Histogram.class.isInstance(metric);
            }
        };

        CHMetricBuilder<Meter> METERS = new CHMetricBuilder<Meter>() {
            @Override
            public Meter newMetric() {
                return new Meter();
            }

            @Override
            public boolean isInstance(Metric metric) {
                return Meter.class.isInstance(metric);
            }
        };

        CHMetricBuilder<Timer> TIMERS = new CHMetricBuilder<Timer>() {
            @Override
            public Timer newMetric() {
                return new Timer();
            }

            @Override
            public boolean isInstance(Metric metric) {
                return Timer.class.isInstance(metric);
            }
        };

        T newMetric();

        boolean isInstance(Metric metric);
    }
	

}
