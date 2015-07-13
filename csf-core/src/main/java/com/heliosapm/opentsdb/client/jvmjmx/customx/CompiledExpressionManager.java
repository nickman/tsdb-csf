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
package com.heliosapm.opentsdb.client.jvmjmx.customx;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Permissions;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.LongMemberValue;
import javassist.bytecode.annotation.StringMemberValue;
import javassist.compiler.CompileError;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Node;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.heliosapm.opentsdb.client.name.AgentName;
import com.heliosapm.opentsdb.client.opentsdb.ConfigurationReader;
import com.heliosapm.opentsdb.client.opentsdb.Constants;
import com.heliosapm.utils.xml.XMLHelper;

/**
 * <p>Title: CompiledExpressionManager</p>
 * <p>Description: The compiler and manager for CompiledExpressions.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.CompiledExpressionManager</code></p>
 */

public class CompiledExpressionManager  implements RemovalListener<String, CompiledExpression> {
	/** The singleton instance */
	private static volatile CompiledExpressionManager instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();

	/** The metric name template field name */
	public static final String METRICTEMPL_FIELD_NAME = "METRIC_NAME_TEMPLATE";
	/** The metric value template field name */
	public static final String VALUETEMPL_FIELD_NAME = "METRIC_VALUE_TEMPLATE";
	
	/** The name of the directory within the agent home where we'll write the transient byte code to */
	public static final String BYTE_CODE_DIR = ".bytecode";
	/** The name of the directory where we'll write the transient byte code to if things don't work out with the agent home */
	public static final String STANDBY_BYTE_CODE_DIR = System.getProperty("java.io.tmpdir") + File.separator + ".tsdb-aop" + File.separator + Constants.SPID; 
	/** Decodes primitive classes to the javassist CtClass equivalent */
	public static final Map<Class<?>, CtClass> PRIMITIVES;
	/** The serial number applied to each generated class */
	private final AtomicLong compiledExpressionSerial = new AtomicLong(100L);
	/** The byte code persistence root directory */
	private final String byteCodeDir;
	/** The shared classpool */
	private final ClassPool cp = new ClassPool();
	/** The CompiledExpression interface ctclass */
	private final CtClass CompiledExpressionCtClass;
	/** The AbstractCompiledExpression interface ctclass */
	private final CtClass AbstractCompiledExpressionCtClass;
	/** The String ctclass */
	private final CtClass StringCtClass;
	/** The Class ctclass */
	private final CtClass ClassCtClass;
	/** The CollectionContext javassist class */
	private final CtClass CollectionContextCtClass;
	/** The Object ctclass */
	private final CtClass ObjectCtClass;
	/** The jmx collection classpath */
	private final ClassPath expCompilerClassPath = new ClassClassPath(getClass());
	/** The doTrace method */
//	private final CtMethod doTraceCtMethod;	
	/** The context trace method */
	private final CtMethod traceCtMethod;
	
	/** The byte code persistence file */
	private final File targetDir;
	/** The protection domain permissions */
	private final Permissions permissions = new Permissions();
	/** The code source for the protection domain */
	private final CodeSource cs;
	/** The protection domain used for compiled classes */
	private final ProtectionDomain pd;
	
	
	
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
	
	
	/** Instance logger */
	private final Logger log = LogManager.getLogger(getClass());
	/** The classloader cache */
	private final Cache<String, CompiledExpression> expressionCache = CacheBuilder.newBuilder()
			.concurrencyLevel(Constants.CORES)
			.initialCapacity(128)
			.recordStats()
			.weakValues()
			.removalListener(this)
			.build();

	private static final String EXPR_DELIM = "#@#";
	/** The token expression matcher */
	public static final Pattern TOKEN_EXPR_PATTERN = Pattern.compile("\\$(.*?)\\{(.*?)\\}");
	

	
	/**
	 * Returns the CompiledExpressionManager singleton instance
	 * @return the CompiledExpressionManager singleton instance
	 */
	public static CompiledExpressionManager getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new CompiledExpressionManager();
				}
			}
		}
		return instance;
	}
	
	/**
	 * Creates a new CompiledExpressionManager
	 */
	private CompiledExpressionManager() {
		try {			
			byteCodeDir = ConfigurationReader.conf(Constants.PROP_OFFLINE_DIR, Constants.DEFAULT_OFFLINE_DIR) + File.separator + AgentName.appName() + File.separator + BYTE_CODE_DIR;
			targetDir = new File(byteCodeDir);
			permissions.add(new AllPermission());
			cs = new CodeSource(targetDir.toURI().toURL(), (Certificate[])null);
			pd = new ProtectionDomain(cs, permissions);			
			cp.appendSystemPath();
			cp.appendClassPath(expCompilerClassPath);
			StringCtClass = cp.get(String.class.getName());
			ObjectCtClass = cp.get(Object.class.getName());
			ClassCtClass = cp.get(Class.class.getName());
			CollectionContextCtClass = cp.get(CollectionContext.class.getName());
			traceCtMethod = CollectionContextCtClass.getDeclaredMethod("trace");
			CompiledExpressionCtClass = cp.get(CompiledExpression.class.getName());
			AbstractCompiledExpressionCtClass = cp.get(AbstractCompiledExpression.class.getName());
//			doTraceCtMethod = AbstractCompiledExpressionCtClass.getDeclaredMethod("doTrace");
			final File collectorDir = new File(byteCodeDir + File.separator + getClass().getPackage().getName().replace('.', File.separatorChar));
			if(collectorDir.exists() && collectorDir.isDirectory()) {
				loadPersisted(collectorDir);
			} else {
				log.warn("No dir found for [{}]", collectorDir);
			}
			log.info("Created CompiledExpressionManager");
		} catch (Exception ex) {
			throw new RuntimeException("Failed to initialize the Compiled Expression Manager", ex);
		}
		
		
	}
	
	
	/**
	 * Loads any cached persisted classes
	 * @param collectorDir The directory where the compiled expression classes will be
	 */
	private void loadPersisted(final File collectorDir) {
		final long startTime = System.currentTimeMillis();
		long maxSerial = Long.MIN_VALUE;
		for(File classFile: collectorDir.listFiles()) {
			if(classFile.getName().endsWith(".class")) {
				BufferedInputStream bis = null;
				FileInputStream fis = null;
				boolean errored = false;
				try {
					fis = new FileInputStream(classFile);
					bis = new BufferedInputStream(fis, (int)classFile.length());
					final CtClass ctClass = cp.makeClassIfNew(bis);
					if(ctClass.getSuperclass().equals(AbstractCompiledExpressionCtClass)) {
						for(Object o: ctClass.getAvailableAnnotations()) {
//							log.info("Class [{}], Annotation Type [{}]", ctClass.getSimpleName(), o.getClass().getName());
							if(o instanceof CompExpr) {
								final CompExpr ce = (CompExpr)o;
//								log.info("CompExpr: id[{}], name[{}], value[{}], script[{}]", ce.serial(), ce.name(), ce.value(), ce.script()); 
								final String key = ce.name() + EXPR_DELIM + ce.value() + EXPR_DELIM + ce.script();
								if(expressionCache.asMap().containsKey(key)) {
									log.warn("Dropping duplicate CompiledExpression [{}]", ctClass.getName());
									errored = true;
									continue; 
								}
								final Class<CompiledExpression> clazz = ctClass.toClass(getClass().getClassLoader(), pd);
								final CompiledExpression compiledExpr = clazz.newInstance();
								expressionCache.put(key, compiledExpr);
								log.info("\tCached Class [{}] with key [{}]", clazz.getSimpleName(), key);
								if(ce.serial() > maxSerial) {
									maxSerial = ce.serial();
								}
							}
						}
					}
				} catch (Exception ex) {
					errored = true;
					log.warn("Failed to load persisted CE", ex);
				} finally {
					if(bis!=null) try { bis.close(); } catch (Exception x) {/* No Op */}
					if(fis!=null) try { fis.close(); } catch (Exception x) {/* No Op */}
					if(errored) {
						classFile.delete();
					}
				}
				
			}
		}
		if(maxSerial > Long.MIN_VALUE) {
			compiledExpressionSerial.set(maxSerial);
		}
		final long elapsed = System.currentTimeMillis() - startTime;
		log.info("Loaded [{}] cached CompiledExpressions in [{}] ms", expressionCache.size(), elapsed);
	}

	/**
	 * Returns the CompiledExpression for the passed trace node
	 * @param traceNode The XML node representing the trace directive
	 * @return the CompiledExpression
	 */
	public CompiledExpression getCompiledExpression(final Node traceNode) {
		if(traceNode==null) throw new IllegalArgumentException("The passed trace node was null");
		final String nodeText = XMLHelper.renderNode(traceNode, true);
		final String innerText = XMLHelper.getNodeTextValue(traceNode, null);
		final String valueExpression = XMLHelper.getAttributeByName(traceNode, "value", "");
		final String valueProcessorScript = XMLHelper.getAttributeByName(traceNode, "vscript", "");
		if(!"trace".equalsIgnoreCase(traceNode.getNodeName())) throw new IllegalArgumentException("The passed node was not a valid trace: [" + nodeText + "]");
		if(innerText==null || innerText.trim().isEmpty()) throw new IllegalArgumentException("The passed node was not a valid trace: [" + nodeText + "]");
		final String key = innerText + EXPR_DELIM + valueExpression + EXPR_DELIM + valueProcessorScript;
		log.info("\tFetching CE for key [{}], inCache:[{}]", key, expressionCache.asMap().containsKey(key));
		try {
			return expressionCache.get(key, new Callable<CompiledExpression>(){
				@Override
				public CompiledExpression call() throws Exception {
					return buildExpression(innerText, valueExpression, valueProcessorScript);
				}
			});
		} catch (Exception ex) {
			log.error("Failed to get CompiledExpression for trace node [" + traceNode + "]", ex);
			throw new RuntimeException("Failed to get CompiledExpression for trace node [" + traceNode + "]", ex);
		}		
	}
	
	public CompiledExpression getCompiledExpression(final String name, final String value, final String script) {
		if(name==null || name.trim().isEmpty()) throw new IllegalArgumentException("The passed name was null or empty");
		final String innerText = name.trim();
		final String valueExpression = (value==null || value.trim().isEmpty()) ? "" : value.trim();
		final String valueProcessorScript = (script==null || script.trim().isEmpty()) ? "" : script.trim();
		final String key = innerText + EXPR_DELIM + valueExpression + EXPR_DELIM + valueProcessorScript;
		log.info("\tFetching CE for key [{}], inCache:[{}]", key, expressionCache.asMap().containsKey(key));
		try {
			return expressionCache.get(key, new Callable<CompiledExpression>(){
				@Override
				public CompiledExpression call() throws Exception {
					return buildExpression(innerText, valueExpression, valueProcessorScript);
				}
			});
		} catch (Exception ex) {
			log.error("Failed to get CompiledExpression for trace node [" + key + "]", ex);
			throw new RuntimeException("Failed to get CompiledExpression for trace node [" + key + "]", ex);
		}				
	}
	
	public static void main(String[] args) {
		CompiledExpressionManager cem = getInstance(); 
//		cem.getCompiledExpression("$ATTRV{}:foo=$OND{},bar=$SCR{gitem.js}", null, null);
		cem.buildExpression("$ATTRV{}:foo=$OND{},bar=$SCR{gitem.js}", null, null);
	}
	
//	ATTRV(new AttributeValueTokenResolver()),
//	ATTRN(new AttributeNameTokenResolver()),
//	OP(new MBeanOperationTokenResolver()),
//	OND(new ObjectNameDomainTokenResolver()),
//	ONK(new ObjectNameKeyPropertyTokenResolver()),
//	ON(new ObjectNamePropertyExpansionTokenResolver()),
//	SCR(new ScriptInvocationTokenResolver()),
//	DESCR(new DescriptorValueTokenResolver());

	
	
	
	// IMPLEMENT:  		
//			protected Object getValue(final CollectionContext ctx) {
//			protected abstract String getMetricFQN(final CollectionContext ctx);
	
	private CompiledExpression buildExpression(final String metricExpression, final String valueExpression, final String valueProcessorScript) {
		try {
			final Map<Integer, String> metricCode = resolve(metricExpression);
			final Map<Integer, String> valueCode = (valueExpression==null || valueExpression.trim().isEmpty()) ? null : resolve(valueExpression);
			final String metricTemplate = metricCode.remove(-1);
			final String valueTemplate = valueCode==null ? null : valueCode.remove(-1);
			final StringBuilder b = new StringBuilder("{\n");
			final long clazzId = this.compiledExpressionSerial.incrementAndGet();
			final String ctClassName = CompiledExpression.class.getName() + "Impl" + clazzId;
			final CtClass implCtClass = cp.makeClass(ctClassName, AbstractCompiledExpressionCtClass);
			
//			implCtClass.setModifiers(implCtClass.getModifiers() | Modifier.FINAL);
			addTemplateField(implCtClass, METRICTEMPL_FIELD_NAME, metricTemplate);
			addTemplateField(implCtClass, VALUETEMPL_FIELD_NAME, valueTemplate);
			b.append("String tmpl = new String(").append(METRICTEMPL_FIELD_NAME).append(");\n");
			for(Map.Entry<Integer, String> entry: metricCode.entrySet()) {
				final int tokenKey = entry.getKey();
				final String code = entry.getValue();
				final String token = "###" + tokenKey + "###";
				final CtMethod getFragMethod = new CtMethod(ObjectCtClass, "getFragment" + tokenKey, new CtClass[] {this.CollectionContextCtClass}, implCtClass);
				implCtClass.addMethod(getFragMethod);
//				log.info("frag: [{}], code: [{}]", getFragMethod.getName(), code);
				getFragMethod.setBody("{" + code + "}");		
				
				// subst(final String token, final String working, final String value)
				b.append("\ttmpl = subst(\"").append(token).append("\", tmpl, getFragment").append(tokenKey).append("($1));\n");				
			}
			b.append("return tmpl;\n}");
			log.info("CodeGen:\n{}", b.toString());
			final CtMethod getMetricFQNMethod = new CtMethod(StringCtClass, "getMetricFQN", new CtClass[] {this.CollectionContextCtClass}, implCtClass);
			implCtClass.addMethod(getMetricFQNMethod);
			getMetricFQNMethod.setBody(b.toString());
			implCtClass.setModifiers(implCtClass.getModifiers() & ~Modifier.ABSTRACT);
			implCtClass.setModifiers(implCtClass.getModifiers() | Modifier.FINAL);
			final ConstPool constpool = implCtClass.getClassFile2().getConstPool();
			final Annotation annot = new Annotation(CompExpr.class.getName(), constpool);
			final AnnotationsAttribute attr = new AnnotationsAttribute(constpool, AnnotationsAttribute.visibleTag);
			annot.addMemberValue("name", new StringMemberValue(metricExpression.trim(), constpool));
			annot.addMemberValue("serial", new LongMemberValue(clazzId, constpool));
			if(valueExpression!=null && valueExpression.trim()!=null) {
				annot.addMemberValue("value", new StringMemberValue(valueExpression.trim(), constpool));
			}
			if(valueProcessorScript!=null && valueProcessorScript.trim()!=null) {
				annot.addMemberValue("script", new StringMemberValue(valueProcessorScript.trim(), constpool));
			}
			attr.addAnnotation(annot);		
			implCtClass.getClassFile().addAttribute(attr);
			
			implCtClass.writeFile(byteCodeDir);
			log.info("Wrote class [{}] to ByteCodeDir [{}]", implCtClass.getSimpleName(), byteCodeDir);
			//  TODO:  only do this once
			final Class<CompiledExpression> clazz = implCtClass.toClass(getClass().getClassLoader(), pd);
			final CompiledExpression ce = clazz.newInstance();
			log.info("Instantiated [{}]", ce.getClass().getName());
			return ce;
		} catch (Exception ex) {
			if(ex instanceof CannotCompileException) {
				CannotCompileException cce = (CannotCompileException)ex;
				log.error("Failed to compile expression. \n\tx: [{}]\n\tr: [{}]\n\te:[{}]", cce.getMessage(), cce.getReason(), cce.getCause().getClass().getName());
				final CompileError ce = (CompileError) cce.getCause();
				log.error("Failed to compile error. \n\tx: [{}]\n\tlex: [{}]\n\te:[{}]", ce.getMessage(), ce.getLex(), ce.getCause()==null ? "None" : ce.getCause().getClass().getName());				
			}
			throw new RuntimeException("Failed to compile expression [" + metricExpression + "] / [" + valueExpression + "]", ex);
		}
	}
	
	/**
	 * Adds a constant template string field to the impl class
	 * @param ctClass The impl class
	 * @param name The field name
	 * @param value The field value
	 * @throws CannotCompileException thrown on compilation error
	 */
	protected void addTemplateField(final CtClass ctClass, final String name, final String value) throws CannotCompileException {
		if(value==null) return;
		final CtField templateField = new CtField(this.StringCtClass, name, ctClass);
		ctClass.addField(templateField, CtField.Initializer.constant(value.trim()));
		templateField.setModifiers(templateField.getModifiers() | Modifier.FINAL);
		templateField.setModifiers(templateField.getModifiers() | Modifier.STATIC);
		templateField.setModifiers(templateField.getModifiers() | Modifier.PRIVATE);
		
	}
	
	
	protected Map<Integer, String> resolve(final String expression) {
		final Matcher m = TOKEN_EXPR_PATTERN.matcher(expression);
		final Map<Integer, String> codeFragments = new TreeMap<Integer, String>();		
		int cnt = 0;
		final StringBuffer b = new StringBuffer();
		while(m.find()) {
			final String tokenerName = m.group(1);
			final Tokener t = Tokener.forName(tokenerName);
			final TokenResolver tr = t.resolver;
			final String tokenerArgs = m.group(2);
			final String jcode = tr.resolve(tokenerArgs);
			final String key = "###" + cnt + "###";
			m.appendReplacement(b, key);
//			template = template.replaceAll(Matcher.quoteReplacement(m.group(0)), key);
			codeFragments.put(cnt, jcode);
			cnt++;
		}
		m.appendTail(b);
//		log.info("Template [{}]", b.toString());
		codeFragments.put(-1, b.toString());
		return codeFragments;
	}
	

	/**
	 * {@inheritDoc}
	 * @see com.google.common.cache.RemovalListener#onRemoval(com.google.common.cache.RemovalNotification)
	 */
	@Override
	public void onRemoval(RemovalNotification<String, CompiledExpression> notification) {
		// TODO Auto-generated method stub
		
	}

}
