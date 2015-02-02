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
import java.io.InputStreamReader;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.heliosapm.opentsdb.client.opentsdb.OpenTSDBReporter;
//import com.codahale.metrics.jvm.ClassLoadingGaugeSet;
//import com.codahale.metrics.jvm.FileDescriptorRatioGauge;
//import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
//import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
//import com.codahale.metrics.jvm.ThreadDeadlockDetector;
//import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.heliosapm.opentsdb.client.opentsdb.OpenTsdb;
import com.heliosapm.opentsdb.client.opentsdb.OpenTsdbReporter;
import com.heliosapm.opentsdb.client.opentsdb.Threading;

/**
 * <p>Title: KitchenSink</p>
 * <p>Description: Examples of each metric type so we can observe the actual implemented metric names</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.KitchenSink</code></p>
 */

public class KitchenSink {
	
	
	// Create a new MetricRegistry
	final MetricRegistry registry;
	// Create a new Gauge Metric
	final Gauge<Long> cacheSizeGauge = new Gauge<Long>() {
		@Override
		public Long getValue() {		
			final Random random = new Random(System.currentTimeMillis());
			return new Long(Math.abs(random.nextInt(1000)));
		}
	};
	// Define a new Counter Metric
	final Counter evictions;
	// Define a new Histogram Metric
	final Histogram resultCounts;
	// Define a new Meter Metric
	final Meter lookupRequests;
	// Define a new Timer Metric
	final Timer timer;
	

	/**
	 * Creates a new KitchenSink
	 */
	public KitchenSink() {
		this(null);
	}

	/**
	 * Creates a new KitchenSink
	 * @param tsdbReporter An optional OpenTSDBReporter reporter
	 */
	public KitchenSink(final OpenTSDBReporter tsdbReporter) {
		registry = tsdbReporter==null ? new MetricRegistry() : tsdbReporter.getRegistry();
		// Register metrics
		registry.register(name(getClass().getSimpleName(), "cache-size", "cmtype=Gauge", "attr=cache-size", "service=cacheservice"), cacheSizeGauge);
		evictions = registry.counter(name(getClass().getSimpleName(), 
						"evictions", 
						"op=cache-evictions", 
						"service=cacheservice"));
		resultCounts = registry.histogram(name(getClass().getSimpleName(), "resultCounts", "op=cache-lookup", "service=cacheservice"));
		lookupRequests = registry.meter(name(getClass().getSimpleName(), "lookupRequests",  "op=cache-lookup", "service=cacheservice"));
		timer = registry.timer(name(getClass().getSimpleName(), "evictelapsed", "op=cache-evictions", "service=cacheservice"));
		// We don't need this. The AgentName service will take care of it.
		/*
		final Map<String, String> rootTags = new HashMap<String, String>();
		rootTags.put("host", "myappserver.loodicrous.org".toLowerCase());
		rootTags.put("app", "KitchenSink");
		*/		
		/* Create the reporter */
		// Define the OpenTsdbReporter. Will create in the ctor
		final OpenTSDBReporter reporter = tsdbReporter==null ? OpenTSDBReporter.forRegistry(registry).build() : tsdbReporter;
		final ConsoleReporter creporter = ConsoleReporter.forRegistry(registry).build();
		/** Start the reporter with a reporting period of 5 seconds */
		if(tsdbReporter==null) {
			reporter.start(1, TimeUnit.SECONDS);
		}
//		creporter.start(5, TimeUnit.SECONDS);
		/** Start a thread to generate some random data */
		Threading.getInstance().schedule(new Runnable(){
			final Random random = new Random(System.currentTimeMillis());
			public void run() {
				evictions.inc(random.nextInt(100));
				resultCounts.update(Math.abs(random.nextInt(100)));
				lookupRequests.mark(Math.abs(random.nextInt(50)));
				timer.update(Math.abs(random.nextInt(50)), TimeUnit.MILLISECONDS);
			}
		}, 3);
		// Let it run
		try { Thread.currentThread().join(); } catch (Exception x) {/* No Op */}
	}
	
	

	/**
	 * Start KitchenSink
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println(org.apache.logging.log4j.LogManager.class.getProtectionDomain().getCodeSource().getLocation());

		/**
		 * If you want a clean shutdown, use this, and just type "exit" <return> at the console.
		 * If you don't care, comment it.
		 * 
		 */
		startExitListener();
		//=========================================================================================
		//  Here are some system props you could set, but mostly don't need to.
		//  See com.heliosapm.opentsdb.client.opentsdb.Constants for the full set of props.
		//=========================================================================================
		
		/** We don't need this, it's the default */
//		System.setProperty("tsdb.http.tsdb.url", "http://localhost:6262");
		System.setProperty("tsdb.http.tsdb.url", "http://localhost:4242");
		/** We don't need this, but if we were using a local Bosun instance, this would work */
//		System.setProperty("tsdb.http.tsdb.url", "http://localhost:8070");
		/** You might need this. Default thread pool size is (<# of cores>*2) + 2 */
//		System.setProperty("tsdb.threadpool.size", "60");
		/** We don't need this, it's enabled automatically,and disabled if the server complains */
//		System.setProperty("tsdb.http.compression.enabled", "false");
		/** We don't need this. The default is 100. The commented example would be used if you
		 * wanted to batch as many metrics together as possible. Server might (should) limit post sizes. */
//		System.setProperty(""tsdb.http.batch.size"", "" + Integer.MAX_VALUE);
		/** We don't need this, by default uses the "/api/config" end point of the target OpenTSDB server. 
		 * The same end point will work for Bosun. */
//		System.setProperty("tsdb.http.check.path", "/api/config");
		/** We don't need this. Defaults to GET which OpenTSDB likes, and it won't accept HEAD
		 * which is a quicker check. Bosun accepts HEAD requests, so changing to HEAD in that case
		 * might make sense.
		 */
//		System.setProperty("tsdb.http.check.method", "HEAD");

		//  Start the KitchenSink
		KitchenSink ks = new KitchenSink();
		System.out.println("KitchenSink Started");
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
