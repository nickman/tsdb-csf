package com.heliosapm.opentsdb.client.opentsdb.jvm;

import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.heliosapm.opentsdb.client.opentsdb.OpenTsdb;
import com.heliosapm.opentsdb.client.opentsdb.OpenTsdbReporter;
import com.heliosapm.opentsdb.client.registry.IMetricRegistryFactory;
import com.heliosapm.opentsdb.client.util.Util;

public class MBeanObserverTest {
	
	public static void log(final Object msg) {
		System.out.println(msg);
	}
	

	public static void main(String[] args) {		
		log("MBeanObserverTest");
		System.setProperty("tsdb.http.tsdb.url", "http://localhost:6262");
		final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
		final BaseMBeanObserver compileTime = MBeanObserverBuilder.newBuilder(server, Util.objectName(ManagementFactory.COMPILATION_MXBEAN_NAME), CompilationMBeanObserver.class).build();
		final BaseMBeanObserver classLoads = MBeanObserverBuilder.newBuilder(server, Util.objectName(ManagementFactory.CLASS_LOADING_MXBEAN_NAME), ClassLoadingMBeanObserver.class).build();
		final BaseMBeanObserver bufferPools = MBeanObserverBuilder.newBuilder(server, Util.objectName(BufferPoolMetricSet.OBJECT_PATTERN), BufferPoolMetricSet.class).build();
		final BaseMBeanObserver mem = MBeanObserverBuilder.newBuilder(server, Util.objectName(ManagementFactory.MEMORY_MXBEAN_NAME), MemoryMonitorMetricSet.class).build();
		MetricRegistry reg = new MetricRegistry();
		reg.registerAll(compileTime);
		reg.registerAll(classLoads);
		reg.registerAll(bufferPools);
		reg.registerAll(mem);
		ConsoleReporter creporter = ConsoleReporter.forRegistry(reg).outputTo(System.out).build();
		OpenTsdbReporter reporter = OpenTsdbReporter.forRegistry(reg).build(OpenTsdb.getInstance());
		reporter.start(5, TimeUnit.SECONDS);
		creporter.start(5, TimeUnit.SECONDS);
		final Random r = new Random(System.currentTimeMillis());
		while(true) {
			ByteBuffer.allocateDirect(Math.abs(r.nextInt(100))+1);
			try { Thread.currentThread().join(3000); } catch (Exception x) {}
		}
		
	}

}
