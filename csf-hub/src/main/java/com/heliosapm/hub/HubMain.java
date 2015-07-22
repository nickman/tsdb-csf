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
package com.heliosapm.hub;

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.MBeanServer;
import javax.management.remote.JMXServiceURL;

import com.heliosapm.shorthand.attach.vm.AttachProvider;
import com.heliosapm.shorthand.attach.vm.VirtualMachine;
import com.heliosapm.shorthand.attach.vm.VirtualMachineBootstrap;
import com.heliosapm.shorthand.attach.vm.VirtualMachineDescriptor;

/**
 * <p>Title: HubMain</p>
 * <p>Description: Entry point to boot a new hub process</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.hub.HubMain</code></p>
 */

public class HubMain {
	/** Our platform MBeanServer */
	protected static final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
	
	protected static final String MY_PID = ManagementFactory.getRuntimeMXBean().getName().split("@")[0]; 
	
	/** The tracked virtual machine */
	protected final Map<JMXServiceURL, VirtualMachine> virtualMachines = new ConcurrentHashMap<JMXServiceURL, VirtualMachine>();
	
	/**
	 * Creates a new HubMain
	 */
	public HubMain() {
		VirtualMachineBootstrap.getInstance();
		scan();
	}
	
	public void scan() {		
		final StringBuilder b = new StringBuilder("\n\t================================\n\tDiscovered Virtual Machines\n\t================================");
		for(VirtualMachineDescriptor vmd: VirtualMachine.list()) {
			VirtualMachine vm  = null;
			try {
				final String displayName = vmd.displayName();
				final String id = vmd.id();
				if(MY_PID.equals(id)) continue;
				final AttachProvider ap = vmd.provider();
				vm = ap.attachVirtualMachine(id);
				final Properties agentProps = vm.getAgentProperties();
				final Properties systemProps = vm.getSystemProperties();
				final JMXServiceURL serviceURL = vm.getJMXServiceURL();
				b.append("\n\t======= VM =======");
				b.append("\n\t\tDisplayName:").append(displayName);
				b.append("\n\t\tID:").append(id);
				b.append("\n\t\tJMX URL:").append(serviceURL);
				b.append("\n\t\tAgentProps:").append(agentProps);
			} catch (Exception ex) {
				log("Attach failure. Stack trace follows...");
				ex.printStackTrace(System.err);
			} finally {
				if(vm!=null) try { vm.detach(); } catch (Exception x) {/* No Op */}
				vm = null;
			}
			
			
		}
		log(b.toString());
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		log("Starting CSF Hub");
		final HubMain hm = new HubMain();
	}
	
	public static void log(final Object fmt, final Object...args) {
		if(args.length==0) {
			System.out.println("[HubMain]" + fmt.toString());
		} else {
			System.out.printf("[HubMain]" + fmt.toString(), args);
		}
	}

}
