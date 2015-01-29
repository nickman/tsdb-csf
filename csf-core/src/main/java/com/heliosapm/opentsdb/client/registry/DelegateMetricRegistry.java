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

import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentMap;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricRegistryListener;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Timer;

/**
 * <p>Title: DelegateMetricRegistry</p>
 * <p>Description: An extension of {@link MetricRegistry} that gets it's metric map from an {@link IMetricRegistry}</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.registry.DelegateMetricRegistry</code></p>
 */

public class DelegateMetricRegistry extends MetricRegistry implements IMetricRegistry {
	final OpenTsdbMetricRegistry registry;
	

	private static final ThreadLocal<ConcurrentMap<String, Metric>> superHack = new ThreadLocal<ConcurrentMap<String, Metric>>(); 
	
	/**
	 * Creates a new DelegateMetricRegistry
	 * @param registry the OpenTsdbMetricRegistry to delegate to
	 * @return the new DelegateMetricRegistry
	 */
	public static DelegateMetricRegistry newInstance(final OpenTsdbMetricRegistry registry) {
		superHack.set(registry.getMetricMap());
		DelegateMetricRegistry dmd = new DelegateMetricRegistry(registry); 
		superHack.remove();
		return dmd;
	}
	
	/**
	 * Creates a new DelegateMetricRegistry
	 */
	private DelegateMetricRegistry(final OpenTsdbMetricRegistry registry) {
		this.registry = registry;		
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.codahale.metrics.MetricRegistry#buildMap()
	 */
	@Override
	protected ConcurrentMap<String, Metric> buildMap() {
		return superHack.get();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.codahale.metrics.MetricRegistry#addListener(com.codahale.metrics.MetricRegistryListener)
	 */
	@Override
	public void addListener(MetricRegistryListener listener) {
		registry.addListener(listener);
	}
	
	public int hashCode() {
		return System.identityHashCode(registry);
	}
	
	
	/**
	 * @param name
	 * @param metric
	 * @return
	 * @throws IllegalArgumentException
	 * @see com.heliosapm.opentsdb.client.registry.OpenTsdbMetricRegistry#register(java.lang.String, com.codahale.metrics.Metric)
	 */
	public <T extends Metric> T register(String name, T metric)
			throws IllegalArgumentException {
		return registry.register(name, metric);
	}

	/**
	 * @param metrics
	 * @throws IllegalArgumentException
	 * @see com.heliosapm.opentsdb.client.registry.OpenTsdbMetricRegistry#registerAll(com.codahale.metrics.MetricSet)
	 */
	public void registerAll(MetricSet metrics) throws IllegalArgumentException {
		registry.registerAll(metrics);
	}

	/**
	 * @param name
	 * @return
	 * @see com.heliosapm.opentsdb.client.registry.OpenTsdbMetricRegistry#counter(java.lang.String)
	 */
	public Counter counter(String name) {
		return registry.counter(name);
	}

	/**
	 * @param name
	 * @return
	 * @see com.heliosapm.opentsdb.client.registry.OpenTsdbMetricRegistry#histogram(java.lang.String)
	 */
	public Histogram histogram(String name) {
		return registry.histogram(name);
	}

	/**
	 * @param name
	 * @return
	 * @see com.heliosapm.opentsdb.client.registry.OpenTsdbMetricRegistry#meter(java.lang.String)
	 */
	public Meter meter(String name) {
		return registry.meter(name);
	}

	/**
	 * @param name
	 * @return
	 * @see com.heliosapm.opentsdb.client.registry.OpenTsdbMetricRegistry#timer(java.lang.String)
	 */
	public Timer timer(String name) {
		return registry.timer(name);
	}

	/**
	 * @param name
	 * @return
	 * @see com.heliosapm.opentsdb.client.registry.OpenTsdbMetricRegistry#remove(java.lang.String)
	 */
	public boolean remove(String name) {
		return registry.remove(name);
	}

	/**
	 * @param filter
	 * @see com.heliosapm.opentsdb.client.registry.OpenTsdbMetricRegistry#removeMatching(com.codahale.metrics.MetricFilter)
	 */
	public void removeMatching(MetricFilter filter) {
		registry.removeMatching(filter);
	}

	/**
	 * @param listener
	 * @see com.heliosapm.opentsdb.client.registry.OpenTsdbMetricRegistry#removeListener(com.codahale.metrics.MetricRegistryListener)
	 */
	public void removeListener(MetricRegistryListener listener) {
		registry.removeListener(listener);
	}

	/**
	 * @return
	 * @see com.heliosapm.opentsdb.client.registry.OpenTsdbMetricRegistry#getNames()
	 */
	public SortedSet<String> getNames() {
		return registry.getNames();
	}

	/**
	 * @return
	 * @see com.heliosapm.opentsdb.client.registry.OpenTsdbMetricRegistry#getGauges()
	 */
	public SortedMap<String, Gauge> getGauges() {
		return registry.getGauges();
	}

	/**
	 * @param filter
	 * @return
	 * @see com.heliosapm.opentsdb.client.registry.OpenTsdbMetricRegistry#getGauges(com.codahale.metrics.MetricFilter)
	 */
	public SortedMap<String, Gauge> getGauges(MetricFilter filter) {
		return registry.getGauges(filter);
	}

	/**
	 * @return
	 * @see com.heliosapm.opentsdb.client.registry.OpenTsdbMetricRegistry#getCounters()
	 */
	public SortedMap<String, Counter> getCounters() {
		return registry.getCounters();
	}

	/**
	 * @param filter
	 * @return
	 * @see com.heliosapm.opentsdb.client.registry.OpenTsdbMetricRegistry#getCounters(com.codahale.metrics.MetricFilter)
	 */
	public SortedMap<String, Counter> getCounters(MetricFilter filter) {
		return registry.getCounters(filter);
	}

	/**
	 * @return
	 * @see com.heliosapm.opentsdb.client.registry.OpenTsdbMetricRegistry#getHistograms()
	 */
	public SortedMap<String, Histogram> getHistograms() {
		return registry.getHistograms();
	}

	/**
	 * @param filter
	 * @return
	 * @see com.heliosapm.opentsdb.client.registry.OpenTsdbMetricRegistry#getHistograms(com.codahale.metrics.MetricFilter)
	 */
	public SortedMap<String, Histogram> getHistograms(MetricFilter filter) {
		return registry.getHistograms(filter);
	}

	/**
	 * @return
	 * @see com.heliosapm.opentsdb.client.registry.OpenTsdbMetricRegistry#getMeters()
	 */
	public SortedMap<String, Meter> getMeters() {
		return registry.getMeters();
	}

	/**
	 * @param filter
	 * @return
	 * @see com.heliosapm.opentsdb.client.registry.OpenTsdbMetricRegistry#getMeters(com.codahale.metrics.MetricFilter)
	 */
	public SortedMap<String, Meter> getMeters(MetricFilter filter) {
		return registry.getMeters(filter);
	}

	/**
	 * @return
	 * @see com.heliosapm.opentsdb.client.registry.OpenTsdbMetricRegistry#getTimers()
	 */
	public SortedMap<String, Timer> getTimers() {
		return registry.getTimers();
	}

	/**
	 * @param filter
	 * @return
	 * @see com.heliosapm.opentsdb.client.registry.OpenTsdbMetricRegistry#getTimers(com.codahale.metrics.MetricFilter)
	 */
	public SortedMap<String, Timer> getTimers(MetricFilter filter) {
		return registry.getTimers(filter);
	}

	/**
	 * @return
	 * @see com.heliosapm.opentsdb.client.registry.OpenTsdbMetricRegistry#getMetrics()
	 */
	public Map<String, Metric> getMetrics() {
		return registry.getMetrics();
	}
	

}
