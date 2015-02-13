/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2007, Helios Development Group and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org. 
 *
 */
package com.heliosapm.attachme.agent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;

import com.heliosapm.attachme.VirtualMachine;
import com.heliosapm.attachme.VirtualMachineBootstrap;
import com.heliosapm.attachme.jar.FileCleaner;
import com.heliosapm.attachme.jar.JarBuilder;

/**
 * <p>Title: LocalAgentInstaller</p>
 * <p>Description: Local java agent installer.</p>
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><b><code>com.heliosapm.attachme.agent.LocalAgentInstaller</code></b>
 */
public class LocalAgentInstaller  {
	/** The created agent jar file name */
	protected static final AtomicReference<String> agentJar = new AtomicReference<String>(null); 
	/** The shorthand agent class name */
	public static final String AGENT_MAIN_NAME = "com.heliosapm.attachme.agent.AgentInstrumentation";
	
	/**
	 * Simple example of the install commands executed.
	 * @param args None
	 */
	public static void main(String[] args) {
		String fileName = createAgent();
		String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
		VirtualMachineBootstrap.getInstance();
		VirtualMachine vm = VirtualMachine.attach(pid);
		vm.loadAgent(fileName);
		System.out.println("Instrumentation Deployed:" + ManagementFactory.getPlatformMBeanServer().isRegistered(AgentInstrumentation.AGENT_INSTR_ON));
		Instrumentation instr = getInstrumentation();
		System.out.println("Instrumentation:" + instr);
		try {
			Field f = instr.getClass().getDeclaredField("mNativeAgent");
			f.setAccessible(true);
			long address = f.getLong(instr);
			System.out.println("mNativeAgent:" + address + "  Hex:" + Long.toHexString(address));
			System.out.println("PID:" + ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
			Thread.currentThread().join();
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
		}
	}
	
	/**
	 * Installs the local agent and returns the Instrumentation.
	 * @return the installed instrumentation
	 */
	public synchronized static Instrumentation getInstrumentation() {
		return getInstrumentation(500);
	}
	
	
	/**
	 * Checks the shorthand agent instance to see if it has already loaded the instrumentation.
	 * If so, returns it.
	 * @return the instrumentation if found, otherwise null
	 */
	private static Instrumentation getAgentInstalledInstrumentation() {
		try {
			Class<?> locatorClass = Class.forName(AGENT_MAIN_NAME);
			return (Instrumentation) locatorClass.getDeclaredMethod("getInstrumentation").invoke(null);
		} catch (Exception ex) {
			return null;
		}
	}
	
	/**
	 * Installs the local agent and returns the Instrumentation.
	 * @param timeout The time to wait for the MBean to deploy
	 * @return the installed instrumentation
	 */
	public synchronized static Instrumentation getInstrumentation(long timeout) {
		Instrumentation instr = null;
		instr = getAgentInstalledInstrumentation();
		if(instr!=null) return instr;
		try {
			instr = (Instrumentation)ManagementFactory.getPlatformMBeanServer().getAttribute(AgentInstrumentation.AGENT_INSTR_ON, "Instrumentation");
			if(instr!=null) {
				return instr;
			}
		} catch (Exception e) {/* No Op */}
		
		String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
		VirtualMachineBootstrap.getInstance();
		VirtualMachine vm = VirtualMachine.attach(pid);
		String fileName = createAgent();
		final CountDownLatch latch = new CountDownLatch(1);
		final NotificationListener listener = new NotificationListener(){
			@Override
			public void handleNotification(Notification notification, Object handback) {
				if(((MBeanServerNotification)notification).getMBeanName().equals(AgentInstrumentation.AGENT_INSTR_ON)) {
					latch.countDown();
				}
			}
		};
		try {
			
			ManagementFactory.getPlatformMBeanServer().addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, listener, new NotificationFilter(){
				/**  */
				private static final long serialVersionUID = -1772307207532733639L;

				@Override
				public boolean isNotificationEnabled(Notification notification) {				
					return (notification instanceof MBeanServerNotification) && notification.getType().equals(MBeanServerNotification.REGISTRATION_NOTIFICATION);
				}
			}, null);
			vm.loadAgent(fileName, ""); // FIXME: pass the id of the MBeanServer to register with. Null means platform.
			if(!latch.await(timeout, TimeUnit.MILLISECONDS)) {
				throw new Exception("Timed out after waiting [" + timeout + "] ms. for AgentInstrumentation MBean Deployment");
			}
			return (Instrumentation)ManagementFactory.getPlatformMBeanServer().getAttribute(AgentInstrumentation.AGENT_INSTR_ON, "Instrumentation");
		} catch (Exception e) {
			throw new RuntimeException("Failed to acquire instrumentation", e);
		} finally {
			try { ManagementFactory.getPlatformMBeanServer().removeNotificationListener(AgentInstrumentation.AGENT_INSTR_ON, listener); } catch (Exception e) {/* No Op */}
		}
		
	}
	
	/**
	 * Creates an agent jar and writes it to the passed output stream
	 * @param os The output stream to write to
	 * @param manifestAttributes The attributes to include in the jar manifest
	 * @param resources A map of resources to include in the jar, keyed by the path of the resource (not implemented yet)
	 * @param jarClasses The classes to add to the jar
	 */
	public static void streamAgent(final OutputStream os, final Map<String, String> manifestAttributes, final Map<String, byte[]> resources, final Class<?>...jarClasses) {
		JarOutputStream jos = null;
		try {
			StringBuilder manifest = new StringBuilder("Manifest-Version: 1.0\n");
			for(Map.Entry<String, String> entry: manifestAttributes.entrySet()) {
				manifest.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
			}
			ByteArrayInputStream bais = new ByteArrayInputStream(manifest.toString().getBytes());
			Manifest mf = new Manifest(bais);
			jos = new JarOutputStream(os, mf);
			addClassesToJar(jos, AgentInstrumentation.class, AgentInstrumentationMBean.class);
			jos.flush();
			jos.close();
			jos = null;
		} catch (Exception ex) {
			throw new RuntimeException("Failed to write out agent jar", ex);
		} finally {
			if(jos!=null) try { jos.close(); } catch (Exception e) {/* No Op */}
		}
	}
	
	
	/**
	 * Creates the temporary agent jar file if it has not been created
	 * @return The created agent file name
	 */
	public static String createAgent() {
		if(agentJar.get()==null) {
			synchronized(agentJar) {
				if(agentJar.get()==null) {
					FileOutputStream fos = null;
					JarBuilder jb = JarBuilder.newInstance(9);
					try {
						File tmpFile = File.createTempFile(LocalAgentInstaller.class.getName(), ".jar");
						System.out.println("Temp File:" + tmpFile.getAbsolutePath());
						FileCleaner.getInstance().deleteOnExit(tmpFile);
						jb.addManifestEntry("Agent-Class", AgentInstrumentation.class.getName())
						.addManifestEntry("Can-Redefine-Classes", true)
						.addManifestEntry("Can-Retransform-Classes", true)
						.addManifestEntry("Premain-Class", AgentInstrumentation.class.getName());
						jb.addClasses(AgentInstrumentation.class, AgentInstrumentationMBean.class);
						fos = new FileOutputStream(tmpFile, false);
						jb.writeJar(fos);
						fos.flush();
						fos.close();
						agentJar.set(tmpFile.getAbsolutePath());
					} catch (Exception e) {
						throw new RuntimeException("Failed to write Agent installer Jar", e);
					} finally {
						if(fos!=null) try { fos.close(); } catch (Exception e) {/* No Op */}
					}

				}
			}
		}
		return agentJar.get();
	}
	
	/**
	 * Writes the passed classes to the passed JarOutputStream
	 * @param jos the JarOutputStream
	 * @param clazzes The classes to write
	 * @throws IOException on an IOException
	 */
	protected static void addClassesToJar(JarOutputStream jos, Class<?>...clazzes) throws IOException {
		for(Class<?> clazz: clazzes) {
			jos.putNextEntry(new ZipEntry(clazz.getName().replace('.', '/') + ".class"));
			jos.write(getClassBytes(clazz));
			jos.flush();
			jos.closeEntry();
		}
	}
	
	/**
	 * Returns the bytecode bytes for the passed class
	 * @param clazz The class to get the bytecode for
	 * @return a byte array of bytecode for the passed class
	 */
	public static byte[] getClassBytes(Class<?> clazz) {
		InputStream is = null;
		try {
			is = clazz.getClassLoader().getResourceAsStream(clazz.getName().replace('.', '/') + ".class");
			ByteArrayOutputStream baos = new ByteArrayOutputStream(is.available());
			byte[] buffer = new byte[8092];
			int bytesRead = -1;
			while((bytesRead = is.read(buffer))!=-1) {
				baos.write(buffer, 0, bytesRead);
			}
			baos.flush();
			return baos.toByteArray();
		} catch (Exception e) {
			throw new RuntimeException("Failed to read class bytes for [" + clazz.getName() + "]", e);
		} finally {
			if(is!=null) { try { is.close(); } catch (Exception e) {/* No Op */} }
		}
	}


}


/*
Groovy code attempting to take an exiting jar file representing byte array and adding an entry to it
without writing out to a physical file system.  (Does not work)
import org.helios.gmx.classloading.ReverseClassLoader;
import java.util.jar.*;
import java.util.zip.*;

rcl = ReverseClassLoader.getInstance();
byte[] jarBytes = rcl.jarContent;
println "JAR Bytes:${jarBytes.length}";
jb4Bytes = rcl.getClass().getClassLoader().getResourceAsStream("jboss/jboss4/jboss-service.xml").getBytes();
println "JB4 Bytes:${jb4Bytes.length}";
File f = new File("/tmp/gmx.jar");
f.delete();
FileOutputStream fos = null;
ZipOutputStream jos = null;
try {
    fos = new FileOutputStream(f);
    jos = new ZipOutputStream(fos);
    
    fos.write(jarBytes);
    jos.putNextEntry(new ZipEntry("jboss-service.xml")); jos.write(jb4Bytes, 0, jb4Bytes.length); jos.closeEntry(); jos.flush();
    fos.flush();
    fos.close();
    println "File Writen";
} finally {
    try { jos.flush(); } catch (e) {}
    try { jos.close(); } catch (e) {}
    try { fos.flush(); } catch (e) {}
    try { fos.close(); } catch (e) {}
}
return null;



*/