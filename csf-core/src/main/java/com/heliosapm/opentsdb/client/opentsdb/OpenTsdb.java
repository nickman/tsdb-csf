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


import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Timer;
import com.heliosapm.opentsdb.client.logging.LoggingConfiguration;
import com.heliosapm.opentsdb.client.name.AgentName;
import com.heliosapm.opentsdb.client.opentsdb.jmx.OpenTsdbObjectNameFactory;
import com.heliosapm.opentsdb.client.registry.IMetricRegistry;
import com.heliosapm.opentsdb.client.registry.IMetricRegistryFactory;
import com.heliosapm.opentsdb.client.registry.OpenTsdbMetricRegistry;



/**
 * <p>Title: OpenTsdb</p>
 * <p>Description: The core OpenTSDB client agent.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Sean Scanlon <sean.scanlon@gmail.com>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.OpenTsdb</code></p>
 */
public class OpenTsdb {
	/** The singleton instance */
	private static volatile OpenTsdb instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();	
	/** The configured batch size */
	protected int batchSize;	
	/** The Http metric poster */
	protected HttpMetricsPoster httpClient;
	
	/** The OpenTSDB agent metric registry */
	private final IMetricRegistry registry = new OpenTsdbMetricRegistry();
//	/** JMX reporter to expose OpenTSDB agent metrics */
//	private final JmxReporter jmxReporter = JmxReporter
//			.forRegistry(registry)
//			.createsObjectNamesWith(new OpenTsdbObjectNameFactory())
//			.registerWith(ManagementFactory.getPlatformMBeanServer())
//			.build();
	
    
    
//    /**
//     * Returns all the metric registries that have had OpenTsdbReporters created with them
//     * @return a set of metric registries containing OpenTsdbMetrics
//     */
//    public Set<IMetricRegistry> getRegistries() {
//    	Set<IMetricRegistry> set = new HashSet<IMetricRegistry>(registries.size());
//    	Set<MRWR> remove = new HashSet<MRWR>();
//    	for(MRWR registryRef: registries) {
//    		IMetricRegistry mr = registryRef.get(); 
//    		if(mr==null) {
//    			remove.add(registryRef);
//    		} else {
//    			set.add(mr);
//    		}
//    	}
//    	if(!remove.isEmpty()) {
//    		registries.removeAll(remove);
//    	}
//    	return set;
//    }
    
//    /**
//     * Adds a registry for tracking
//     * @param registry The registry to track
//     * @return true if added, false if it was already added
//     */
//    public boolean addRegistry(final MetricRegistry registry) {
//    	if(registry==null) throw new IllegalArgumentException("The passed metric was null");
//    	return registries.add(new MRWR(IMetricRegistryFactory.wrap(registry)));
//    }
//    
//    /**
//     * Adds a registry for tracking
//     * @param registry The registry to track
//     * @return true if added, false if it was already added
//     */
//    public boolean addRegistry(final OpenTsdbMetricRegistry registry) {
//    	if(registry==null) throw new IllegalArgumentException("The passed metric was null");
//    	return registries.add(new MRWR(registry));
//    }
    
    
    
	
			
	
    /** Static class logger */
    protected static final Logger logger = LogManager.getLogger(OpenTsdb.class);
    
    
    static final String METRIC_NAME_PREFIX = "tsdbclient.";
    
    
	/**
	 * Acquires the OpenTsdb singleton instance
	 * @return the OpenTsdb singleton instance
	 */
	public static OpenTsdb getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new OpenTsdb(); 
					instance.httpClient = HttpMetricsPoster.getInstance();
				}
			}
		}
		return instance;
	}
	
    
	
	private OpenTsdb() {
		batchSize = ConfigurationReader.confInt(Constants.PROP_BATCH_SIZE,  Constants.DEFAULT_BATCH_SIZE);			
//		jmxReporter.start();
	}
	
//	/**
//	 * Adds a metric name to the OpenTsdb metric set
//	 * @param name The name to add
//	 */
//	public void addOpenTsdbMetricName(final String name) {
//		if(name!=null && !name.isEmpty()) {
//			allMetricNames.add(name);
//		}
//	}
//	
	
//	/**
//	 * Returns a set of all the metric names in the registry
//	 * @param recurse true to recurse through metric sets, false for top level only
//	 * @return all the metric names in the registry
//	 */
//	public Set<String> dumpMetricNames(final boolean recurse) {
//		final Set<String> metricNames = new TreeSet<String>();
//		if(recurse) {
//			metricNames.addAll(allMetricNames);
//		}
//		Set<MRWR> remove = new HashSet<MRWR>();
//		for(MRWR mr: registries) {
//			IMetricRegistry r = mr.get();
//			if(r==null) {
//				remove.add(mr);
//				continue;
//			}
//			metricNames.addAll(r.getNames());
//		}
//		if(!remove.isEmpty()) {
//			registries.removeAll(remove);
//		}
//		return metricNames;
//	}
//	
//	protected void getUniqueMetricNames(final MetricSet metricSet, final Set<String> metricNames) {
//        if(metricSet==null) return;        
//        if(metricSet instanceof MetricRegistry) {
//        	final MetricRegistry registry = (MetricRegistry)metricSet;
//        	metricNames.addAll(registry.getGauges().keySet());
//        	metricNames.addAll(registry.getCounters().keySet());
//        	metricNames.addAll(registry.getHistograms().keySet());
//        	metricNames.addAll(registry.getMeters().keySet());
//        	metricNames.addAll(registry.getTimers().keySet());        
//        } else {
//        	recurse(metricSet, metricNames);
//        }
//		
//		
//	}
//	
//	/**
//	 * Recurses through the passed metric set to find all the unique metric names
//	 * @param metricSet The metric set to recurse
//	 * @param metricNames The set of metric names to add to
//	 */
//	protected void recurse(final MetricSet metricSet, final Set<String> metricNames) {
//		if(metricSet==null) return;
//		for(Map.Entry<String, Metric> entry: metricSet.getMetrics().entrySet()) {
//			if(entry.getValue() instanceof MetricSet) {
//				recurse((MetricSet)entry.getValue(), metricNames);
//			} else {
//				metricNames.add(entry.getKey());
//			}			
//		}		
//	}
//	
//	/**
//	 * Returns the total number of metrics in all OpenTsdbReported registries
//	 * @return the total number of metrics
//	 */
//	public int getMetricCount() {
//		return allMetricNames.size();
//	}
//	
	/**
	 * Returns the current batch size
	 * @return the current batch size
	 */
	public int getBatchSize() {
		return batchSize;
	}

	/**
	 * Sets a new batch size
	 * @param batchSize the new batch size
	 */
	public void setBatchSize(final int batchSize) {
		this.batchSize = batchSize;
	}
	
	/**
	 * Attempts a series of methods of divining the host name
	 * @return the determined host name
	 */
	public String getHostName() {
		return AgentName.getInstance().getHostName();
	}
	
	/**
	 * Attempts to find a reliable app name
	 * @return the app name
	 */
	public String getAppName() {
		return AgentName.getInstance().getAppName();
	}
	
	
	/**
	 * Returns the tsdb client registry
	 * @return the tsdb client registry
	 */
	public IMetricRegistry getMetricRegistry() {
		return registry;
	}
	
//	/**
//	 * Returns the tsdb client jmxReporter
//	 * @return the tsdb client jmxReporter
//	 */
//	public JmxReporter getJmxReporter() {
//		return jmxReporter;
//	}
	
	
	

    /**
     * Send a metric to opentsdb
     *
     * @param metric the metric to send
     */
    public void send(final OpenTsdbMetric metric) {
        send(Collections.singleton(metric));
    }

    /**
     * send a set of metrics to opentsdb
     *
     * @param metrics the metric set to send
     */
    public void send(final Set<OpenTsdbMetric> metrics) {
        // we set the patch size because of existing issue in opentsdb where large batch of metrics failed
        // see at https://groups.google.com/forum/#!topic/opentsdb/U-0ak_v8qu0
        // we recommend batch size of 5 - 10 will be safer
        // alternatively you can enable chunked request
        if (batchSize > 0 && metrics.size() > batchSize) {
            final Set<OpenTsdbMetric> smallMetrics = new HashSet<OpenTsdbMetric>();
            for (final OpenTsdbMetric metric: metrics) {
                smallMetrics.add(metric);
                if (smallMetrics.size() >= batchSize) {
                    sendHelper(smallMetrics);
                    smallMetrics.clear();
                }
            }
            sendHelper(smallMetrics);
        } else {
            sendHelper(metrics);
        }
    }

    
    
    private void sendHelper(final Set<OpenTsdbMetric> metrics) {
        /*
         * might want to bind to a specific version of the API.
         * according to: http://opentsdb.net/docs/build/html/api_http/index.html#api-versioning
         * "if you do not supply an explicit version, ... the latest version will be used."
         * circle back on this if it's a problem.
         */
    	if(httpClient==null) {
    		httpClient = HttpMetricsPoster.getInstance();
    	}
        if (!metrics.isEmpty()) {
        	httpClient.postMetrics(metrics);
//        	StringBuilder b = new StringBuilder();
//        	for(OpenTsdbMetric m: metrics) {
//        		b.append("\n").append(m.getMetric()).append(" : ").append(m.getTags().toString().replace("{", "").replace("}", ""));
//        	}
//        	System.out.println(b.toString());
        }
    }
    
    public static final Pattern CM_TYPE_PATTERN = Pattern.compile("\\.?cmtype=.*");

    public static String attachCMType(final String name, final String type) {
    	if(name==null || name.trim().isEmpty()) return name;
    	if(!CM_TYPE_PATTERN.matcher(name.trim()).matches()) {
    		return name + ".cmtype=" + type;
    	}
    	return name.trim();
    }

	/**
	 * Creates a new local client metric counter
	 * @param clazz the class the metric is being recorded for
	 * @param names the metric name tags
	 * @return the counter
	 * @see com.codahale.metrics.MetricRegistry#counter(java.lang.String)
	 */
	public Counter counter(final Class<?> clazz, final String...names) {
		return registry.counter(attachCMType(MetricRegistry.name(METRIC_NAME_PREFIX + clazz.getSimpleName(), names), "Counter"));
	}



	/**
	 * Creates a new local client metric timer
	 * @param clazz the class the metric is being recorded for
	 * @param names the metric name tags
	 * @return the timer
	 * @see com.codahale.metrics.MetricRegistry#histogram(java.lang.String)
	 */
	public Histogram histogram(final Class<?> clazz, final String...names) {
		return registry.histogram(attachCMType(MetricRegistry.name(METRIC_NAME_PREFIX + clazz.getSimpleName(), names), "Histogram"));
	}



	/**
	 * Creates a new local client metric meter
	 * @param clazz the class the metric is being recorded for
	 * @param names the metric name tags
	 * @return the meter
	 * @see com.codahale.metrics.MetricRegistry#meter(java.lang.String)
	 */
	public Meter meter(final Class<?> clazz, final String...names) {
		return registry.meter(attachCMType(MetricRegistry.name(METRIC_NAME_PREFIX + clazz.getSimpleName(), names), "Counter"));
	}



	/**
	 * Creates a new local client metric timer
	 * @param clazz the class the metric is being recorded for
	 * @param names the metric name tags
	 * @return the timer
	 * @see com.codahale.metrics.MetricRegistry#timer(java.lang.String)
	 */
	public Timer timer(final Class<?> clazz, final String...names) {
		return registry.timer(attachCMType(MetricRegistry.name(METRIC_NAME_PREFIX + clazz.getSimpleName(), names), "Timer"));
	}
	
    static {
    	// init boot logging as early as possible
    	LoggingConfiguration.getInstance();
    }
	

}
