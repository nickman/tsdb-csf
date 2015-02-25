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

import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import jsr166e.LongAdder;

import org.cliffc.high_scale_lib.NonBlockingHashMapLong;
import org.cliffc.high_scale_lib.NonBlockingHashMapLong.IteratorLong;
import org.cliffc.high_scale_lib.NonBlockingHashSet;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Timer;
import com.heliosapm.opentsdb.client.opentsdb.ConfigurationReader;
import com.heliosapm.opentsdb.client.opentsdb.Constants;
import com.heliosapm.opentsdb.client.opentsdb.MetricBuilder;
import com.heliosapm.opentsdb.client.opentsdb.OTMetric;
import com.heliosapm.opentsdb.client.opentsdb.opt.LongIdOTMetricCache.OTMetricIdListener;

/**
 * <p>Title: LongIdMetricRegistry</p>
 * <p>Description: An optimized metric registry implementation.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.LongIdMetricRegistry</code></p>
 */

public class LongIdMetricRegistry implements LongIdMetricSet, OTMetricIdListener {
	/** The singleton instance */
	private static volatile LongIdMetricRegistry instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();

	
	/** The metric cache */
	private final NonBlockingHashMapLong<Metric> metrics;
	/** The sub-metric cache */
	private final NonBlockingHashMapLong<Set<OTMetric>> submetrics;
	
	/** The long id otmetric cache */
	private final LongIdOTMetricCache otCache;
	
	/** The cummulative count of removed metric ids */
	private final LongAdder removalCount = new LongAdder();
	
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
		submetrics = new NonBlockingHashMapLong<Set<OTMetric>>(initialSize, space4Speed);
		otCache = LongIdOTMetricCache.getInstance();
		otCache.registerMetricIdListener(this);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.opt.LongIdOTMetricCache.OTMetricIdListener#onRemoved(long, com.heliosapm.opentsdb.client.opentsdb.OTMetric)
	 */
	@Override
	public void onRemoved(final long otMetricId, final OTMetric otMetric) {		
		metrics.remove(otMetricId);
		submetrics.remove(otMetricId);
		removalCount.increment();
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
        	register(MetricBuilder.metric(name).buildNoCache(), metric);
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
    public <T extends Metric> OTMetric register(final OTMetric otMetric, final T metric) throws IllegalArgumentException {
    	return register(otMetric, metric, null);
    }

    
    /**
     * Given a {@link Metric}, registers it under the given {@link OTMetric}.
     *
     * @param otMetric   the name of the metric
     * @param metric the metric
     * @param <T>    the type of the metric
     * @param ext    An optional reporter extension for metrics which have submetrics created by the reporter (timers, meters)
     * @return the passed OTMetric, or if we're requesting an extension, returns the created extension OTMetric
     * @throws IllegalArgumentException if the name is already registered
     */    
    public <T extends Metric> OTMetric register(final OTMetric otMetric, final T metric, final String ext) throws IllegalArgumentException {
        if (metric instanceof MetricSet) {
            registerAll(otMetric, (MetricSet) metric);
        } else {
        	// check if the passed otMetric is registered
        	final long otId = otMetric.longHashCode();
        	OTMetric otm = otCache.getOTMetric(otId);
        	if(otm==null) {
        		otMetric.setCHMetricType(CHMetric.getCHMetricType(metric));
        		otCache.put(otMetric);
        	}
        	// If ext is NOT null, we're registering a sub-metric,
        	// but it might be the first time we're seeing the passed metric.
        	final Metric exMetric = metrics.putIfAbsent(otId, metric);
        	if(exMetric==null) {
        		onMetricAdded(otMetric, metric);
        	}
        	if(ext!=null) {
        		otm = subMetric(otMetric, ext);
        		otMetric.setCHMetricType(CHMetric.getCHMetricType(metric));
        		return otm;
        	}        	
        }    
        return otMetric;
    }
    
    /**
     * Creates and registers a new sub-metric OTMetric.
     * Also registers the sub-metric as a child of the passed parent
     * so that when the parent is removed from the registry, we can
     * remove all the children too.
     * @param parentOTMetric The parent metric
     * @param ext The sub-metric extension
     * @return the new sub-metric OTMetric
     */
    protected OTMetric subMetric(final OTMetric parentOTMetric, final String ext) {
    	final OTMetric otm = MetricBuilder.metric(parentOTMetric).ext(ext).optBuild().setParentMetric(parentOTMetric.longHashCode());
    	Set<OTMetric> subs = submetrics.get(parentOTMetric.longHashCode());
    	if(subs==null) {
    		synchronized(submetrics) {
    			subs = submetrics.get(parentOTMetric.longHashCode());
    	    	if(subs==null) {
    	    		subs = new NonBlockingHashSet<OTMetric>();
    	    		submetrics.put(parentOTMetric.longHashCode(), subs);
    	    	}
    		}
    	}
    	subs.add(otm);
    	return otm;
    }
    
    
    private void onMetricAdded(final OTMetric otMetric, final Metric metric) {
    	/* No Op */
    }
    
    private void onMetricRemoved(final OTMetric otMetric, final Metric metric) {
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
    public Counter counter(final String name) {
        return getOrAdd(name, CHMetricBuilder.COUNTERS);
    }
    
    /**
     * Creates a new {@link Counter} and registers it under the given name.
     *
     * @param name the name of the metric
     * @return a new {@link Counter}
     */
    public Counter counter(final OTMetric name) {
        return getOrAdd(name, CHMetricBuilder.COUNTERS);
    }
    
    
    /**
     * Creates a new {@link Histogram} and registers it under the given name.
     *
     * @param name the name of the metric
     * @return a new {@link Histogram}
     */
    public Histogram histogram(final String name) {
        return getOrAdd(name, CHMetricBuilder.HISTOGRAMS);
    }
    
    /**
     * Creates a new {@link Histogram} and registers it under the given name.
     *
     * @param name the name of the metric
     * @return a new {@link Histogram}
     */
    public Histogram histogram(final OTMetric name) {
        return getOrAdd(name, CHMetricBuilder.HISTOGRAMS);
    }
    
    
    /**
     * Creates a new {@link Meter} and registers it under the given name.
     *
     * @param name the name of the metric
     * @return a new {@link Meter}
     */
    public Meter meter(final String name) {
        return getOrAdd(name, CHMetricBuilder.METERS);
    }
    
    /**
     * Creates a new {@link Meter} and registers it under the given name.
     *
     * @param name the name of the metric
     * @return a new {@link Meter}
     */
    public Meter meter(final OTMetric name) {
        return getOrAdd(name, CHMetricBuilder.METERS);
    }
    

    /**
     * Creates a new {@link Timer} and registers it under the given name.
     *
     * @param name the name of the metric
     * @return a new {@link Timer}
     */
    public Timer timer(final String name) {
        return getOrAdd(name, CHMetricBuilder.TIMERS);
    }
    
    /**
     * Creates a new {@link Timer} and registers it under the given name.
     *
     * @param name the name of the metric
     * @return a new {@link Timer}
     */
    public Timer timer(final OTMetric name) {
        return getOrAdd(name, CHMetricBuilder.TIMERS);
    }
    
    
    /**
     * Removes the metric with the given OTMetric
     *
     * @param otMetric the name of the metric
     * @return whether or not the metric was removed
     */
    public boolean remove(final OTMetric otMetric) {
    	if(otMetric==null) throw new IllegalArgumentException("The passed OTMetric was null");
    	final long otId = otMetric.longHashCode();
		final Metric metric = metrics.remove(otId);
		if (metric != null) {
			onMetricRemoved(otMetric, metric);
			return true;
		}
    	return false;
    }
    

    /**
     * Removes the metric with the given name.
     *
     * @param name the name of the metric
     * @return whether or not the metric was removed
     */
    public boolean remove(final String name) {
    	if(name==null || name.trim().isEmpty()) throw new IllegalArgumentException("The passed name was null or empty");
    	return remove(MetricBuilder.metric(name).buildNoCache());
    }
    
    /**
     * Removes the metric with the given id.
     *
     * @param otMetricId the id of the OTMetric
     * @return whether or not the metric was removed
     */
    public boolean remove(final long otMetricId) {    	
		final Metric metric = metrics.remove(otMetricId);
		if (metric != null) {
			final OTMetric otMetric = otCache.getOTMetric(otMetricId);
			if(otMetric!=null) {
				onMetricRemoved(otMetric, metric);
			}
			return true;
		}
    	return false;
    }

    /**
     * Removes all metrics which match the given filter.
     *
     * @param filter a filter
     */
    @SuppressWarnings("rawtypes")
	public void removeMatching(final OTMetricFilter filter) {
    	if(filter==null) throw new IllegalArgumentException("The passed filter");
    	final IteratorLong iter = (IteratorLong) ((AbstractSet<Long>)metrics.keySet()).iterator();
    	while(iter.hasMoreElements()) {
    		final long id = iter.nextLong();
    		final Metric metric = metrics.get(id);
    		final OTMetric otm = otCache.getOTMetric(id);
    		if(metric!=null && otm!=null) {
    			if (filter.matches(otm, metric)) {
    				iter.remove();
    			}
    		}
    	}
    }
    
    /**
     * Returns a map of all the gauges in the registry and their names.
     *
     * @return all the gauges in the registry
     */
    public Map<OTMetric, Gauge> getGauges() {
        return getGauges(OTMetricFilter.ALL);
    }

    /**
     * Returns a map of all the gauges in the registry and their names which match the given filter.
     *
     * @param filter    the metric filter to match
     * @return all the gauges in the registry
     */
    public Map<OTMetric, Gauge> getGauges(final OTMetricFilter filter) {
        return getMetrics(Gauge.class, filter);
    }

    /**
     * Returns a map of all the counters in the registry and their names.
     *
     * @return all the counters in the registry
     */
    public Map<OTMetric, Counter> getCounters() {
        return getCounters(OTMetricFilter.ALL);
    }

    /**
     * Returns a map of all the counters in the registry and their names which match the given
     * filter.
     *
     * @param filter    the metric filter to match
     * @return all the counters in the registry
     */
    public Map<OTMetric, Counter> getCounters(final OTMetricFilter filter) {
        return getMetrics(Counter.class, filter);
    }

    /**
     * Returns a map of all the histograms in the registry and their names.
     *
     * @return all the histograms in the registry
     */
    public Map<OTMetric, Histogram> getHistograms() {
        return getHistograms(OTMetricFilter.ALL);
    }

    /**
     * Returns a map of all the histograms in the registry and their names which match the given
     * filter.
     *
     * @param filter    the metric filter to match
     * @return all the histograms in the registry
     */
    public Map<OTMetric, Histogram> getHistograms(final OTMetricFilter filter) {
        return getMetrics(Histogram.class, filter);
    }

    /**
     * Returns a map of all the meters in the registry and their names.
     *
     * @return all the meters in the registry
     */
    public Map<OTMetric, Meter> getMeters() {
        return getMeters(OTMetricFilter.ALL);
    }

    /**
     * Returns a map of all the meters in the registry and their names which match the given filter.
     *
     * @param filter    the metric filter to match
     * @return all the meters in the registry
     */
    public Map<OTMetric, Meter> getMeters(final OTMetricFilter filter) {
        return getMetrics(Meter.class, filter);
    }

    /**
     * Returns a map of all the timers in the registry and their names.
     *
     * @return all the timers in the registry
     */
    public Map<OTMetric, Timer> getTimers() {
        return getTimers(OTMetricFilter.ALL);
    }

    /**
     * Returns a map of all the timers in the registry and their names which match the given filter.
     *
     * @param filter    the metric filter to match
     * @return all the timers in the registry
     */
    public Map<OTMetric, Timer> getTimers(final OTMetricFilter filter) {
        return getMetrics(Timer.class, filter);
    }
    
    
    private <T extends Metric> Map<OTMetric, T> getMetrics(final Class<T> klass, final OTMetricFilter filter) {
    	if(filter==null) throw new IllegalArgumentException("The passed filter was null");
    	if(klass==null) throw new IllegalArgumentException("The passed class was null");
    	final HashMap<OTMetric, T> metricMap = new HashMap<OTMetric, T>();
    	final IteratorLong iter = (IteratorLong) ((AbstractSet<Long>)metrics.keySet()).iterator();
    	while(iter.hasMoreElements()) {
    		final long id = iter.nextLong();
    		final Metric metric = metrics.get(id);
    		final OTMetric otm = otCache.getOTMetric(id);
    		if(metric!=null && otm!=null) {
    			if (filter.matches(otm, metric)) {
    				metricMap.put(otm, (T)metric);
    			}
    		}
    	}
    	return metricMap;
    }
    
    private <T extends Metric> T getOrAdd(final OTMetric otMetric, CHMetricBuilder<T> builder) {
    	return getOrAdd(otMetric, builder, null);
    }
    
    private <T extends Metric> T getOrAdd(final OTMetric otMetric, CHMetricBuilder<T> builder, final String ext) {
    	if(otMetric==null) throw new IllegalArgumentException("The passed OTMetric was null");
    	if(builder==null) throw new IllegalArgumentException("The passed CHMetricBuilder was null");    	
    	final Metric metric = metrics.get(otMetric.longHashCode());
        if (builder.isInstance(metric)) {
            return (T)metric;
        } else if (metric == null) {
            try {
            	final Metric m = builder.newMetric(otMetric.longHashCode());
                register(otMetric, m);
                return (T)m;
            } catch (IllegalArgumentException e) {
                final Metric added = metrics.get(otMetric.longHashCode());
                if (builder.isInstance(added)) {
                    return (T) added;
                }
            }
        }
        throw new IllegalArgumentException(otMetric + " is already used for a different type of metric");    	
    }
    
    private <T extends Metric> T getOrAdd(final String name, final CHMetricBuilder<T> builder) {
    	return getOrAdd(name, builder, null);
    }
    
    private <T extends Metric> T getOrAdd(final String name, final CHMetricBuilder<T> builder, final String ext) {
    	if(name==null) throw new IllegalArgumentException("The passed metric name was null");
    	if(builder==null) throw new IllegalArgumentException("The passed CHMetricBuilder was null");   
    	return getOrAdd(MetricBuilder.metric(name).buildNoCache(), builder, ext);
    }

    


    


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.opt.LongIdMetricSet#getMetrics()
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
            public Counter newMetric(final long otmId) {
                return new OTCounter(otmId);
            }

            @Override
            public boolean isInstance(final Metric metric) {
                return Counter.class.isInstance(metric);
            }
        };

        CHMetricBuilder<Histogram> HISTOGRAMS = new CHMetricBuilder<Histogram>() {

            @Override
            public Histogram newMetric(final long otmId) {
                return new OTHistogram(otmId);
            }

            @Override
            public boolean isInstance(final Metric metric) {
                return Histogram.class.isInstance(metric);
            }
        };

        CHMetricBuilder<Meter> METERS = new CHMetricBuilder<Meter>() {

            @Override
            public Meter newMetric(final long otmId) {
                return new OTMeter(otmId);
            }

            @Override
            public boolean isInstance(final Metric metric) {
                return Meter.class.isInstance(metric);
            }
        };

        CHMetricBuilder<Timer> TIMERS = new CHMetricBuilder<Timer>() {
            @Override
            public Timer newMetric(final long otmId) {
                return new OTTimer(otmId);
            }

            @Override
            public boolean isInstance(final Metric metric) {
                return Timer.class.isInstance(metric);
            }
        };

        T newMetric(final long otmId);

        boolean isInstance(Metric metric);
    }
    
    public static interface OTLongIdMetric extends Metric {
    	/**
    	 * Returns the id of the underlying OTMetric
    	 * @return the id of the underlying OTMetric
    	 */
    	public long getId();
    }
	
    public static class OTCounter extends Counter implements OTLongIdMetric {
    	/** The OTMetric id for this metric */
    	private final long otmId;

		/**
		 * Creates a new OTCounter
		 * @param otmId the OTMetric id
		 */
		public OTCounter(final long otmId) {
			super();
			this.otmId = otmId;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.opt.LongIdMetricRegistry.OTLongIdMetric#getId()
		 */
		@Override
		public long getId() {
			return otmId;
		}
    }
    
    public static class OTHistogram extends Histogram implements OTLongIdMetric {
    	/** The OTMetric id for this metric */
    	private final long otmId;

		/**
		 * Creates a new OTHistogram
		 * @param otmId the OTMetric id
		 */
		public OTHistogram(final long otmId) {
			super(new ExponentiallyDecayingReservoir());
			this.otmId = otmId;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.opt.LongIdMetricRegistry.OTLongIdMetric#getId()
		 */
		@Override
		public long getId() {
			return otmId;
		}
    }
    
    public static class OTMeter extends Meter implements OTLongIdMetric {
    	/** The OTMetric id for this metric */
    	private final long otmId;

		/**
		 * Creates a new OTMeter
		 * @param otmId the OTMetric id
		 */
		public OTMeter(final long otmId) {
			super();
			this.otmId = otmId;
		}
		
		/**
		 * Creates a new OTMeter
		 * @param otmId the OTMetric id
		 * @param clock The clock to use
		 */
		public OTMeter(final long otmId, final Clock clock) {
			super(clock);
			this.otmId = otmId;
		}
		

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.opt.LongIdMetricRegistry.OTLongIdMetric#getId()
		 */
		@Override
		public long getId() {
			return otmId;
		}
    }
    
    public static class OTTimer extends Timer implements OTLongIdMetric {
    	/** The OTMetric id for this metric */
    	private final long otmId;

		/**
		 * Creates a new OTTimer
		 * @param otmId the OTMetric id
		 */
		public OTTimer(final long otmId) {
			super();
			this.otmId = otmId;
		}
		
		/**
		 * Creates a new OTTimer
		 * @param otmId the OTMetric id
		 * @param clock The clock to use for this timer
		 */
		public OTTimer(final long otmId, final Clock clock) {
			super(new ExponentiallyDecayingReservoir(), clock);
			this.otmId = otmId;
		}
		

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.opt.LongIdMetricRegistry.OTLongIdMetric#getId()
		 */
		@Override
		public long getId() {
			return otmId;
		}
    }
    
    
    public static class OTGauge implements Gauge, OTLongIdMetric {
    	/** The OTMetric id for this metric */
    	private final long otmId;
    	/** The delegate gauge */
    	private final Gauge delegate;

		/**
		 * Creates a new OTGauge
		 * @param otmId the OTMetric id
		 * @param gauge The delegate gauge
		 */
		public OTGauge(final long otmId, final Gauge gauge) {
			super();
			this.otmId = otmId;
			delegate = gauge;
		}

		

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.opt.LongIdMetricRegistry.OTLongIdMetric#getId()
		 */
		@Override
		public long getId() {
			return otmId;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.codahale.metrics.Gauge#getValue()
		 */
		@Override
		public Object getValue() {
			return delegate.getValue();
		}
		
    }
    
    
    
}
