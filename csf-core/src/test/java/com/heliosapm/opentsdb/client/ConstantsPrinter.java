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

package com.heliosapm.opentsdb.client;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.TreeSet;

import com.heliosapm.opentsdb.client.opentsdb.Constants;

/**
 * <p>Title: ConstantsPrinter</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.ConstantsPrinter</code></p>
 */

public class ConstantsPrinter {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			Set<String> props = new TreeSet<String>();
			for(Field field: Constants.class.getDeclaredFields()) {
				String name = field.getName();
				if(name.startsWith("PROP_")) {
					Field df = null;
					try { df = Constants.class.getDeclaredField(name.replace("PROP", "DEFAULT")); } catch (Exception ex2) {}
					if(df!=null) {
						props.add(String.format("| %s | %s | %s |", field.get(null), df.get(null), field.getName()));						
					} else {
						props.add(String.format("| %s | None | %s |", field.get(null), field.getName()));
						//log("prop: %s, No Default", name);						
					}
				}
			}
			for(String s: props) {
				log(s);
			}
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
		}

	}
	
	public static void log(final Object fmt, final Object...args) {
		System.out.println(String.format(fmt.toString(), args));
	}

}
