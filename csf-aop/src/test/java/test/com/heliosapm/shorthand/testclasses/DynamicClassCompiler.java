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
package test.com.heliosapm.shorthand.testclasses;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;

/**
 * <p>Title: DynamicClassCompiler</p>
 * <p>Description: A test utility for generating dynamic classes</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.shorthand.testclasses.DynamicClassCompiler</code></p>
 */
public class DynamicClassCompiler {
	
	/** The debug class output directory */
	public static final String DEBUG_CLASS_DIR;
	
	static {
		String tmp = System.getProperty("java.io.tmpdir");
		if(!tmp.endsWith(File.separator)) tmp = tmp + File.separator;
		tmp = tmp + "js";
		DEBUG_CLASS_DIR = tmp;
		System.out.println("DEBUG Class Dir:" + DEBUG_CLASS_DIR);
		File f = new File(DEBUG_CLASS_DIR);
		if(!f.exists()) {
			f.mkdirs();
		}
	}
	
	
	/**
	 * Generates a class that simply extends the passed parent
	 * @param name The name of the new class
	 * @param parent The parent class to extend or implement
	 * @return the URL where the class can be classloaded from
	 */
	public static URL generateClass(String name, Class<?> parent) {
		final String urlKey = name + ".jar";
//		URL _url = BufferManager.getInstance().getMemBufferURL(urlKey);
		
		ClassPool cp = new ClassPool();
		cp.appendSystemPath();
		cp.appendClassPath(new ClassClassPath(parent));
		try {
			URL _url = File.createTempFile(".tmpClazz", urlKey).toURI().toURL();
			CtClass parentClazz = cp.get(parent.getName());
			CtClass clazz = cp.makeClass(name, parentClazz);
			JarOutputStream jos = new JarOutputStream(_url.openConnection().getOutputStream(), new Manifest());
			String entryName = name.replace('.', '/') + ".class";
			jos.putNextEntry(new JarEntry(entryName));
			byte[] byteCode = clazz.toBytecode();
			System.out.println("Byte code size for [" + name + "]:" + byteCode.length);
			jos.write(clazz.toBytecode());
			jos.closeEntry();
			jos.flush();
			jos.finish();
			jos.close();
			clazz.writeFile(DEBUG_CLASS_DIR);
			clazz.detach();
			parentClazz.detach();						
//			BufferManager.getInstance().registerMemBuffer(_url, memBuffer);
			return _url;
		} catch (IOException ex) {
			throw new RuntimeException("Failed to write dynamic class [" + name + "] bytecode to membuffer", ex);
		} catch (CannotCompileException cex) {
			throw new RuntimeException("Failed to compile dynamic class [" + name + "]", cex);
		} catch (NotFoundException nfe) {
			throw new RuntimeException("Failed to load CtClass from ClassPool for [" + parent.getName() + "]", nfe);
		}
	}
}
