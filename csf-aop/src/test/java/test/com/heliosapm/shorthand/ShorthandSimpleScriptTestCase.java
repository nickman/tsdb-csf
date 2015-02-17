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
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import javax.management.ObjectName;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import test.com.heliosapm.base.BaseTest;
import test.com.heliosapm.shorthand.testclasses.DynamicClassCompiler;
import test.com.heliosapm.shorthand.testclasses.FunShorthandTypeAndMethodAnnotation;
import test.com.heliosapm.shorthand.testclasses.TypeOnlyAnnotated;

import com.google.gson.annotations.Since;
import com.heliosapm.opentsdb.client.aop.ShorthandScript;
import com.heliosapm.opentsdb.client.aop.ShorthandScriptMBean;
import com.heliosapm.opentsdb.client.opentsdb.opt.Measurement;
import com.heliosapm.opentsdb.client.util.JMXHelper;

/**
 * <p>Title: ShorthandSimpleScriptTestCase</p>
 * <p>Description: Test cases for the simple shorthand script parser</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.shorthand.script.ShorthandSimpleScriptTestCase</code></p>
 */

public class ShorthandSimpleScriptTestCase extends BaseTest {
	/**
	 * Tests ther parsing of a shorthand script
	 * @param shorthand The shorthand script source
	 * @param targetClass The expected target class
	 * @param targetClassAnnotation The expected target class annotation flag
	 * @param targetClassInterface  The expected is interface enabled flag
	 * @param inherritanceEnabled The expected inherritance enabled flag
	 * @param methodName The expected method name
	 * @param methodNameExpression The expected method name pattern
	 * @param methodSignature  The expected method signature
	 * @param methodSignatureExpression  The expected method signature pattern
	 * @param targetMethodAnnotation The expected target method is annotation flag
	 * @param enumIndex The expected enum collector index
	 * @param bitMask The expected metrics enabled bitmask
	 * @param methodTemplate The expected method template
	 * @param allowReentrant The expected allow reentrancy invocation option flag
	 * @param disableOnTrigger The expected disable on trigger invocation option flag
	 * @param startDisabled The expected start disabled invocation option flag
	 * @param batchTransform The expected batch transform invocation option flag
	 * @param residentTransformer The expected resident transformer invocation option flag
	 * @return the parsed shorthand script
	 * @throws Exception thrown on any error
	 */
	@SuppressWarnings("static-method")
	@Ignore
	protected ShorthandScriptMBean test(String shorthand, Class<?> targetClass, boolean targetClassAnnotation,
			boolean targetClassInterface, boolean inherritanceEnabled,
			String methodName, Pattern methodNameExpression,
			String methodSignature, Pattern methodSignatureExpression,
			boolean targetMethodAnnotation, 
			int enumIndex, int bitMask,
			String methodTemplate, boolean allowReentrant,
			boolean disableOnTrigger, boolean startDisabled, boolean batchTransform, boolean residentTransformer) throws Exception {
		final ShorthandScriptMBean script = ShorthandScript.parse(shorthand);
		
		Assert.assertEquals("Unexpected target class", targetClass, script.getTargetClass());
		Assert.assertEquals("Unexpected target class annot flag", targetClassAnnotation, script.isTargetClassAnnotation());
		// This guy asserts as true since an annotation does qualify as an interface.
		// but that's not what we want.
		//Assert.assertEquals("Unexpected target class iface flag", targetClass.isInterface(), script.isTargetClassInterface());
		Assert.assertEquals("Unexpected target class iface flag", targetClassInterface, script.isTargetClassInterface());
		Assert.assertEquals("Unexpected target class inherritance flag", inherritanceEnabled, script.isInherritanceEnabled());
		
		Assert.assertEquals("Unexpected method name", methodName, script.getMethodName());
		Assert.assertEquals("Unexpected method name expression", methodNameExpression, script.getMethodNameExpression());
		Assert.assertEquals("Unexpected method signature", methodSignature, script.getMethodSignature());
		Assert.assertEquals("Unexpected method signature expression", methodSignatureExpression, script.getMethodSignatureExpression());
		
		Assert.assertEquals("Unexpected target method annot flag", targetMethodAnnotation, script.isTargetMethodAnnotation());
		
		Assert.assertEquals("Unexpected enum index", enumIndex, script.getEnumIndex());
		Assert.assertEquals("Unexpected bitmask", bitMask, script.getBitMask());
		
		Assert.assertEquals("Unexpected method name template", methodTemplate, script.getMetricNameTemplate());
		
		Assert.assertEquals("Unexpected allow reentrancy inv option", allowReentrant, script.isAllowReentrant());
		Assert.assertEquals("Unexpected disable on trigger inv option", disableOnTrigger, script.isDisableOnTrigger());
		Assert.assertEquals("Unexpected start disabled inv option", startDisabled, script.isStartDisabled());
		Assert.assertEquals("Unexpected batch transform inv option", batchTransform, script.isBatchTransform());
		Assert.assertEquals("Unexpected resident transformer inv option", residentTransformer, script.isResidentTransformer());
		
		final int _bitMask = script.getBitMask();
		Assert.assertEquals("Unexpected bitmask", bitMask, _bitMask);
		
		return script;
	}
	
	/**
	 * Tests ther parsing of a shorthand script
	 * @param shorthand The shorthand script source
	 * @param targetClassName The expected target class name
	 * @param targetClassAnnotation The expected target class annotation flag
	 * @param targetClassInterface  The expected is interface enabled flag
	 * @param inherritanceEnabled The expected inherritance enabled flag
	 * @param methodName The expected method name
	 * @param methodNameExpression The expected method name pattern
	 * @param methodSignature  The expected method signature
	 * @param methodSignatureExpression  The expected method signature pattern
	 * @param targetMethodAnnotation The expected target method is annotation flag
	 * @param enumIndex The expected enum collector index
	 * @param bitMask The expected metrics enabled bitmask
	 * @param methodTemplate The expected method template
	 * @param allowReentrant The expected allow reentrancy invocation option flag
	 * @param disableOnTrigger The expected disable on trigger invocation option flag
	 * @param startDisabled The expected start disabled invocation option flag
	 * @param batchTransform The expected batch transform invocation option flag
	 * @param residentTransformer The expected resident transformer invocation option flag 
	 * @return the parsed shorthand script
	 * @throws Exception thrown on any error
	 */
	@SuppressWarnings("static-method")
	@Ignore
	protected ShorthandScriptMBean test(String shorthand, String targetClassName, boolean targetClassAnnotation,
			boolean targetClassInterface, boolean inherritanceEnabled,
			String methodName, Pattern methodNameExpression,
			String methodSignature, Pattern methodSignatureExpression,
			boolean targetMethodAnnotation, 
			int enumIndex, int bitMask,
			String methodTemplate, boolean allowReentrant,
			boolean disableOnTrigger, boolean startDisabled, boolean batchTransform, boolean residentTransformer) throws Exception {
		final ShorthandScriptMBean script = ShorthandScript.parse(shorthand);
		
		Assert.assertEquals("Unexpected target class name", targetClassName, script.getTargetClass().getName());
		Assert.assertEquals("Unexpected target class annot flag", targetClassAnnotation, script.isTargetClassAnnotation());
		// This guy asserts as true since an annotation does qualify as an interface.
		// but that's not what we want.
		//Assert.assertEquals("Unexpected target class iface flag", targetClass.isInterface(), script.isTargetClassInterface());
		Assert.assertEquals("Unexpected target class iface flag", targetClassInterface, script.isTargetClassInterface());
		Assert.assertEquals("Unexpected target class inherritance flag", inherritanceEnabled, script.isInherritanceEnabled());
		
		Assert.assertEquals("Unexpected method name", methodName, script.getMethodName());
		Assert.assertEquals("Unexpected method name expression", methodNameExpression, script.getMethodNameExpression());
		Assert.assertEquals("Unexpected method signature", methodSignature, script.getMethodSignature());
		Assert.assertEquals("Unexpected method signature expression", methodSignatureExpression, script.getMethodSignatureExpression());
		
		Assert.assertEquals("Unexpected target method annot flag", targetMethodAnnotation, script.isTargetMethodAnnotation());
		
		Assert.assertEquals("Unexpected enum index", enumIndex, script.getEnumIndex());
		Assert.assertEquals("Unexpected bitmask", bitMask, script.getBitMask());
		
		Assert.assertEquals("Unexpected method name template", methodTemplate, script.getMetricNameTemplate());
		
		Assert.assertEquals("Unexpected allow reentrancy inv option", allowReentrant, script.isAllowReentrant());
		Assert.assertEquals("Unexpected disable on trigger inv option", disableOnTrigger, script.isDisableOnTrigger());
		Assert.assertEquals("Unexpected start disabled inv option", startDisabled, script.isStartDisabled());
		Assert.assertEquals("Unexpected batch transform inv option", batchTransform, script.isBatchTransform());
		Assert.assertEquals("Unexpected resident transformer inv option", residentTransformer, script.isResidentTransformer());
		
		final int _bitMask = script.getBitMask();
		Assert.assertEquals("Unexpected bitmask", bitMask, _bitMask);
		
		return script;
	}
	
	
	/** Shortcut for match all pattern */
	protected static final Pattern MA = ShorthandScript.MATCH_ALL;
	/** Shortcut and early init for the enum collector index for MethodInterceptor */
	protected static final int MI = Measurement.ALL_MASK;

	/**
	 * Tests a simple expression with a zero bitmask
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testBasicZeroBitMask() throws Exception {
		test("java.lang.Object equals MethodInterceptor[0] 'java/lang/Object'", 
				Object.class, false,	// target class, target class annot
				false, false,  			// target class iface, target class inherritance
				"equals", null, 		// methodName, methodName pattern
				null, MA, false, 		// method sig, method sig pattern, target method annot 
				MI, 0, 			// enum index, bit mask
				"java/lang/Object",  	// method template
				false, false, false, false, true);  	// allow reentrant, disable on trigger, startDisabled, batchTransform, residentTransformer		
	}
	
	/**
	 * Tests a simple expression with a zero bitmask and inherritance
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testInherritedBasicZeroBitMask() throws Exception {
		test("java.lang.Object+ equals MethodInterceptor[0] 'java/lang/Object'", 
				Object.class, false,	// target class, target class annot
				false, true,  			// target class iface, target class inherritance
				"equals", null, 		// methodName, methodName pattern
				null, MA, false, 		// method sig, method sig pattern, target method annot 
				MI, 0, 					// enum index, bit mask
				"java/lang/Object",  	// method template
				false, false, false, false, true);  	// allow reentrant, disable on trigger, startDisabled, batchTransform, residentTransformer
		//test("java.lang.Object equals [0] '$package[0]/$package[1]/$class/$method'", "java.lang.Object", "equals", "java/lang/Object/equals", false, false, 0, 3);
	}
	
	/**
	 * Tests a simple expression with a zero bitmask and inherritance
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testAnnotatedClassBasicZeroBitMask() throws Exception {
		test("@com.google.gson.annotations.Since foo MethodInterceptor[0] 'since/$class/$2'", 
				Since.class, true,		// target class, target class annot
				false, false,  			// target class iface, target class inherritance
				"foo", null, 			// methodName, methodName pattern
				null, MA, false, 		// method sig, method sig pattern, target method annot 
				MI, 0, 					// enum index, bit mask
				"since/$class/$2",  	// method template
				false, false, false, false, true);  	// allow reentrant, disable on trigger, startDisabled, batchTransform, residentTransformer
	}
	
	/**
	 * Tests the correct reading of one instrumentation option
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testAnnotatedClassInstrumentOption() throws Exception {
		test("@com.google.gson.annotations.Since foo -s MethodInterceptor[0] 'since/$class/$2'", 
				Since.class, true,		// target class, target class annot
				false, false,  			// target class iface, target class inherritance
				"foo", null, 			// methodName, methodName pattern
				null, MA, false, 		// method sig, method sig pattern, target method annot 
				MI, 0, 					// enum index, bit mask
				"since/$class/$2",  	// method template
				false, false, true, false, true);  	// allow reentrant, disable on trigger, startDisabled, batchTransform, residentTransformer
	}
	
	/**
	 * Tests the correct reading of multiple instrumentation option
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testAnnotatedClassInstrumentOptions() throws Exception {
		test("@com.google.gson.annotations.Since foo -sda MethodInterceptor[0] 'since/$class/$2'", 
				Since.class, true,		// target class, target class annot
				false, false,  			// target class iface, target class inherritance
				"foo", null, 			// methodName, methodName pattern
				null, MA, false, 		// method sig, method sig pattern, target method annot 
				MI, 0, 					// enum index, bit mask
				"since/$class/$2",  	// method template
				true, true, true, false, true);  	// allow reentrant, disable on trigger, startDisabled, batchTransform, residentTransformer
	}
	
	/**
	 * Tests the correct reading of multiple instrumentation options and batch transform
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testAnnotatedClassInstrumentOptionsBatchTransform() throws Exception {
		test("@com.google.gson.annotations.Since foo -sdab MethodInterceptor[0] 'since/$class/$2'", 
				Since.class, true,		// target class, target class annot
				false, false,  			// target class iface, target class inherritance
				"foo", null, 			// methodName, methodName pattern
				null, MA, false, 		// method sig, method sig pattern, target method annot 
				MI, 0, 					// enum index, bit mask
				"since/$class/$2",  	// method template
				true, true, true, true, false);  	// allow reentrant, disable on trigger, startDisabled, batchTransform, residentTransformer
	}
	
	/**
	 * Tests the correct reading of multiple instrumentation options and batch and resident transform
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testAnnotatedClassInstrumentOptionsBatchResidentTransform() throws Exception {
		test("@com.google.gson.annotations.Since foo -sdabr MethodInterceptor[0] 'since/$class/$2'", 
				Since.class, true,		// target class, target class annot
				false, false,  			// target class iface, target class inherritance
				"foo", null, 			// methodName, methodName pattern
				null, MA, false, 		// method sig, method sig pattern, target method annot 
				MI, 0, 					// enum index, bit mask
				"since/$class/$2",  	// method template
				true, true, true, true, true);  	// allow reentrant, disable on trigger, startDisabled, batchTransform, residentTransformer
	}
	
	
	
	
	/**
	 * Tests handling of a wildcard method name, wildcard bitmask and a type annotation class locator
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testLocateOnAnnotatedWildcardMethod() throws Exception {
		ShorthandScriptMBean script = test("@com.heliosapm.shorthand.testclasses.annotations.FunShorthandTypeAndMethodAnnotation * MethodInterceptor[*] 'fun/fun/fun'",
				FunShorthandTypeAndMethodAnnotation.class, true,		// target class, target class annot
				false, false,  											// target class iface, target class inherritance
				null, MA, 												// methodName, methodName pattern
				null, MA, false, 										// method sig, method sig pattern, target method annot 
				MI, Measurement.ALL_MASK, 					// enum index, bit mask
				"fun/fun/fun",  										// method template
				false, false, false, false, true);  	// allow reentrant, disable on trigger, startDisabled, batchTransform, residentTransformer
		Set<Class<?>> targetClasses = script.getTargetClasses();
		Assert.assertEquals("Unexpected number of target classes", 1, targetClasses.size());
		Assert.assertEquals("Unexpected target class", targetClasses.iterator().next(), TypeOnlyAnnotated.class);
		
		Map<Class<?>, Set<Member>> targetMembers = script.getTargetMembers();
		Assert.assertEquals("Unexpected number of target classes in target members", 1, targetMembers.size());
		Set<String> actualMethodNames = new TreeSet<String>();
		for(Member member: targetMembers.values().iterator().next()) {
			actualMethodNames.add(member.getName());
		}
		Assert.assertArrayEquals("Unexpected target method names", new String[]{"myPublic"}, actualMethodNames.toArray(new String[0]));
	}
	
	/**
	 * Test case to verify that a dynamic target class can be located using the URL classloader syntax 
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testLocateDynamicClassFromURL() throws Exception {
		URL url = DynamicClassCompiler.generateClass("foo.bar", HashMap.class);
		log("Dynamic Class URL [%s]", url);
		test("foo.bar<-" + url.toString() + " put MethodInterceptor[0] 'foo/bar/put'", 
				"foo.bar", false,		// target class, target class annot
				false, false,  			// target class iface, target class inherritance
				"put", null, 			// methodName, methodName pattern
				null, MA, false, 		// method sig, method sig pattern, target method annot 
				MI, 0, 					// enum index, bit mask
				"foo/bar/put",  	// method template
				false, false, false, false, true);  	// allow reentrant, disable on trigger, startDisabled, batchTransform, residentTransformer		
	}
	
	/**
	 * Test case to verify that a dynamic target class can be located using an ObjectName referenced class loader 
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testLocateDynamicClassFromObjectName() throws Exception {
		URL url = DynamicClassCompiler.generateClass("foo.snafu", HashMap.class);
		log("Dynamic Class URL [%s]", url);
		ObjectName on = JMXHelper.publishClassLoader("shorthand.classloaders:url=" + ObjectName.quote(url.toString()), url);
		test("foo.snafu<-" + on.toString() + " put MethodInterceptor[0] 'foo/snafu/put'", 
				"foo.snafu", false,		// target class, target class annot
				false, false,  			// target class iface, target class inherritance
				"put", null, 			// methodName, methodName pattern
				null, MA, false, 		// method sig, method sig pattern, target method annot 
				MI, 0, 					// enum index, bit mask
				"foo/snafu/put",  	// method template
				false, false, false, false, true);  	// allow reentrant, disable on trigger, startDisabled, batchTransform, residentTransformer		
		
//		//log("Cleanup Complete");
//		for(int i = 0; i < 10000; i++) {
//			//System.gc();
//			log("Loop #%s  MemBuffer Instances: %s   Highwater: %s  Destroys: %s  IsClReg: %s", i, BufferManager.getInstance().getMemBufferInstances(), BufferManager.getInstance().getMemBufferInstanceHighwater(), BufferManager.getInstance().getMemBufferDestroys(), JMXHelper.isRegistered(on));
//			try { Thread.currentThread().join(5000); } catch (Exception ex) {/* No Op*/}
//			if(i==10) url = null;
//			if(i==20) JMXHelper.unregisterMBean(on);
//			
//		}
		
	}
	
	
	/*
	 	public void myPublic(){}
		protected void myProtected(){}
		private void myPrivate(){}   // nudge, nudge
	 */
	
	
}
