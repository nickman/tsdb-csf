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
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.jmxmp.JMXMPConnectorServer;

import org.w3c.dom.Node;

import com.heliosapm.opentsdb.client.util.JMXHelper;
import com.heliosapm.opentsdb.client.util.URLHelper;
import com.heliosapm.opentsdb.client.util.XMLHelper;

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
	
	/**
	 * Boots the XMLConfig
	 * @param source The soure of the XML. Can be a URL, URI or file name
	 */
	public static void boot(final String source) {
		log("XMLLoader: [%s]", source);
		if(source==null || source.trim().isEmpty()) return;
		final URL url = URLHelper.toURL(source.trim());
		if(!URLHelper.resolves(url)) {
			loge("Cannot read from [%s]", url);
		}
		try {
			final Node configRoot = XMLHelper.parseXML(url).getDocumentElement();
			loadSysProps(XMLHelper.getChildNodeByName(configRoot, "sysprops"));
			initLogging();
			jmxmpServer(configRoot);
			initJmxCollector(configRoot);
			initAop(configRoot);			
			
		} catch (Exception ex) {
			loge("Failed to parse configuration [%s]. Stack trace follows:", url);
			ex.printStackTrace(System.err);
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
			for(Node jmxmp: XMLHelper.getChildNodesByName(jmxmpNode, "server", false)) {
				String iface = XMLHelper.getAttributeByName(jmxmp, "iface", "127.0.0.1");
				int port = XMLHelper.getAttributeByName(jmxmp, "port", -1);
				if(port==-1) continue;
				String jmxDomain = XMLHelper.getAttributeByName(jmxmp, "jmxdomain", "DefaultDomain");
				try {
					JMXServiceURL surl = new JMXServiceURL("jmxmp", iface, port);
					MBeanServer mbs = JMXHelper.getLocalMBeanServer(true, jmxDomain);
					if(mbs==null) continue;
					JMXMPConnectorServer jmxmpServer = new JMXMPConnectorServer(surl, null, mbs);
					jmxmpServer.start();
					log("Started JMXMP Server on [%s]", surl);
				} catch (Exception ex) {
					loge("Failed to load JMXMP Server. Stack trace follows:");
					ex.printStackTrace(System.err);								
				}
				
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
				final Class<?> transformerClass = Class.forName("com.heliosapm.opentsdb.client.aoplite.RetransformerLite");
				Object retransformer = transformerClass.getDeclaredMethod("getInstance").invoke(null);
				if(retransformer!=null) {
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
			final Node platformNode = XMLHelper.getChildNodeByName(rootConfigNode, "platform-mbeanobserver");
			if(platformNode!=null) {
				long period = XMLHelper.getLongAttributeByName(platformNode, "period", 15L);
				final Class<?> observerClass = Class.forName("com.heliosapm.opentsdb.client.jvmjmx.MBeanObserverSet");
				observerClass.getDeclaredMethod("build", MBeanServer.class, long.class, TimeUnit.class)
					.invoke(null, JMXHelper.getHeliosMBeanServer(), period, TimeUnit.SECONDS);
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
			final Class<?> logConfClazz = Class.forName("com.heliosapm.opentsdb.client.logging.LoggingConfiguration");
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
