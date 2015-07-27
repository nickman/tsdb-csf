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

import java.io.File;
import java.io.StringReader;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.MBeanServer;
import javax.management.remote.JMXServiceURL;

import org.w3c.dom.Node;

import com.heliosapm.hub.JVMMatch.JMatch;
import com.heliosapm.shorthand.attach.vm.AttachProvider;
import com.heliosapm.shorthand.attach.vm.VirtualMachine;
import com.heliosapm.shorthand.attach.vm.VirtualMachineBootstrap;
import com.heliosapm.shorthand.attach.vm.VirtualMachineDescriptor;
import com.heliosapm.utils.io.StdInCommandHandler;
import com.heliosapm.utils.jmx.JMXHelper;
import com.heliosapm.utils.xml.XMLHelper;
import com.sun.jdmk.remote.cascading.CascadingService;

/**
 * <p>Title: HubMain</p>
 * <p>Description: Entry point to boot a new hub process</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.hub.HubMain</code></p>
 */

public class HubMain implements Runnable {
	/** Our platform MBeanServer */
	protected static final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
	
	protected static final String MY_PID = ManagementFactory.getRuntimeMXBean().getName().split("@")[0]; 
	
	/** The tracked virtual machine */
	protected final Map<String, MountedJVM> virtualMachines = new ConcurrentHashMap<String, MountedJVM>();
	
	protected CascadingService cascadeService; 
	protected final AtomicBoolean running = new AtomicBoolean(false);
	protected final AtomicBoolean scanning = new AtomicBoolean(false);
	private JMatch[] matchers;
	
	
	/**
	 * Creates a new HubMain
	 * @param args Command line args
	 */
	public HubMain(final String[] args) {
		VirtualMachineBootstrap.getInstance();
		if(args.length==0) {
			dump();
			return;
		}
		File f = new File(args[0]);
		if(!f.canRead()) {
			System.err.println("The specified config file cannot be read [" + args[0] + "]");
			return;
		}
		final Node node = XMLHelper.parseXML(f).getDocumentElement();
		configure(node);
		matchers = JMatch.fromNode(node);
		cascadeService = new CascadingService(server);
		JMXHelper.registerMBean(cascadeService, CascadingService.CASCADING_SERVICE_DEFAULT_NAME);
		log("\n\t====================================\n\tCSF Hub Started\n\t====================================");
		final Thread main = Thread.currentThread();
		final Thread scanner = new Thread(this, "JVMScanner");
		scanner.setDaemon(true);
		scanner.start();
//		StdInCommandHandler.getInstance().registerCommand("x", new Runnable(){
//			public void run() {
//				main.interrupt();
//			}
//		});
		try { Thread.currentThread().join(); } catch (Exception ex) {/* No Op */}
		log("CSF Hub Shutting down...");
	}
	
	public void run() {
		while(true) {
			try { scan(); } catch (Exception x) {/* No Op */}
			try { Thread.currentThread().join(5000); } catch (Exception x) {/* No Op */}
		}
	}
	
		
	
	/**
	 * Scans the detected running JVMs
	 */
	protected void scan() {
		if(scanning.compareAndSet(false, true)) {
			try {
				for(VirtualMachineDescriptor vmd: VirtualMachine.list()) {
					final String descr = vmd.id() + "@" + vmd.displayName();
					final String displayName = vmd.displayName();
					final String id = vmd.id();
					try {
						if(MY_PID.equals(id) || virtualMachines.containsKey(id) || displayName.toLowerCase().contains("csf-hub")) continue;
						VirtualMachine jvm = null;						
						try {
							jvm = vmd.provider().attachVirtualMachine(vmd.id());
							for(JMatch j: matchers) {
								try {
									if(j.match(vmd.displayName(), jvm, j.getMatch(), j.getMatchKey())) {
										final MountedJVM vm = new MountedJVM(vmd, cascadeService, virtualMachines, j);
										virtualMachines.put(vm.getId(), vm);
										log("Mounted JVM [%s] : [%s]", vm.getId(), vm.getDisplayName());
										if(vm.readyForPlatform()) {
											vm.enableCollectors();
										}										
										break;
									}
									
								} catch (Exception ex) {
									loge("Matcher exception (Programmer Error) Stack trace follows:");
									ex.printStackTrace(System.err);
								}
							}
						} finally {
							if(jvm!=null) try { jvm.detach(); } catch (Exception x) {/* No Op */}
						}
					} catch (Exception ex) {
						loge("Scan failure on [%s]: %s. My PID is [%s]. Stack trace follows....", descr, ex, MY_PID);
						ex.printStackTrace(System.err);
					}
				}
				for(MountedJVM vm: virtualMachines.values()) {
					if(vm.readyForPlatform()) {
						vm.enableCollectors();
					}										
				}
			} finally {
				scanning.set(false);
			}
		}
	}
	
	/**
	 * Configures the hub based on the passed node
	 * @param configNode The configuration node
	 */
	protected void configure(final Node configNode) {
		try {
			final Node jmxmpNode = XMLHelper.getChildNodeByName(configNode, "jmxmp");
			if(jmxmpNode!=null) {
				try {
					final Class<?> installerClass = Class.forName("com.heliosapm.opentsdb.client.jvmjmx.JMXMPInstaller");
					installerClass.getDeclaredMethod("installJMXMPServer", Node.class).invoke(null, jmxmpNode);
				} catch (Exception ex) {
					loge("Failed to install JMXMP Connector Servers. Stack trace follows:");
					ex.printStackTrace(System.err);							
				}
			}			
		} catch (Exception ex) {
			loge("Failed to boot JMXMP servers. Stack trace follows.");
			ex.printStackTrace(System.err);
		}
		loadSysProps(XMLHelper.getChildNodeByName(configNode, "sysprops"));		
	}
	

	
	

	/**
	 * Reads the XML config sysprops and sets them in this system
	 * @param node The sysprops config node
	 */
	private static void loadSysProps(final Node node) {
		if(node==null) return;
		log("Loading SysProps");
		String sysPropsCData = XMLHelper.getNodeTextValue(node);
		log("SysProps RAW:\n %s", sysPropsCData);
		if(sysPropsCData==null || sysPropsCData.trim().isEmpty()) return;
		sysPropsCData = sysPropsCData.trim();		
		try {
			Properties p = new Properties();
			p.load(new StringReader(sysPropsCData));
			StringBuilder b = new StringBuilder();
			if(!p.isEmpty()) {
				for(final String key: p.stringPropertyNames()) {
					final String value = p.getProperty(key);
					b.append("\n\t").append(key).append(" : ").append(value);
					System.setProperty(key, value);
				}
				log("XMLConfig set System Properties:%s", b.toString());
			}
		} catch (Exception ex) {
			loge("Failed to read sysprops from XML. Stack trace follows:");
			ex.printStackTrace(System.err);
		}
		
	}
	
	
	/**
	 * Dumps information about each discovered VM
	 */
	public void dump() {		
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
				b.append("\n\t\tAgentProps:");
				for(Map.Entry<String, String> entry: toMap(agentProps).entrySet()) {
					b.append("\n\t\t\t").append(entry.getKey()).append(" : ").append(entry.getValue());
				}				
				b.append("\n\t\tSystemProps:");
				for(Map.Entry<String, String> entry: toMap(systemProps).entrySet()) {
					b.append("\n\t\t\t").append(entry.getKey()).append(" : ").append(entry.getValue());
				}				
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
	
	private static TreeMap<String, String> toMap(final Properties p) {
		final TreeMap<String, String> t = new TreeMap<String, String>();
		for(String key: p.stringPropertyNames()) {
			t.put(key, p.getProperty(key));
		}
		return t;
	}

	/**
	 * @param args
	 */
	public static void main(final String[] args) {
		log("Starting CSF Hub\n\tArgs: %s", Arrays.toString(args));
		
		final HubMain hm = new HubMain(args);
	}
	
	public static void log(final Object fmt, final Object...args) {
		if(args.length==0) {
			System.out.println("[HubMain]" + fmt.toString());
		} else {
			System.out.printf("[HubMain]" + fmt.toString() + "\n", args);
		}
	}
	
	public static void loge(final Object fmt, final Object...args) {
		if(args.length==0) {
			System.err.println("[HubMain]" + fmt.toString());
		} else {
			System.err.printf("[HubMain]" + fmt.toString() + "\n", args);
		}
	}
	

}
