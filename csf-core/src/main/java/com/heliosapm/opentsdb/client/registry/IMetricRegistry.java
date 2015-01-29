/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2015, Helios Development Group and individual contributors
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

import java.util.SortedMap;
import java.util.SortedSet;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistryListener;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Timer;

/**
 * <p>Title: IMetricRegistry</p>
 * <p>Description: An interface to unify {@link com.codahale.metrics.MetricRegistry} 
 * and {@link com.heliosapm.opentsdb.client.registry.OpenTsdbMetricRegistry}</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.registry.IMetricRegistry</code></p>
 */

public interface IMetricRegistry extends MetricSet {
	/**
	 * Registers the named metric
	 * @param name The metric name
	 * @param metric The metric
	 * @return the registered metric
	 * @throws IllegalArgumentException if the name is already registered
	 */
	public <T extends Metric> T register(String name,T metric) throws IllegalArgumentException;
	
	/**
	 * Removes the metric with the given name.
	 * @param name the metric name
	 * @return true if removed, false if not found
	 */
	public boolean remove(String name);
	
	/**
	 * Adds a {@link MetricRegistryListener} to a collection of listeners that will be notified on metric creation. 
	 * Listeners will be notified in the order in which they are added.
	 * The listener will be notified of all existing metrics when it first registers.
	 * @param listener the listener that will be notified
	 */
	public void addListener(MetricRegistryListener listener);
	
	
	/**
	 * Creates a new {@link Counter} and registers it under the given name.
	 * @param name The name of the counter
	 * @return the created counter
	 */
	public Counter counter(String name);
	
	/**
	 * Creates a new {@link Timer} and registers it under the given name.
	 * @param name The metric name
	 * @return the created timer
	 */
	public Timer timer(String name);
	
	/**
	 * Returns a set of the names of all the metrics in the registry.
	 * @return the names
	 */
	public SortedSet<String> getNames();
	
	/**
	 * Removes a MetricRegistryListener from this registry's collection of listeners.
	 * @param listener the listener that will be removed
	 */
	public void removeListener(MetricRegistryListener listener);
	
	/**
	 * Registers all the metrics in the passed metric set
	 * @param metricSet a set of metrics
	 * @throws IllegalArgumentException if any of the names are already registered
	 */
	public void registerAll(MetricSet metricSet) throws IllegalArgumentException;
	
	/**
	 * Creates a new {@link Histogram} and registers it under the given name.
	 * @param name The metric name
	 * @return the created histogram
	 */
	public Histogram histogram(String name);
	
	/**
	 * Creates a new {@link Meter} and registers it under the given name.
	 * @param name The metric name
	 * @return the created meter
	 */
	public Meter meter(String name);
	
	/**
	 * Removes all metrics which match the given filter.
	 * @param filter a filter
	 */
	public void removeMatching(MetricFilter filter);

	/**
	 * Returns a map of all the gauges in the registry and their names.
	 * @return all the gauges in the registry
	 */
	@SuppressWarnings("rawtypes")
	public SortedMap<String, Gauge> getGauges();
	
	/**
	 * Returns a map of all the matching gauges in the registry and their names.
	 * @param filter the metric filter to match
	 * @return all the gauges in the registry
	 */
	@SuppressWarnings("rawtypes")
	public SortedMap<String, Gauge> getGauges(MetricFilter filter);
	
	/**
	 * Returns a map of all the counters in the registry and their names.
	 * @return all the counters in the registry
	 */
	public SortedMap<String, Counter> getCounters();
	
	/**
	 * Returns a map of all the matching counters in the registry and their names.
	 * @param filter the metric filter to match
	 * @return all the counters in the registry
	 */
	public SortedMap<String, Counter> getCounters(MetricFilter filter);
	
	/**
	 * Returns a map of all the histograms in the registry and their names.
	 * @return all the histograms in the registry
	 */
	public SortedMap<String, Histogram> getHistograms();
	
	/**
	 * Returns a map of all the matching histograms in the registry and their names.
	 * @param filter the metric filter to match
	 * @return all the histograms in the registry
	 */
	public SortedMap<String, Histogram> getHistograms(MetricFilter filter);
	
	/**
	 * Returns a map of all the meters in the registry and their names.
	 * @return all the meters in the registry
	 */
	public SortedMap<String, Meter> getMeters();
	
	/**
	 * Returns a map of all the matching meters in the registry and their names.
	 * @param filter the metric filter to match
	 * @return all the meters in the registry
	 */
	public SortedMap<String, Meter> getMeters(MetricFilter filter);
	
	/**
	 * Returns a map of all the matching meters in the registry and their names.
	 * @param filter the metric filter to match
	 * @return all the meters in the registry
	 */
	public SortedMap<String, Timer> getTimers(MetricFilter filter);
	
	/**
	 * Returns a map of all the timers in the registry and their names.
	 * @return all the timers in the registry
	 */
	public SortedMap<String, Timer> getTimers();

}
