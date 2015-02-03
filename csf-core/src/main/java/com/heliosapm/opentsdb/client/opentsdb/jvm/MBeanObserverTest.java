package com.heliosapm.opentsdb.client.opentsdb.jvm;

import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;

import com.codahale.metrics.MetricRegistry;
import com.heliosapm.opentsdb.client.opentsdb.OpenTSDBReporter;
import com.heliosapm.opentsdb.client.util.Util;

public class MBeanObserverTest {
	
	public static void log(final Object msg) {
		System.out.println(msg);
	}
	

	public static void main(String[] args) {		
		log("MBeanObserverTest");
		System.setProperty("tsdb.http.tsdb.url", "http://localhost:6262");
//		System.setProperty("tsdb.http.tsdb.url", "http://localhost:4242");
		//System.setProperty(Constants.PROP_TIME_IN_SEC, "false");
//		System.setProperty("tsdb.http.compression.enabled", "false");
		
		final String HOTSPOT_INTERNAL_MBEAN_NAME = "sun.management:type=HotspotInternal";
		final String HOTSPOT_INTERNAL_CLASS_NAME = "sun.management.HotspotInternal";
		
		
		final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
		try {
			server.createMBean(HOTSPOT_INTERNAL_CLASS_NAME, Util.objectName(HOTSPOT_INTERNAL_MBEAN_NAME));
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
		}
		
		final BaseMBeanObserver compileTime = MBeanObserverBuilder.newBuilder(server, Util.objectName(ManagementFactory.COMPILATION_MXBEAN_NAME), CompilationMBeanObserver.class).build();
		final BaseMBeanObserver classLoads = MBeanObserverBuilder.newBuilder(server, Util.objectName(ManagementFactory.CLASS_LOADING_MXBEAN_NAME), ClassLoadingMBeanObserver.class).build();
		final BaseMBeanObserver bufferPools = MBeanObserverBuilder.newBuilder(server, Util.objectName(BufferPoolMetricSet.OBJECT_PATTERN), BufferPoolMetricSet.class).build();
		final BaseMBeanObserver mem = MBeanObserverBuilder.newBuilder(server, Util.objectName(ManagementFactory.MEMORY_MXBEAN_NAME), MemoryMonitorMetricSet.class).build();
		final BaseMBeanObserver gc = MBeanObserverBuilder.newBuilder(server, Util.objectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*"), GCMonitorMetricSet.class).build();
		
		
		
		MetricRegistry reg = new MetricRegistry();
		reg.registerAll(compileTime);
		reg.registerAll(classLoads);
		reg.registerAll(bufferPools);
		reg.registerAll(mem);
		reg.registerAll(gc);
//		ConsoleReporter creporter = ConsoleReporter.forRegistry(reg).outputTo(System.out).build();
		OpenTSDBReporter reporter = OpenTSDBReporter.forRegistry(reg).build();
		reporter.start(5, TimeUnit.SECONDS);
//		creporter.start(5, TimeUnit.SECONDS);
//		KitchenSink ks = new KitchenSink(reporter);
		while(true) {
//			ByteBuffer.allocateDirect(Math.abs(r.nextInt(100))+1);
			try { Thread.currentThread().join(3000); } catch (Exception x) {}
		}
		
	}

}
