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
package com.heliosapm.opentsdb.client.classloaders;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.Enumeration;

/**
 * <p>Title: WeaklyReferencedClassLoader</p>
 * <p>Description: A weakly referenced classloader wrapper</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.classloaders.WeaklyReferencedClassLoader</code></p>
 */

public class WeaklyReferencedClassLoader extends ClassLoader {
	/** The delegate and weakly referenced classloader */
	protected final WeakReference<ClassLoader> delegateClassLoader;

	/**
	 * Creates a new WeaklyReferencedClassLoader
	 * @param delegate The delegate and weakly referenced classloader
	 */
	public WeaklyReferencedClassLoader(final ClassLoader delegate) {
		delegateClassLoader = new WeakReference<ClassLoader>(delegate);
	}
	
	private ClassLoader delegate() {
		final ClassLoader cl = delegateClassLoader.get(); 
		if(cl==null) throw new IllegalStateException("The weakly referenced classloader has been cleared");
		return cl;
	}
	

	/**
	 * @return
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return delegate().hashCode();
	}

	/**
	 * @param obj
	 * @return
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		return delegate().equals(obj);
	}

	/**
	 * @return
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return delegate().toString();
	}

	/**
	 * @param name
	 * @return
	 * @throws ClassNotFoundException
	 * @see java.lang.ClassLoader#loadClass(java.lang.String)
	 */
	public Class<?> loadClass(String name) throws ClassNotFoundException {
		return delegate().loadClass(name);
	}

	/**
	 * @param name
	 * @return
	 * @see java.lang.ClassLoader#getResource(java.lang.String)
	 */
	public URL getResource(String name) {
		return delegate().getResource(name);
	}

	/**
	 * @param name
	 * @return
	 * @throws IOException
	 * @see java.lang.ClassLoader#getResources(java.lang.String)
	 */
	public Enumeration<URL> getResources(String name) throws IOException {
		return delegate().getResources(name);
	}

	/**
	 * @param name
	 * @return
	 * @see java.lang.ClassLoader#getResourceAsStream(java.lang.String)
	 */
	public InputStream getResourceAsStream(String name) {
		return delegate().getResourceAsStream(name);
	}

	/**
	 * @param enabled
	 * @see java.lang.ClassLoader#setDefaultAssertionStatus(boolean)
	 */
	public void setDefaultAssertionStatus(boolean enabled) {
		delegate().setDefaultAssertionStatus(enabled);
	}

	/**
	 * @param packageName
	 * @param enabled
	 * @see java.lang.ClassLoader#setPackageAssertionStatus(java.lang.String, boolean)
	 */
	public void setPackageAssertionStatus(String packageName, boolean enabled) {
		delegate().setPackageAssertionStatus(packageName, enabled);
	}

	/**
	 * @param className
	 * @param enabled
	 * @see java.lang.ClassLoader#setClassAssertionStatus(java.lang.String, boolean)
	 */
	public void setClassAssertionStatus(String className, boolean enabled) {
		delegate().setClassAssertionStatus(className, enabled);
	}

	/**
	 * 
	 * @see java.lang.ClassLoader#clearAssertionStatus()
	 */
	public void clearAssertionStatus() {
		delegate().clearAssertionStatus();
	}
	
	
}
