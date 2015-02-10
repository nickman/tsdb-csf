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

package com.heliosapm.opentsdb.client.aop;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javassist.ByteArrayClassPath;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import javassist.NotFoundException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.heliosapm.attachme.agent.LocalAgentInstaller;
import com.heliosapm.opentsdb.client.opentsdb.MetricBuilder;
import com.heliosapm.opentsdb.client.opentsdb.*;

/**
 * <p>Title: Retransformer</p>
 * <p>Description: Service to provide very basic instrumentation (elapsed time and exception counts) for equally simple class/method signatures.
 * This is firmly on the roadmap to beef up.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.Retransformer</code></p>
 */

public class Retransformer {
	private static volatile Retransformer instance = null;
	private static final Object lock = new Object();
	
	public static Retransformer getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new Retransformer();
				}
			}
		}
		return instance;
	}
	
	private final Logger log = LogManager.getLogger(getClass());
	private final Instrumentation instrumentation;
	
	/**
	 * Creates a new Retransformer
	 */
	private Retransformer() {
		instrumentation = LocalAgentInstaller.getInstrumentation();
	}
	
	
	public static String i2b(final String internalName) {
		return internalName.replace('/', '.');
	}
	
	public static String b2i(final String binaryName) {
		return binaryName.replace('.', '/');
	}

	
	private class ByteCapturingClassTransform implements ClassFileTransformer {
		/** The internal name */
		final String internalName;
		/** The captured byte code */
		byte[] byteCode = null;
		
		
		/**
		 * Creates a new ByteCapturingClassTransform
		 * @param clazz The class to capture the byte code for
		 */
		ByteCapturingClassTransform(final Class<?> clazz) {
			this.internalName = b2i(clazz.getName());
		}
		
		/**
		 * Creates a new ByteCapturingClassTransform to supply instrumented byte code
		 * @param clazz The class to set the byte code for
		 * @param byteCode The instrumented byte code
		 */
		ByteCapturingClassTransform(final Class<?> clazz, final byte[] byteCode) {
			this.internalName = b2i(clazz.getName());
			this.byteCode = byteCode;
		}
		
		
		
		/**
		 * Returns the byte code if successful 
		 * @return the byte code
		 */
		public byte[] getByteCode() {
			return byteCode;
		}
		
		
		/**
		 * {@inheritDoc}
		 * @see java.lang.instrument.ClassFileTransformer#transform(java.lang.ClassLoader, java.lang.String, java.lang.Class, java.security.ProtectionDomain, byte[])
		 */
		@Override
		public byte[] transform(final ClassLoader loader, final String className, final Class<?> classBeingRedefined, 
				final ProtectionDomain protectionDomain, final byte[] classfileBuffer) throws IllegalClassFormatException {
			if(className.equals(internalName)) {
				if(byteCode==null) {
					byteCode = classfileBuffer;
				} else {
					log.info("Instrumented {}", className);
					return byteCode;
				}
			}
			return classfileBuffer;
		}		
	}
	
	public byte[] getByteCodeFor(final Class<?> clazz) {
		return getByteCodeFor(clazz, null);
	}
	
	public byte[] getByteCodeFor(final Class<?> clazz, final byte[] byteCode) {
		final ByteCapturingClassTransform transformer = new ByteCapturingClassTransform(clazz, byteCode);
		try {
			instrumentation.addTransformer(transformer, true);
			try {
				instrumentation.retransformClasses(clazz);
				return transformer.getByteCode();
			} catch (Exception ex) {
				log.error("Failed to get byte code for [{}]", clazz.getName());
				return null;
			}
		} finally {
			instrumentation.removeTransformer(transformer);
		}
		
	}
	
	public static final Map<Class<?>, CtClass> PRIMITIVES;
	
	static {
		final Map<Class<?>, CtClass> tmp = new HashMap<Class<?>, CtClass>();
		tmp.put(byte.class, CtClass.byteType);
		tmp.put(boolean.class, CtClass.booleanType);
		tmp.put(char.class, CtClass.charType);
		tmp.put(short.class, CtClass.shortType);
		tmp.put(int.class, CtClass.intType);
		tmp.put(long.class, CtClass.longType);
		tmp.put(float.class, CtClass.floatType);
		tmp.put(double.class, CtClass.doubleType);
		tmp.put(void.class, CtClass.voidType);
		PRIMITIVES = Collections.unmodifiableMap(tmp);
		
	}
	
	private static CtClass[] sig(final ClassPool cp, final Method method) throws NotFoundException {
		final int args = method.getParameterTypes().length;
		if(args == 0) return new CtClass[0];
		final CtClass[] cts = new CtClass[args];
		int index = 0;
		for(Class<?> param: method.getParameterTypes()) {
			if(PRIMITIVES.containsKey(param)) {
				cts[index] = PRIMITIVES.get(param);
			} else {
				cts[index] = cp.get(param.getName());
			}
			index++;
		}
		return cts;
	}
	
	public void instrument(final Class<?> clazz, String...methodNames) {
		log.info("Retransforming {} / {}", clazz.getName(), methodNames);
		Set<String> mNames = new HashSet<String>(Arrays.asList(methodNames));
		final Map<String, Method> methods = new HashMap<String, Method>();
		for(Method m: clazz.getMethods()) {
			if(m.getDeclaringClass().equals(Object.class)) continue;
			if(mNames.contains(m.getName())) methods.put(m.getName(), m);
		}
		for(Method m: clazz.getDeclaredMethods()) {			
			if(mNames.contains(m.getName())) methods.put(m.getName(), m);
		}
		try {
			final byte[] byteCode = getByteCodeFor(clazz);
			final ClassPool cp = new ClassPool();
			cp.appendClassPath(new ClassClassPath(this.getClass()));
			cp.appendClassPath(new ByteArrayClassPath(clazz.getName(), byteCode));
			cp.appendClassPath(new LoaderClassPath(clazz.getClassLoader()));
			cp.importPackage("com.heliosapm.opentsdb.client.opentsdb");
			final CtClass target = cp.get(clazz.getName());
			for(Method mx: methods.values()) {
				final String metricName = MetricBuilder.metric(mx.getName()).ext("elapsed").tag("class", clazz.getSimpleName()).tag("package", clazz.getPackage().getName()).build().toString();
				log.info("Adding metric name {}", metricName);
				CtMethod ctm = target.getDeclaredMethod(mx.getName(), sig(cp, mx));
				target.removeMethod(ctm);
	            ctm.addLocalVariable("xxxstart", CtClass.longType);
	            ctm.addLocalVariable("xxxelapsed", CtClass.longType);
	            ctm.insertBefore("xxxstart = System.nanoTime();");
	            ctm.insertAfter("xxxelapsed = System.nanoTime() - xxxstart; OTMetricCache.getInstance().getOTMetric(\"" + metricName + "\").trace(xxxelapsed);");            
	            target.addMethod(ctm);
			}
			final byte[] instrumentedByteCode = target.toBytecode();
			getByteCodeFor(clazz, instrumentedByteCode);
			//ByteCapturingClassTransform transformer = new ByteCapturingClassTransform(clazz, instrumentedByteCode);
		} catch (Exception ex) {
			log.error("Failed to instrument [{}]", clazz.getName(), ex);
			throw new RuntimeException(ex);
		}
	}

}

/*
import javax.management.loading.MLet;
import javax.management.*;
import java.lang.management.*;
MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
ObjectName ON = new ObjectName("com.heliosapm.opentsdb:service=TSDBCSF");
System.setProperty("tsdb.http.compression.enabled", "false");
if(!mbs.isRegistered(ON)) {
    tsdburl = new File("/media/sf_C_DRIVE/hprojects/tsdb-csf/csf-core/target/tsdb-csf-1.0-SNAPSHOT.jar").toURI().toURL();
    String className = "com.heliosapm.opentsdb.client.boot.ManualLoader";
    MLet mlet = new MLet([tsdburl] as URL[], ClassLoader.getSystemClassLoader().getParent(), false);
    ClassLoader CURRENT = Thread.currentThread().getContextClassLoader();
    try {
        Thread.currentThread().setContextClassLoader(mlet);
        clazz = Class.forName(className, true, mlet);
        clazz.getDeclaredMethod("boot", MLet.class, MBeanServer.class).invoke(null, mlet, mbs);
    } finally {
        Thread.currentThread().setContextClassLoader(CURRENT);
    }    
}
//try { mbs.unregisterMBean(new ObjectName("com.heliosapm.opentsdb:service=TSDBCSF")); } catch (e) {}    
cl = mbs.getClassLoader(ON);
ClassLoader CURRENT = Thread.currentThread().getContextClassLoader();
try {
    Thread.currentThread().setContextClassLoader(cl);
    ret = Class.forName("com.heliosapm.opentsdb.client.aop.Retransformer", true, cl);
    ret.getInstance().instrument(net.opentsdb.tsd.GraphHandler.class, "doGraph", "parseQuery", "readFile");

} finally {
    Thread.currentThread().setContextClassLoader(CURRENT);
}    

println "Done";
     
     
/*
java.lang.NoClassDefFoundError: com/heliosapm/opentsdb/client/opentsdb/OTMetricCache
    at net.opentsdb.tsd.GraphHandler.parseQuery(GraphHandler.java:885) ~[tsdb-2.0.0.jar:2b8bfa2]
    at net.opentsdb.tsd.GraphHandler.doGraph(GraphHandler.java:162) ~[tsdb-2.0.0.jar:2b8bfa2]
    at net.opentsdb.tsd.GraphHandler.execute(GraphHandler.java:121) ~[tsdb-2.0.0.jar:2b8bfa2]
    at net.opentsdb.tsd.RpcHandler.handleHttpQuery(RpcHandler.java:255) ~[tsdb-2.0.0.jar:2b8bfa2]
    at net.opentsdb.tsd.RpcHandler.messageReceived(RpcHandler.java:163) ~[tsdb-2.0.0.jar:2b8bfa2]
    at org.jboss.netty.handler.codec.http.HttpChunkAggregator.messageReceived(HttpChunkAggregator.java:148) ~[netty-3.6.2.Final.jar:na]
    at org.jboss.netty.channel.Channels.fireMessageReceived(Channels.java:296) ~[netty-3.6.2.Final.jar:na]
    at org.jboss.netty.handler.codec.frame.FrameDecoder.unfoldAndFireMessageReceived(FrameDecoder.java:459) ~[netty-3.6.2.Final.jar:na]
    at org.jboss.netty.handler.codec.replay.ReplayingDecoder.callDecode(ReplayingDecoder.java:536) ~[netty-3.6.2.Final.jar:na]
    at org.jboss.netty.handler.codec.replay.ReplayingDecoder.messageReceived(ReplayingDecoder.java:435) ~[netty-3.6.2.Final.jar:na]
    at net.opentsdb.tsd.ConnectionManager.handleUpstream(ConnectionManager.java:87) ~[tsdb-2.0.0.jar:2b8bfa2]
    at org.jboss.netty.channel.Channels.fireMessageReceived(Channels.java:268) ~[netty-3.6.2.Final.jar:na]
    at org.jboss.netty.channel.Channels.fireMessageReceived(Channels.java:255) ~[netty-3.6.2.Final.jar:na]
    at org.jboss.netty.channel.socket.nio.NioWorker.read(NioWorker.java:88) ~[netty-3.6.2.Final.jar:na]
    at org.jboss.netty.channel.socket.nio.AbstractNioWorker.process(AbstractNioWorker.java:107) ~[netty-3.6.2.Final.jar:na]
    at org.jboss.netty.channel.socket.nio.AbstractNioSelector.run(AbstractNioSelector.java:312) ~[netty-3.6.2.Final.jar:na]
    at org.jboss.netty.channel.socket.nio.AbstractNioWorker.run(AbstractNioWorker.java:88) ~[netty-3.6.2.Final.jar:na]
    at org.jboss.netty.channel.socket.nio.NioWorker.run(NioWorker.java:178) ~[netty-3.6.2.Final.jar:na]
    at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1145) ~[na:1.7.0_25]
    at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:615) ~[na:1.7.0_25]
    at java.lang.Thread.run(Thread.java:724) ~[na:1.7.0_25]
Caused by: java.lang.ClassNotFoundException: com.heliosapm.opentsdb.client.opentsdb.OTMetricCache
    at java.net.URLClassLoader$1.run(URLClassLoader.java:366) ~[na:1.7.0_25]
    at java.net.URLClassLoader$1.run(URLClassLoader.java:355) ~[na:1.7.0_25]
    at java.security.AccessController.doPrivileged(Native Method) ~[na:1.7.0_25]
    at java.net.URLClassLoader.findClass(URLClassLoader.java:354) ~[na:1.7.0_25]
    at java.lang.ClassLoader.loadClass(ClassLoader.java:424) ~[na:1.7.0_25]
    at sun.misc.Launcher$AppClassLoader.loadClass(Launcher.java:308) ~[na:1.7.0_25]
    at java.lang.ClassLoader.loadClass(ClassLoader.java:357) ~[na:1.7.0_25]
*/    
      

