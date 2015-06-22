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

package com.heliosapm.opentsdb.client.aoplite;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

import javassist.ByteArrayClassPath;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import javassist.NotFoundException;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.QueryExp;
import javax.management.StandardMBean;
import javax.management.StringValueExp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.heliosapm.attachme.agent.LocalAgentInstaller;
import com.heliosapm.opentsdb.client.boot.JavaAgent;
import com.heliosapm.opentsdb.client.name.AgentName;
import com.heliosapm.opentsdb.client.opentsdb.ConfigurationReader;
import com.heliosapm.opentsdb.client.opentsdb.Constants;
import com.heliosapm.opentsdb.client.opentsdb.MetricBuilder;
import com.heliosapm.opentsdb.client.util.Util;
import com.heliosapm.utils.instrumentation.InstrumentationMBean;
import com.heliosapm.utils.jmx.JMXHelper;
import com.heliosapm.utils.url.URLHelper;

/**
 * <p>Title: RetransformerLite</p>
 * <p>Description: Service to provide very basic instrumentation (elapsed time and exception counts) for equally simple class/method signatures.
 * This is firmly on the roadmap to beef up.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aoplite.RetransformerLite</code></p>
 */
public class RetransformerLite extends StandardMBean implements RetransformerLiteMBean {
	/** The singleton instance */
	private static volatile RetransformerLite instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	
	/** The retransformer's JMX ObjectName */
	public final ObjectName OBJECT_NAME;
	
	/**
	 * Acquires the retransformer singleton instance
	 * @return the retransformer singleton instance
	 */
	public static RetransformerLite getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new RetransformerLite();
				}
			}
		}
		return instance;
	}
	
	/** Instance logger */
	private final Logger log = LogManager.getLogger(getClass());
	/** The JVM's instrumentation instance */
	private final Instrumentation instrumentation;
	/** The directory where the transient byte code will be written */
	private final String byteCodeDir;
	
	/** The instrumented class method names in a set keyed by the instrumented class */
	private final Map<Class<?>, Set<String>> instrumentedClasses = Collections.synchronizedMap(new WeakHashMap< Class<?>, Set<String>>()); 
			
	
	/** The name of the directory within the agent home where we'll write the transient byte code to */
	public static final String BYTE_CODE_DIR = ".bytecode";
	/** The name of the directory where we'll write the transient byte code to if things don't work out with the agent home */
	public static final String STANDBY_BYTE_CODE_DIR = System.getProperty("java.io.tmpdir") + File.separator + ".tsdb-aop" + File.separator + Constants.SPID; 
	/** Class and package name splitter */
	public static final Pattern DOT_SPLITTER = Pattern.compile("\\.");
	/**
	 * Creates a new RetransformerLite
	 */
	private RetransformerLite() {
		super(RetransformerLiteMBean.class, false);
		OBJECT_NAME = JMXHelper.objectName(Util.getJMXDomain() + ":service=RetransformerLite");
		Instrumentation instr = null;
		try {
			instr = JMXHelper.getAttribute(InstrumentationMBean.OBJECT_NAME, "Instance");
			log.info("Acquired Instrumentation instance from [{}]", InstrumentationMBean.OBJECT_NAME);
		} catch (Exception ex) {
			log.warn("Failed to get Instrumentation instance from [{}]", InstrumentationMBean.OBJECT_NAME, ex);
		}
		try {
			Class<?> transformerManagerClass = Class.forName("com.heliosapm.opentsdb.client.aop.TransformerManager");
			log.info("Loaded class [{}]", transformerManagerClass);
			Object obj = transformerManagerClass.getDeclaredMethod("getInstrumentation").invoke(null);
			if(obj==null) {
				log.warn("Return value from getInstrumentation() was null");				
			} else {
				log.info("Returned value from getInstrumentation(): {}", obj);
			}
			if(instr==null) instr = (Instrumentation)obj;
		} catch (Throwable t) {
			log.warn("\n\t==============================\n\tFailed to load TransformerManager\n\t==============================\n");
			try {
//				instr = JavaAgent.INSTRUMENTATION;
				if(instr!=null) {
					log.info("JavaAgent saved the day. Instrumentation is: {}", instr);
				} else {
					instr = LocalAgentInstaller.getInstrumentation();		
					log.info("LocalAgentInstaller saved the day. Instrumentation is: {}", instr);
				}
			} catch (Exception ex) {/* No Op */}
		}
		instrumentation = instr;
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
		final int cnt = JMXHelper.registerMBeanEverywhere(this, OBJECT_NAME);
		log.info("Registered RetransformerLite on [{}] MBeanServers", cnt);
		
	}
	
	/**
	 * Re-registers specific known problematic class file tranformers
	 * from non-retransform to retransform.
	 * @return The number of switched transformers
	 */
	public int switchTransformers() {
		try {
			return TransformerManagerLite.switchTransformers(instrumentation);			
		} catch (Exception ex) {
			return 0;
		}
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
	
	/**
	 * Instruments the specified class, discarding any instrumentation on the class
	 * @param clazz The class to instrument
	 * Otherwise the existing instrumentation will be discarded and replaced with this new definition
	 * @param methodNames The method names to instrument
	 * @return the number of methods instrumented at the end of this procedure
	 */
	public int instrument(final Class<?> clazz, String...methodNames) {
		return instrument(clazz, false, methodNames);
	}
	
	
	/**
	 * Instruments the specified class
	 * @param clazz The class to instrument
	 * @param merge If true, if the class is already instrumented, the additions will be merged to the existing instrumentation.
	 * Otherwise the existing instrumentation will be discarded and replaced with this new definition
	 * @param methodNames The method names to instrument
	 * @return the number of methods instrumented at the end of this procedure
	 */
	public int instrument(final Class<?> clazz, final boolean merge, String...methodNames) {
		log.info("Retransforming {} / {}", clazz.getName(), methodNames);
		Set<String> mNames = new HashSet<String>(Arrays.asList(methodNames));
		final Map<String, Method> methods = new HashMap<String, Method>();
//		for(Method m: clazz.getMethods()) {
//			if(m.getDeclaringClass().equals(Object.class)) continue;
//			if(mNames.contains(m.getName())) methods.put(m.getName(), m);
//		}
		final Set<String> existingInstrMethods = instrumentedClasses.get(clazz);
		for(Method m: clazz.getDeclaredMethods()) {
//			if(Modifier.isPrivate(m.getModifiers())) continue;
			if(mNames.contains(m.getName())) {
				methods.put(m.toGenericString(), m);
			} else {
				if(merge && existingInstrMethods!=null && !existingInstrMethods.isEmpty()) {
					if(existingInstrMethods.contains(m.getName())) {
						methods.put(m.toGenericString(), m);
					}
				}
			}
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
				final String metricName = MetricBuilder.metric("method.elapsedns").tag("method", mx.getName()).tag("class", Util.clean(clazz.getSimpleName())).tag("package", Util.clean(clazz.getPackage().getName())).build().toString();
				log.info("Adding metric name {}", metricName);
				CtMethod ctm = target.getDeclaredMethod(mx.getName(), sig(cp, mx));
				target.removeMethod(ctm);
	            ctm.addLocalVariable("xxxstart", CtClass.longType);
	            ctm.addLocalVariable("xxxelapsed", CtClass.longType);
	            ctm.insertBefore("xxxstart = System.nanoTime();");
	            ctm.insertAfter("xxxelapsed = System.nanoTime() - xxxstart; System.out.println(\"Elapsed:\" + xxxelapsed); OTMetricCache.getInstance().getOTMetric(\"" + metricName + "\").trace(xxxelapsed);");            
	            target.addMethod(ctm);
			}
			final byte[] instrumentedByteCode = target.toBytecode();
			getByteCodeFor(clazz, instrumentedByteCode);
			if(byteCodeDir!=null) {
				target.writeFile(byteCodeDir);
				log.debug("Saved transformed class [{}] to [{}]", target.getName(), byteCodeDir);
			}
			instrumentedClasses.put(clazz, methods.keySet());
			final String internalName = b2i(clazz.getName());
			final ClassFileTransformer instrumentor = new ClassFileTransformer() {				
				@Override
				public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
						ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
					if(internalName.equals(className)) {
						return instrumentedByteCode;
					}
					cp.appendClassPath(new ByteArrayClassPath(i2b(className), classfileBuffer));
					cp.appendClassPath(new LoaderClassPath(loader));
					try {
						CtClass ctclazz = cp.get(i2b(className));
						if(ctclazz.subclassOf(target)) {
							log.info("Subclass of target:" + className);
							ctclazz.writeFile(byteCodeDir);
						}
						return classfileBuffer;
					} catch (Exception ex) {
						ex.printStackTrace(System.err);
						return classfileBuffer;
					}
				}
			};
			try {
				instrumentation.addTransformer(instrumentor, true);
				instrumentation.retransformClasses(clazz);
			} finally {
				//instrumentation.removeTransformer(instrumentor);
			}
			return methods.size();
			//ByteCapturingClassTransform transformer = new ByteCapturingClassTransform(clazz, instrumentedByteCode);
		} catch (Exception ex) {
			log.error("Failed to instrument [{}]", clazz.getName(), ex);
			throw new RuntimeException(ex);
		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aoplite.RetransformerLiteMBean#instrument(java.lang.String, java.lang.String)
	 */
	@Override
	public int instrument(final String className, final String methodExpr) {
		return instrument(null, className, methodExpr);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aoplite.RetransformerLiteMBean#instrument(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public int instrument(final String classLoaderName, final String className, final String methodExpr) {
		try {
			final ClassLoader classLoader = classLoaderFrom(classLoaderName);
			// If this fails, do the scorched earth fallback and scan the Instrumentation's classes.
			Class<?> clazz = null;
			try {
				clazz = Class.forName(className, true, classLoader);
			} catch (Exception ex) {
				log.info("Failed to find class {}. Taking stronger measures.....", className);
				for(Class<?> c: instrumentation.getAllLoadedClasses()) {
					if(className.equals(c.getName())) {
						clazz = c;
						log.info("Located class {}", className);
						break;
					}
				}
				if(clazz==null) {
					log.error("Failed to find class {}", className); //, className);
					throw ex;
				}
			}
			
			final Pattern p = Pattern.compile(methodExpr);
			Set<String> targetMethods = new HashSet<String>();
			for(Method m: clazz.getMethods()) {
				if(p.matcher(m.getName()).matches()) {
					targetMethods.add(m.getName());
				}
			}
			for(Method m: clazz.getDeclaredMethods()) {
				if(p.matcher(m.getName()).matches()) {
					targetMethods.add(m.getName());
				}
			}
			return instrument(clazz, targetMethods.toArray(new String[targetMethods.size()]));
		} catch (Throwable ex) {
			final String msg = String.format("Failed to execute instrumentation directive [%s/%s/%s]", classLoaderName, className, methodExpr);
			log.error(msg, ex);
			throw new RuntimeException(msg, ex);
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aoplite.RetransformerLiteMBean#restoreClass(java.lang.String)
	 */
	@Override	
	public void restoreClass(final String className) {
		if(className==null || className.isEmpty()) throw new IllegalArgumentException("The passed class name was null or empty");
		final String _cn = className.trim();
		Class<?> clazz = null;
		synchronized(instrumentedClasses) {
			for(Map.Entry<Class<?>, ?> entry: instrumentedClasses.entrySet()) {
				if(entry.getKey().getName().equals(_cn)) {
					clazz = entry.getKey();
					break;					
				}
			}
		}		
		if(clazz==null) throw new IllegalArgumentException("The class [" + className + "] is not instrumented");
		try {
			instrumentation.retransformClasses(clazz);
			instrumentedClasses.remove(clazz);
			log.info("Uninstrumented class [{}]", className);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to uninstrument class [" + className + "]", ex);
		}		
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aoplite.RetransformerLiteMBean#restoreAllClasses()
	 */
	@Override
	public void restoreAllClasses() {
		try {
			instrumentation.retransformClasses(instrumentedClasses.keySet().toArray(new Class[instrumentedClasses.size()]));
			instrumentedClasses.clear();
		} catch (Exception e) {
			log.error("Failed to restore all classes", e);
		}
	}

	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aoplite.RetransformerLiteMBean#getInstrumentedClassNames()
	 */
	@Override
	public String[] getInstrumentedClassNames() {
		Set<Class<?>> clazzes = new HashSet<Class<?>>(instrumentedClasses.keySet());
		Set<String> classNames = new HashSet<String>(clazzes.size());
		for(Class<?> clazz: clazzes) {
			classNames.add(clazz.getName());
		}
		return classNames.toArray(new String[classNames.size()]);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aoplite.RetransformerLiteMBean#getInstrumentedClassCount()
	 */
	@Override
	public int getInstrumentedClassCount() {
		return instrumentedClasses.size();
	}
	
	
	public Set<String> printClassLocations(final String className) {
		Set<String> set = new HashSet<String>(6);
		for(Class<?> clazz: instrumentation.getAllLoadedClasses()) {
			if(className.equals(clazz.getName())) {
				ProtectionDomain pd = clazz.getProtectionDomain();
				if(pd==null) {
					set.add("No Protection Domain");
					continue;
				}
				CodeSource cs = pd.getCodeSource();
				if(cs==null) {
					set.add("No Code Source");
					continue;
				}
				URL location = cs.getLocation();
				if(location==null) {
					set.add("No Location");
					continue;
				}
				set.add(location.toString());
			}
		}
		return set;
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aoplite.RetransformerLiteMBean#searchClasses(java.lang.String, int, int, int)
	 */
	@Override
	public Set<String> searchClasses(final String pattern, final int segments, final int scanLimit, final int matchLimit) {
		return searchClasses(null, pattern, segments, scanLimit, matchLimit);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aoplite.RetransformerLiteMBean#searchClasses(java.lang.String, java.lang.String, int, int, int)
	 */
	@Override
	public Set<String> searchClasses(final String classLoader, final String pattern, final int segments, final int scanLimit, final int matchLimit) {		
		final Set<String> classNames = new HashSet<String>();
		final Pattern p = Pattern.compile(pattern);
		int scanned = 0;		
		final String[] segmentSlots = new String[segments<1 ? 0 : segments];
		final Class<?>[] classArray = (classLoader==null || classLoader.trim().isEmpty()) ? 
				instrumentation.getAllLoadedClasses() : 
					instrumentation.getInitiatedClasses("bootstrap".equalsIgnoreCase(classLoader) ? null : classLoaderFrom(classLoader)); 
		for(Class<?> clazz: classArray) {
			if(clazz.isArray()) continue;
			scanned++;
			
			if(p.matcher(clazz.getName()).matches()) {
				if(segments<1) classNames.add(clazz.getName()); 
				classNames.add(trunc(clazz.getName(), segmentSlots));
			}
			if(scanned==scanLimit || classNames.size()==matchLimit) break;
		}
		return classNames;
	}
	
	/**
	 * Splits the passed class name, truncates to the size of the slots array, then concats the result
	 * back to a dot separated string.
	 * @param className The class name to split
	 * @param slots The slots array to write into
	 * @return the truncated class name
	 */
	protected static String trunc(final String className, final String[] slots) {
		final String[] split = DOT_SPLITTER.split(className);
		Arrays.fill(slots, "");
		System.arraycopy(split, 0, slots, 0, Math.min(split.length, slots.length));
		if(split.length < slots.length) {
			StringBuilder b = new StringBuilder(Arrays.toString(slots).replace("[", "").replace("]", "").replace(", ", "."));
			b.reverse();
			while(b.charAt(0)=='.') b.deleteCharAt(0);
			return b.reverse().toString();
		}
		return Arrays.toString(slots).replace("[", "").replace("]", "").replace(", ", ".");		
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aoplite.RetransformerLiteMBean#listClassLoaders()
	 */
	@Override
	public Map<String, Set<String>> listClassLoaders() {
		final List<MBeanServer> mbservers = MBeanServerFactory.findMBeanServer(null);
		final Map<String, Set<String>> map = new HashMap<String, Set<String>>(mbservers.size());
		for(MBeanServer mbs: mbservers) {
			String dd = mbs.getDefaultDomain();
			if(dd==null || dd.trim().isEmpty()) dd = "DefaultDomain";
			map.put(dd, new HashSet<String>());
		}
		final QueryExp classLoaderExp = Query.isInstanceOf(new StringValueExp(ClassLoader.class.getName()));
		for(MBeanServer mbs: mbservers) {
			String dd = mbs.getDefaultDomain();
			if(dd==null || dd.trim().isEmpty()) dd = "DefaultDomain";
			final Set<String> set = map.get(dd);
			try {
				for(ObjectName on: mbs.queryNames(null, classLoaderExp)) {
					set.add(on.toString());
				}
			} catch (Exception ex) {/* No Op */}
		}
		return map;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aoplite.RetransformerLiteMBean#printClassLoaderFor(java.lang.String)
	 */
	@Override
	public String printClassLoaderFor(final String name) {
		return classLoaderFrom(name).toString();
	}
	
	
	/**
	 * Attempts to derive a classloader from the passed object.
	 * @param obj The object to derive a classloader from
	 * @return a classloader
	 */
	protected static ClassLoader classLoaderFrom(Object obj) {
		if(obj==null) {
			return Thread.currentThread().getContextClassLoader();
		} else if(obj instanceof ClassLoader) {
			return (ClassLoader)obj;
		} else if(obj instanceof Class) {
			return ((Class<?>)obj).getClassLoader();
		} else if(obj instanceof URL) {
			return new URLClassLoader(new URL[]{(URL)obj}); 
		} else if(URLHelper.isValidURL(obj.toString())) {
			URL url = URLHelper.toURL(obj.toString());
			return new URLClassLoader(new URL[]{url});
		} else if(obj instanceof ObjectName) {
			return getClassLoader((ObjectName)obj);
		} else if(JMXHelper.isObjectName(obj.toString())) {
			return getClassLoader(JMXHelper.objectName(obj.toString()));
		} else if(obj instanceof File) {
			File f = (File)obj;
			return new URLClassLoader(new URL[]{URLHelper.toURL(f)});
		} else if(new File(obj.toString()).canRead()) {
			File f = new File(obj.toString());
			return new URLClassLoader(new URL[]{URLHelper.toURL(f)});			
		} else {
			return obj.getClass().getClassLoader();
		}		
	}
	
	
	/**
	 * Returns the classloader represented by the passed ObjectName
	 * @param on The ObjectName to resolve the classloader from
	 * @return a classloader
	 */
	protected static ClassLoader getClassLoader(ObjectName on) {
		try {
			for(MBeanServer server: MBeanServerFactory.findMBeanServer(null)) {
				if(server.isRegistered(on)) {
					if(server.isInstanceOf(on, ClassLoader.class.getName())) {
						return server.getClassLoader(on);
					}
					return server.getClassLoaderFor(on);					
				}
			}
			throw new RuntimeException("Failed to get classloader for object name [" + on + "]");
		} catch (Exception ex) {
			throw new RuntimeException("Failed to get classloader for object name [" + on + "]", ex);
		}
	}	
	

}

/*
import javax.management.loading.MLet;
import javax.management.*;
import java.lang.management.*;
MBeanServer mbs = JMXHelper.getHeliosMBeanServer();
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
    
    

// the following code re-registers the jboss aop transformer ;
instrumentation = org.jboss.aop.standalone.PluggableInstrumentor.instrumentor;
println "Inst:${instrumentation.getClass().getName()}";
tmgr = instrumentation.mTransformerManager;
jbossTransformer = null;
if(tmgr!=null) {
    tmgr.mTransformerList.each() {
        if("org.jboss.aop.standalone.AOPTransformer".equals(it.transformer().getClass().getName())) {
            jbossTransformer = it.transformer();
        }
    }
    if(jbossTransformer!=null) {
        instrumentation.removeTransformer(jbossTransformer);        
        instrumentation.addTransformer(jbossTransformer, true);
        println "Switched JBoss AOP Transformer";
    }    
}


jbossTransformer = null;
tmgr = instrumentation.mRetransfomableTransformerManager;
if(tmgr!=null) {
    tmgr.mTransformerList.each() {
        println "\tRetrans:${it.transformer().getClass().getName()}";
        jbossTransformer = it.transformer();
    }
}
    
*/    
      

