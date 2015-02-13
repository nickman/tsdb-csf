/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2007, Helios Development Group and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org. 
 *
 */
package com.heliosapm.opentsdb.client.registry;

import static com.heliosapm.opentsdb.client.util.Util.clean;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cliffc.high_scale_lib.NonBlockingHashMap;

import com.codahale.metrics.Counter;
import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricRegistryListener;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Timer;
import com.heliosapm.opentsdb.client.opentsdb.ConfigurationReader;
import com.heliosapm.opentsdb.client.opentsdb.Constants;

/**
 * <p>Title: OpenTsdbMetricFactory</p>
 * <p>Description: {@link com.codahale.metrics.MetricRegistry} implementation for OpenTSDB.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author CodaHale/DropWizard team
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.OpenTsdbMetricFactory</code></p>
 */

public class OpenTsdbMetricRegistry implements IMetricRegistry {
	
	/** Static class logger */
	protected static final Logger LOG = LogManager.getLogger(OpenTsdbMetricRegistry.class);
	/** Metric type key */
	public static final String CMTYPE = "cmtype";
	/** Dot split pattern */
	protected static final Pattern DOT_SPLITTER = Pattern.compile("\\.");
	
	
	
	
    /** The registry's map of metrics keyed by the OpenTSDB metric name */
    private final ConcurrentMap<String, Metric> metrics;
    /** A list of metric registry event listeners */
    private final List<MetricRegistryListener> listeners;

	
	/**
	 * Creates a new OpenTsdbMetricFactory
	 * @param estimatedSize The estimated number of metrics that will reside in the top level registry metric map
	 */
	public OpenTsdbMetricRegistry(final int estimatedSize) {
		this.metrics = buildMap(estimatedSize);
		this.listeners = new CopyOnWriteArrayList<MetricRegistryListener>();
		IMetricRegistryFactory.register(this);
	}
	
	/**
	 * Creates a new OpenTsdbMetricFactory
	 */
	public OpenTsdbMetricRegistry() {
		this(ConfigurationReader.confInt(Constants.PROP_REG_MAP_SIZE, Constants.DEFAULT_REG_MAP_SIZE));
	}
	
	/**
	 * Creates a new OpenTsdbMetricFactory from the passed metric set
	 * @param metricSet the metric set to wrap
	 */
	public OpenTsdbMetricRegistry(final MetricSet metricSet) {
		this(ConfigurationReader.confInt(Constants.PROP_REG_MAP_SIZE, Constants.DEFAULT_REG_MAP_SIZE));
		if(metricSet==null) throw new IllegalArgumentException("The passed metric set was null");
		registerAll(metricSet);
	}

	ConcurrentMap<String, Metric> getMetricMap() {
		return metrics;
	}
	
	List<MetricRegistryListener> getListenerList() {
		return listeners;
	}
	
	
    /**
     * Creates a new {@link ConcurrentMap} implementation for use inside the registry. Override this
     * to create a {@link MetricRegistry} with space- or time-bounded metric lifecycles, for
     * example.
     * @param estimatedSize The estimated number of metrics that will reside in the top level registry metric map
     * @return a new {@link ConcurrentMap}
     */
    protected ConcurrentMap<String, Metric> buildMap(final int estimatedSize) {
        return new NonBlockingHashMap<String, Metric>(estimatedSize < Constants.DEFAULT_REG_MAP_SIZE ?  Constants.DEFAULT_REG_MAP_SIZE : estimatedSize);
    }
	
	
    /**
     * Concatenates elements to form an OpenTSDB metric in the sort-of format of a JMX ObjectName.
     *
     * @param name     the first element of the name
     * @param names    the remaining elements of the name
     * @return {@code name} as the ObjectName domain, followed by a colon, then {@code names} concatenated by equals signs
     */
    public static String name(final String name, final String... names) {
    	final StringBuilder builder = new StringBuilder();
    	if(name!=null) {
    		String _name = name.trim();
    		if(!_name.isEmpty()) {
    			builder.append(_name).append(".");
    		}
    	}
        if (names != null) {
            for (String s : names) {
                append(builder, s);
            }
        }
        return builder.toString();
    }
    
    /**
     * Concatenates a class name and elements to form an OpenTSDB metric, eliding any null values or
     * empty strings.
     *
     * @param klass    the first element of the name
     * @param names    the remaining elements of the name
     * @return {@code name} as the ObjectName domain, followed by a colon, then {@code names} concatenated by equals signs
     */
    public static String name(Class<?> klass, String... names) {
        return name(klass.getName(), names);
    }
    
    /**
     * Concatenates a class simple name and elements to form an OpenTSDB metric, eliding any null values or
     * empty strings.
     *
     * @param klass    the first element of the name
     * @param names    the remaining elements of the name
     * @return {@code name} as the ObjectName domain, followed by a colon, then {@code names} concatenated by equals signs
     */
    public static String shortname(Class<?> klass, String... names) {
        return name(klass.getSimpleName(), names);
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
    public <T extends Metric> T register(String name, T metric) throws IllegalArgumentException {
        if (metric instanceof MetricSet) {
            registerAll(name, (MetricSet) metric);
        } else {
            final Metric existing = metrics.putIfAbsent(name, metric);
            if (existing == null) {
                onMetricAdded(name, metric);
            } else {
                throw new IllegalArgumentException("A metric named " + name + " already exists");
            }
        }
        return metric;
    }
    
    /**
     * Given a metric set, registers them.
     *
     * @param metrics    a set of metrics
     * @throws IllegalArgumentException if any of the names are already registered
     */
    public void registerAll(MetricSet metrics) throws IllegalArgumentException {
        registerAll(null, metrics);
    }
    
    
    
    /**
     * Return the {@link Counter} registered under this name; or create and register 
     * a new {@link Counter} if none is registered.
     *
     * @param name the name of the metric
     * @return a new or pre-existing {@link Counter}
     */
    public Counter counter(String name) {
        return getOrAdd(name, MetricBuilder.COUNTERS);
    }

    /**
     * Return the {@link Histogram} registered under this name; or create and register 
     * a new {@link Histogram} if none is registered.
     *
     * @param name the name of the metric
     * @return a new or pre-existing {@link Histogram}
     */
    public Histogram histogram(String name) {
        return getOrAdd(name, MetricBuilder.HISTOGRAMS);
    }

    /**
     * Return the {@link Meter} registered under this name; or create and register 
     * a new {@link Meter} if none is registered.
     *
     * @param name the name of the metric
     * @return a new or pre-existing {@link Meter}
     */
    public Meter meter(String name) {
        return getOrAdd(name, MetricBuilder.METERS);
    }

    /**
     * Return the {@link Timer} registered under this name; or create and register 
     * a new {@link Timer} if none is registered.
     *
     * @param name the name of the metric
     * @return a new or pre-existing {@link Timer}
     */
    public Timer timer(String name) {
        return getOrAdd(name, MetricBuilder.TIMERS);
    }
    
    @SuppressWarnings("unchecked")
    private <T extends Metric> T getOrAdd(String name, MetricBuilder<T> builder) {
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
        
    private <T extends Metric> SortedMap<String, T> getMetrics(Class<T> klass, MetricFilter filter) {
        final TreeMap<String, T> timers = new TreeMap<String, T>();
        for (Map.Entry<String, Metric> entry : metrics.entrySet()) {
            if (klass.isInstance(entry.getValue()) && filter.matches(entry.getKey(),
                                                                     entry.getValue())) {
                timers.put(entry.getKey(), (T) entry.getValue());
            }
        }
        return Collections.unmodifiableSortedMap(timers);
    }

    /**
     * Removes the metric with the given name.
     *
     * @param name the name of the metric
     * @return whether or not the metric was removed
     */
    public boolean remove(String name) {
        final Metric metric = metrics.remove(name);
        if (metric != null) {
            onMetricRemoved(name, metric);
            return true;
        }
        return false;
    }

    /**
     * Removes all metrics which match the given filter.
     *
     * @param filter a filter
     */
    public void removeMatching(MetricFilter filter) {
        for (Map.Entry<String, Metric> entry : metrics.entrySet()) {
            if (filter.matches(entry.getKey(), entry.getValue())) {
                remove(entry.getKey());
            }
        }
    }
    
    
    private void registerAll(String prefix, MetricSet metrics) throws IllegalArgumentException {
        for (Map.Entry<String, Metric> entry : metrics.getMetrics().entrySet()) {
            if (entry.getValue() instanceof MetricSet) {
                registerAll(name(prefix, entry.getKey()), (MetricSet) entry.getValue());
            } else {
                register(name(prefix, entry.getKey()), entry.getValue());
            }
        }
    }    
    
    private void onMetricAdded(String name, Metric metric) {
        for (MetricRegistryListener listener : listeners) {
            notifyListenerOfAddedMetric(listener, metric, name);
        }
    }

    private void notifyListenerOfAddedMetric(MetricRegistryListener listener, Metric metric, String name) {
        if (metric instanceof Gauge) {
            listener.onGaugeAdded(name, (Gauge<?>) metric);
        } else if (metric instanceof Counter) {
            listener.onCounterAdded(name, (Counter) metric);
        } else if (metric instanceof Histogram) {
            listener.onHistogramAdded(name, (Histogram) metric);
        } else if (metric instanceof Meter) {
            listener.onMeterAdded(name, (Meter) metric);
        } else if (metric instanceof Timer) {
            listener.onTimerAdded(name, (Timer) metric);
        } else {
            throw new IllegalArgumentException("Unknown metric type: " + metric.getClass());
        }
    }

    private void onMetricRemoved(String name, Metric metric) {
        for (MetricRegistryListener listener : listeners) {
            notifyListenerOfRemovedMetric(name, metric, listener);
        }
    }

    private void notifyListenerOfRemovedMetric(String name, Metric metric, MetricRegistryListener listener) {
        if (metric instanceof Gauge) {
            listener.onGaugeRemoved(name);
        } else if (metric instanceof Counter) {
            listener.onCounterRemoved(name);
        } else if (metric instanceof Histogram) {
            listener.onHistogramRemoved(name);
        } else if (metric instanceof Meter) {
            listener.onMeterRemoved(name);
        } else if (metric instanceof Timer) {
            listener.onTimerRemoved(name);
        } else {
            throw new IllegalArgumentException("Unknown metric type: " + metric.getClass());
        }
    }

    
    
    private static void append(final StringBuilder builder, final String partName) {
    	if(partName==null) return;
    	final String part = partName.replace(" ", "");
    	final int blength = builder.length();
    	if(!part.isEmpty()) {
	    	/*
	    	 * could be:
	    	 *  metricname: builder length = 0 (replace '=' with '.' ?)
	    	 * 	pair: A=B, last char can be ':' or ','
	    	 *  value: B if last char is '='
	    	 *  key: A if last char is NOT '=', but can be ':' or ','
	    	 */
	    	
	    	if (blength > 0) {
	    		final char lastc = builder.charAt(blength-1);
	    		final int eqIndex = part.indexOf('=');
	    		if(eqIndex > 1 && (lastc == ':' || lastc == ',') && eqIndex < part.length()-1) {
	    			// pair
	    			builder.append(part).append(",");
	    		} else {
	    			if(eqIndex==0) {
	    				if(lastc == '=') {
	    					// value
	    					builder.append(part).append(",");
	    				} else {
	    					if(lastc != '=' && (lastc == ':' || lastc == ',')) {
	    						// key
	    						builder.append(part).append("=");
	    					}
	    				}
	    			}
	    		}
	    	} else {
	    		// metric name
	    		builder.append(part.replace('=', '.')).append("");
	    	}
    	}
    	if(blength == builder.length()) {
    		LOG.trace("No assignment for append([{}], [{}])", builder, partName);
    	}
    }
    
    /**
     * Attempts to convert a native metrics name to an OpenTSDB metric.
     * Assumes that at least some the dot notated segments have <b><code>=</code></b> separated key/value pairs
     * @param name The name to convert
     * @return the converted name or null if a compliant conversion could not be made
     */
    public static String rewriteMetricsName(final String name) {
    	if(name==null || name.trim().isEmpty()) {
    		return null;  // what else ?
    	}
    	String[] dots = DOT_SPLITTER.split(name.trim());
    	Map<String, String> tags = new TreeMap<String, String>();
    	StringBuilder b = new StringBuilder();
    	boolean hasMn = false;
    	for(String s: dots) {
    		int index = s.indexOf('=');
    		if(index==-1) {
    			b.append(clean(s)).append(".");
    			hasMn = true;
    		} else {
    			String key = clean(s.substring(0, index));
    			String value = clean(s.substring(index+1));
    			if(!key.isEmpty() && !value.isEmpty()) {
//    				if(CMTYPE.equalsIgnoreCase(key)) continue;
    				tags.put(key, value);
    			}
    		}
    	}
    	return !hasMn ? null : b.deleteCharAt(b.length()-1).toString();
    }


 

    /**
     * Adds a {@link MetricRegistryListener} to a collection of listeners that will be notified on
     * metric creation.  Listeners will be notified in the order in which they are added.
     * <p/>
     * <b>N.B.:</b> The listener will be notified of all existing metrics when it first registers.
     *
     * @param listener the listener that will be notified
     */
    public void addListener(MetricRegistryListener listener) {
        listeners.add(listener);

        for (Map.Entry<String, Metric> entry : metrics.entrySet()) {
            notifyListenerOfAddedMetric(listener, entry.getValue(), entry.getKey());
        }
    }

    /**
     * Removes a {@link MetricRegistryListener} from this registry's collection of listeners.
     *
     * @param listener the listener that will be removed
     */
    public void removeListener(MetricRegistryListener listener) {
        listeners.remove(listener);
    }

    /**
     * Returns a set of the names of all the metrics in the registry.
     *
     * @return the names of all the metrics
     */
    public SortedSet<String> getNames() {
        return Collections.unmodifiableSortedSet(new TreeSet<String>(metrics.keySet()));
    }

    /**
     * Returns a map of all the gauges in the registry and their names.
     *
     * @return all the gauges in the registry
     */
    @SuppressWarnings("rawtypes")
    public SortedMap<String, Gauge> getGauges() {
        return getGauges(MetricFilter.ALL);
    }

    /**
     * Returns a map of all the gauges in the registry and their names which match the given filter.
     *
     * @param filter    the metric filter to match
     * @return all the gauges in the registry
     */
    @SuppressWarnings("rawtypes")
    public SortedMap<String, Gauge> getGauges(MetricFilter filter) {
        return getMetrics(Gauge.class, filter);
    }

    /**
     * Returns a map of all the counters in the registry and their names.
     *
     * @return all the counters in the registry
     */
    public SortedMap<String, Counter> getCounters() {
        return getCounters(MetricFilter.ALL);
    }

    /**
     * Returns a map of all the counters in the registry and their names which match the given
     * filter.
     *
     * @param filter    the metric filter to match
     * @return all the counters in the registry
     */
    public SortedMap<String, Counter> getCounters(MetricFilter filter) {
        return getMetrics(Counter.class, filter);
    }

    /**
     * Returns a map of all the histograms in the registry and their names.
     *
     * @return all the histograms in the registry
     */
    public SortedMap<String, Histogram> getHistograms() {
        return getHistograms(MetricFilter.ALL);
    }

    /**
     * Returns a map of all the histograms in the registry and their names which match the given
     * filter.
     *
     * @param filter    the metric filter to match
     * @return all the histograms in the registry
     */
    public SortedMap<String, Histogram> getHistograms(MetricFilter filter) {
        return getMetrics(Histogram.class, filter);
    }

    /**
     * Returns a map of all the meters in the registry and their names.
     *
     * @return all the meters in the registry
     */
    public SortedMap<String, Meter> getMeters() {
        return getMeters(MetricFilter.ALL);
    }

    /**
     * Returns a map of all the meters in the registry and their names which match the given filter.
     *
     * @param filter    the metric filter to match
     * @return all the meters in the registry
     */
    public SortedMap<String, Meter> getMeters(MetricFilter filter) {
        return getMetrics(Meter.class, filter);
    }

    /**
     * Returns a map of all the timers in the registry and their names.
     *
     * @return all the timers in the registry
     */
    public SortedMap<String, Timer> getTimers() {
        return getTimers(MetricFilter.ALL);
    }

    /**
     * Returns a map of all the timers in the registry and their names which match the given filter.
     *
     * @param filter    the metric filter to match
     * @return all the timers in the registry
     */
    public SortedMap<String, Timer> getTimers(MetricFilter filter) {
        return getMetrics(Timer.class, filter);
    }
	
	/**
	 * {@inheritDoc}
	 * @see com.codahale.metrics.MetricSet#getMetrics()
	 */
	@Override
	public Map<String, Metric> getMetrics() {
		return Collections.unmodifiableMap(metrics);		
	}

    /**
     * A quick and easy way of capturing the notion of default metrics.
     */
    private interface MetricBuilder<T extends Metric> {
        MetricBuilder<Counter> COUNTERS = new MetricBuilder<Counter>() {
            @Override
            public Counter newMetric() {
                return new Counter();
            }

            @Override
            public boolean isInstance(Metric metric) {
                return Counter.class.isInstance(metric);
            }
        };

        MetricBuilder<Histogram> HISTOGRAMS = new MetricBuilder<Histogram>() {
            @Override
            public Histogram newMetric() {
                return new Histogram(new ExponentiallyDecayingReservoir());
            }

            @Override
            public boolean isInstance(Metric metric) {
                return Histogram.class.isInstance(metric);
            }
        };

        MetricBuilder<Meter> METERS = new MetricBuilder<Meter>() {
            @Override
            public Meter newMetric() {
                return new Meter();
            }

            @Override
            public boolean isInstance(Metric metric) {
                return Meter.class.isInstance(metric);
            }
        };

        MetricBuilder<Timer> TIMERS = new MetricBuilder<Timer>() {
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



/*

KitchenSink.cmtype=Counter.op=cache-evictions.service=cacheservice
KitchenSink.cmtype=Gauge.attr=cache-size.service=cacheservice
KitchenSink.cmtype=Histogram.op=cache-lookup.service=cacheservice
KitchenSink.cmtype=Meter.op=cache-lookup.service=cacheservice
KitchenSink.cmtype=Timer.op=cache-evictions.service=cacheservice
fd
heap.committed:
heap.init:
heap.max:
heap.usage:
heap.used:
loaded:
non-heap.committed:
non-heap.init:
non-heap.max:
non-heap.usage:
non-heap.used:
pools.Code-Cache.usage:
pools.PS-Eden-Space.usage:
pools.PS-Old-Gen.usage:
pools.PS-Perm-Gen.usage:
pools.PS-Survivor-Space.usage:
total.committed:
total.init:
total.max:
total.used:
unloaded:

==================

BAD METRIC: metric:heap.max:.value, tags:{"app":"KitchenSink","host":"tpmint.local"}
BAD METRIC: metric:non-heap.usage:.value, tags:{"app":"KitchenSink","host":"tpmint.local"}
BAD METRIC: metric:heap.committed:.value, tags:{"app":"KitchenSink","host":"tpmint.local"}
BAD METRIC: metric:pools.PS-Perm-Gen.usage:.value, tags:{"app":"KitchenSink","host":"tpmint.local"}
BAD METRIC: metric:non-heap.committed:.value, tags:{"app":"KitchenSink","host":"tpmint.local"}
BAD METRIC: metric:non-heap.init:.value, tags:{"app":"KitchenSink","host":"tpmint.local"}
BAD METRIC: metric:pools.Code-Cache.usage:.value, tags:{"app":"KitchenSink","host":"tpmint.local"}
BAD METRIC: metric:loaded:.value, tags:{"app":"KitchenSink","host":"tpmint.local"}
BAD METRIC: metric:heap.usage:.value, tags:{"app":"KitchenSink","host":"tpmint.local"}
BAD METRIC: metric:non-heap.used:.value, tags:{"app":"KitchenSink","host":"tpmint.local"}
BAD METRIC: metric:total.committed:.value, tags:{"app":"KitchenSink","host":"tpmint.local"}
BAD METRIC: metric:non-heap.max:.value, tags:{"app":"KitchenSink","host":"tpmint.local"}
BAD METRIC: metric:total.used:.value, tags:{"app":"KitchenSink","host":"tpmint.local"}
BAD METRIC: metric:heap.used:.value, tags:{"app":"KitchenSink","host":"tpmint.local"}
BAD METRIC: metric:heap.init:.value, tags:{"app":"KitchenSink","host":"tpmint.local"}
BAD METRIC: metric:unloaded:.value, tags:{"app":"KitchenSink","host":"tpmint.local"}
BAD METRIC: metric:total.init:.value, tags:{"app":"KitchenSink","host":"tpmint.local"}
BAD METRIC: metric:pools.PS-Survivor-Space.usage:.value, tags:{"app":"KitchenSink","host":"tpmint.local"}
BAD METRIC: metric:pools.PS-Old-Gen.usage:.value, tags:{"app":"KitchenSink","host":"tpmint.local"}
BAD METRIC: metric:total.max:.value, tags:{"app":"KitchenSink","host":"tpmint.local"}
BAD METRIC: metric:pools.PS-Eden-Space.usage:.value, tags:{"app":"KitchenSink","host":"tpmint.local"}


==================


KitchenSink.cmtype=Counter.op=cache-evictions.service=cacheservice
KitchenSink.cmtype=Gauge.attr=cache-size.service=cacheservice
KitchenSink.cmtype=Histogram.op=cache-lookup.service=cacheservice
KitchenSink.cmtype=Meter.op=cache-lookup.service=cacheservice
KitchenSink.cmtype=Timer.op=cache-evictions.service=cacheservice
fd
heap.committed
heap.init
heap.max
heap.usage
heap.used
loaded
non-heap.committed
non-heap.init
non-heap.max
non-heap.usage
non-heap.used
pools.Code-Cache.usage
pools.PS-Eden-Space.usage
pools.PS-Old-Gen.usage
pools.PS-Perm-Gen.usage
pools.PS-Survivor-Space.usage
total.committed
total.init
total.max
total.used
unloaded


*/