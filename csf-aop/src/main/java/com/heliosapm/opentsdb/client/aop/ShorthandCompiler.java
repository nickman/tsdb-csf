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
package com.heliosapm.opentsdb.client.aop;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Member;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.ProtectionDomain;
import java.security.spec.RSAPrivateKeySpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import javax.management.ObjectName;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.heliosapm.opentsdb.client.aop.naming.MetricNameCompiler;
import com.heliosapm.opentsdb.client.aop.naming.MetricNameProvider;
import com.heliosapm.opentsdb.client.logging.LoggingConfiguration;
import com.heliosapm.opentsdb.client.name.AgentName;
import com.heliosapm.opentsdb.client.opentsdb.ConfigurationReader;
import com.heliosapm.opentsdb.client.opentsdb.Constants;
import com.heliosapm.opentsdb.client.opentsdb.MetricBuilder;
import com.heliosapm.opentsdb.client.opentsdb.OTMetric;
import com.heliosapm.opentsdb.client.opentsdb.opt.LongIdOTMetricCache;
import com.heliosapm.opentsdb.client.opentsdb.opt.Measurement;
import com.heliosapm.opentsdb.client.opentsdb.sink.MetricSink;
import com.heliosapm.opentsdb.client.util.Util;
import com.heliosapm.utils.lang.StringHelper;

/**
 * <p>Title: ShorthandCompiler</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.ShorthandCompiler</code></p>
 */

public class ShorthandCompiler {
	/** The singleton instance */
	private static volatile ShorthandCompiler instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	/** Instance logger */
	protected final Logger log = LogManager.getLogger(getClass());
	/** The instrumentation instance to instrument with */
	protected final Instrumentation instrumentation;
	/** The shared classpool to use for instrumenting */
	protected final ClassPool classPool;
	/** The long array ctClass type */
	protected final CtClass longArrCtClass;
	/** The {@link Measurement} ctClass type */
	protected final CtClass measurementCtClass;
	/** The {@link MetricSink} ctClass type */
	protected final CtClass metricSinkCtClass;
	/** The {@link DefaultShorthandInterceptor} ct class */
	protected final CtClass interceptorCtClass;
	/** The {@link Throwable} ct class */
	protected final CtClass throwableCtClass;
	/** The directory where the transient byte code will be written */
	private final String byteCodeDir;	
	/** The compiler's JMX ObjectName */
	public final ObjectName OBJECT_NAME;
	
	/** The name of the directory within the agent home where we'll write the transient byte code to */
	public static final String BYTE_CODE_DIR = ".bytecode";
	/** The name of the directory where we'll write the transient byte code to if things don't work out with the agent home */
	public static final String STANDBY_BYTE_CODE_DIR = System.getProperty("java.io.tmpdir") + File.separator + ".tsdb-aop" + File.separator + Constants.SPID; 
	

	/**
	 * Acquires and returns the ShorthandCompiler singleton instance
	 * @return the ShorthandCompiler singleton instance
	 */
	public static ShorthandCompiler getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new ShorthandCompiler();
				}
			}
		}
		return instance;
	}

	/**
	 * Creates a new ShorthandCompiler
	 */
	private ShorthandCompiler() {
		OBJECT_NAME = Util.objectName(Util.getJMXDomain() + ":service=ShorthandCompiler");
		instrumentation = TransformerManager.getInstrumentation();
		classPool = new ClassPool();
		try {
			classPool.appendSystemPath();
			classPool.importPackage("java.lang");
			classPool.importPackage(Measurement.class.getPackage().getName());
			classPool.importPackage(MetricSink.class.getPackage().getName());
			classPool.importPackage(DefaultShorthandInterceptor.class.getPackage().getName());
			longArrCtClass = classPool.get(long[].class.getName());
			measurementCtClass = classPool.get(Measurement.class.getName());
			metricSinkCtClass = classPool.get(MetricSink.class.getName());
			interceptorCtClass = classPool.get(DefaultShorthandInterceptor.class.getName());
			throwableCtClass = classPool.get(Throwable.class.getName());
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
		String _byteCodeDir = ConfigurationReader.conf(Constants.PROP_OFFLINE_DIR, Constants.DEFAULT_OFFLINE_DIR) + File.separator + AgentName.appName() + File.separator + BYTE_CODE_DIR;
		File f = new File(_byteCodeDir);
		boolean byteCodeDirReady = true;
		if(f.exists()) {
			if(!f.isDirectory()) {
				byteCodeDirReady = false;
				log.warn("Transient bytecode directory {} is a file. Will attempt default: {}",  _byteCodeDir, STANDBY_BYTE_CODE_DIR);
				f = new File(STANDBY_BYTE_CODE_DIR);
			}
		} 
		if(!byteCodeDirReady) {
			if(!f.mkdirs()) {
				log.warn("Failed to create transient bytecode directory {}. Will attempt default: {}",  _byteCodeDir, STANDBY_BYTE_CODE_DIR);
				f = new File(STANDBY_BYTE_CODE_DIR);
				if(!f.exists()) {
					if(!f.mkdirs()) {
						byteCodeDirReady = false;
						log.warn("Failed to create standby transient bytecode directory [{}]. Persisted transient classes will be disabled");						
					}					
				}
			}
		}
		byteCodeDir = byteCodeDirReady ? f.getAbsolutePath() : null;
		log.info("Transient ByteCode Directory: {}", byteCodeDir);
//		final int cnt = Util.registerMBeanEverywhere(this, OBJECT_NAME);
//		log.info("Registered RetransformerLite on [{}] MBeanServers", cnt);
		
	}
	
	/**
	 * Acquires a metric name provider for the passed fefines Shorthand joinpoint
	 * @param clazz The class the joinpoint is in
	 * @param member The class member the joinpoint is on
	 * @param nameTemplate The metric naming template
	 * @return The metric name provider
	 */
	public MetricNameProvider getNameProvider(final Class<?> clazz, final Member member, final String nameTemplate) {
		return MetricNameCompiler.getMetricNameProvider(clazz, member, nameTemplate);
	}
	
	/**
	 * Retrieves the CtClass for the passed java class from the passed ClassPool, tracking which CtClasses have been loaded so they can be detached 
	 * @param clazz The java class to the get the CtClass for
	 * @param cp The class pool to get the CtClas from
	 * @param remove The set of CtClasses to track with
	 * @return the CtClass
	 */
	protected CtClass getCtClass(final Class<?> clazz, final ClassPool cp, final Set<CtClass> remove) {
		try {
			final CtClass ctc = cp.get(clazz.getName());
			remove.add(ctc);
			return ctc;
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
	static class TestClass {
		final Random r = new Random(System.currentTimeMillis());
		final KeyFactory kf;
		
		public TestClass() {
			try {
				kf = KeyFactory.getInstance("RSA");
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}
		
		static final AtomicLong invokeCount = new AtomicLong(0);
		public Collection<PrivateKey> keys(final int count) {
			final long ic = invokeCount.incrementAndGet();
//			if(ic%4==0) throw new RuntimeException();
			Collection<PrivateKey> keys = new ArrayList<PrivateKey>(count);
			try { 
//				Thread.currentThread().join(1, 1);
				
				final RSAPrivateKeySpec spec = new RSAPrivateKeySpec(new BigInteger(1024, 10, r), new BigInteger(1024, 10, r));				
//				for(int z = 0; z < count; z++) {									
					keys.add(kf.generatePrivate(spec));
					//blockNoop();
//				}
				
				
			} catch (Exception ex) {/* No Op */}
			return keys;
		}
		
		public synchronized void blockNoop() {
			try { Thread.currentThread().join(0, 1); } catch (Exception x) {}
		}
	}
	
	/**
	 * System out pattern logger
	 * @param fmt The message format
	 * @param args The tokens
	 */
	public static void log(final Object fmt, final Object...args) {
		System.out.println(String.format(fmt.toString(), args));
	}

	
	public static void main(String args[]) {
		log("Shorthand compiler test");
		ManagementFactory.getThreadMXBean().setThreadContentionMonitoringEnabled(true);
		ManagementFactory.getThreadMXBean().setThreadCpuTimeEnabled(true);
		
		// ===================================================================================
		// ===================================================================================
		final int cores = 1; //Constants.CORES;
		final int LOOPS = 1; //100;
		// ===================================================================================
		// ===================================================================================
		LoggingConfiguration.getInstance();
		
		final ThreadFactory tf = new ThreadFactory() {
			private final AtomicInteger serial = new AtomicInteger();
			@Override
			public Thread newThread(final Runnable r) {
				Thread t = new Thread(r, "TestWorkerThread#" + serial.incrementAndGet());
				t.setDaemon(true);
				return t;
			}
		};
		final ThreadPoolExecutor tpe = new ThreadPoolExecutor(cores, cores, 180, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(120, true), tf);
		tpe.prestartAllCoreThreads();
		
		long start = System.currentTimeMillis();
		testMulti(tpe, LOOPS);
		log("Invocation Count: %s", TestClass.invokeCount);
		final long noInstElapsed = System.currentTimeMillis() - start;
		log("PreInstrument Time: %s ms.", noInstElapsed);
		final ShorthandCompiler compiler = ShorthandCompiler.getInstance();
		//final ShorthandScript script = ShorthandScript.parse(TestClass.class.getName() + " keys m:[" + Measurement.ALL_MASK + "] 'class=TestClass,method=${method}'");
		final ShorthandScript script = ShorthandScript.parse(TestClass.class.getName() + " keys m:[4095] 'class=TestClass,method=${method}'");
		
//		log("Script: %s", script);
		final Map<Class<?>, Set<ShorthandScript>> compileJob = new HashMap<Class<?>, Set<ShorthandScript>>(1);
		compileJob.put(TestClass.class, Collections.singleton(script));
		compiler.compile(compileJob);
		start = System.currentTimeMillis();
		TestClass.invokeCount.set(0);
		testMulti(tpe, LOOPS);
		log("Invocation Count: %s", TestClass.invokeCount);
		final long instElapsed = System.currentTimeMillis() - start;
		log("Instrument Time: %s ms.", instElapsed);
		long diffMs = (instElapsed - noInstElapsed);
		long diffNs = TimeUnit.NANOSECONDS.convert(diffMs, TimeUnit.MILLISECONDS);		
		log(LongIdOTMetricCache.getInstance().renderMetrics());
		log("Total Instr Diff: %s ns, Diff Per Call: %s ns,  %s ms", diffNs, diffNs/(TestClass.invokeCount.get()), diffMs/(TestClass.invokeCount.get()));
		try { Thread.currentThread().join(); } catch (Exception x) {}
		System.exit(0);
		
//		start = System.currentTimeMillis();
//		for(int i = 0; i < 10000; i++) {
//			try { tc.sleep(i); } catch (Exception ex) { }
//			if(i%1000==0) log(MetricSink.sink().renderMetrics());
//		}
//		elapsed = System.currentTimeMillis()-start;
//		log("PostInstrument Time: %s ns.", elapsed);
		
	}
	
	protected static void testMulti(final ThreadPoolExecutor threadPool, final int loopsPerThread) {
		final int threads = threadPool.getMaximumPoolSize();
		final CountDownLatch latch = new CountDownLatch(threads);
		final CountDownLatch startLatch = new CountDownLatch(1);
		final TestClass tc = new TestClass();
		for(int x = 0; x < threads; x++) {
			threadPool.execute(new Runnable(){
				final Random r = new Random(System.currentTimeMillis());
				public void run() {
					try {
						startLatch.await();
//						log("Started Worker Thread [%s]", Thread.currentThread().getName());
					} catch (Exception ex) {
						ex.printStackTrace(System.err);
						throw new RuntimeException(ex);
					}
					double d = 0;
					for(int i = 0; i < loopsPerThread; i++) {
						try { tc.keys(20); } catch (Exception ex) { }	
					}
					latch.countDown();
//					log("Worker Thread [%s] Done. Result: %s", Thread.currentThread().getName(), d);
				}
			});
		}
		startLatch.countDown();
		try {
			log("Waiting for test to complete");
			latch.await();
			log("RUN COMPLETE");
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
		}
	}
	
	/**
	 * Compiles the passed scripts
	 * @param scriptsToCompile A map of sets of parsed shorthand scripts keyed by the class the scripts are instrumenting.
	 */
	// Return a classfiletransformer
	public void compile(final Map<Class<?>, Set<ShorthandScript>> scriptsToCompile) {
		if(scriptsToCompile==null || scriptsToCompile.isEmpty()) return;
		final Map<String, Integer> classesToTransform = new HashMap<String, Integer>(scriptsToCompile.size());
		final Map<String, byte[]> byteCode = new HashMap<String, byte[]>(scriptsToCompile.size());
		final ClassPool cp = new ClassPool(classPool);
		final Set<CtClass> remove = new HashSet<CtClass>();
		try {
			for(Map.Entry<Class<?>, Set<ShorthandScript>> entry: scriptsToCompile.entrySet()) {
				final Class<?> clazz = entry.getKey();
				final Set<ShorthandScript> scripts = entry.getValue();
				if(scripts.isEmpty()) continue;
				if(!instrumentation.isModifiableClass(clazz)) {
					log.warn("The class [{}] with [{}] shorthand scripts is not modifiable. Skipping.", clazz.getName(), scripts.size());
				}
				for(ShorthandScript script: scripts) {
					final Map<Class<?>, Set<Member>> targets = script.getTargetMembers();
					for(Map.Entry<Class<?>, Set<Member>> target: targets.entrySet()) {
						final Class<?> targetClass = target.getKey();
						final CtClass targetCtClass = getCtClass(targetClass, cp, remove);
						final Set<Member> targetMembers = target.getValue();
						for(Member member: targetMembers) {
//							final MetricNameProvider mnp = getNameProvider(clazz, member, script.getMetricNameTemplate());
							final String metricName = "instrumented:method=sleep,class=TestClass";
							final OTMetric metric = MetricBuilder.metric(metricName).measurement(script.measurementBitMask).subMetric(script.measurementBitMask).optBuild();
							final CtMethod ctMethod = targetCtClass.getMethod(member.getName(), StringHelper.getMemberDescriptor(member));
							targetCtClass.removeMethod(ctMethod);
							instrument(ctMethod, metric.longHashCode(), script.getMeasurementBitMask());
							targetCtClass.addMethod(ctMethod);
						}
						targetCtClass.writeFile(byteCodeDir);
						byteCode.put(b2i(clazz), targetCtClass.toBytecode());
						log("Wrote Class File for [%s]", targetClass.getName());
					}					
				}								
				classesToTransform.put(b2i(clazz), System.identityHashCode(clazz.getClassLoader()));
				final ClassFileTransformer cft = new ClassFileTransformer() {
					@Override
					public byte[] transform(final ClassLoader loader, final String className, final Class<?> classBeingRedefined,
							final ProtectionDomain protectionDomain, final byte[] classfileBuffer)
							throws IllegalClassFormatException {
						
						return byteCode.get(className);
					}
				};
				instrumentation.addTransformer(cft, true);
				try {
					instrumentation.retransformClasses(clazz);
				} finally {
					try { instrumentation.removeTransformer(cft); } catch (Exception x) {/* No Op */}
				}
			}
		} catch (Exception ex) {
			log.error("Failed to instrument", ex);
		}
	}
	
	
	/**
	 * Instruments the passed CtMethod
	 * @param ctMethod The method to instrument
	 * @param metricId The parent metric Id
	 * @param measurementMask The enabled measurement mask
	 */
	protected void instrument(final CtMethod ctMethod, final long metricId, final int measurementMask) {
		if(ctMethod==null) throw new IllegalArgumentException("The passed CtMethod was null");
		try {
			if(Measurement.hasBodyBlock(measurementMask)) {
				DefaultShorthandInterceptor.install(metricId, measurementMask);
//				final String metricIdFieldName =  "_metricId_" + Math.abs(metricId) + "_" + measurementMask;
//				final String valueFieldName =  "_measured_" + Math.abs(metricId) + "_" + measurementMask;
//				final String interceptorFieldName =  "_interceptor_" + Math.abs(metricId) + "_" + measurementMask;
//				final String metricIdFieldName =  "_ametricId_"; // + Math.abs(metricId) + "_" + measurementMask;
				final String valueFieldName =  "_measured_"; // + Math.abs(metricId) + "_" + measurementMask;
				final String interceptorFieldName =  "_interceptor_"; // + Math.abs(metricId) + "_" + measurementMask;
				
//				ctMethod.addLocalVariable(metricIdFieldName, CtClass.longType);
				ctMethod.addLocalVariable(valueFieldName, longArrCtClass);
				ctMethod.addLocalVariable(interceptorFieldName, interceptorCtClass);
				final String bodyCode = "{ \n\t" +  
						interceptorFieldName + " = DefaultShorthandInterceptor.get(" + metricId + "L," + measurementMask + ");\n\t"
						+ valueFieldName + " = " + interceptorFieldName +  
//							".enter(" + measurementMask + "," + metricId + "L);\n\t"
						".enter();\n\t"
				+ " }";
				log("Body Code:\n" + bodyCode);
				ctMethod.insertBefore(bodyCode);
				//ctMethod.insertBefore(valueFieldName + " = DefaultShorthandInterceptor.get(" + metricId + "L," + measurementMask + ").enter(" + measurementMask + "," + metricId + "L);");
				ctMethod.insertAfter(interceptorFieldName + ".exit(" + valueFieldName + ");");
			}
			if(Measurement.hasCatchBlock(measurementMask)) {
				ctMethod.addCatch("{ DefaultShorthandInterceptor.get("  + metricId + "L," + measurementMask + ").throwExit($e); throw new RuntimeException(); }", throwableCtClass, "$e");
			}
			if(Measurement.hasFinallyBlock(measurementMask)) {
				ctMethod.insertAfter("{ DefaultShorthandInterceptor.get("  + metricId + "L," + measurementMask + ").finalExit(); }", true);
			}
			
		} catch (Exception ex) {
			log.error("Failed to instrument [{}.{}] with metricId [{}] and mask [{}]", ctMethod.getDeclaringClass().getName(), ctMethod.getMethodInfo().getDescriptor(), metricId, measurementMask, ex);
			throw new RuntimeException(ex);
		}
	}
	
	/**
	 * Converts the internal format class name to the binary name format
	 * @param internalName The internal name
	 * @return the binary name
	 */
	public static String i2b(final String internalName) {
		return internalName.replace('/', '.');
	}
	
	/**
	 * Converts the binary format class name to the internal name format
	 * @param binaryName The binary name
	 * @return the internal name
	 */
	public static String b2i(final String binaryName) {
		return binaryName.replace('.', '/');
	}
	
	/**
	 * Returns the internal format name for the passed class
	 * @param clazz The class
	 * @return the internal name of the passed class
	 */
	public static String b2i(final Class<?> clazz) {
		return clazz.getName().replace('.', '/');
	}

}
