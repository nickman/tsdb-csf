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


import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.heliosapm.opentsdb.client.logging.LoggingConfiguration;
import com.heliosapm.opentsdb.client.name.AgentName;



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
	
	protected Logger traceLogger = LogManager.getLogger("trace-metrics");
	
			
	
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
	
	
    
    
    public void send(final ChannelBuffer chBuff, final int metricCount) {
    	if(httpClient==null) {
    		httpClient = HttpMetricsPoster.getInstance();
    	}
    	if(chBuff.readableBytes()<5) return;
//    	try {
//    		System.err.println(new JSONArray(chBuff.toString(Constants.UTF8)).toString(1));
//    	} catch (Exception ex) {
//    		ex.printStackTrace(System.err);
//    	}
    	httpClient.send(chBuff, metricCount);
    	logger.debug("Sent [{}] metrics", metricCount);
    }
    
    
//    private void sendHelper(final Set<OpenTsdbMetric> metrics) {
//        /*
//         * might want to bind to a specific version of the API.
//         * according to: http://opentsdb.net/docs/build/html/api_http/index.html#api-versioning
//         * "if you do not supply an explicit version, ... the latest version will be used."
//         * circle back on this if it's a problem.
//         */
//    	if(httpClient==null) {
//    		httpClient = HttpMetricsPoster.getInstance();
//    	}
//        if (!metrics.isEmpty()) {
//        	httpClient.postMetrics(metrics);
////        	StringBuilder b = new StringBuilder();
////        	for(OpenTsdbMetric m: metrics) {
////        		b.append("\n").append(m.getMetric()).append(" : ").append(m.getTags().toString().replace("{", "").replace("}", ""));
////        	}
////        	System.out.println(b.toString());
//        }
//    }
    
    public static final Pattern CM_TYPE_PATTERN = Pattern.compile("\\.?cmtype=.*");

    public static String attachCMType(final String name, final String type) {
    	if(name==null || name.trim().isEmpty()) return name;
    	if(!CM_TYPE_PATTERN.matcher(name.trim()).matches()) {
    		return name + ".cmtype=" + type;
    	}
    	return name.trim();
    }

	
    static {
    	// init boot logging as early as possible
    	LoggingConfiguration.getInstance();
    }
	

}
