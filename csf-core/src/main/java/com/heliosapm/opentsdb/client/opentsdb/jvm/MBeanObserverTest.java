package com.heliosapm.opentsdb.client.opentsdb.jvm;

import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.heliosapm.opentsdb.client.util.Util;

public class MBeanObserverTest {
	
	public static void log(final Object msg) {
		System.out.println(msg);
	}
	

	public static void main(String[] args) {		
		log("MBeanObserverTest");
		final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
		final BaseMBeanObserver compileTime = MBeanObserverBuilder.newBuilder(server, Util.objectName(ManagementFactory.COMPILATION_MXBEAN_NAME), CompilationMBeanObserver.class).build();
		final BaseMBeanObserver classLoads = MBeanObserverBuilder.newBuilder(server, Util.objectName(ManagementFactory.CLASS_LOADING_MXBEAN_NAME), ClassLoadingMBeanObserver.class).build();
		MetricRegistry reg = new MetricRegistry();
		reg.registerAll(compileTime);
		reg.registerAll(classLoads);
		ConsoleReporter reporter = ConsoleReporter.forRegistry(reg).outputTo(System.out).build();
		reporter.start(5, TimeUnit.SECONDS);
		try { Thread.currentThread().join(); } catch (Exception x) {}
	}

}
