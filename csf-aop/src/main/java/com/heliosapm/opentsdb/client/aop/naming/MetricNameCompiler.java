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
package com.heliosapm.opentsdb.client.aop.naming;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.Modifier;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

/**
 * <p>Title: MetricNameCompiler</p>
 * <p>Description: The javassist compiler to convert a metric template expression to a metric name</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.naming.MetricNameCompiler</code></p>
 */
public class MetricNameCompiler {
	
	private static final AtomicLong classSerial = new AtomicLong();
			
	private static final RemovalListener<Class<?>, Cache<Member, Cache<String, MetricNameProvider>>> classRemovalListener
	 = new RemovalListener<Class<?>, Cache<Member, Cache<String, MetricNameProvider>>>() {
			@Override
			public void onRemoval(RemovalNotification<Class<?>, Cache<Member, Cache<String, MetricNameProvider>>> notification) {
				log("MetricNameProviders Expiration: Class:[%s] Cause:[%s]", notification.getKey().getName(), notification.getCause().name());
			}
		};
		
	private static final RemovalListener<Member, Cache<String, MetricNameProvider>> methodRemovalListener
	 = new RemovalListener<Member, Cache<String, MetricNameProvider>>() {
			@Override
			public void onRemoval(RemovalNotification<Member, Cache<String, MetricNameProvider>> notification) {
				final Member member = notification.getKey();
				final String genericString = (member instanceof Constructor) ? ((Constructor)member).toGenericString() : ((Method)member).toGenericString();
				log("MetricNameProviders Expiration: Member:[%s] Cause:[%s]", genericString, notification.getCause().name());
			}
		};
		
	private static final RemovalListener<String, MetricNameProvider> providerRemovalListener
	 = new RemovalListener<String, MetricNameProvider>() {
			@Override
			public void onRemoval(RemovalNotification<String, MetricNameProvider> notification) {
				log("MetricNameProviders Expiration: Expression:[%s] Cause:[%s]", notification.getKey(), notification.getCause().name());
			}
		};
	
	private static final Cache<Class<?>, Cache<Member, Cache<String, MetricNameProvider>>> pCache = CacheBuilder.newBuilder()
			.weakKeys().removalListener(classRemovalListener).build();
	
	private static final CtClass[] EMPTY_ARR = {};
	
	public static String[] getMetricNameCodePoints(Class<?> clazz, Member member, String metricNameExpression) {
		if(clazz==null) throw new IllegalArgumentException("The passed class was null");
		if(member==null) throw new IllegalArgumentException("The passed member was null");
		if(metricNameExpression==null || metricNameExpression.trim().isEmpty())  throw new IllegalArgumentException("The passed metricNameExpression was null or empty");
		if(member instanceof Field) throw new java.lang.UnsupportedOperationException("Field members are not supported");
		String[] codePoints = null;
		StringBuffer b = new StringBuffer();
		Matcher matcher = MetricNamingToken.ALL_PATTERNS.matcher(metricNameExpression);
		StringBuilder javassistExpressions = new StringBuilder();
		while(matcher.find()) {
			String matchedPattern = matcher.group(0);
			MetricNamingToken token = MetricNamingToken.matchToken(matcher.group(0));
			String[] replacers = token.extractor.getStringReplacement(matchedPattern, clazz, member);
			log("Replacers %s", Arrays.toString(replacers));
			matcher.appendReplacement(b, replacers[0]);
			if(token.runtime) {
				javassistExpressions.append(replacers[1]).append(",");
			}				
		}
		matcher.appendTail(b);
		if(javassistExpressions.length()>0) {
			codePoints = new String[2];
			javassistExpressions.deleteCharAt(javassistExpressions.length()-1);
			codePoints[1] = javassistExpressions.toString(); 
		} else {
			codePoints = new String[1];
		}
		codePoints[0] = b.toString();
		return codePoints;
	}
	
	
	private static MetricNameProvider compile(final Class<?> clazz, final Member member, final String metricNameExpression) {
		ClassPool classPool = new ClassPool(true);
		String className = clazz.getName() + "." + member.getName() + "MetricNameProvider" + classSerial.incrementAndGet();
		try {
			CtClass ctClass = classPool.makeClass(className);
			ctClass.addInterface(classPool.get(MetricNameProvider.class.getName()));
			CtMethod ctMethod = new CtMethod(classPool.get(String.class.getName()), "getMetricName", EMPTY_ARR, ctClass);
			CtMethod ctStaticMethod = new CtMethod(classPool.get(String.class.getName()), "metricName", EMPTY_ARR, ctClass);
			ctStaticMethod.setModifiers(ctStaticMethod.getModifiers() | Modifier.STATIC);
			StringBuffer b = new StringBuffer();
			Matcher matcher = MetricNamingToken.ALL_PATTERNS.matcher(metricNameExpression);
			List<String> javassistExpressions = new ArrayList<String>();
			while(matcher.find()) {
				String matchedPattern = matcher.group(0);
				log("Matched Pattern [%s]", matchedPattern);
				MetricNamingToken token = MetricNamingToken.matchToken(matcher.group(0));
				String[] replacers = token.extractor.getStringReplacement(matchedPattern, clazz, member);
				log("Replacers %s", Arrays.toString(replacers));
				matcher.appendReplacement(b, replacers[0]);
				if(token.runtime) {
					javassistExpressions.add(replacers[1]);
				}				
			}
			matcher.appendTail(b);
			if(javassistExpressions.isEmpty()) {
				ctMethod.setBody(String.format("{return \"%s\";}", b.toString()));
				ctStaticMethod.setBody(String.format("{return \"%s\";}", b.toString()));
				log("Stat Source: [%s]", b);
			} else {
				StringBuilder src = new StringBuilder("{ return String.format(\"").append(b.toString()).append("\",");
				for(String x: javassistExpressions) {
					src.append(x).append(",");
				}
				src.deleteCharAt(src.length()-1);
				src.append(");}");
				log("Runtime Source: [%s]", src);
				ctMethod.setBody(src.toString());
				ctStaticMethod.setBody(src.toString());
			}
			ctClass.addMethod(ctMethod);
			ctClass.addMethod(ctStaticMethod);
			ctClass.writeFile(System.getProperty("java.io.tmpdir"));
			return (MetricNameProvider)ctClass.toClass().newInstance();
		} catch (Exception ex) {
			throw new RuntimeException("Failed to compile [" + className + "]", ex);
		}
		
	}
	
	/**
	 * Attempts to locate the MetricNameProvider for the passed class, method and expression
	 * @param clazz The target class
	 * @param member The target method or constructor (fields not supported)
	 * @param metricNameExpression The metric name expression
	 * @return the matching MetricNameProvider or null if one was not found
	 */
	public static MetricNameProvider getMetricNameProvider(final Class<?> clazz, final Member member, final String metricNameExpression) {
		try {
			return pCache.get(clazz, new Callable<Cache<Member,Cache<String,MetricNameProvider>>>() {
				public Cache<Member,Cache<String,MetricNameProvider>> call() {
					return CacheBuilder.newBuilder().weakKeys().removalListener(methodRemovalListener).build();
				}
			}).get(member, new Callable<Cache<String,MetricNameProvider>>() {
				public Cache<String, MetricNameProvider> call() {
					return CacheBuilder.newBuilder().weakKeys().removalListener(providerRemovalListener).build();
				}			
			}).get(metricNameExpression, new Callable<MetricNameProvider>() {
				@Override
				public MetricNameProvider call() throws Exception {
					return compile(clazz, member, metricNameExpression);
				}
			});
		} catch (ExecutionException eex) {
			throw new RuntimeException("Failed to acquire MetricNameProvider", eex);
		}
	}
	
	/**
	 * Simple out formatted logger
	 * @param fmt The format of the message
	 * @param args The message arguments
	 */
	public static void log(String fmt, Object...args) {
		System.out.println(String.format(fmt, args));
	}	
}
