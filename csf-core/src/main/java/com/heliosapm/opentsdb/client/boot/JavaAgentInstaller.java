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

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.heliosapm.attachme.VirtualMachine;
import com.heliosapm.attachme.VirtualMachineDescriptor;

/**
 * <p>Title: JavaAgentInstaller</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.boot.JavaAgentInstaller</code></p>
 */

public class JavaAgentInstaller {

	/**
	 * Creates a new JavaAgentInstaller
	 */
	public JavaAgentInstaller() {
		// TODO Auto-generated constructor stub
	}
	
	public static void log(final Object fmt, final Object...args) {
		System.out.println(String.format(fmt.toString(), args));
	}
	
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
			loge("Usage: java com.heliosapm.opentsdb.client.boot.JavaAgentInstaller <PID | Name to match>");
		}
		final long pid;
		if(isPid(args[0])) {
			pid = Long.parseLong(args[0].trim());
		} else {
			pid = findPid(args[0]);
		}
		if(pid < 1) {
			System.exit((int)pid);
		}
		
		log("Installing JavaAgent to PID: %s from JAR: %s", pid, JavaAgentInstaller.class.getProtectionDomain().getCodeSource().getLocation());
		VirtualMachine vm = null;
		try {
			final String loc = new File(JavaAgentInstaller.class.getProtectionDomain().getCodeSource().getLocation().getFile()).getAbsolutePath();
			vm = VirtualMachine.attach("" + pid);
			log("Connected to process %s, loading Agent from %s", vm.id(), loc);
			if(args.length > 1) {
				StringBuilder b = new StringBuilder();
				for(int i = 1; i < args.length; i++) {
					b.append(args[i]).append("|~");
				}
				b.deleteCharAt(b.length()-1); b.deleteCharAt(b.length()-1);
				log("Loading with options [%s]", b);
				vm.loadAgent(loc, b.toString());
			} else {
				log("Loading with no options");
				vm.loadAgent(loc);
			}
			log("Agent loaded to process %s", vm.id());
			System.exit(0);
		} catch (Exception ex) {
			loge("Failed to attach to process %s. Stack trace follows...", pid);
			ex.printStackTrace(System.err);
		} finally {
			if(vm!=null) {
				try { vm.detach(); log("Detached from process %s", pid); } catch (Exception ex) {}
			}
		}		
	}
	
	// INSTALL SYSPROP !!
	
	private static long findPid(final String argZero) {
		Pattern p = Pattern.compile(argZero.trim());
		final Map<Long, String> matchedPids = new HashMap<Long, String>();
		final Map<Long, String> allPids = new HashMap<Long, String>();
		final long myPid = Long.parseLong(ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
		for(VirtualMachineDescriptor vmd: VirtualMachine.list()) {
			long pid = Long.parseLong(vmd.id().trim());
			if(pid==myPid) continue;
			allPids.put(pid, vmd.displayName());
			if(!p.matcher(vmd.displayName()).matches()) continue;
			matchedPids.put(pid, vmd.displayName());
		}
		if(matchedPids.isEmpty()) {
			log("Failed to find any JVM processes matching [%s]\nAvailable JVMs are:", argZero);
			for(Map.Entry<Long, String> entry: matchedPids.entrySet()) {
				log("\n\tPID: [%s], Display: [%s]", entry.getKey(), entry.getValue());
			}			
			return -1L;
		} else if(matchedPids.size() != 1) {
			log("Matched more than one JVM process. Processes found were:");
			for(Map.Entry<Long, String> entry: matchedPids.entrySet()) {
				log("\n\tPID: [%s], Display: [%s]", entry.getKey(), entry.getValue());
			}
			return -2L;
		} else {
			return matchedPids.keySet().iterator().next();
		}
	}
	
	

}
