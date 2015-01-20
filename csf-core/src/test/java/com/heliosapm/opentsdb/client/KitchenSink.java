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
package com.heliosapm.opentsdb.client;

import static com.codahale.metrics.MetricRegistry.name;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;


import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.heliosapm.opentsdb.client.logging.LoggingConfiguration;
import com.heliosapm.opentsdb.client.opentsdb.Constants;
//import com.codahale.metrics.jvm.ClassLoadingGaugeSet;
//import com.codahale.metrics.jvm.FileDescriptorRatioGauge;
//import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
//import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
//import com.codahale.metrics.jvm.ThreadDeadlockDetector;
//import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.heliosapm.opentsdb.client.opentsdb.OpenTsdb;
import com.heliosapm.opentsdb.client.opentsdb.OpenTsdbReporter;
import com.heliosapm.opentsdb.client.opentsdb.Threading;
import com.heliosapm.opentsdb.client.opentsdb.jmx.OpenTsdbObjectNameFactory;
import com.heliosapm.opentsdb.client.opentsdb.jvm.BufferPoolMetricSet;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: KitchenSink</p>
 * <p>Description: Examples of each metric type so we can observe the actual implemented metric names</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.KitchenSink</code></p>
 */

public class KitchenSink {
	
	// Gauge, Counter, Histogram, Meter, Timer
	static final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
	final MetricRegistry registry = new MetricRegistry();
	final Gauge<Long> cacheSizeGauge = new Gauge<Long>() {
		@Override
		public Long getValue() {		
			final Random random = new Random(System.currentTimeMillis());
			return new Long(Math.abs(random.nextInt(1000)));
		}
	};
	final Counter evictions = registry.counter(name(getClass().getSimpleName(), "cmtype=Counter", "op=cache-evictions", "service=cacheservice"));
	final Histogram resultCounts = registry.histogram(name(getClass().getSimpleName(), "cmtype=Histogram", "op=cache-lookup", "service=cacheservice"));
	final Meter lookupRequests = registry.meter(name(getClass().getSimpleName(), "cmtype=Meter", "op=cache-lookup", "service=cacheservice"));
	final Timer timer = registry.timer(name(getClass().getSimpleName(), "cmtype=Timer", "op=cache-evictions", "service=cacheservice"));
	
	OpenTsdbReporter reporter;
	JmxReporter jmxReporter;
	// =============================================================
	//  JVM Monitors
	// =============================================================
//	final ThreadStatesGaugeSet threadStateGauge = new ThreadStatesGaugeSet(ManagementFactory.getThreadMXBean(), new ThreadDeadlockDetector());
//	final ClassLoadingGaugeSet classLoadingGauge = new ClassLoadingGaugeSet(ManagementFactory.getClassLoadingMXBean());
//	final FileDescriptorRatioGauge fileDescriptorRatioGauge = new FileDescriptorRatioGauge(ManagementFactory.getOperatingSystemMXBean());
//	final GarbageCollectorMetricSet garbageCollectorMetricSet = new GarbageCollectorMetricSet(ManagementFactory.getGarbageCollectorMXBeans());
//	final MemoryUsageGaugeSet memoryUsageGaugeSet = new MemoryUsageGaugeSet(ManagementFactory.getMemoryMXBean(), ManagementFactory.getMemoryPoolMXBeans());
	final BufferPoolMetricSet bufferPoolMonitor;
	
	
	/**
	 * Creates a new KitchenSink
	 */
	public KitchenSink() {
		registry.register(name(getClass().getSimpleName(), "cmtype=Gauge", "attr=cache-size", "service=cacheservice"), cacheSizeGauge);
		final Map<String, String> rootTags = new HashMap<String, String>();
		rootTags.put("host", "PP-WK-NWHI-01.cpex.com".toLowerCase());
		rootTags.put("app", "ptms");		
//		registry.registerAll(threadStateGauge);
//		registry.registerAll(classLoadingGauge);
//		registry.registerAll(garbageCollectorMetricSet);
//		registry.registerAll(memoryUsageGaugeSet);
		if(Util.loadClassByName("java.lang.management.BufferPoolMXBean", null)!=null) {
			bufferPoolMonitor = new BufferPoolMetricSet(mbs);
			registry.registerAll(bufferPoolMonitor);
		} else {
			bufferPoolMonitor = null;
		}
		reporter = OpenTsdbReporter.forRegistry(registry).withTags(rootTags).build(OpenTsdb.getInstance());		
		reporter = OpenTsdbReporter.forRegistry(registry).withTags(rootTags).build(OpenTsdb.getInstance());
		jmxReporter = JmxReporter.forRegistry(registry).createsObjectNamesWith(new OpenTsdbObjectNameFactory()).build();
		reporter.start(5, TimeUnit.SECONDS);		
		jmxReporter.start();
		Threading.getInstance().schedule(new Runnable(){
			final Random random = new Random(System.currentTimeMillis());
			public void run() {
				evictions.inc(random.nextInt(100));
				resultCounts.update(Math.abs(random.nextInt(100)));
				lookupRequests.mark(Math.abs(random.nextInt(50)));
				timer.update(Math.abs(random.nextInt(50)), TimeUnit.MILLISECONDS);
			}
		}, 3);
			
		
		try { Thread.currentThread().join(); } catch (Exception x) {/* No Op */}
		
	}
	
	

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		LoggingConfiguration.getInstance();
		InputStream is = null;
		startExitListener();
//        try {
//        	org.apache.logging.log4j.spi.LoggerContext lctx = LogManager.getFactory().getContext(KitchenSink.class.getName(), null, null, true, null, "TSDB");
//    		final LoggerContext ctx = new LoggerContext("TSDB-CSF");
//                    	
//        	URL configUrl = KitchenSink.class.getResource("/log4j/log4j2.xml");
//        	is = configUrl.openStream();
//        	ConfigurationSource csource = new ConfigurationSource(is, configUrl);
//        	XmlConfiguration config = new XmlConfiguration(csource); 
//        	ctx.start(config);        	
//        	log = LogManager.getLogger(KitchenSink.class);
//        	log.info("Configuration Loaded: {}", ((LoggerContext)lctx).getName());
//        } catch (Exception x) {
//        	x.printStackTrace(System.err);
//        } finally {
//        	if(is!=null) try { is.close(); } catch (Exception x) {/* No Op */}
//        }
        
//		ConsoleOutHandler.init();
//		System.setProperty("tsdb.http.tsdb.url", "http://10.12.114.48:4242");
		System.setProperty("tsdb.http.tsdb.url", "http://localhost:4242");
		System.setProperty("tsdb.threadpool.size", "60");
		System.setProperty("tsdb.http.compression.enabled", "true");
		
		
		System.out.println("Host:" + OpenTsdb.getInstance().getHostName());
		System.out.println("App:" + OpenTsdb.getInstance().getAppName());
		
		KitchenSink ks = new KitchenSink();
		

	}
	
	private static void startExitListener() {
		new Thread("ExitWatcher") {
			public void run() {
				BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
				while(true) {
					try {
						String s = br.readLine();
						if(s.equalsIgnoreCase("exit")) {
							System.exit(0);
						}
					} catch (Exception ex) {
						/* No Op */
					}
				}				
			}
		}.start();
		
	}

}
