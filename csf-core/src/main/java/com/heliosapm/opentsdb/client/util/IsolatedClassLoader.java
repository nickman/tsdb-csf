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
package com.heliosapm.opentsdb.client.util;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

/**
 * <p>Title: IsolatedClassLoader</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.util.IsolatedClassLoader</code></p>
 */

public class IsolatedClassLoader {
	/**
	 * A parent-last classloader that will try the child classloader first and then the parent.
	 * This takes a fair bit of doing because java really prefers parent-first.
	 * 
	 * For those not familiar with class loading trickery, be wary
	 */
	private static class ParentLastURLClassLoader extends ClassLoader 
	{
	    private ChildURLClassLoader childClassLoader;

	    /**
	     * This class allows me to call findClass on a classloader
	     */
	    private static class FindClassClassLoader extends ClassLoader
	    {
	        public FindClassClassLoader(ClassLoader parent)
	        {
	            super(parent);
	        }

	        @Override
	        public Class<?> findClass(String name) throws ClassNotFoundException
	        {
	            return super.findClass(name);
	        }
	    }

	    /**
	     * This class delegates (child then parent) for the findClass method for a URLClassLoader.
	     * We need this because findClass is protected in URLClassLoader
	     */
	    private static class ChildURLClassLoader extends URLClassLoader
	    {
	        private FindClassClassLoader realParent;

	        public ChildURLClassLoader( URL[] urls, FindClassClassLoader realParent )
	        {
	            super(urls, null);

	            this.realParent = realParent;
	        }

	        @Override
	        public Class<?> findClass(String name) throws ClassNotFoundException
	        {
	            try
	            {
	                // first try to use the URLClassLoader findClass
	                return super.findClass(name);
	            }
	            catch( ClassNotFoundException e )
	            {
	                // if that fails, we ask our real parent classloader to load the class (we give up)
	                return realParent.loadClass(name);
	            }
	        }
	    }

	    public ParentLastURLClassLoader(final URL url)
	    {
	        super(Thread.currentThread().getContextClassLoader());

	        URL[] urls = new URL[]{url};

	        childClassLoader = new ChildURLClassLoader( urls, new FindClassClassLoader(this.getParent()) );
	    }

	    @Override
	    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
	    {
	        try
	        {
	            // first we try to find a class inside the child classloader
	            return childClassLoader.findClass(name);
	        }
	        catch( ClassNotFoundException e )
	        {
	            // didn't find it, try the parent
	            return super.loadClass(name, resolve);
	        }
	    }
	}

}
