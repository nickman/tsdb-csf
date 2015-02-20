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

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * <p>Title: EvalPoc</p>
 * <p>Description: Quickie JS eval for metric naming</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.naming.EvalPoc</code></p>
 */
public class EvalPoc {

	/**
	 * Runs a test eval
	 * @param args None
	 */
	public static void main(String[] args) {
		log("EvalPOC");
		try {
			Method method = Foo.class.getDeclaredMethod("getBar", int.class);
			//    \\$\\{(.*)?@\\((.*)?\\)(.*)?\\}
			String replacement[] = Extractors.ANNOTATION.getStringReplacement("${@(Instrumented).version()}", Foo.class, method);
			log("Replacement:[%s]", Arrays.toString(replacement));
			
			replacement = Extractors.THIS.getStringReplacement("${this}", Foo.class, method);
			log("Replacement:[%s]", Arrays.toString(replacement));
			replacement = Extractors.THIS.getStringReplacement("${this:}", Foo.class, method);
			log("Replacement:[%s]", Arrays.toString(replacement)); 
			replacement = Extractors.THIS.getStringReplacement("${this: $0.toString().toUpperCase()}", Foo.class, method);
			log("Replacement:[%s]", Arrays.toString(replacement));
			
			replacement = Extractors.ARG.getStringReplacement("${arg[0]}", Foo.class, method);
			log("Replacement:[%s]", Arrays.toString(replacement));			
			replacement = Extractors.ARG.getStringReplacement("${arg:(\"\" + ($1 + $1))}", Foo.class, method);
			log("Replacement:[%s]", Arrays.toString(replacement));
			

//			replacement = Extractors.RETURN.getStringReplacement("${return}", Foo.class, method);
//			log("Return Replacement:[%s]", Arrays.toString(replacement));			
//			replacement = Extractors.RETURN.getStringReplacement("${return:$_.toUpperCase()}", Foo.class, method);
//			log("Return Replacement:[%s]", Arrays.toString(replacement));			

			
			replacement = Extractors.JAVA.getStringReplacement("${java:$_ + $1}", Foo.class, method);
			log("JAVA Replacement:[%s]", Arrays.toString(replacement));		
			
			MetricNameProvider mnp = MetricNameCompiler.getMetricNameProvider(Foo.class, method, "${package}/${class}/${method}");


		} catch (Exception ex) {
			ex.printStackTrace(System.err);
		}

	}
	
	class Foo {
//		@Instrumented(lastInstrumented=1094, types= {"a", "b"}, version=9)
		public String getBar(int i) {
			return "#" + i;
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
