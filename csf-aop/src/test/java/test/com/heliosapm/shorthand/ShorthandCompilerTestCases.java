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
package test.com.heliosapm.shorthand;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import test.com.heliosapm.base.BaseTest;

import com.heliosapm.opentsdb.client.aop.ShorthandScript;

/**
 * <p>Title: ShorthandCompilerTestCases</p>
 * <p>Description: Test cases for the Shorthand compiler</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.shorthand.ShorthandCompilerTestCases</code></p>
 */
@SuppressWarnings("static-method")
public class ShorthandCompilerTestCases extends BaseTest {
	
	/**
	 * Attributes to test
	targetClass=class java.lang.Object
	targetClassAnnotation:false
	targetClassLoader:sun.misc.Launcher$ExtClassLoader@3ad6a0e0
	targetClassInterface:false
	inherritanceEnabled:true
	methodName:null
	methodNameExpression:eq.*|to.*
	methodSignature:null
	methodSignatureExpression:.*
	targetMethodAnnotation:false
	methodAnnotationClass:null
	methodAttribute:[PUBLIC]
	methodInvocationOption:[TRANSFORMER_BATCH]
	measurements:[ELAPSED, RETURN, RETURNRATE]
	subMetrics:[gauge, count, hcount, median, mcount, mean_rate]
	metricNameTemplate:java/lang/Object
	allowReentrant:false
	disableOnTrigger:false
	startDisabled:false
	batchTransform:false
	residentTransformer:true
	classLoaders:[]

	 */
	
	// "java.lang.Object+<-SYSTEM.PARENT [eq.*|to.*] 'java/lang/Object'"


	/**
	 * Tests a simple class and method locator
	 * @throws Exception thrown on any error
	 */	
	@Test
	public void testSimpleClassMethod() throws Exception {
		final Map<Class<?>, Set<Member>> members = ShorthandScript.parse("java.lang.Object hashCode 'X'").getTargetMembers();
		Assert.assertEquals("Invalid number of classes in map", 1, members.size());
		final Class<?> clazz = members.keySet().iterator().next();
		Assert.assertEquals("Incorrect class as member key", Object.class, clazz);
		final Set<Member> mset = members.get(clazz);
		Assert.assertNotNull("The member set was null", mset);
		Assert.assertEquals("Invalid number of members in set", 1, mset.size());
		final Member member = mset.iterator().next();
		Assert.assertEquals("The member was not a method", Method.class, member.getClass());
		Assert.assertEquals("The method name was unexpected", "hashCode", member.getName());
	}
	
	/**
	 * Tests a simple class and method locator with a signature
	 * @throws Exception thrown on any error
	 */	
	@Test
	public void testSimpleClassMethodWithSig() throws Exception {
		final Map<Class<?>, Set<Member>> members = ShorthandScript.parse("java.lang.Object equals (Ljava/lang/Object;) 'X'").getTargetMembers();
		Assert.assertEquals("Invalid number of classes in map", 1, members.size());
		final Class<?> clazz = members.keySet().iterator().next();
		Assert.assertEquals("Incorrect class as member key", Object.class, clazz);
		final Set<Member> mset = members.get(clazz);
		Assert.assertNotNull("The member set was null", mset);
		Assert.assertEquals("Invalid number of members in set", 1, mset.size());
		final Member member = mset.iterator().next();
		Assert.assertEquals("The member was not a method", Method.class, member.getClass());
		Assert.assertEquals("The method name was unexpected", "equals", member.getName());
	}
	
	/**
	 * Tests a simple class and method locator with an attribute
	 * @throws Exception thrown on any error
	 */	
	@Test
	public void testSimpleClassMethodWithAttr() throws Exception {
		final Map<Class<?>, Set<Member>> members = ShorthandScript.parse("java.lang.Object hashCode [PUBLIC] 'X'").getTargetMembers();
		Assert.assertEquals("Invalid number of classes in map", 1, members.size());
		final Class<?> clazz = members.keySet().iterator().next();
		Assert.assertEquals("Incorrect class as member key", Object.class, clazz);
		final Set<Member> mset = members.get(clazz);
		Assert.assertNotNull("The member set was null", mset);
		Assert.assertEquals("Invalid number of members in set", 1, mset.size());
		final Member member = mset.iterator().next();
		Assert.assertEquals("The member was not a method", Method.class, member.getClass());
		Assert.assertEquals("The method name was unexpected", "hashCode", member.getName());
	}
	
	/**
	 * Tests a simple class and method locator with an attribute and a signature
	 * @throws Exception thrown on any error
	 */	
	@Test
	public void testSimpleClassMethodWithSigAndAttr() throws Exception {
		final Map<Class<?>, Set<Member>> members = ShorthandScript.parse("java.lang.Object equals (Ljava/lang/Object;)[PUBLIC] 'X'").getTargetMembers();
		Assert.assertEquals("Invalid number of classes in map", 1, members.size());
		final Class<?> clazz = members.keySet().iterator().next();
		Assert.assertEquals("Incorrect class as member key", Object.class, clazz);
		final Set<Member> mset = members.get(clazz);
		Assert.assertNotNull("The member set was null", mset);
		Assert.assertEquals("Invalid number of members in set", 1, mset.size());
		final Member member = mset.iterator().next();
		Assert.assertEquals("The member was not a method", Method.class, member.getClass());
		Assert.assertEquals("The method name was unexpected", "equals", member.getName());
	}
	
	
	
	/**
	 * Tests a simple class and method locator that should fail since we're specifying a private method attribute
	 * @throws Exception thrown on any error
	 */	
	@Test
	public void testSimpleClassMethodAttrMiss() throws Exception {
		final Map<Class<?>, Set<Member>> members = ShorthandScript.parse("java.lang.Object hashCode [PRIVATE] 'X'").getTargetMembers();
		Assert.assertEquals("Invalid number of classes in map", 1, members.size());
		final Class<?> clazz = members.keySet().iterator().next();
		Assert.assertEquals("Incorrect class as member key", Object.class, clazz);
		final Set<Member> mset = members.get(clazz);
		Assert.assertNotNull("The member set was null", mset);
		Assert.assertEquals("Invalid number of members in set", 0, mset.size());
	}
	
	

	/**
	 * Tests a simple class and method locator that should fail since we're specifying an invalid signature
	 * @throws Exception thrown on any error
	 */	
	@Test
	public void testSimpleClassMethodSigMiss() throws Exception {
		final Map<Class<?>, Set<Member>> members = ShorthandScript.parse("java.lang.Object equals (;) 'X'").getTargetMembers();
		Assert.assertEquals("Invalid number of classes in map", 1, members.size());
		final Class<?> clazz = members.keySet().iterator().next();
		Assert.assertEquals("Incorrect class as member key", Object.class, clazz);
		final Set<Member> mset = members.get(clazz);
		Assert.assertNotNull("The member set was null", mset);
		Assert.assertEquals("Invalid number of members in set", 0, mset.size());
	}
	
	/**
	 * Tests a simple class and method locator to find classes that inherrit the specified class
	 * @throws Exception thrown on any error
	 */	
	@Test
	public void testSimpleClassInherritance() throws Exception {
		final Map<Class<?>, Set<Member>> members = ShorthandScript.parse("java.lang.CharSequence+ toString 'X'").getTargetMembers();
		log("CharSequence Inherrited: %s", members.size());
	}

	
	
}
