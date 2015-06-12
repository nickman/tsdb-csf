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
package com.heliosapm.utils.classload;

import java.lang.management.ManagementFactory;
import java.net.URL;
import java.net.URLClassLoader;

import javax.management.MBeanServer;
import javax.management.ObjectName;


/**
 * <p>Title: IsolatedClassLoader</p>
 * <p>Description: A parent last isolated classloader</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author <a href="http://stackoverflow.com/users/209856/karoberts">karoberts</a> on StackOverflow
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.utils.classload.IsolatedClassLoader</code></p>
 */

public class IsolatedClassLoader extends ClassLoader implements IsolatedClassLoaderMBean {
	/** The child class loader */
	protected final ChildURLClassLoader childClassLoader;
	/** The JMX ObjectName to register the class loader under */
	protected final ObjectName objectName;
	
	/**
	 * Creates a new IsolatedClassLoader
	 * @param objectName The JMX ObjectName to register the management interface with.
	 * Ignored if null.
	 * @param urls The classpath the loader will load from
	 */
	public IsolatedClassLoader(final ObjectName objectName, final URL[] urls) {
		super(Thread.currentThread().getContextClassLoader());
		this.objectName = objectName;
		childClassLoader = new ChildURLClassLoader( urls, new FindClassClassLoader(this.getParent()) );
		try {
			if(this.objectName!=null) {
				final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
				if(server.isRegistered(this.objectName)) {
					server.unregisterMBean(this.objectName);
				}
				server.registerMBean(this, this.objectName);
			}
		} catch (Exception ex) {
			System.err.println("Failed to register IsolatedClassLoader MBean [" + this.objectName + "]. Stack trace follows...");
			ex.printStackTrace(System.err);
		}
	}
	
	/**
	 * Creates a new IsolatedClassLoader
	 * @param urls The classpath the loader will load from
	 */
	public IsolatedClassLoader(final URL[] urls) {
		this(null, urls);
	}	
	
	public URL[] getURLs() {
		return childClassLoader.getURLs();
	}
	
    /**
     * Appends the specified URL to the list of URLs to search for
     * classes and resources.
     * <p>
     * If the URL specified is <code>null</code> or is already in the
     * list of URLs, or if this loader is closed, then invoking this
     * method has no effect.
     *
     * @param url the URL to be added to the search path of URLs
     */
	public void addURL(final URL url) {
		childClassLoader.addURL(url);
	}
	
  /**
   * {@inheritDoc}
   * @see java.lang.ClassLoader#loadClass(java.lang.String, boolean)
   */
  @Override
  protected synchronized Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
      try {
          // first we try to find a class inside the child classloader
          return childClassLoader.findClass(name);
      } catch( ClassNotFoundException e ) {
          // didn't find it, try the parent
          return super.loadClass(name, resolve);
      }
  }
	

  /**
   * This class delegates (child then parent) for the findClass method for a URLClassLoader.
   * We need this because findClass is protected in URLClassLoader
   */
  private static class ChildURLClassLoader extends URLClassLoader {
      /** The real parent class loader */
    private FindClassClassLoader realParent;

    /**
     * Creates a new ChildURLClassLoader
     * @param urls The URLs comprising the isolated classpath
     * @param realParent The real parent classloader
     */
    public ChildURLClassLoader( URL[] urls, FindClassClassLoader realParent ) {
          super(urls, null);
          this.realParent = realParent;
      }

      /**
     * {@inheritDoc}
     * @see java.net.URLClassLoader#findClass(java.lang.String)
     */
    @Override
      public Class<?> findClass(String name) throws ClassNotFoundException {
      	Class<?> loaded = super.findLoadedClass(name);
        if( loaded != null ) return loaded;	        	
          try {
              // first try to use the URLClassLoader findClass
              return super.findClass(name);
          }  catch( ClassNotFoundException e ) {
              // if that fails, we ask our real parent classloader to load the class (we give up)
              return realParent.loadClass(name);
          }
      }
      
    /**
     * {@inheritDoc}
     * @see java.net.URLClassLoader#addURL(java.net.URL)
     */
    public void addURL(final URL url) {
    	  super.addURL(url);
      }
  }
  
  /**
   * This class allows me to call findClass on a classloader
   */
  private static class FindClassClassLoader extends ClassLoader {
      public FindClassClassLoader(ClassLoader parent) {
          super(parent);
      }

      @Override
      public Class<?> findClass(String name) throws ClassNotFoundException {
          return super.findClass(name);
      }
  }
  

}
