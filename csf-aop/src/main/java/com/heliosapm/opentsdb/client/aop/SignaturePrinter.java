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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import com.heliosapm.utils.lang.StringHelper;

/**
 * <p>Title: SignaturePrinter</p>
 * <p>Description: Command line utility class to print method signatures</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.SignaturePrinter</code></p>
 */

public class SignaturePrinter {
	
	/**
	 * System out pattern logger
	 * @param fmt The message format
	 * @param args The tokens
	 */
	public static void log(final Object fmt, final Object...args) {
		System.out.println(String.format(fmt.toString(), args));
	}


	/**
	 * Prints the matching method signatures
	 * @param args &lt;Class Name&gt; &lt;Method Name&gt;
	 */
	public static void main(String...args) {
		if((args.length!=2)) {
			log("Usage: java com.heliosapm.opentsdb.client.aop.SignaturePrinter <classname> <methodname>");
		}
		try {
			Class<?> clazz = Class.forName(args[0]);
			Map<String, Method> mmap = new HashMap<String, Method>();
			for(Method m: clazz.getMethods()) {
				if(m.getName().equals(args[1])) {
					mmap.put(StringHelper.getMethodDescriptor(m), m);
				}
			}
			for(Method m: clazz.getDeclaredMethods()) {
				if(m.getName().equals(args[1])) {
					mmap.put(StringHelper.getMethodDescriptor(m), m);
				}
			}
			for(Map.Entry<String, Method> entry: mmap.entrySet()) {
				log("%s: [%s]", entry.getValue().getName(), entry.getKey());
			}
			
		} catch (Exception ex) {
			log("ClassLoad Failed");
		}
	}

}
