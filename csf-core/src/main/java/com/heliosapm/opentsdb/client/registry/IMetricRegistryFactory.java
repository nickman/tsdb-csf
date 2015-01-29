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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

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
 * <p>Title: IMetricRegistryFactory</p>
 * <p>Description: A factory for creating {@link IMetricRegistry} wrappers</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.registry.IMetricRegistryFactory</code></p>
 */

public class IMetricRegistryFactory {

	/** A wek ref map of known wrapped MetricRegistries keyed by their system id hashcodes. */
	private static final Map<Integer, WeakReference<IMetricRegistry>> metricRegistries = new ConcurrentHashMap<Integer, WeakReference<IMetricRegistry>>();
	
	private static final ReferenceQueue<IMetricRegistry> refQueue = new ReferenceQueue<IMetricRegistry>();
	
	private static class MetricRegistryReference extends WeakReference<IMetricRegistry> {
		final int id;
		public MetricRegistryReference(final IMetricRegistry referent, final ReferenceQueue<? super IMetricRegistry> q) {
			super(referent, q);
			this.id =  referent.hashCode();
		}
		
		static MetricRegistryReference newInstance(final IMetricRegistry referent) {
			return new MetricRegistryReference(referent, refQueue);
		}
	}
	
	/**
	 * Returns a wrapped {@link MetricRegistry} so that it implements {@link IMetricRegistry}.
	 * A weak reference cache is maintained to ensure the same {@link IMetricRegistry} instance is returned
	 * for the same {@link MetricRegistry}. 
	 * @param registry The registry to wrap
	 * @return the wrapped registry.
	 */
	public static IMetricRegistry wrap(final MetricRegistry registry) {
		if(registry==null) throw new IllegalArgumentException("The passed registry was null");
		final Integer sysIdHash = System.identityHashCode(registry);
		IMetricRegistry imr = null;
		WeakReference<IMetricRegistry> wmr = metricRegistries.get(sysIdHash);
		if(wmr!=null) imr = wmr.get();
		if(imr==null) {
			synchronized(metricRegistries) {
				wmr = metricRegistries.get(sysIdHash);
				if(wmr!=null) imr = wmr.get();				
				if(imr==null) {
					imr = new IMetricRegistryWrapper(registry);
					metricRegistries.put(sysIdHash, MetricRegistryReference.newInstance(imr));
				}
			}
		}
		return imr;
	}
	
	/**
	 * Returns a wrapped {@link IMetricRegistry} for the passed {@link MetricSet}.
	 * A weak reference cache is maintained to ensure the same {@link IMetricRegistry} instance is returned
	 * for the same {@link MetricRegistry}. 
	 * @param metricSet The set to wrap
	 * @return the wrapped registry.
	 */
	public static IMetricRegistry wrap(final MetricSet metricSet) {
		if(metricSet instanceof IMetricRegistry) return (IMetricRegistry)metricSet;
		return new OpenTsdbMetricRegistry(metricSet);
	}

	static void register(final OpenTsdbMetricRegistry otreg) {
		metricRegistries.put(otreg.hashCode(), MetricRegistryReference.newInstance(otreg));
	}
	
	static class IMetricRegistryWrapper implements IMetricRegistry {
		final MetricRegistry registry;
		
		IMetricRegistryWrapper(final MetricRegistry registry) {
			if(registry==null) throw new IllegalArgumentException("The passed registry was null");
			this.registry = registry;
		}
		
		/**
		 * {@inheritDoc}
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			return System.identityHashCode(registry);
		}

		/**
		 * {@inheritDoc}
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (!(obj instanceof IMetricRegistryWrapper))
				return false;
			IMetricRegistryWrapper other = (IMetricRegistryWrapper) obj;
			if (registry == null) {
				if (other.registry != null)
					return false;
			} else if (!registry.equals(other.registry))
				return false;
			return true;
		}




		/**
		 * @param name
		 * @param metric
		 * @return
		 * @throws IllegalArgumentException
		 * @see com.codahale.metrics.MetricRegistry#register(java.lang.String, com.codahale.metrics.Metric)
		 */
		public <T extends Metric> T register(String name, T metric)
				throws IllegalArgumentException {
			return registry.register(name, metric);
		}

		/**
		 * @param metrics
		 * @throws IllegalArgumentException
		 * @see com.codahale.metrics.MetricRegistry#registerAll(com.codahale.metrics.MetricSet)
		 */
		public void registerAll(MetricSet metrics)
				throws IllegalArgumentException {
			registry.registerAll(metrics);
		}

		/**
		 * @param name
		 * @return
		 * @see com.codahale.metrics.MetricRegistry#counter(java.lang.String)
		 */
		public Counter counter(String name) {
			return registry.counter(name);
		}

		/**
		 * @param name
		 * @return
		 * @see com.codahale.metrics.MetricRegistry#histogram(java.lang.String)
		 */
		public Histogram histogram(String name) {
			return registry.histogram(name);
		}

		/**
		 * @param name
		 * @return
		 * @see com.codahale.metrics.MetricRegistry#meter(java.lang.String)
		 */
		public Meter meter(String name) {
			return registry.meter(name);
		}

		/**
		 * @param name
		 * @return
		 * @see com.codahale.metrics.MetricRegistry#timer(java.lang.String)
		 */
		public Timer timer(String name) {
			return registry.timer(name);
		}

		/**
		 * @param name
		 * @return
		 * @see com.codahale.metrics.MetricRegistry#remove(java.lang.String)
		 */
		public boolean remove(String name) {
			return registry.remove(name);
		}

		/**
		 * @param filter
		 * @see com.codahale.metrics.MetricRegistry#removeMatching(com.codahale.metrics.MetricFilter)
		 */
		public void removeMatching(MetricFilter filter) {
			registry.removeMatching(filter);
		}

		/**
		 * @param listener
		 * @see com.codahale.metrics.MetricRegistry#addListener(com.codahale.metrics.MetricRegistryListener)
		 */
		public void addListener(MetricRegistryListener listener) {
			registry.addListener(listener);
		}

		/**
		 * @param listener
		 * @see com.codahale.metrics.MetricRegistry#removeListener(com.codahale.metrics.MetricRegistryListener)
		 */
		public void removeListener(MetricRegistryListener listener) {
			registry.removeListener(listener);
		}

		/**
		 * @return
		 * @see com.codahale.metrics.MetricRegistry#getNames()
		 */
		public SortedSet<String> getNames() {
			return registry.getNames();
		}

		/**
		 * @return
		 * @see com.codahale.metrics.MetricRegistry#getGauges()
		 */
		public SortedMap<String, Gauge> getGauges() {
			return registry.getGauges();
		}

		/**
		 * @param filter
		 * @return
		 * @see com.codahale.metrics.MetricRegistry#getGauges(com.codahale.metrics.MetricFilter)
		 */
		public SortedMap<String, Gauge> getGauges(MetricFilter filter) {
			return registry.getGauges(filter);
		}

		/**
		 * @return
		 * @see com.codahale.metrics.MetricRegistry#getCounters()
		 */
		public SortedMap<String, Counter> getCounters() {
			return registry.getCounters();
		}

		/**
		 * @param filter
		 * @return
		 * @see com.codahale.metrics.MetricRegistry#getCounters(com.codahale.metrics.MetricFilter)
		 */
		public SortedMap<String, Counter> getCounters(MetricFilter filter) {
			return registry.getCounters(filter);
		}

		/**
		 * @return
		 * @see com.codahale.metrics.MetricRegistry#getHistograms()
		 */
		public SortedMap<String, Histogram> getHistograms() {
			return registry.getHistograms();
		}

		/**
		 * @param filter
		 * @return
		 * @see com.codahale.metrics.MetricRegistry#getHistograms(com.codahale.metrics.MetricFilter)
		 */
		public SortedMap<String, Histogram> getHistograms(MetricFilter filter) {
			return registry.getHistograms(filter);
		}

		/**
		 * @return
		 * @see com.codahale.metrics.MetricRegistry#getMeters()
		 */
		public SortedMap<String, Meter> getMeters() {
			return registry.getMeters();
		}

		/**
		 * @param filter
		 * @return
		 * @see com.codahale.metrics.MetricRegistry#getMeters(com.codahale.metrics.MetricFilter)
		 */
		public SortedMap<String, Meter> getMeters(MetricFilter filter) {
			return registry.getMeters(filter);
		}

		/**
		 * @return
		 * @see com.codahale.metrics.MetricRegistry#getTimers()
		 */
		public SortedMap<String, Timer> getTimers() {
			return registry.getTimers();
		}

		/**
		 * @param filter
		 * @return
		 * @see com.codahale.metrics.MetricRegistry#getTimers(com.codahale.metrics.MetricFilter)
		 */
		public SortedMap<String, Timer> getTimers(MetricFilter filter) {
			return registry.getTimers(filter);
		}

		/**
		 * @return
		 * @see com.codahale.metrics.MetricRegistry#getMetrics()
		 */
		public Map<String, Metric> getMetrics() {
			return registry.getMetrics();
		}

		
		
	}
	
	  /**
	  * Returns all the metric registries that have had OpenTsdbReporters created with them
	  * @return a set of metric registries containing OpenTsdbMetrics
	  */
	 public static Set<IMetricRegistry> getRegistries() {
	 	Set<IMetricRegistry> set = new HashSet<IMetricRegistry>(metricRegistries.size());
	 	synchronized(metricRegistries) {
	 	 	for(WeakReference<IMetricRegistry> mr: metricRegistries.values()) {
	 	 		IMetricRegistry imr = mr.get();
	 	 		if(imr==null) continue;
	 			set.add(imr); 		
	 	 	} 		
	 	}
	 	return set;
	 }
	 
	 
	/**
	 * Returns a set of all the metric names in the registry
	 * @param recurse true to recurse through metric sets, false for top level only
	 * @return all the metric names in the registry
	 */
	public static Set<String> dumpMetricNames(final boolean recurse) {
		final Set<String> metricNames = new TreeSet<String>();
		final Set<IMetricRegistry> mrs = getRegistries();
		for(IMetricRegistry mr: mrs) {
			for(Map.Entry<String, Metric> entry: mr.getMetrics().entrySet()) {
				final Metric m = entry.getValue();
				if(m instanceof MetricSet) {
					recurse((MetricSet)m, metricNames);
				} else {
					metricNames.add(entry.getKey());
				}
			}
		}
		return metricNames;
	}
	
	public static void getUniqueMetricNames(final MetricSet metricSet, final Set<String> metricNames) {
	    if(metricSet==null) return;        
	    if(metricSet instanceof IMetricRegistry) {
	    	final IMetricRegistry registry = (IMetricRegistry)metricSet;
	    	metricNames.addAll(registry.getGauges().keySet());
	    	metricNames.addAll(registry.getCounters().keySet());
	    	metricNames.addAll(registry.getHistograms().keySet());
	    	metricNames.addAll(registry.getMeters().keySet());
	    	metricNames.addAll(registry.getTimers().keySet());        
	    } else {
	    	recurse(metricSet, metricNames);
	    }
	}
	
	/**
	 * Recurses through the passed metric set to find all the unique metric names
	 * @param metricSet The metric set to recurse
	 * @param metricNames The set of metric names to add to
	 */
	protected static void recurse(final MetricSet metricSet, final Set<String> metricNames) {
		if(metricSet==null) return;
		for(Map.Entry<String, Metric> entry: metricSet.getMetrics().entrySet()) {
			if(entry.getValue() instanceof MetricSet) {
				recurse((MetricSet)entry.getValue(), metricNames);
			} else {
				metricNames.add(entry.getKey());
			}			
		}		
	}
	
	

}
