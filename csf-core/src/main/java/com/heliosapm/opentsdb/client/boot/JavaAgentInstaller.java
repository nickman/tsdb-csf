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
package com.heliosapm.opentsdb.client.boot;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

import org.json.JSONArray;
import org.json.JSONObject;

import com.heliosapm.attachme.VirtualMachine;
import com.heliosapm.attachme.VirtualMachineDescriptor;
import com.heliosapm.utils.classload.IsolatedClassLoader;
import com.heliosapm.utils.classload.IsolatedClassLoaderMBean;
import com.heliosapm.utils.url.URLHelper;

/**
 * <p>Title: JavaAgentInstaller</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.boot.JavaAgentInstaller</code></p>
 */

public class JavaAgentInstaller {

	/** This JVMs PID as a long */
	public static final long myPid = Long.parseLong(ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
	/** This JVMs PID as a String */
	public static final String myId = Long.toString(myPid);
	/** The property set in system and agent props to indicate the agent is installed */
	public static final String AGENT_INSTALLED_PROP = "tsdb.csf.agent.installed"; 
	
	
	/**
	 * Creates a new JavaAgentInstaller
	 */
	public JavaAgentInstaller() {
		// TODO Auto-generated constructor stub
	}
	
	/**
	 * System out format logger
	 * @param fmt The message format
	 * @param args The message args
	 */
	public static void log(final Object fmt, final Object...args) {
		System.out.println(String.format(fmt.toString(), args));
	}
	
	/**
	 * System err format logger
	 * @param fmt The message format
	 * @param args The message args
	 */
	public static void loge(final Object fmt, final Object...args) {
		System.err.println(String.format(fmt.toString(), args));
	}
	private static boolean isPid(final String argZero) {
		try {
			Long.parseLong(argZero.trim());
			return true;
		} catch (Exception ex) {
			return false;
		}
	}
	

	
	public static void main(final String[] args) {
		if(args.length==0) {
			loge("Usage: java com.heliosapm.opentsdb.client.boot.JavaAgentInstaller \n\t<PID | Name to match> \n\t[-list | -listjson] \n\t[-p k=v] \n\t[-config URL|File]");
			return;
		}
		final long pid;
		if(isPid(args[0])) {
			pid = Long.parseLong(args[0].trim());
		} else if("-list".equalsIgnoreCase(args[0])) {
			printJVMs(myId);
			return;
		} else if("-listjson".equalsIgnoreCase(args[0])) {
			printJVMsInJSON(myId);
			return;			
		} else {
			pid = findPid(args[0]);
		}
		if(pid < 1) {
			System.exit(-1);
		}
		
		log("Installing JavaAgent to PID: %s from JAR: %s", pid, JavaAgentInstaller.class.getProtectionDomain().getCodeSource().getLocation());
		VirtualMachine vm = null;
		try {
			//final String loc = new File(JavaAgentInstaller.class.getProtectionDomain().getCodeSource().getLocation().getFile()).getAbsolutePath();
			final String loc = agentBootJar(false);
			vm = VirtualMachine.attach("" + pid);
			log("Connected to process %s, loading Agent from %s", vm.id(), loc);
			if(args.length > 1) {
				StringBuilder b = new StringBuilder();
				for(int i = 1; i < args.length; i++) {
					b.append(args[i]).append("|~");
					if("-config".equalsIgnoreCase(args[i])) {
						i++;
						final URL url = URLHelper.toURL(args[i]);
						b.append(url).append("|~");
					}
				}
				b.append(JavaAgentInstaller.class.getProtectionDomain().getCodeSource().getLocation());				
				log("Loading with options [%s]", b);
				vm.loadAgent(loc, b.toString());
			} else {
				log("Loading with no options");
				vm.loadAgent(loc);
			}
			log("Agent loaded to process %s", vm.id());
			System.exit((int)pid);
		} catch (Exception ex) {
			loge("Failed to attach to process %s. Stack trace follows...", pid);
			ex.printStackTrace(System.err);
		} finally {
			if(vm!=null) {
				try { vm.detach(); log("Detached from process %s", pid); } catch (Exception ex) {}
			}
		}		
	}
	
	/**
	 * Prints the running JVMs in plain text
	 * @param myId The id of this JVM so's we don't print it
	 */
	public static void printJVMs(final String myId) {
		for(VirtualMachineDescriptor vmd: VirtualMachine.list()) {
			if(myId.equals(vmd.id())) continue;
			String vmDisplay = vmd.displayName();
			if(vmDisplay==null || vmDisplay.trim().isEmpty()) {
				vmDisplay = "Unknown";
			}			
			log("\n\tPID: [%s], Display: [%s]", vmd.id(), vmDisplay);
		}		
	}
	
	/**
	 * Prints the running JVMs in plain text
	 * @param myId The id of this JVM so's we don't print it
	 */
	public static void printJVMsInJSON(final String myId) {
		final JSONArray arr = new JSONArray();
		for(VirtualMachineDescriptor vmd: VirtualMachine.list()) {
			if(myId.equals(vmd.id())) continue;
			String vmDisplay = vmd.displayName();
			if(vmDisplay==null || vmDisplay.trim().isEmpty()) {
				vmDisplay = "Unknown";
			}			
			JSONObject vm = new JSONObject();
			vm.put("pid", Long.parseLong(vmd.id().trim()));
			vm.put("display", vmDisplay); 
			arr.put(vm);
		}
		log(arr.toString(2));
	}
	
	public static String formatVMDisplay(final VirtualMachineDescriptor vmd) {
		String commandLine = vmd.displayName();
        int firstSpace = commandLine.indexOf(' ');
        if (firstSpace > 0) {
            commandLine = commandLine.substring(firstSpace + 1);
        }
        String arg0 = commandLine;
        int lastFileSeparator = arg0.lastIndexOf('/');
        if (lastFileSeparator > 0) {
             return arg0.substring(lastFileSeparator + 1);
        }

        lastFileSeparator = arg0.lastIndexOf('\\');
        if (lastFileSeparator > 0) {
             return arg0.substring(lastFileSeparator + 1);
        }

        int lastPackageSeparator = arg0.lastIndexOf('.');
        if (lastPackageSeparator > 0) {
             return arg0.substring(lastPackageSeparator + 1);
        }        
        return arg0;
	}
	
	private static long findPid(final String argZero) {
		Pattern p = Pattern.compile(argZero.trim());
		final Map<Long, String> matchedPids = new HashMap<Long, String>();
		final Map<Long, String> allPids = new HashMap<Long, String>();
		
		for(VirtualMachineDescriptor vmd: VirtualMachine.list()) {
			long pid = Long.parseLong(vmd.id().trim());
			if(pid==myPid) continue;
			String vmDisplay = 
					//formatVMDisplay(vmd);   
					vmd.displayName();
			if(vmDisplay==null || vmDisplay.trim().isEmpty()) {
				vmDisplay = "Unknown";
			}
					
			allPids.put(pid, vmDisplay);
			if(!p.matcher(vmDisplay).matches()) continue;
			matchedPids.put(pid, vmd.displayName());
		}
		if(matchedPids.isEmpty()) {
			log("\n\t=======================================================\n\tFailed to find any JVM processes matching [%s]. Available JVMs follow:\n\t=======================================================", argZero);
			for(Map.Entry<Long, String> entry: allPids.entrySet()) {
				log("\n\tPID: [%s], Display: [%s]", entry.getKey(), entry.getValue());
			}			
			return -1L;
		} else if(matchedPids.size() != 1) {
			log("\n\t=======================================================\n\tMatched more than one JVM process. Processes found follow:\n\t=======================================================");
			for(Map.Entry<Long, String> entry: matchedPids.entrySet()) {
				log("\n\tPID: [%s], Display: [%s]", entry.getKey(), entry.getValue());
			}
			return -2L;
		} else {
			return matchedPids.keySet().iterator().next();
		}
	}
	
	/**
	 * Creates the java agent jar file
	 * @param deleteOnExit true to delete the jar on this VM exit, false otherwise
	 */
	private static String agentBootJar(final boolean deleteOnExit) {
		FileOutputStream fos = null;
		JarOutputStream jos = null;
		File jarFile = null;
		try {
			jarFile = File.createTempFile("tsdb-csf-javaagent", ".jar");
			if(deleteOnExit) jarFile.deleteOnExit();
			StringBuilder manifest = new StringBuilder();
			manifest.append("Manifest-Version: 1.0\n");
			manifest.append("Premain-Class: ").append(JavaAgent.class.getName()).append("\n");
			manifest.append("Agent-Class: ").append(JavaAgent.class.getName()).append("\n");
			manifest.append("Can-Redefine-Classes: ").append("true\n");
			manifest.append("Can-Retransform-Classes: ").append("true\n");
			manifest.append("Can-Set-Native-Method-Prefix: ").append(JavaAgent.class.getName()).append("\n");
			manifest.append("Agent-Class: ").append(JavaAgent.class.getName()).append("\n");
			
			
			ByteArrayInputStream bais = new ByteArrayInputStream(manifest.toString().getBytes());
			Manifest mf = new Manifest(bais);
			fos = new FileOutputStream(jarFile, false);
			jos = new JarOutputStream(fos, mf);
			// =================
			writeClassesToJar(jos, JavaAgent.class, IsolatedClassLoader.class, IsolatedClassLoaderMBean.class);
			// =================			
			jos.close();
			fos.flush();
			fos.close();
			return jarFile.getAbsolutePath();
		} catch (Exception e) {
			throw new RuntimeException("Failed to Plugin Jar for [" + jarFile.getAbsolutePath() + "]", e);
		} finally {
			if(jos!=null) try { jos.close(); } catch (Exception e) {/* No Op */}
			if(fos!=null) try { fos.close(); } catch (Exception e) {/* No Op */}
			
		}		
		
	}


	/**
	 * Writes the byte code for the passed classes to a JAR output styream.
	 * @param jos The JAR output stream
	 * @param classes The classes to write
	 * @throws IOException on any IO exception
	 */
	public static void writeClassesToJar(final JarOutputStream jos, final Class<?>...classes) throws IOException {
		for(Class<?> clazz: classes) {
			for(Class<?> inner: clazz.getDeclaredClasses()) {
				writeClassesToJar(jos, inner);
			}
			InputStream clazzIn = null;
			try {
				String entryName = clazz.getName().replace('.', '/') + ".class";
				clazzIn = JavaAgentInstaller.class.getClassLoader().getResourceAsStream(entryName);								
				byte[] byteCode  = new byte[clazzIn.available()];
				clazzIn.read(byteCode);
				log("Loaded [%s] bytes for ["+ clazz.getSimpleName() +"] class", byteCode.length);				
				ZipEntry ze  = new ZipEntry(entryName);
				jos.putNextEntry(ze);
				jos.write(byteCode);
				jos.flush();
				jos.closeEntry();
			} finally {				
				if(clazzIn!=null) try { clazzIn.close(); } catch (Exception e) {/* No Op */}							
			}
		}
	}
	

}
