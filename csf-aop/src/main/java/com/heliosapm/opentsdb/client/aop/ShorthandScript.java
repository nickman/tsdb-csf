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

import static com.heliosapm.opentsdb.client.aop.ShorthandToken.IND_INHERRIT;
import static com.heliosapm.opentsdb.client.aop.ShorthandToken.IND_INSTOPTIONS;
import static com.heliosapm.opentsdb.client.aop.ShorthandToken.IND_MEASURMENT;
import static com.heliosapm.opentsdb.client.aop.ShorthandToken.IND_METHOD;
import static com.heliosapm.opentsdb.client.aop.ShorthandToken.IND_METHOD_ANNOT;
import static com.heliosapm.opentsdb.client.aop.ShorthandToken.IND_METHOD_ATTRS;
import static com.heliosapm.opentsdb.client.aop.ShorthandToken.IND_METRICNAME;
import static com.heliosapm.opentsdb.client.aop.ShorthandToken.IND_SIGNATURE;
import static com.heliosapm.opentsdb.client.aop.ShorthandToken.IND_SUBMETRIC;
import static com.heliosapm.opentsdb.client.aop.ShorthandToken.IND_TARGETCLASS;
import static com.heliosapm.opentsdb.client.aop.ShorthandToken.IND_TARGETCLASS_ANNOT;
import static com.heliosapm.opentsdb.client.aop.ShorthandToken.IND_TARGETCLASS_CL;

import java.lang.annotation.Annotation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.scanners.TypeElementsScanner;
import org.reflections.util.ConfigurationBuilder;

import com.google.common.collect.Multimap;
import com.heliosapm.opentsdb.client.opentsdb.ConfigurationReader;
import com.heliosapm.opentsdb.client.opentsdb.Constants;
import com.heliosapm.opentsdb.client.opentsdb.opt.Measurement;
import com.heliosapm.opentsdb.client.opentsdb.opt.SubMetric;
import com.heliosapm.opentsdb.client.util.StringHelper;


/**
 * <p>Title: ShorthandScriptMBean</p>
 * <p>Description: Class responsible for parsing a shorthand expression and locating the intended classes and methods to instrument.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.ShorthandScript</code></p>
 * <h4><pre>
		 [@]<ClassName>[+] [(Method Attributes)] [@]<MethodName>[<Signature>] [Invocation Options] <CollectorName>[<BitMask>|<CollectionNames>] <MetricFormat> DISABLED
	</pre></h4>

 */
public class ShorthandScript implements ShorthandScriptMBean {
	/** The shorthand expression parser */
	/*
	 * TODO: Need to integrate:
	 * ===============================
	 * References:
	 * -----------
	 * OTMetric / OTMetric ID: Resolve from naming directives
	 * CHMetric:  Derived ?
	 * SubMetric BitMask:  Specified, Default to std bitmask
	 * Measurement BitMask: Specified, Default to std bitmask
	 * ===============================
	 * Naming tags:
	 * ------------
	 * method:  simple method name, argument cardinality, provided
	 * class  (simple class name)
	 * package (optional)
	 * 
	 */
	protected static final Pattern SH_PATTERN = Pattern.compile(
	        		"(@)?" +                         	// (0)	The class annotation indicator
	                "(.*?)" +                         	// (1)	The classname (MANDATORY)
	                "(\\+)?" +                         	// (2)	The classname options (+ for inherritance) 
	                "(?:<\\-(.*?))?" + 					// (3)	The optional classloader expression for the target class name
					"\\s" + 							// spacer
	                "(?:\\((.*?)\\)\\s)?" +         	// (4)	The optional method accessibilities. Defaults to "pub"
	                "(@)?" +                         	// (5)	The method annotation indicator
	                "(\\[?.*?\\]?)" +                   // (6)	The method name expression, wrapped in "[ ]" if a regular expression  (MANDATORY)
	                "(?:\\((.*)\\))?" +            		// (7)	The optional method signature
	                "(?:\\[(.*)\\])?" +         		// (8)	The optional method attributes
//	                "\\s" +                             // spacer
	                "(?:\\-(\\w+))?" +                 	// (9)	The method instrumentation options (-dr)
	                "(?:\\[(.*)\\])?" +         		// (10)	The measurement bitmask option. [] is mandatory if specified. It may contain the bitmask int, or comma separated Measurement names
//					"\\s+?" + 							// optional spacer
	                "(?:\\[(.*)\\])?" +         		// (11)	The sub-metric bitmask option. [] is mandatory if specified. It may contain the bitmask int, or comma separated SubMetric names	                
	                "\\s" +                            	// spacer
	                "(?:'(.*)')"                    	// (12)	The metric name format      
	);	
	
	//	"java.lang.Object+ equals 'java/lang/Object'"

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		SignaturePrinter.main(Object.class.getName(), "equals");
//		log("Pattern:%s", SH_PATTERN.pattern());
//		log("Pattern Groups:%s", SH_PATTERN.matcher("").groupCount());
//		log(parse("java.lang.Object+<-SYSTEM.PARENT [eq.*|to.*] 'java/lang/Object'").toString());
	}
	
	/** The whitespace cleaner */
	public static final Pattern WH_CLEANER = Pattern.compile("\\s+");
	/** The single quote cleaner */
	public static final Pattern SQ_CLEANER = Pattern.compile("'");
	
	/** A comma splitter */
	public static final Pattern COMMA_SPLITTER = Pattern.compile(",");
	
	/** MetricName delimiter splitter */
	public static final Pattern DELIM_SPLITTER = Pattern.compile("/");
	/** Match everyting pattern */
	public static final Pattern MATCH_ALL = Pattern.compile(".*");
	
	/** The shorthand symbol for inherrits or extends */
	public static final String PLUS = "+";
	/** The shorthand symbol indicating the script should be compiled and installed, but disabled */
	public static final String DISABLED = "DISABLED";
	
	
	
	/** The symbol for a class ctor */
	public static final String INIT = "<init>";
	
	/** The JVM's end of line character */
	public static final String EOL = System.getProperty("line.separator", "\n");

	//==============================================================================================
	//		Parallel Scan Thread Pool
	//==============================================================================================
	
	
	/** An executor service to execute classpath scans in parallel */
	private static final ExecutorService scanExecutor = Executors.newCachedThreadPool(new ThreadFactory(){
		private final AtomicInteger serial = new AtomicInteger();
		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, "ReflectionsScanningThread#" + serial.incrementAndGet());
			t.setDaemon(true);
			return t;
		}
	});
	
	static {
		((ThreadPoolExecutor)scanExecutor).prestartCoreThread();
		((ThreadPoolExecutor)scanExecutor).prestartCoreThread();
	}


	//==============================================================================================
	//		Target Class Attributes
	//==============================================================================================
	/** The target class for instrumentation */
	protected Class<?> targetClass = null;
	/** Indicates if the target class is an annotation */
	protected boolean targetClassAnnotation = false;
	/** The target class classloader */
	protected ClassLoader targetClassLoader = null;
	/** Indicates if the target class is an interface */
	protected boolean targetClassInterface = false;	
	/** Indicates if inherritance off the target class is enabled */
	protected boolean inherritanceEnabled = false;
	
	//==============================================================================================
	//		Target Method Attributes
	//==============================================================================================
	/** The target method name, null if expr is used */
	protected String methodName = null;
	/** The target method name expression, null if name is used */
	protected Pattern methodNameExpression = null;
	/** The target method signature, null if expr is used */
	protected String methodSignature = null;
	/** The target method signature expression, null if signature is used */
	protected Pattern methodSignatureExpression = null;
	/** Indicates if the target method is an annotation */
	protected boolean targetMethodAnnotation = false;
	/** The target method level annotation class */
	protected Class<? extends Annotation> methodAnnotationClass = null;	
	/** The method attributes (from {@link MethodAttribute}) */
	protected int methodAttribute = MethodAttribute.DEFAULT_MASK;

	//==============================================================================================
	//		Instrumentation Attributes
	//==============================================================================================
	/** The method invocation options (from {@link InvocationOption}) */
	protected int methodInvocationOption = InvocationOption.DEFAULT_MASK;
	/** The measurement bitmask */
	protected int measurementBitMask = Measurement.DEFAULT_MASK;
	/** The subMetric bitmask */
	protected int subMetricBitMask = SubMetric.DEFAULT_MASK;
	
	/** The metric name template */
	protected String metricNameTemplate = null;
	/** Indicates if the instrumented method should have the instrumentation enabled when the method is called reentrantly (i.e. self-calls) */
	protected boolean allowReentrant = false;
	/** Indicates if all instrumentation on the current thread should be disabled when the method is invoked */
	protected boolean disableOnTrigger = false;
	/** Indicates if the instrumentation should be disabled at start time (and require intervention to activate) */
	protected boolean startDisabled = false;
	/** Indicates if the instrumentation should batch transform (see {@link InvocationOption#TRANSFORMER_BATCH}) */
	protected boolean batchTransform = false;
	/** Indicates if the instrumentation's classfile transformer should stay resident (see {@link InvocationOption#TRANSFORMER_RESIDENT}) */
	protected boolean residentTransformer = false;
	
	/** Indicates if the parser will be tolerant of invalid settings in the expression */
	protected final boolean parsingTolerance;
	
	//==============================================================================================

	/** Empty vars map const */
	protected static final Map<String, String> EMPTY_CL_MAP = Collections.unmodifiableMap(new HashMap<String, String>(0));
	
	/**
	 * System out pattern logger
	 * @param fmt The message format
	 * @param args The tokens
	 */
	public static void log(final Object fmt, final Object...args) {
		System.out.println(String.format(fmt.toString(), args));
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		final int maxLen = 10;
		StringBuilder builder = new StringBuilder();
		builder.append("ShorthandScript [\n\ttargetClass=");
		builder.append(targetClass);
		builder.append("\n\ttargetClassAnnotation:");
		builder.append(targetClassAnnotation);
		builder.append("\n\ttargetClassLoader:");
		builder.append(targetClassLoader);
		builder.append("\n\ttargetClassInterface:");
		builder.append(targetClassInterface);
		builder.append("\n\tinherritanceEnabled:");
		builder.append(inherritanceEnabled);
		builder.append("\n\tmethodName:");
		builder.append(methodName);
		builder.append("\n\tmethodNameExpression:");
		builder.append(methodNameExpression);
		builder.append("\n\tmethodSignature:");
		builder.append(methodSignature);
		builder.append("\n\tmethodSignatureExpression:");
		builder.append(methodSignatureExpression);
		builder.append("\n\ttargetMethodAnnotation:");
		builder.append(targetMethodAnnotation);
		builder.append("\n\tmethodAnnotationClass:");
		builder.append(methodAnnotationClass);
		builder.append("\n\tmethodAttribute:");
		builder.append(Arrays.toString(MethodAttribute.getEnabled(methodAttribute)));
		builder.append("\n\tmethodInvocationOption:");
		builder.append(Arrays.toString(InvocationOption.getEnabled(methodInvocationOption)));
		builder.append("\n\tmeasurements:");
		builder.append(Arrays.toString(Measurement.getEnabled(measurementBitMask)));
		builder.append("\n\tsubMetrics:");
		builder.append(Arrays.toString(SubMetric.getEnabled(subMetricBitMask)));		
		builder.append("\n\tmetricNameTemplate:");
		builder.append(metricNameTemplate);
		builder.append("\n\tallowReentrant:");
		builder.append(allowReentrant);
		builder.append("\n\tdisableOnTrigger:");
		builder.append(disableOnTrigger);
		builder.append("\n\tstartDisabled:");
		builder.append(startDisabled);
		builder.append("\n\tbatchTransform:");
		builder.append(batchTransform);
		builder.append("\n\tresidentTransformer:");
		builder.append(residentTransformer);
		builder.append("\n\tclassLoaders:");
		builder.append(classLoaders != null ? toString(classLoaders.entrySet(),
				maxLen) : null);
		builder.append("\n]");
		return builder.toString();
	}



	private String toString(Collection<?> collection, int maxLen) {
		StringBuilder builder = new StringBuilder();
		builder.append("[");
		int i = 0;
		for (Iterator<?> iterator = collection.iterator(); iterator.hasNext()
				&& i < maxLen; i++) {
			if (i > 0)
				builder.append(", ");
			builder.append(iterator.next());
		}
		builder.append("]");
		return builder.toString();
	}

	
	
	/**
	 * Returns a parsed ShorthandScript instance for the passed sourcexx
	 * @param source The source to parse
	 * @param classLoaders A map of classloader names keyed by the type the classloader is for (i.e. <b>target</b> or <b>collector</b>)
	 * @return a parsed ShorthandScript instance 
	 */
	public static ShorthandScript parse(CharSequence source, Map<String, String> classLoaders) {
		if(source==null || source.toString().trim().isEmpty()) throw new ShorthandParseFailureException("The passed source was null or empty", "<null>");
		return new ShorthandScript(source.toString().trim(), classLoaders);
	}
	

	/**
	 * Returns a parsed ShorthandScript instance for the passed source
	 * @param source The source to parse
	 * @return a parsed ShorthandScript instance 
	 */
	public static ShorthandScript parse(CharSequence source) {
		return parse(source, EMPTY_CL_MAP);
	}

	

	
	/** The processor supplied classloader pre-defs */
	protected final Map<String, String> classLoaders;
	
	/** The predef classloader key for target classes */
	public static final String PREDEF_CL_TARGET = "target";
	/** The predef classloader key for instrumentation classes */
	public static final String PREDEF_CL_INSTR = "collector";
	
	
	/**
	 * Creates a new ShorthandScript
	 * @param source The source to parse
	 * @param classLoaders A map of classloader names keyed by the type the classloader is for (i.e. <b>target</b> or <b>collector</b>)
	 */
	private ShorthandScript(String source, Map<String, String> classLoaders) {
		this.classLoaders = classLoaders;
		parsingTolerance = ConfigurationReader.confBool(Constants.PROP_SHORTHAND_TOLERANT_PROPERTY, Constants.DEFAULT_SHORTHAND_TOLERANT_PROPERTY);
		String whiteSpaceCleanedSource = WH_CLEANER.matcher(source).replaceAll(" ");
		Matcher matcher = SH_PATTERN.matcher(whiteSpaceCleanedSource);
		if(!matcher.matches()) {
			throw new ShorthandParseFailureException("Shorthand script regex pattern not recognized", whiteSpaceCleanedSource);
		}
		final int fieldCount = matcher.groupCount();
		String[] parsedFields = new String[fieldCount];
		for(int i = 1; i <= fieldCount; i++ ) {
			parsedFields[i-1] = matcher.group(i);
		}
		
		log(printParsedValues(parsedFields));
		validateMandatoryFields(whiteSpaceCleanedSource, parsedFields);
		validateTargetClass(whiteSpaceCleanedSource, parsedFields[IND_TARGETCLASS.ordinal()], parsedFields[IND_TARGETCLASS_CL.ordinal()], parsedFields[IND_TARGETCLASS_ANNOT.ordinal()], parsedFields[IND_INHERRIT.ordinal()]);
		validateTargetMethod(whiteSpaceCleanedSource, parsedFields[IND_METHOD.ordinal()], parsedFields[IND_METHOD_ANNOT.ordinal()], parsedFields[IND_SIGNATURE.ordinal()], parsedFields[IND_INSTOPTIONS.ordinal()]);
		validateTargetMethodAttributes(whiteSpaceCleanedSource, parsedFields[IND_METHOD_ATTRS.ordinal()]); 
		validateMethodSignature(whiteSpaceCleanedSource, parsedFields[IND_SIGNATURE.ordinal()]);
		validateMethodInvocationOptions(whiteSpaceCleanedSource, parsedFields[IND_INSTOPTIONS.ordinal()]);
		validateMethodInstrumentation(whiteSpaceCleanedSource, parsedFields[IND_MEASURMENT.ordinal()], parsedFields[IND_SUBMETRIC.ordinal()]);		
		metricNameTemplate = parsedFields[IND_METRICNAME.ordinal()].trim();
	}
	
	/**
	 * Prints the parsed values for each of the Shorthand Regex groups
	 * @param parsedFields The string arr if the regex parsed groups
	 * @return the formatted values
	 */
	protected String printParsedValues(final String[] parsedFields) {
		StringBuilder b = new StringBuilder("Parsed Values: [");
		for(ShorthandToken token: ShorthandToken.values()) {
			b.append("\n\t").append(token.name()).append(":").append(parsedFields[token.ordinal()]);
		}
		
		return b.append("\n]").toString();
	}
	
	/**
	 * Validates, loads and configures the target method instrumentation collector and configuration.
	 * TODO:  Detect inactive measurements where data is collected but not reported (or vice-versa ?)
	 * @param source The source (for reporting in any exception thrown)
	 * @param measurementNames The configured measurements
	 * @param subMetricNames The configured subMetrics
	 * @throws ShorthandParseFailureException thrown if tolerance is false and there are invalid measurements or subMetrics.
	 */
	protected void validateMethodInstrumentation(final String source, final String measurementNames, final String subMetricNames) {
		try {
			Measurement[] measurements = measurementNames==null ? Measurement.getEnabled(Measurement.DEFAULT_MASK) : Measurement.decode(!parsingTolerance, measurementNames);
			if(measurements.length==0) {
				if(!parsingTolerance) throw new Exception("No valid measurements found from names [" + measurementNames + "]");
				measurements  = Measurement.getEnabled(Measurement.DEFAULT_MASK);
			}
			this.measurementBitMask = Measurement.getMaskFor(measurements);
		} catch (Exception ex) {
			throw new ShorthandParseFailureException("Failed to parse measurements", source, ex);
		}
		try {
			SubMetric[] subMetrics = subMetricNames==null ? SubMetric.getEnabled(SubMetric.DEFAULT_MASK) : SubMetric.decode(!parsingTolerance, subMetricNames);
			if(subMetrics.length==0) {
				if(!parsingTolerance) throw new Exception("No valid subMetrics found from names [" + subMetricNames + "]");
				subMetrics  = SubMetric.getEnabled(SubMetric.DEFAULT_MASK);
			}
			this.subMetricBitMask = SubMetric.getMaskFor(subMetrics);
		} catch (Exception ex) {
			throw new ShorthandParseFailureException("Failed to parse subMetrics", source, ex);
		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandScriptMBean#getTargetMembers()
	 */
	@Override
	public Map<Class<?>, Set<Member>> getTargetMembers() {
		Set<Class<?>> targetClasses = getTargetClasses();
		Map<Class<?>, Set<Member>> targetMembers = new HashMap<Class<?>, Set<Member>>(targetClasses.size());
		for(Class<?> clazz: targetClasses) {
			targetMembers.put(clazz, new HashSet<Member>());
		}
		Class<? extends Annotation> annotationClass = null;
		if(targetMethodAnnotation) {
			annotationClass = methodAnnotationClass;
		}
		for(Map.Entry<Class<?>, Set<Member>> entry: targetMembers.entrySet()) {
			for(Method m: entry.getKey().getDeclaredMethods()) {
				if(targetMethodAnnotation) {
					if(m.getAnnotation(annotationClass)!=null) {
						if(isMatchingSignature(m) && isMatchingAttribute(m)) {
							entry.getValue().add(m);
						}
					}
				} else if(methodName!=null && methodName.equals(m.getName())) {
					if(isMatchingSignature(m) && isMatchingAttribute(m)) {
						entry.getValue().add(m);
					}
				} else if(methodNameExpression!=null && methodNameExpression.matcher(m.getName()).matches()) {
					if(isMatchingSignature(m) && isMatchingAttribute(m)) entry.getValue().add(m);
				}
			}
		}
		return targetMembers;
	}
	
	
	/**
	 * Determines if the passed member matches either the defined signature or the signature expression
	 * @param member The member to test 
	 * @return true for a match, false otherwise
	 */
	protected boolean isMatchingSignature(Member member) {
		String desc = StringHelper.getMemberSignature(member);
		if(methodSignature!=null) {
			return methodSignature.equals(desc);
		}
		return methodSignatureExpression.matcher(desc).matches();
	}
	
	/**
	 * Determines if the passed member's modifiers match the method attribute defined in the script
	 * @param member The member to test
	 * @return true for a match, false otherwise
	 */
	protected boolean isMatchingAttribute(Member member) {
		//log("isMatchingAttribute:  %s [%s]  ma [%s]", member.getName(), member.getModifiers(), methodAttribute);
		return (methodAttribute & member.getModifiers())==methodAttribute; 
	}
	
	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandScriptMBean#getTargetClasses()
	 * FIXME:  Can be optimized if we know what the classloader is and it is localized
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Set<Class<?>> getTargetClasses() {
		if(!targetClassAnnotation && (!inherritanceEnabled || Modifier.isFinal(targetClass.getModifiers()))) {
			return new HashSet<Class<?>>(Arrays.asList(targetClass));
		}		
		ConfigurationBuilder cb = new ConfigurationBuilder()
			.addClassLoader(targetClass.getClassLoader())
			.addClassLoader(Thread.currentThread().getContextClassLoader())
			.addScanners(new SubTypesScanner());
		if(targetClassLoader!=null) {
			cb.addClassLoader(targetClassLoader);
		}
		
		if(targetClassAnnotation) {
			cb.addScanners(new TypeAnnotationsScanner());
		}
		cb.setExecutorService(scanExecutor);
		Reflections reflections = new Reflections(ConfigurationBuilder.build());
		
		if(targetClassAnnotation) {
			return reflections.getTypesAnnotatedWith((Class<? extends Annotation>) targetClass, inherritanceEnabled);
		} else if(inherritanceEnabled) {
//			printReflectionsRepo(reflections);
//			log("Classpath: %s", ManagementFactory.getRuntimeMXBean().getClassPath());
//			log("Command Line: %s", ManagementFactory.getRuntimeMXBean().getInputArguments());
			Set<?> subTypes = reflections.getSubTypesOf(targetClass);
			Set<Class<?>> results = new HashSet<Class<?>>((Collection<? extends Class<?>>) subTypes);
			return results;
		}
		Set<Class<?>> results  = new HashSet<Class<?>>(Arrays.asList(targetClass));
		return results;
	}
	
	private void printReflectionsRepo(final Reflections ref) {
		final StringBuilder b = new StringBuilder("\n\t======= Reflections List:");
		for(String s: ref.getStore().keySet()) {
			b.append("\n").append("Scanner:").append(s);
			Multimap<String, String> mm = ref.getStore().get(s);
			for(Map.Entry<String, Collection<String>> entry : mm.asMap().entrySet()) {
				b.append("\n\t").append(entry.getKey());
				for(String n: entry.getValue()) {
					b.append("\n\t\t").append(n);
				}
			}
		}
		log(b.toString());
	}
	
	/**
	 * Validates, loads and configures the target method invocation options
	 * @param source The source (for reporting in any ecxeption thrown)
	 * @param parsedInvocationOptions The method option characters
	 */
	protected void validateMethodInvocationOptions(String source, String parsedInvocationOptions) {
		allowReentrant = InvocationOption.isAllowReentrant(parsedInvocationOptions);
		disableOnTrigger = InvocationOption.isDisableOnTrigger(parsedInvocationOptions);
		startDisabled = InvocationOption.isStartDisabled(parsedInvocationOptions);
		// ==========================
		batchTransform = InvocationOption.isBatchTransform(parsedInvocationOptions);
		residentTransformer = InvocationOption.isResidentTransformer(parsedInvocationOptions);
		if(!batchTransform && !residentTransformer) {
			residentTransformer = true;
		}
	}

	
	/**
	 * Validates, loads and configures the target method[s]
	 * @param source The source (for reporting in any exception thrown)
	 * @param parsedMethodSignature The method signature or pattern
	 */
	protected void validateMethodSignature(String source, String parsedMethodSignature) {
		if(parsedMethodSignature!=null && !parsedMethodSignature.trim().isEmpty()) {
			methodSignature = parsedMethodSignature.trim(); 
			boolean patternStart = methodSignature.startsWith("(");
			boolean patternEnd = methodSignature.endsWith(")");
			if((patternStart && patternEnd) || (!patternStart && !patternEnd)) {
				if(patternStart && patternEnd) {
					try {
						this.methodSignatureExpression = Pattern.compile(methodSignature.substring(1, methodSignature.length()-1));
						this.methodSignature = null;
					} catch (Exception ex) {
						throw new ShorthandParseFailureException("Failed to compile method signature pattern " + methodSignature, source);
					}
				} else {
					// TODO
				}
			} else {
				throw new ShorthandParseFailureException("Method signature [" + methodSignature + "] seemed to want to be an expression but was missing an opener or closer", source);
			}
		} else {
			methodSignature = null;
			methodSignatureExpression = MATCH_ALL;
		}
	}
	
	/**
	 * Validates, loads and configures the target method attributes
	 * @param source The source (for reporting in any ecxeption thrown)
	 * @param parsedMethodAttributes The method attributes (from {@link MethodAttribute})
	 */
	protected void validateTargetMethodAttributes(String source, String parsedMethodAttributes) {
		if(parsedMethodAttributes!=null && !parsedMethodAttributes.trim().isEmpty()) {
			String[] attrs = COMMA_SPLITTER.split(parsedMethodAttributes.trim());
			methodAttribute = MethodAttribute.enableFor(attrs);			
		}
	}
	
	/**
	 * Validates, loads and configures the target method[s]
	 * @param source The source (for reporting in any ecxeption thrown)
	 * @param parsedMethodName The method name or pattern
	 * @param parsedMethodAnnotation The method annotation indicator
	 * @param parsedMethodSignature The method signature or pattern
	 * @param parsedMethodInvOptions The method invocation options (from {@link InvocationOption})
	 */
	@SuppressWarnings("unchecked")
	protected void validateTargetMethod(String source, String parsedMethodName, String parsedMethodAnnotation, String parsedMethodSignature, String parsedMethodInvOptions) {
		if(parsedMethodAnnotation!=null) {
			targetMethodAnnotation = "@".equals(parsedMethodAnnotation.trim());
		} else {
			targetMethodAnnotation = false;
		}		
		if(parsedMethodName!=null && !parsedMethodName.trim().isEmpty()) {
			methodName = parsedMethodName.trim(); 
			boolean patternStart = methodName.startsWith("[");
			boolean patternEnd = methodName.endsWith("]");
			if((patternStart && patternEnd) || (!patternStart && !patternEnd)) {
				
				if(patternStart && patternEnd) {
					// This means we're looking at a method expression
					if(targetMethodAnnotation) throw new ShorthandParseFailureException("Cannot combine method annotation and method name expression", source);
					try {
						this.methodNameExpression = Pattern.compile(methodName.substring(1, methodName.length()-1));
						this.methodName = null;
					} catch (Exception ex) {
						throw new ShorthandParseFailureException("Failed to compile method name pattern " + methodName, source);
					}
				} else {
					// This means we're NOT looking at a method expression, so it's either an annotation or a simple method name
					this.methodNameExpression = null;
					if(targetMethodAnnotation) {
						// It's a method annotation
						this.methodName = null;
						try {
							methodAnnotationClass = (Class<? extends Annotation>)Class.forName(methodName);
						} catch (Exception ex) {
							throw new ShorthandParseFailureException("Failed to load method level annotation class [" + methodName + "]", source, ex);
						}
					} else {
						// It's a simple method name
						// unless it's "*"
						if(methodName.equals("*")) {
							this.methodNameExpression = MATCH_ALL;
							this.methodName = null;
						} else {
							methodAnnotationClass = null;
						}
					}
					
				}
			} else {
				throw new ShorthandParseFailureException("Method name [" + methodName + "] seemed to want to be an expression but was missing an opener or closer", source);
			}
		} else {
			this.methodName = null;
			this.methodNameExpression = MATCH_ALL;
		}
	}
	
	
	
	/**
	 * Validates, loads and configures the target class[es]
	 * @param source The source (for reporting in any ecxeption thrown)
	 * @param parsedClassName The target class name
	 * @param parsedClassLoader The classloader expression for the target class
	 * @param parsedAnnotationIndicator The parsed annotation indicator
	 * @param inherritanceIndicator The parsed inherritance indicator
	 */
	protected void validateTargetClass(String source, String parsedClassName, String parsedClassLoader, String parsedAnnotationIndicator, String inherritanceIndicator) {
		String className = parsedClassName.trim();
		if(parsedClassLoader!=null && !parsedClassLoader.trim().isEmpty()) {
			targetClassLoader = ClassLoaderRepository.getInstance().getClassLoader(parsedClassLoader.trim()); 
		} else if(this.classLoaders.containsKey(PREDEF_CL_TARGET)) {
			targetClassLoader = ClassLoaderRepository.getInstance().getClassLoader(classLoaders.get(PREDEF_CL_TARGET)); 
		} else {
			targetClassLoader = ClassLoaderRepository.getInstance().getClassLoader(parsedClassLoader);
		}
		if(targetClassLoader==null) targetClassLoader = Thread.currentThread().getContextClassLoader();
		if(parsedAnnotationIndicator!=null) {
			targetClassAnnotation = "@".equals(parsedAnnotationIndicator.trim());
		} else {
			targetClassAnnotation = false;
		}
		if(inherritanceIndicator!=null) {
			inherritanceEnabled = "+".equals(inherritanceIndicator.trim());
		} else {
			inherritanceEnabled = false;
		}
		if(targetClassAnnotation && inherritanceEnabled) {
			loge("WARNING: Target class was marked as an annotation and for inherritance.");
		}
		try {
			targetClass = Class.forName(className, true, targetClassLoader);
			// If the class is an annotation, we don't want to mark is an interface,
			// although the JVM considers it to be. We want them to be mutually exclusive.
			if(targetClass.isAnnotation()) {
				targetClassAnnotation = true;
				inherritanceEnabled = false;
				targetClassInterface = false;
			} else if(targetClass.isInterface()) {
				targetClassInterface = true;
				inherritanceEnabled = true;
				targetClassAnnotation = false;
			}
		} catch (Exception ex) {
			throw new ShorthandParseFailureException("Failed to locate target class [" + className + "]", source, ex);
		}
	}
	
//	/**
//	 * Resolves the passed metric bitmask expression, determines the applicable metric instances and returns a bitmask enabled for them
//	 * @param bitmaskStr The expression to evaluate
//	 * @return a bitmask for the metrics to enable
//	 * @throws ShorthandInvalidBitMaskException thrown if the expression cannot be interpreted
//	 */
//	public static int resolveBitMask(String bitmaskStr) throws ShorthandInvalidBitMaskException {
//		try {
//			if(bitmaskStr==null || bitmaskStr.isEmpty()) return MetricCollection.getDefaultBitMask();
//			if("*".equalsIgnoreCase(bitmaskStr.trim())) return MetricCollection.getAllEnabledBitMask();
//			if(isNumber(bitmaskStr)) return Integer.parseInt(bitmaskStr);
//			if(bitmaskStr.indexOf(',')!=-1) {
//				try {
//					return MetricCollection.enableFor((Object[])COMMA_SPLITTER.split(bitmaskStr));
//				} catch (Exception ex) {
//					throw new ShorthandInvalidBitMaskException("Invalid bitmask", bitmaskStr, ex);
//				}
//			} 
////			ICollector mc = MetricCollection.forValueOrNull(bitmaskStr);
////			if(mc!=null) return mc.getMask();
////			throw new ShorthandInvalidBitMaskException("Invalid bitmask", bitmaskStr);
//			return -1;
//		} catch (Exception ex) {
//			throw new ShorthandInvalidBitMaskException("Unexpected error interpreting bitmask", bitmaskStr, ex);
//		}
//	}	
	
	/**
	 * Validates that the mandatory fields are not null or empty
	 * @param source The source (for reporting in any ecxeption thrown)
	 * @param fields The fields to validate
	 */
	protected static void validateMandatoryFields(String source, String[] fields) {
		if(fields==null || fields.length < 11) throw new ShorthandParseFailureException("Invalid parsed field count [" + (fields==null ? 0 : fields.length) + "]", source);
		if(fields[IND_TARGETCLASS.ordinal()]==null || fields[IND_TARGETCLASS.ordinal()].trim().isEmpty()) throw new ShorthandParseFailureException("Mandatory field for TARGET_CLASS was null or empty", source);
		//if(fields[IND_METHOD]==null || fields[IND_METHOD].trim().isEmpty()) throw new ShorthandParseFailureException("Mandatory field for TARGET_METHOD was null or empty", source);
		//if(fields[IND_COLLECTORNAME.ordinal()]==null || fields[IND_COLLECTORNAME.ordinal()].trim().isEmpty()) throw new ShorthandParseFailureException("Mandatory field for COLLECTORNAME was null or empty", source);
		//if(fields[IND_BITMASK]==null || fields[IND_BITMASK].trim().isEmpty()) throw new ShorthandParseFailureException("Mandatory field for BITMASK was null or empty", source);
		if(fields[IND_METRICNAME.ordinal()]==null || fields[IND_METRICNAME.ordinal()].trim().isEmpty()) throw new ShorthandParseFailureException("Mandatory field for METRICNAME was null or empty", source);
	}
	
	
	/**
	 * Validates that the passed string value is an int
	 * @param s The string value to check
	 * @return true if the passed string value is an int, false otherwise
	 */
	@SuppressWarnings("unused")
	private static boolean isNumber(CharSequence s) {
		try {
			Integer.parseInt(s.toString().trim());
			return true;
		} catch (Exception ex) {
			return false;
		}
	}	
	


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandScriptMBean#getTargetClass()
	 */
	@Override
	public Class<?> getTargetClass() {
		return targetClass;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandScriptMBean#isTargetClassAnnotation()
	 */
	@Override
	public boolean isTargetClassAnnotation() {
		return targetClassAnnotation;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandScriptMBean#isInherritanceEnabled()
	 */
	@Override
	public boolean isInherritanceEnabled() {
		return inherritanceEnabled;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandScriptMBean#getMethodName()
	 */
	@Override
	public String getMethodName() {
		return methodName;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandScriptMBean#getMethodNameExpression()
	 */
	@Override
	public Pattern getMethodNameExpression() {
		return methodNameExpression;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandScriptMBean#getMethodSignature()
	 */
	@Override
	public String getMethodSignature() {
		return methodSignature;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandScriptMBean#getMethodSignatureExpression()
	 */
	@Override
	public Pattern getMethodSignatureExpression() {
		return methodSignatureExpression;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandScriptMBean#isTargetMethodAnnotation()
	 */
	@Override
	public boolean isTargetMethodAnnotation() {
		return targetMethodAnnotation;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandScriptMBean#getMethodInvocationOption()
	 */
	@Override
	public int getMethodInvocationOption() {
		return methodInvocationOption;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandScriptMBean#getMethodAttribute()
	 */
	@Override
	public int getMethodAttribute() {
		return methodAttribute;
	}


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandScriptMBean#getMetricNameTemplate()
	 */
	@Override
	public String getMetricNameTemplate() {
		return metricNameTemplate;
	}

	/**
	 * Simple err formatted logger
	 * @param fmt The format of the message
	 * @param args The message arguments
	 */
	public static void loge(Object fmt, Object...args) {
		System.err.println(String.format(fmt.toString(), args));
	}
	
	/**
	 * Simple err formatted logger
	 * @param fmt The format of the message
	 * @param t The throwable to print stack trace for
	 * @param args The message arguments
	 */
	public static void loge(String fmt, Throwable t, Object...args) {
		System.err.println(String.format(fmt, args));
		t.printStackTrace(System.err);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandScriptMBean#isTargetClassInterface()
	 */
	@Override
	public boolean isTargetClassInterface() {
		return targetClassInterface;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandScriptMBean#isAllowReentrant()
	 */
	@Override
	public boolean isAllowReentrant() {
		return allowReentrant;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandScriptMBean#isBatchTransform()
	 */
	@Override
	public boolean isBatchTransform() {
		return batchTransform;
	}


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandScriptMBean#isResidentTransformer()
	 */
	@Override
	public boolean isResidentTransformer() {
		return residentTransformer;
	}
	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandScriptMBean#isDisableOnTrigger()
	 */
	@Override
	public boolean isDisableOnTrigger() {
		return disableOnTrigger;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandScriptMBean#isStartDisabled()
	 */
	@Override
	public boolean isStartDisabled() {
		return startDisabled;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandScriptMBean#getTargetClassLoader()
	 */
	@Override
	public ClassLoader getTargetClassLoader() {
		return targetClassLoader;
	}


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandScriptMBean#getMethodAnnotation()
	 */
	@Override
	public Class<? extends Annotation> getMethodAnnotation() {
		return methodAnnotationClass;
	}	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandScriptMBean#getMeasurementBitMask()
	 */
	@Override
	public int getMeasurementBitMask() {		
		return measurementBitMask;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandScriptMBean#getSubMetricsBitMask()
	 */
	@Override
	public int getSubMetricsBitMask() {	
		return subMetricBitMask;
	}
}
