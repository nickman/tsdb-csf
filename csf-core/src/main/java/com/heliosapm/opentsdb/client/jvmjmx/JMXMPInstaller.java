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
package com.heliosapm.opentsdb.client.jvmjmx;

import java.util.HashMap;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.jmxmp.JMXMPConnectorServer;

import org.w3c.dom.Node;

import com.heliosapm.utils.jmx.JMXHelper;
import com.heliosapm.utils.xml.XMLHelper;

/**
 * <p>Title: JMXMPInstaller</p>
 * <p>Description: Static utility class to install JMXMP connector servers</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.JMXMPInstaller</code></p>
 */

public class JMXMPInstaller {
	/** The IPv4 wildcard net interface */
	public static final String IP4_WILDCARD = "0.0.0.0";
	/** The IPv6 wildcard net interface */
	public static final String IP6_WILDCARD = "0:0:0:0:0:0:0:0";
	/** The default IPv4 net interface */
	public static final String DEFAULT_IPV4_IFACE = "127.0.0.1";
	/** The default IPv6 net interface */
	public static final String DEFAULT_IPV6_IFACE = "::1";
	
	/**
	 * Creates, installs and starts JMXMP connector servers 
	 * @param jmxmpNode The JMXMP server configuration. Looks like this:
	 * <p><b><code> 
			&lt;jmxmp&gt;
			    &lt;server port="18011" iface="0.0.0.0" jmxdomain="DefaultDomain"/&gt;
			&lt;/jmxmp&gt;
		</code></b>
	 */
	public static void installJMXMPServer(final Node jmxmpNode) {
		for(Node jmxmp: XMLHelper.getChildNodesByName(jmxmpNode, "server", false)) {
			String iface = XMLHelper.getAttributeByName(jmxmp, "iface", getDefaultAddress());
			int port = XMLHelper.getAttributeByName(jmxmp, "port", -1);
			if(port==-1) continue;
			String jmxDomain = XMLHelper.getAttributeByName(jmxmp, "jmxdomain", "DefaultDomain");
			try {
				JMXServiceURL surl = new JMXServiceURL("jmxmp", iface, port);
				MBeanServer mbs = JMXHelper.getLocalMBeanServer(true, jmxDomain);
				if(mbs==null) continue;
				final Map<String, String> env = new HashMap<String, String>();
				env.put(JMXMPConnectorServer.SERVER_ADDRESS_WILDCARD, "" + isWildcardAddress(iface));
				JMXMPConnectorServer jmxmpServer = new JMXMPConnectorServer(surl, env, mbs);
				// FIXME:  start in daemon thread
				jmxmpServer.start();
				log("Started JMXMP Server on [%s]", surl);
			} catch (Exception ex) {
				loge("Failed to load JMXMP Server. Stack trace follows:");
				ex.printStackTrace(System.err);								
			}			
		}		
	}
	
	/**
	 * Indicates if the passed iface address is a wildcard address
	 * @param iface The iface address to test
	 * @return true if a wildcard, false otherwise
	 */
	public static boolean isWildcardAddress(final String iface) {
		if(iface==null || iface.trim().isEmpty()) return false;
		return IP4_WILDCARD.equals(iface.trim()) || IP4_WILDCARD.equals(iface.trim()); 
	}
	
	/**
	 * Returns the appropriate default iface address depending on IP4 preference
	 * @return the default iface address 
	 */
	public static String getDefaultAddress() {
		return isIP4Preferred() ? DEFAULT_IPV4_IFACE : DEFAULT_IPV6_IFACE;
	}
	
	/**
	 * Indicates if this JVM is running with IPv4 preferred
	 * @return true if IPv4 is preferred, false otherwise
	 */
	public static boolean isIP4Preferred() {
		return System.getProperty("java.net.preferIPv4Stack", "false").trim().toLowerCase().equals("true");
	}
	
	/**
	 * System out format logger
	 * @param fmt The message format
	 * @param args The message args
	 */
	public static void log(final Object fmt, final Object...args) {
		System.out.println("[JMXMPInstaller]" + String.format(fmt.toString(), args));
	}
	
	/**
	 * System err format logger
	 * @param fmt The message format
	 * @param args The message args
	 */
	public static void loge(final Object fmt, final Object...args) {
		System.err.println("[JMXMPInstaller]" + String.format(fmt.toString(), args));
	}

	private JMXMPInstaller() {}

}
