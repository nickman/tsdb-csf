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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.security.AllPermission;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.Permissions;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;


/**
 * <p>Title: IsolatedArchiveLoader</p>
 * <p>Description: Isolating classloader that restricts the classes it loads to those available from the passed URLs and the root system classloader.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.util.IsolatedArchiveLoader</code></p>
 */
public class IsolatedArchiveLoader extends URLClassLoader {
	/** This classloader's parent which is delegated system class loads */
	protected static SystemOnlyClassLoader ncl;
	
	/** Class byte code arrays loaded from the configured archives */
	protected final Map<String, ByteBuffer> archiveByteCode = new HashMap<String, ByteBuffer>(2048);
	/** Classes loaded from the configured archives */
	protected final Map<String, Class<?>> archiveClasses = new ConcurrentHashMap<String, Class<?>>(2048);
	/** Resources loaded from the configured archives */
	protected final Map<String, ByteBuffer> archiveResources = new HashMap<String, ByteBuffer>(2048);
	/** Maps the class name to the URL it was loaded from */
	protected final Map<String, URL> codeSource = new ConcurrentHashMap<String, URL>(2048);
	/** The protection domain for each URL source */
	protected final Map<URL, ProtectionDomain> protectionDomains = new ConcurrentHashMap<URL, ProtectionDomain>(2048);

	/**
	 * Creates a new IsolatedArchiveLoader that restricts its classloading to the passed URLs. 
	 * @param urls The URLs this classloader will load from
	 */
	public IsolatedArchiveLoader(final URL...urls)  {
		super(urls, getNullClassLoader());		
		StringBuilder b = new StringBuilder();
		if(urls!=null) {
			for(URL url: urls) {
				b.append("\n\t").append(url);
			}
		}
		loadByteCode(urls);
		System.out.println("Isolated Class Loader for URLs: [" + b + "]");
	}
	
	/**
	 * Loads and indexes the byte code from the passed URLs
	 * @param urls
	 */
	protected void loadByteCode(final URL...urls) {
		InputStream is = null;
		JarInputStream jis = null;
		JarEntry jer = null;
		String jerName = null;
		try {
			for(URL url : urls) {
				if(!protectionDomains.containsKey(url)) {
					final CodeSource cs = new CodeSource(url, (CodeSigner[])null);
					final Permissions pc = new Permissions();
					pc.add(new AllPermission());					
					final ProtectionDomain pd = new ProtectionDomain(cs, pc, this, new Principal[]{});
					protectionDomains.put(url, pd);
				}
				
				is = url.openStream();
				jis = new JarInputStream(is, false);
				while((jer = jis.getNextJarEntry()) != null){
					jerName = jer.getName();					
					if(!jer.isDirectory()) {
						InputStream inneris = null;
						try {
							URL jurl = new URL("jar:" + url.toString() + "!/" + jerName);
							inneris = jurl.openStream();
							int size = inneris.available();
//							log("JAR Entry Name [%s] : [%s] bytes", jerName, size);
							if(size==-1) continue;
							final ByteBuffer bb = ByteBuffer.allocateDirect(size);
							byte[] content = new byte[size];
							inneris.read(content, 0, content.length);
							bb.put(content);
							content = null;
							bb.flip();
							if(jerName.endsWith(".class")) {
								archiveByteCode.put(jerName.replace(".class", "").replace('/', '.'), bb);
								codeSource.put(jerName.replace(".class", "").replace('/', '.'), url);
//								log("Indexed class: [%s]", jerName.replace(".class", "").replace('/', '.'));
							} else {
								archiveResources.put(jerName.replace('/', '.'), bb);
//								log("Indexed resource: [%s]", jerName.replace('/', '.'));
							}							
						} finally {
							if(inneris!=null) try { inneris.close(); } catch (Exception x) {/* No Op */}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		} finally {
			if(is!=null) try { is.close(); } catch (Exception x) {/* No Op */}
			if(jis!=null) try { jis.close(); } catch (Exception x) {/* No Op */}
			System.gc();
		}
	}
	
	public static void main(String[] args) {
		log("Iso test");
		IsolatedArchiveLoader ial = new IsolatedArchiveLoader(URLHelper.toURL(new File("c:/hprojects/tsdb-csf/csf-core/target/csf-core-1.0-SNAPSHOT.jar")));
		try {
			Class<?> ja = ial.loadClass("com.heliosapm.opentsdb.client.boot.JavaAgent");
			log("Class: [%s]", ja.getName());
			log("Source: [%s]", ja.getProtectionDomain().getCodeSource().getLocation());
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
		}
	}
	
	/**
	 * Returns the SystemOnlyClassLoader that will be an IsolatedClassLoader's parent.
	 * @return the SystemOnlyClassLoader
	 */
	private static ClassLoader getNullClassLoader()  {
		ncl = new SystemOnlyClassLoader();
		return ncl;
	}
	
	/**
	 * System out format logger
	 * @param fmt The message format
	 * @param args The message args
	 */
	public static void log(final Object fmt, final Object...args) {
		System.out.println("[IsolatedArchiveLoader]" + String.format(fmt.toString(), args));
	}
	
	
	
	/**
	 * Attempts to load the named the class from the configured URLs and if not found, delegates to the parent.
	 * @param name The class name
	 * @return the loaded class
	 * @throws ClassNotFoundException
	 */
	private Class<?> loadSystemClass(final String name) throws ClassNotFoundException {
		try {
			Class<?> clazz = archiveClasses.get(name);
			if(clazz==null) {
				synchronized(archiveClasses) {
					clazz = archiveClasses.get(name);
					if(clazz==null) {
						ByteBuffer bb = archiveByteCode.get(name);
						if(bb==null) throw new ClassNotFoundException("Failed to find class [" + name + "] in isolated class loader");
						byte[] byteCode = new byte[bb.capacity()];
						bb.rewind();
						bb.get(byteCode);
						clazz = defineClass(name, byteCode, 0, byteCode.length, protectionDomains.get(codeSource.get(name)));
						byteCode = null;
						archiveClasses.put(name, clazz);
					}					
				}
			}
			if(JMXHelper.isDebugAgentLoaded()) {
				System.out.println("IsolatedArchiveLoader [" + name + "]");
			}			
			return clazz;
		} catch (ClassNotFoundException cle) {
			return ncl.forReal(name);
		}
		
//		try {
//			Class<?> clazz = super.findClass(name);
//			if(JMXHelper.isDebugAgentLoaded()) {
//				System.out.println("IsolatedArchiveLoader [" + name + "]");
//			}			
//			return clazz;
//		} catch (ClassNotFoundException cle) {
//			return ncl.forReal(name);
//		}
	}
	

	public URL getResource(String name) {
		URL url = super.getResource(name);
		if(url==null) {
			url = ncl.getRealResource(name);
		} else {
			if(JMXHelper.isDebugAgentLoaded()) {
				System.out.println("IsolatedArchiveLoader [" + name + "]");
			}						
		}
		return url;
	}
	
	public Enumeration<URL> getResources(String name) throws IOException {
		Enumeration<URL> en = super.getResources(name);
		if(en==null) {
			en = ncl.getRealResources(name);
		} else {
			if(JMXHelper.isDebugAgentLoaded()) {
				System.out.println("IsolatedArchiveLoader [" + name + "]");
			}			
		}
		return en;
	}
	
	public InputStream getResourceAsStream(String name) {
		InputStream is = super.getResourceAsStream(name);
		if(is==null) {
			is = ncl.getRealResourceAsStream(name);
		} else {
			if(JMXHelper.isDebugAgentLoaded()) {
				System.out.println("IsolatedArchiveLoader [" + name + "]");
			}			
		}
		return is;
	}

	/**
	 * Attempts to load the named the class from the configured URLs and if not found, delegates to the parent.
	 * @param name The class name
	 * @param resolve true to resolve the class.
	 * @return the loaded class
	 * @throws ClassNotFoundException
	 */	
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		return loadSystemClass(name);
	}
	
	/**
	 * Attempts to load the named the class from the configured URLs and if not found, delegates to the parent.
	 * @param name The class name
	 * @return the loaded class
	 * @throws ClassNotFoundException
	 */
	public Class<?> loadClass(String name) throws ClassNotFoundException {
		return loadSystemClass(name);
	}	
	
	/**
	 * Attempts to load the named the class from the configured URLs and if not found, delegates to the parent.
	 * @param name The class name
	 * @return the loaded class
	 * @throws ClassNotFoundException
	 */	
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		return loadSystemClass(name);
	}
}
