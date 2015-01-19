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


import static com.heliosapm.opentsdb.client.opentsdb.Constants.APP_TAG;
import static com.heliosapm.opentsdb.client.opentsdb.Constants.HOST_TAG;
import static com.heliosapm.opentsdb.client.util.Util.appName;
import static com.heliosapm.opentsdb.client.util.Util.hostName;

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.heliosapm.opentsdb.client.opentsdb.jmx.OpenTsdbObjectNameFactory;
import com.heliosapm.opentsdb.client.util.Util;



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
	
	/** The cached app name */
	private volatile String appName = null;
	/** The cached host name */
	private volatile String hostName = null;
	/** The OpenTSDB agent metric registry */
	private final MetricRegistry registry = new MetricRegistry();
	/** JMX reporter to expose OpenTSDB agent metrics */
	private final JmxReporter jmxReporter = JmxReporter
			.forRegistry(registry)
			.createsObjectNamesWith(new OpenTsdbObjectNameFactory())
			.registerWith(ManagementFactory.getPlatformMBeanServer())
			.build();
			
	
    /** Static class logger */
    protected static final Logger logger = LogManager.getLogger(OpenTsdb.class);
    
    /** The global tags */
    final Map<String, String> GLOBAL_TAGS = new ConcurrentHashMap<String, String>(6);
    
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
		hostName = getHostName();
		appName = getAppName();		
		batchSize = ConfigurationReader.confInt(Constants.PROP_BATCH_SIZE,  Constants.DEFAULT_BATCH_SIZE);
		loadExtraTags();
		jmxReporter.start();
	}
	
	/**
	 * Loads sysprop defined extra tags 
	 */
	private void loadExtraTags() {
		String v = System.getProperty(Constants.PROP_EXTRA_TAGS, "").replace(" ", "");
		if(!v.isEmpty()) {
			Matcher m = Constants.KVP_PATTERN.matcher(v);
			while(m.find()) {
				String key = Util.clean(m.group(1));
				if(HOST_TAG.equalsIgnoreCase(key) || APP_TAG.equalsIgnoreCase(key)) continue;
				String value = Util.clean(m.group(1));
				GLOBAL_TAGS.put(key, value);
			}
		}
	}
	
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
		if(hostName!=null) {
			return hostName;
		}		
		hostName = hostName();
		System.setProperty(Constants.PROP_HOST_NAME, hostName);
		GLOBAL_TAGS.put(HOST_TAG, hostName);
		return hostName;
	}
	
	/**
	 * Attempts to find a reliable app name
	 * @return the app name
	 */
	public String getAppName() {
		if(appName!=null) {
			return appName;
		}
		appName = appName();
		System.setProperty(Constants.PROP_APP_NAME, appName);
		GLOBAL_TAGS.put(APP_TAG, appName);
		return appName;
	}
	
	/**
	 * Returns an id string displaying the host and app name
	 * @return the id string
	 */
	public String getId() {
		return getAppName() + "@" + getHostName();
	}
	
	/**
	 * Returns the tsdb client registry
	 * @return the tsdb client registry
	 */
	public MetricRegistry getMetricRegistry() {
		return registry;
	}
	
	/**
	 * Returns the tsdb client jmxReporter
	 * @return the tsdb client jmxReporter
	 */
	public JmxReporter getJmxReporter() {
		return jmxReporter;
	}
	
	
	/**
	 * Resets the cached app and host names
	 */
	void resetNames() {
		appName = null;
		hostName = null;
	}
	
	/**
	 * Resets the cached app and host names. If a new name is set, the corresponding
	 * system property {@link Constants#PROP_HOST_NAME} and/or {@link Constants#PROP_APP_NAME}
	 * will be updated. 
	 * @param newHostName The new host name to set. Ignored if null or empty.
	 * @param newAppName The new app name to set. Ignored if null or empty.
	 */
	void resetNames(final String newHostName, final String newAppName) {
		if(newHostName!=null && newHostName.trim().isEmpty()) {
			hostName = newHostName.trim();
			System.setProperty(Constants.PROP_HOST_NAME, hostName);
			GLOBAL_TAGS.put(HOST_TAG, hostName);
		}
		if(newAppName!=null && newAppName.trim().isEmpty()) {
			appName = newAppName.trim();
			System.setProperty(Constants.PROP_APP_NAME, appName);
			GLOBAL_TAGS.put(APP_TAG, appName);
		}
	}
	

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

}
