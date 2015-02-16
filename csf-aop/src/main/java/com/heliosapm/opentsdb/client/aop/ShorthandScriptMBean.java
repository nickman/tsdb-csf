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

import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;



/**
 * <p>Title: ShorthandScriptMBean</p>
 * <p>Description: JMX MBean interface for {@link ShorthandScript}</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.ShorthandScriptMBean</code></p>
 */

public interface ShorthandScriptMBean {
	/**
	 * Returns a map of sets of members (methods and constructors) targetted for instrumentation, keyed by the classes they are declared in.
	 * @return a map of sets of members keyed by the declaring class
	 */
	public abstract Map<Class<?>, Set<Member>> getTargetMembers();

	/**
	 * Locates the targetted classes and returns them in a set
	 * @return a set of target classes
	 */
	public abstract Set<Class<?>> getTargetClasses();

	/**
	 * Returns the 
	 * @return the targetClass
	 */
	public abstract Class<?> getTargetClass();

	/**
	 * Returns the 
	 * @return the targetClassIsAnnotation
	 */
	public abstract boolean isTargetClassAnnotation();

	/**
	 * Returns the 
	 * @return the inherritanceEnabled
	 */
	public abstract boolean isInherritanceEnabled();

	/**
	 * Returns the 
	 * @return the methodName
	 */
	public abstract String getMethodName();

	/**
	 * Returns the 
	 * @return the methodNameExpression
	 */
	public abstract Pattern getMethodNameExpression();

	/**
	 * Returns the 
	 * @return the methodSignature
	 */
	public abstract String getMethodSignature();

	/**
	 * Returns the 
	 * @return the methodSignatureExpression
	 */
	public abstract Pattern getMethodSignatureExpression();

	/**
	 * Returns the 
	 * @return the targetMethodIsAnnotation
	 */
	public abstract boolean isTargetMethodAnnotation();

	/**
	 * Returns the 
	 * @return the methodInvocationOption
	 */
	public abstract int getMethodInvocationOption();

	/**
	 * Returns the 
	 * @return the methodAttribute
	 */
	public abstract int getMethodAttribute();

	/**
	 * Returns the 
	 * @return the enumIndex
	 */
	public abstract int getEnumIndex();

	/**
	 * Returns the 
	 * @return the bitMask
	 */
	public abstract int getBitMask();

	/**
	 * Returns the template to build the metric name from
	 * @return the methodTemplate
	 */
	public abstract String getMetricNameTemplate();

	/**
	 * Returns the 
	 * @return the targetClassInterface
	 */
	public abstract boolean isTargetClassInterface();

	/**
	 * Indicates if the instrumentation injected into this method will remain active during reentrant calls
	 * @return true if the instrumentation remains active, false otherwise
	 */
	public abstract boolean isAllowReentrant();

	/**
	 * Indicates if the instrumentation should batch transform (see {@link InvocationOption#TRANSFORMER_BATCH}) 
	 * @return true for batch transform, false otherwise
	 */
	public abstract boolean isBatchTransform();

	/**
	 * Indicates if the instrumentation's classfile transformer should stay resident (see {@link InvocationOption#TRANSFORMER_RESIDENT})
	 * @return true for resident, false otherwise
	 */
	public abstract boolean isResidentTransformer();

	/**
	 * Indicates if all intrumentation should be disabled for the current thread until this method exits
	 * @return true to disable instrumentation for the current thread until this method exits, false otherwise
	 */
	public abstract boolean isDisableOnTrigger();

	/**
	 * Indicates if the instrumentation for this method shold start disabled 
	 * @return true if the instrumentation for this method shold start disabled
	 */
	public abstract boolean isStartDisabled();

	/**
	 * Returns the target class classloader
	 * @return the target class classloader
	 */
	public abstract ClassLoader getTargetClassLoader();

	/**
	 * Returns the method level annotation classloader
	 * @return the method level annotation classloader
	 */
	public abstract ClassLoader getMethodAnnotationClassLoader();

	/**
	 * Returns the enum collector class classloader
	 * @return the enum collector class classloader
	 */
	public abstract ClassLoader getEnumCollectorClassLoader();

	/**
	 * Returns the target method level annotation class
	 * @return the target method level annotation class
	 */
	public abstract Class<? extends Annotation> getMethodAnnotation();

}
