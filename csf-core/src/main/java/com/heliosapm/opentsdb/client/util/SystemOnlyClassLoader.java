/**
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
 */
package com.heliosapm.opentsdb.client.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;

/**
 * <p>Title: SystemOnlyClassLoader</p>
 * <p>Description: Classloader that only loads from the root classloader. 
 * It serves as a parent classloader and by default its <b><code>findClass</code></b> and <b><code>loadClass</code></b>
 * methods throw a <b><code>ClassNotFoundException</code></b> which causes the class lookup to be delegated to its child
 * classloader[s]. Each child classloader (ideally a {@link IsolatedArchiveLoader} should then attempt to load the specified
 * class and if it fails to find the class (as it might for a system class), it can delegate back up to this classloader using the <b><code>forReal</code></b>
 * method which will then attempt to load and return the class.</p>
 * <p>Essentially, this classloader supports a fully isolated set of classes.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.util.SystemOnlyClassLoader</code></p>
 */

public class SystemOnlyClassLoader extends URLClassLoader {
	/** The root classloader (where java.lang.Object was loaded from) */
	protected final ClassLoader ROOT = ClassLoader.getSystemClassLoader().getParent();
	/**
	 * Creates a new SystemOnlyClassLoader 
	 */
	public SystemOnlyClassLoader()  {
		super(new URL[]{});
		
	}
	
	/**
	 * Attempts to locate the named class that is already loaded and if not found, attempts to load it.
	 * @param name The name of the class to load
	 * @return the loaded class
	 * @throws ClassNotFoundException thrown if the class if not found
	 */
	public Class<?> forReal(String name) throws ClassNotFoundException {
//		try {
			if(JMXHelper.isDebugAgentLoaded()) {
				System.out.println("SystemOnlyClassLoader [" + name + "]");
			}
			return findSystemClass(name);
//		} catch (ClassNotFoundException ce) {
//			return ROOT.loadClass(name); 
//		}
	}
	

	public URL getResource(String name) {
		return null;
	}
	
	public Enumeration<URL> getResources(String name) {
		return null;
	}
	
	public InputStream getResourceAsStream(String name) {
		return null;
	}
	
	public URL getRealResource(String name) {
		if(JMXHelper.isDebugAgentLoaded()) {
			System.out.println("SystemOnlyClassLoader [" + name + "]");
		}		
		return ROOT.getResource(name);
	}
	
	public Enumeration<URL> getRealResources(String name) throws IOException {
		if(JMXHelper.isDebugAgentLoaded()) {
			System.out.println("SystemOnlyClassLoader [" + name + "]");
		}		
		return ROOT.getResources(name);
	}
	
	public InputStream getRealResourceAsStream(String name) {
		if(JMXHelper.isDebugAgentLoaded()) {
			System.out.println("SystemOnlyClassLoader [" + name + "]");
		}		
		return ROOT.getResourceAsStream(name);
	}
	

	/**
	 * Always throws ClassNotFoundException
	 * @param name
	 * @return
	 * @throws ClassNotFoundException
	 */
	private Class<?> loadSystemClass(String name) throws ClassNotFoundException {
			throw new ClassNotFoundException(name);
	}
	
	/**
	 * Always throws ClassNotFoundException
	 * @param name
	 * @param resolve
	 * @return
	 * @throws ClassNotFoundException
	 */
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		return loadSystemClass(name);
	}
	
	/**
	 * Always throws ClassNotFoundException
	 * @param name
	 * @return
	 * @throws ClassNotFoundException
	 */
	public Class<?> loadClass(String name) throws ClassNotFoundException {
		return loadSystemClass(name);
	}	
	
	/**
	 * Always throws ClassNotFoundException
	 * @param name
	 * @return
	 * @throws ClassNotFoundException
	 */
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		return loadSystemClass(name);
	}
	
}
