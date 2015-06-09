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

import java.io.StringReader;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.remote.JMXServiceURL;

import org.w3c.dom.Node;

import com.heliosapm.utils.jmx.JMXHelper;
import com.heliosapm.utils.url.URLHelper;
import com.heliosapm.utils.xml.XMLHelper;

/**
 * <p>Title: XMLLoader</p>
 * <p>Description: Loads csf configuration from an XML resource</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.boot.XMLLoader</code></p>
 */

public class XMLLoader {
	
	/** The UTF8 char set */
	public static final Charset UTF8 = Charset.forName("UTF8");
	
	/** The default instrumentation provider package */
	public static final String DEFAULT_INSTR_PACKAGE = "com.heliosapm.opentsdb.instrumentation";
	
	/** The MBeanServer tsf will deploy it's MBeans to */
	public static MBeanServer deploymentMBeanServer = null;
	/** The HeiosMBeanServer if one was created  */
	public static Object heliosMBeanServer = null;

	public static ClassLoader agentClassLoader = null;
	
	/**
	 * Boots the XMLConfig
	 * @param source The soure of the XML. Can be a URL, URI or file name
	 */
	public static void boot(final String source) {
		agentClassLoader = Thread.currentThread().getContextClassLoader();
		log("XMLLoader: [%s]", source);
		log("ClassLoader: [%s]", agentClassLoader);
		log("CurrentThread: [%s]", Thread.currentThread());
		if(source==null || source.trim().isEmpty()) return;
		final URL url = URLHelper.toURL(source.trim());
		if(!URLHelper.resolves(url)) {
			loge("Cannot read from [%s]", url);
		}
		try {
			final Node configRoot = XMLHelper.parseXML(url).getDocumentElement();
			final String heliosDomain = XMLHelper.getAttributeByName(configRoot, "domain", "DefaultDomain");
			for(MBeanServer mbs: MBeanServerFactory.findMBeanServer(null)) {
				String dd = mbs.getDefaultDomain();
				if(dd==null) dd = "DefaultDomain";
				if(heliosDomain.equals(dd)) {
					deploymentMBeanServer = mbs;
					break;
				}
			}						
			if(deploymentMBeanServer==null) {
				createHeliosMBean(heliosDomain);
			}
			System.setProperty("tsdb.jmx.server.domain", heliosDomain);
			
			
			
			loadSysProps(XMLHelper.getChildNodeByName(configRoot, "sysprops"));
			initLogging();			
			jmxmpServer(configRoot);
			loadResources(configRoot);
			initJmxCollector(configRoot);
			initCustomJMXCollectors(configRoot);
			initAop(configRoot);						
		} catch (Exception ex) {
			loge("Failed to parse configuration [%s]. Stack trace follows:", url);
			ex.printStackTrace(System.err);
		}
	}
	
	protected static void initCustomJMXCollectors(final Node configRoot) {
		final Node customJMXNode = XMLHelper.getChildNodeByName(configRoot, "customjmx", false);
		if(customJMXNode!=null) {
			try {
				Class<?> clazz = Class.forName("com.heliosapm.opentsdb.client.jvmjmx.custom.CustomMBeanMonitorInstaller");
				clazz.getDeclaredMethod("getInstance", Node.class).invoke(null, customJMXNode);
			} catch (Exception ex) {
				loge("Failed to install CustomJMXCollectors: %s", ex.toString());
			}
		}
	}
	
	/**
	 * Creates a seperate helios domain MBeanServer
	 * @param domain The default domain of the new MBeanServer
	 */
	protected static void createHeliosMBean(final String domain) {
		try {
			Class<?> clazz = Class.forName("com.heliosapm.opentsdb.client.util.HeliosMBeanServer");
			Constructor<?> ctor = clazz.getDeclaredConstructor(String.class, boolean.class, JMXServiceURL.class);
			heliosMBeanServer = ctor.newInstance(domain, true, null);
			deploymentMBeanServer = (MBeanServer)clazz.getDeclaredMethod("getMBeanServer").invoke(heliosMBeanServer);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
	
	/**
	 * Loads and executes the agent AOP transformations
	 * @param retransformer The retransformer instance
	 * @param node The transformation configuration node
	 */
	public static void loadAop(final Object retransformer, final Node node) {
		if(retransformer==null) {
			loge("Failed to load transformations. Retransformer was null.");
			return;
		}
		try {
			Method instrument = retransformer.getClass().getDeclaredMethod("instrument", String.class, String.class);
			Method instrumentWithCl = retransformer.getClass().getDeclaredMethod("instrument", String.class, String.class, String.class);
			for(Node inode: XMLHelper.getChildNodesByName(node, "instr", false)) {
				final String className = XMLHelper.getAttributeValueByName(inode, "class");
				try {
					final String classLoader = XMLHelper.getAttributeValueByName(inode, "classloader");
					Object cnt = null;
					if(classLoader != null && !classLoader.trim().isEmpty()) {
						cnt = instrumentWithCl.invoke(retransformer, 
								classLoader,
								className, 
								XMLHelper.getAttributeValueByName(inode, "methods")
							);
					} else {
						cnt = instrument.invoke(retransformer, 
								className, 
								XMLHelper.getAttributeValueByName(inode, "methods")
							);
					}
					
					log("Intrumented [%s] methods in [%s]", cnt, className);
				} catch (Exception ex) {
					loge("Failed to transform class [%s]: [%s]", className, ex);
				}
			}
			// instr 
		} catch (Exception ex) {
			loge("Failed to load transformations. Stack trace follows:");
			ex.printStackTrace(System.err);
		}		
	}
	
	/**
	 * Loads classloader and other resource type definitions
	 * @param configRoot The configuration root
	 */
	public static void loadResources(final Node configRoot) {
		final List<Node> resourceNodes = new ArrayList<Node>();
		for(Node parent: XMLHelper.getChildNodesByName(configRoot, "resources", false)) {
			for(Node resource: XMLHelper.getChildNodesByName(parent, "resource", false)) {
				resourceNodes.add(resource);
			}
		}
		if(resourceNodes.isEmpty()) return;
		final Method configMethod;
		try {
			final Class<?> classLoaderRepoClass = Class.forName("com.heliosapm.opentsdb.client.classloaders.ClassLoaderRepository");				
			configMethod = classLoaderRepoClass.getDeclaredMethod("config", Node.class);
		} catch (Exception ex) {
			loge("Failed to install resources. Stack trace follows:");
			ex.printStackTrace(System.err);		
			return;
		}				

		for(Node resourceNode: resourceNodes) {
			try {
				configMethod.invoke(null, resourceNode);
			} catch (Exception ex) {
				loge("Failed to install resource node [" + XMLHelper.renderNode(resourceNode) + "]", ex);
			}
		}
	}
	
	/**
	 * Installs a JMXMP Connector Server for the configured MBeanServer
	 * @param rootConfigNode the root configuration node
	 * <p>Example: <b><code> 
			&lt;jmxmp&gt;
			    &lt;server port="18011" iface="0.0.0.0" jmxdomain="DefaultDomain"/&gt;
			&lt;/jmxmp&gt;
		</code></b>
	 */
	public static void jmxmpServer(final Node rootConfigNode) {
		final Node jmxmpNode = XMLHelper.getChildNodeByName(rootConfigNode, "jmxmp");
		if(jmxmpNode!=null) {
			try {
				final Class<?> installerClass = Class.forName("com.heliosapm.opentsdb.client.jvmjmx.JMXMPInstaller");
				installerClass.getDeclaredMethod("installJMXMPServer", Node.class).invoke(null, jmxmpNode);
			} catch (Exception ex) {
				loge("Failed to install JMXMP Connector Servers. Stack trace follows:");
				ex.printStackTrace(System.err);							
			}
		}
	}
	
	
	/**
	 * Initializes the agent AOP retransformer
	 * @param rootConfigNode The csf root configuration node
	 */
	public static void initAop(final Node rootConfigNode) {
		try {
			final Node liteNode = XMLHelper.getChildNodeByName(rootConfigNode, "lite-instrumentation");
			if(liteNode!=null) {
				// instrumentation // class
				final Node instrumentationNode = XMLHelper.getChildNodeByName(liteNode, "instrumentation");
				if(instrumentationNode!=null) {
					String provider = XMLHelper.getAttributeByName(instrumentationNode, "class", null);
					try {
						Class<?> providerClass = Class.forName(provider);
						Object instr = providerClass.getDeclaredMethod("getInstrumentation").invoke(providerClass.getDeclaredField("INSTANCE").get(null));
						if(instr!=null && instr instanceof Instrumentation) {
							Instrumentation oldInstr = JavaAgent.INSTRUMENTATION;
							JavaAgent.INSTRUMENTATION = (Instrumentation)instr;
							log("Replaced [%s] with [%s]", oldInstr, instr);
						}
					} catch (Exception ex) {
						log("Failed to run InstrumentationProvider [%s] : %s", provider, ex);
//						ex.printStackTrace(System.err);
					}
				}
				final Class<?> transformerClass = Class.forName("com.heliosapm.opentsdb.client.aoplite.RetransformerLite");
				Object retransformer = transformerClass.getDeclaredMethod("getInstance").invoke(null);
				if(retransformer!=null) {
					if(XMLHelper.getChildNodeByName(liteNode, "cftransformerfix")!=null) {
						try {
							Object result = retransformer.getClass().getDeclaredMethod("switchTransformers").invoke(retransformer);
							log("Switched %s class file transformers", result);
						} catch (Exception ex) {
							loge("Failed to install cftransformerfix: %s", ex);
						}
					}
					loadAop(retransformer, liteNode);
				} else {
					loge("Failed to load transformations. Retransformer was null.");
				}
			}
		} catch (Exception ex) {
			loge("Failed to load Retransformer Lite. Stack trace follows:");
			ex.printStackTrace(System.err);			
		}		
	}
	
	/**
	 * Initializes the agent JMX collector
	 * @param rootConfigNode The csf root configuration node
	 */
	public static void initJmxCollector(final Node rootConfigNode) {
		try {
			final Node platformNode = XMLHelper.getChildNodeByName(rootConfigNode, "platform");
			if(platformNode!=null) {
				// MBeanObserverSet build(final RuntimeMBeanServerConnection mbeanServer, final Node xmlConfigNode) {
				final Class<?> observerClass = Class.forName("com.heliosapm.opentsdb.client.jvmjmx.MBeanObserverSet");
				observerClass.getDeclaredMethod("build", MBeanServerConnection.class, Node.class)
					.invoke(null, JMXHelper.getHeliosMBeanServer(), platformNode);
				log("Initialized tsdb-csf MXBeanObserver");				
			}
		} catch (Exception ex) {
			loge("Failed to initialize JMXCollector. Stack trace follows:");
			ex.printStackTrace(System.err);			
		}		
	}
	
	/**
	 * Initializes the agent logging
	 */
	public static void initLogging() {
		log("Initializing Agent Logging...");
		try {
			final Class<?> log4jServerClazz = Class.forName("org.apache.logging.log4j.core.jmx.Server", true, agentClassLoader);
			log4jServerClazz.getDeclaredMethod("reregisterMBeansAfterReconfigure", MBeanServer.class).invoke(null, deploymentMBeanServer);
			final Class<?> logConfClazz = Class.forName("com.heliosapm.opentsdb.client.logging.LoggingConfiguration", true, agentClassLoader);
			logConfClazz.getDeclaredMethod("getInstance").invoke(null);
			log("Initialized tsdb-csf Logging");
		} catch (Exception ex) {
			loge("Failed to initialize logging. Stack trace follows:");
			ex.printStackTrace(System.err);
		}
		
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
	 * System out format logger
	 * @param fmt The message format
	 * @param args The message args
	 */
	public static void log(final Object fmt, final Object...args) {
		System.out.println("[CSF-JavaAgent]" + String.format(fmt.toString(), args));
	}
	
	/**
	 * System err format logger
	 * @param fmt The message format
	 * @param args The message args
	 */
	public static void loge(final Object fmt, final Object...args) {
		System.err.println("[CSF-JavaAgent]" + String.format(fmt.toString(), args));
	}


	private XMLLoader() {}

}
