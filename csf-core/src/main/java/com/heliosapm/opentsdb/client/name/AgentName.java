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
package com.heliosapm.opentsdb.client.name;

import static com.heliosapm.opentsdb.client.opentsdb.Constants.APP_TAG;
import static com.heliosapm.opentsdb.client.opentsdb.Constants.HOST_TAG;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.ObjectName;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.heliosapm.opentsdb.client.logging.LoggingConfiguration;
import com.heliosapm.opentsdb.client.opentsdb.Constants;
import com.heliosapm.opentsdb.client.opentsdb.OffHeapFIFOFile;
import com.heliosapm.opentsdb.client.opentsdb.Threading;
import com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: AgentName</p>
 * <p>Description: The tsdb-csf agent host and name controller.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.name.AgentName</code></p>
 */

public class AgentName extends NotificationBroadcasterSupport  implements AgentNameMXBean {
	/** The singleton instance */
	private static volatile AgentName instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	
	private final AtomicReference<ByteBuffer[]> bufferizedAgentName = new AtomicReference<ByteBuffer[]>(null);
	
	
	/*
	 * Looks like this:
	 * ================
	 * <total size>	 
	 * <app tag size>
	 * <app tag bytes>
	 * <hots tag size>
	 * <hots tag bytes>
	 */
	
	
	
	/** A set of agent name change listeners */
	private final Set<AgentNameChangeListener> listeners = new CopyOnWriteArraySet<AgentNameChangeListener>();
	
    /** The global tags */
    final Map<String, String> GLOBAL_TAGS = new ConcurrentHashMap<String, String>(6);
	
	/** The cached app name */
	private volatile String appName = null;
	/** The cached host name */
	private volatile String hostName = null;
	
	/** Instance logger */
	private final Logger log;
	
	/** Notification serial number source */
	final AtomicLong notifSerial = new AtomicLong(0L);
	/** The JMX ObjectName */
	public final ObjectName OBJECT_NAME;
	
	/**
	 * Acquires the AgentName singleton instancecs
	 * @return the AgentName singleton instance
	 */
	public static AgentName getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new AgentName();
				}
			}
		}
		return instance;
	}
	
	private AgentName() {
		super(Threading.getInstance().getThreadPool(), NOTIF_INFOS);
		OBJECT_NAME = Util.objectName(Util.getJMXDomain() + ":service=AgentName");
		loadExtraTags();
		getAppName();
		getHostName();
		initBufferized();
		Util.registerMBean(this, OBJECT_NAME);
		sendInitialNotif();
		LoggingConfiguration.getInstance().initAppLogging(this);
		log = LogManager.getLogger(getClass());
	}
	
	/**  */
	private static final byte[] COMMA_BYTE = ",".getBytes(Constants.UTF8); 
	
	private synchronized void initBufferized() {		
		ByteBuffer[] buffs = new ByteBuffer[3];		
		final byte[] appTag = ("\"" + Constants.APP_TAG + "\":\"" + appName + "\"").getBytes(Constants.UTF8);
		final byte[] hostTag = ("\"" + Constants.HOST_TAG + "\":\"" + hostName + "\"").getBytes(Constants.UTF8);
		buffs[0] = (ByteBuffer)ByteBuffer.allocateDirect(appTag.length).put(appTag).flip();
		buffs[1] = (ByteBuffer)ByteBuffer.allocateDirect(hostTag.length).put(hostTag).flip();
		buffs[2] = (ByteBuffer)ByteBuffer.allocateDirect(appTag.length + hostTag.length + COMMA_BYTE.length).put(appTag).put(COMMA_BYTE).put(hostTag).flip();		
		final ByteBuffer[] oldBuff = bufferizedAgentName.getAndSet(buffs);
		OffHeapFIFOFile.clean(oldBuff);
	}

	private void sendInitialNotif() {
		final Notification n = new Notification(NOTIF_ASSIGNED, OBJECT_NAME, notifSerial.incrementAndGet(), System.currentTimeMillis(), "AgentName assigned [" + appName + "@" + hostName + "]");
		Map<String, String> userData = new HashMap<String, String>(2);
		userData.put(Constants.APP_TAG, appName);
		userData.put(Constants.HOST_TAG, hostName);
		n.setUserData(userData);
		sendNotification(n);
	}
	
	/**
	 * Loads sysprop defined extra tags 
	 */
	private void loadExtraTags() {
		String v = System.getProperty(Constants.PROP_EXTRA_TAGS, "").replace(" ", "");
		if(!v.isEmpty()) {
			Matcher m = Constants.KVP_PATTERN.matcher(v);
			while(m.find()) {
				String key = Util.clean(m.group(1));
				if(HOST_TAG.equalsIgnoreCase(key) || APP_TAG.equalsIgnoreCase(key)) continue;
				String value = Util.clean(m.group(1));
				GLOBAL_TAGS.put(key, value);
			}
			log.info("Initial Global Tags:{}", GLOBAL_TAGS);
		}
	}
	
	/**
	 * Returns the agent's global tags
	 * @return the agent's global tags
	 */
	public Map<String, String> getGlobalTags() {
		return new TreeMap<String, String>(GLOBAL_TAGS);
	}

	/**
	 * Attempts a series of methods of divining the host name
	 * @return the determined host name
	 */
	public String getHostName() {
		if(hostName!=null) {
			return hostName;
		}		
		hostName = hostName();		
		System.setProperty(Constants.PROP_HOST_NAME, hostName);
		GLOBAL_TAGS.put(HOST_TAG, hostName);
		return hostName;
	}
	
	/**
	 * Returns an id string displaying the host and app name
	 * @return the id string
	 */
	public String getId() {
		return getAppName() + "@" + getHostName();
	}
	
	/**
	 * Returns the agent name app tag, already prepped for serialization
	 * @return the agent name app tag buffer
	 */
	public ByteBuffer getAgentNameAppTagBuffer() {
		return bufferizedAgentName.get()[0].asReadOnlyBuffer();
	}
	
	/**
	 * Returns the agent name host tag, already prepped for serialization
	 * @return the agent name host tag buffer
	 */
	public ByteBuffer getAgentNameHostTagBuffer() {
		return bufferizedAgentName.get()[1].asReadOnlyBuffer();
	}
	
	/**
	 * Returns the agent name app and host tags, already prepped for serialization
	 * @return the agent name app and host tags buffer
	 */
	public ByteBuffer getAgentNameTagsBuffer() {
		return bufferizedAgentName.get()[2].asReadOnlyBuffer();
	}

	
	/**
	 * Attempts to find a reliable app name
	 * @return the app name
	 */
	public String getAppName() {
		if(appName!=null) {
			return appName;
		}
		appName = appName();
		System.setProperty(Constants.PROP_APP_NAME, appName);
		GLOBAL_TAGS.put(APP_TAG, appName);
		return appName;
	}
	
	/**
	 * Adds an AgentName change listener 
	 * @param listener the listener to add
	 */
	public void addAgentNameChangeListener(final AgentNameChangeListener listener) {
		if(listener!=null) listeners.add(listener);
	}
	
	/**
	 * Removes an AgentName change listener 
	 * @param listener the listener to remove
	 */
	public void removeAgentNameChangeListener(final AgentNameChangeListener listener) {
		if(listener!=null) listeners.remove(listener);
	}
	
	/**
	 * Fires an AgentName change event
	 * @param app The new app name, or null if only the host changed
	 * @param host The new host name, or null if only the app changed
	 */
	private void fireAgentNameChange(final String app, final String host) {
		if(app==null && host==null) return;
		Map<String, String> userData = new HashMap<String, String>(2);
		String notifType = null;
		if(app!=null && host!=null) {
			notifType = NOTIF_BOTH_NAME_CHANGE;
			userData.put(Constants.APP_TAG, app);
			userData.put(Constants.HOST_TAG, host);
			
		} else {
			if(app==null) {
				notifType = NOTIF_HOST_NAME_CHANGE;				
				userData.put(Constants.HOST_TAG, host);				
			} else {
				notifType = NOTIF_APP_NAME_CHANGE;				
				userData.put(Constants.APP_TAG, app);				
			}
		}
		final Notification n = new Notification(notifType, OBJECT_NAME, notifSerial.incrementAndGet(), System.currentTimeMillis(), "AgentName reset. New Id: [" + appName + "@" + hostName + "]");		
		n.setUserData(userData);
		sendNotification(n);
		for(final AgentNameChangeListener listener: listeners) {
			Threading.getInstance().async(new Runnable(){
				public void run() {
					listener.onAgentNameChange(app, host);
				}
			});
		}
	}
	
	/**
	 * Resets the cached app and host names. If a new name is set, the corresponding
	 * system property {@link Constants#PROP_HOST_NAME} and/or {@link Constants#PROP_APP_NAME}
	 * will be updated. 
	 * @param newAppName The new app name to set. Ignored if null or empty.
	 * @param newHostName The new host name to set. Ignored if null or empty.
	 */
	public void resetNames(final String newAppName, final String newHostName) {
		boolean hostUpdated = false;
		boolean appUpdated = false;
		if(newHostName!=null && newHostName.trim().isEmpty()) {
			hostName = newHostName.trim();
			System.setProperty(Constants.PROP_HOST_NAME, hostName);
			GLOBAL_TAGS.put(HOST_TAG, hostName);
			hostUpdated = true;
		}
		if(newAppName!=null && newAppName.trim().isEmpty()) {
			appName = newAppName.trim();
			System.setProperty(Constants.PROP_APP_NAME, appName);
			GLOBAL_TAGS.put(APP_TAG, appName);
			appUpdated = true;
		}
		initBufferized();
		log.info("Names reset: app:[{}], host:[{}]", newAppName, newHostName);
		fireAgentNameChange(appUpdated ? newAppName : null, hostUpdated ? newHostName : null); 
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.name.AgentNameMXBean#resetAppName(java.lang.String)
	 */
	@Override
	public void resetAppName(final String newAppName) {
		if(newAppName!=null && newAppName.trim().isEmpty() && !newAppName.trim().equals(appName)) {
			appName = newAppName.trim();
			initBufferized();
			System.setProperty(Constants.PROP_APP_NAME, appName);
			GLOBAL_TAGS.put(APP_TAG, appName);
			log.info("AppName reset: app:[{}]", newAppName);
			fireAgentNameChange(newAppName, null);
		}

	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.name.AgentNameMXBean#resetHostName(java.lang.String)
	 */
	@Override
	public void resetHostName(final String newHostName) {
		if(newHostName!=null && newHostName.trim().isEmpty() && !newHostName.trim().equals(hostName)) {
			hostName = newHostName.trim();
			initBufferized();
			System.setProperty(Constants.PROP_HOST_NAME, hostName);
			GLOBAL_TAGS.put(HOST_TAG, hostName);
			log.info("HostName reset: host:[{}]", newHostName);
			fireAgentNameChange(null, newHostName);
		}

	}
	
	
	/**
	 * Attempts a series of methods of divining the host name
	 * @return the determined host name
	 */
	public static String hostName() {	
		String host = System.getProperty(Constants.PROP_HOST_NAME, "").trim();
		if(host!=null && !host.isEmpty()) return host;
		host = Util.getHostNameByNic();
		if(host!=null) return host;		
		host = Util.getHostNameByInet();
		if(host!=null) return host;
		host = System.getenv(Constants.IS_WIN ? "COMPUTERNAME" : "HOSTNAME");
		if(host!=null && !host.trim().isEmpty()) return host;
		return Constants.HOST;
	}	
	
	
	/**
	 * Attempts to find a reliable app name
	 * @return the app name
	 */
	public static String appName() {
		String appName = System.getProperty(Constants.PROP_APP_NAME, "").trim();
		if(appName!=null && !appName.isEmpty()) return appName;
		appName = getSysPropAppName();
		if(appName!=null && !appName.trim().isEmpty()) return appName.trim();
		appName = getJSAppName();
		if(appName!=null && !appName.trim().isEmpty()) return appName.trim();		
		appName = System.getProperty("sun.java.command", null);
		if(appName!=null && !appName.trim().isEmpty()) {
			String app = cleanAppName(appName);
			if(app!=null && !app.trim().isEmpty()) {
				return app;
			}
		}
		appName = getVMSupportAppName();
		if(appName!=null && !appName.trim().isEmpty()) return appName;
		//  --main from args ?
		return Constants.SPID;
	}
	
	/**
	 * Attempts to find the remote app name
	 * @param remote An MBeanServer connection to the remote app
	 * @return the remote app name
	 */
	public static String remoteAppName(final RuntimeMBeanServerConnection remote) {
		final ObjectName runtimeMXBean = Util.objectName(ManagementFactory.RUNTIME_MXBEAN_NAME);
		try {
			RuntimeMXBean rt = JMX.newMXBeanProxy(remote, runtimeMXBean, RuntimeMXBean.class, false);
			final Map<String, String> sysProps = rt.getSystemProperties();
			String appName = sysProps.get(Constants.PROP_APP_NAME).trim();
			if(appName!=null && !appName.isEmpty()) return appName;
			appName = sysProps.get(Constants.REMOTE_PROP_APP_NAME).trim();
			if(appName!=null && !appName.isEmpty()) return appName;			
			appName = getRemoteJSAppName(remote);
			if(appName!=null && !appName.isEmpty()) return appName;
			appName = sysProps.get("sun.java.command").trim();
			if(appName!=null && !appName.isEmpty()) {
				return cleanAppName(appName);
			}
			return rt.getName().split("@")[0];
		} catch (Exception ex) {
			throw new RuntimeException("Failed to get any app name", ex);
		}		
	}
	
	/**
	 * Attempts to find the remote host name
	 * @param remote An MBeanServer connection to the remote app
	 * @return the remote host name
	 */
	public static String remoteHostName(final RuntimeMBeanServerConnection remote) {
		final ObjectName runtimeMXBean = Util.objectName(ManagementFactory.RUNTIME_MXBEAN_NAME);
		try {
			RuntimeMXBean rt = JMX.newMXBeanProxy(remote, runtimeMXBean, RuntimeMXBean.class, false);
			final Map<String, String> sysProps = rt.getSystemProperties();
			String appName = sysProps.get(Constants.PROP_HOST_NAME).trim();
			if(appName!=null && !appName.isEmpty()) return appName;
			appName = sysProps.get(Constants.REMOTE_PROP_HOST_NAME).trim();
			if(appName!=null && !appName.isEmpty()) return appName;			
			appName = getRemoteJSHostName(remote);
			if(appName!=null && !appName.isEmpty()) return appName;
			return rt.getName().split("@")[1];
		} catch (Exception ex) {
			throw new RuntimeException("Failed to get any host name", ex);
		}		
	}
	
	
	
	/**
	 * Attempts to determine the app name by looking up the value of the 
	 * system property named in the value of the system prop {@link Constants#SYSPROP_APP_NAME}
	 * @return The app name or null if {@link Constants#SYSPROP_APP_NAME} was not defined
	 * or did not resolve.
	 */
	public static String getSysPropAppName() {
		String appProp = System.getProperty(Constants.SYSPROP_APP_NAME, "").trim();
		if(appProp==null || appProp.isEmpty()) return null;
		boolean env = appProp==null || appProp.isEmpty();
		String appName = env ? System.getenv(appProp) : System.getProperty(appProp, "").trim();
		if(appName!=null && !appName.trim().isEmpty()) return appName;
		return null;		
	}
	
	/**
	 * Attempts to determine the app name by looking up the value of the 
	 * system property {@link Constants#JS_APP_NAME}, and compiling its value
	 * as a JS script, then returning the value of the evaluation of the script.
	 * The following binds are passed to the script: <ul>
	 * 	<li><b>sysprops</b>: The system properties</li>
	 * 	<li><b>agprops</b>: The agent properties which will be an empty properties instance if {@link #getAgentProperties()} failed.</li>
	 *  <li><b>envs</b>: A map of environment variables</li>
	 *  <li><b>mbs</b>: The platform MBeanServer</li>
	 *  <li><b>cla</b>: The command line arguments as an array of strings</li>
	 * </ul>
	 * @return The app name or null if {@link Constants#JS_APP_NAME} was not defined
	 * or did not compile, or did not return a valid app name
	 */
	public static String getJSAppName() {
		String js = System.getProperty(Constants.JS_APP_NAME, "").trim();
		if(js==null || js.isEmpty()) return null;
		try {
			ScriptEngine se = new ScriptEngineManager().getEngineByExtension("js");
			Bindings b = new SimpleBindings();
			b.put("sysprops", System.getProperties());
			b.put("envs", System.getenv());
			b.put("mbs", ManagementFactory.getPlatformMBeanServer());
			b.put("cla", ManagementFactory.getRuntimeMXBean().getInputArguments().toArray(new String[0]));
			Properties p = Util.getAgentProperties();
			if(p!=null) {
				b.put("agprops", Util.getAgentProperties());
			} else {
				b.put("agprops", new Properties());
			}
			Object value = se.eval(js, b);
			if(value!=null && !value.toString().trim().isEmpty()) return value.toString().trim();
			return null;
		} catch (Exception ex) {
			return null;
		}
	}
	
	/**
	 * Attempts to determine a remote app name by looking up the value of the 
	 * system property {@link Constants#REMOTE_JS_APP_NAME}, and compiling its value
	 * as a JS script, then returning the value of the evaluation of the script.
	 * The following binds are passed to the script: <ul>
	 *  <li><b>mbs</b>: The remote MBeanServerConnection</li>
	 * </ul>
	 * @param remote The remote MBeanServer connection
	 * @return The app name or null if {@link Constants#REMOTE_JS_APP_NAME} was not defined
	 * or did not compile, or did not return a valid app name
	 */
	public static String getRemoteJSAppName(final MBeanServerConnection remote) {
		String js = System.getProperty(Constants.REMOTE_JS_APP_NAME, "").trim();
		if(js==null || js.isEmpty()) return null;
		try {
			ScriptEngine se = new ScriptEngineManager().getEngineByExtension("js");
			Bindings b = new SimpleBindings();
			b.put("mbs", remote);
			Object value = se.eval(js, b);
			if(value!=null && !value.toString().trim().isEmpty()) return value.toString().trim();
			return null;
		} catch (Exception ex) {
			return null;
		}
	}
	
	/**
	 * Attempts to determine a remote host name by looking up the value of the 
	 * system property {@link Constants#REMOTE_JS_HOST_NAME}, and compiling its value
	 * as a JS script, then returning the value of the evaluation of the script.
	 * The following binds are passed to the script: <ul>
	 *  <li><b>mbs</b>: The remote MBeanServerConnection</li>
	 * </ul>
	 * @param remote The remote MBeanServer connection
	 * @return The host name or null if {@link Constants#REMOTE_JS_HOST_NAME} was not defined
	 * or did not compile, or did not return a valid app name
	 */
	public static String getRemoteJSHostName(final MBeanServerConnection remote) {
		String js = System.getProperty(Constants.REMOTE_JS_HOST_NAME, "").trim();
		if(js==null || js.isEmpty()) return null;
		try {
			ScriptEngine se = new ScriptEngineManager().getEngineByExtension("js");
			Bindings b = new SimpleBindings();
			b.put("mbs", remote);
			Object value = se.eval(js, b);
			if(value!=null && !value.toString().trim().isEmpty()) return value.toString().trim();
			return null;
		} catch (Exception ex) {
			return null;
		}
	}
	
	
	
	
	/**
	 * Attempts to find an app name from a few different properties
	 * found in <b><code>sun.misc.VMSupport.getAgentProperties()</code></b>.
	 * Current properties are: <ul>
	 * 	<li>sun.java.command</li>
	 * 	<li>program.name</li>
	 * </ul>
	 * @return an app name or null if the reflective invocation fails,
	 * or no property was found, or if the clean of the found app names
	 * did not return an acceptable name.
	 */
	public static String getVMSupportAppName() {
		Properties p = Util.getAgentProperties();
		String app = p.getProperty("sun.java.command", null);
		if(app!=null && !app.trim().isEmpty()) {
			app = cleanAppName(app);			
			if(app!=null && !app.trim().isEmpty()) return app;
		}
		app = p.getProperty("program.name", null);
		if(app!=null && !app.trim().isEmpty()) {
			app = cleanAppName(app);			
			if(app!=null && !app.trim().isEmpty()) return app;				
		}
		return null;
	}
	
	/**
	 * Cleans an app name
	 * @param appName The app name
	 * @return the cleaned name or null if the result is no good
	 */
	public static String cleanAppName(final String appName) {
		final String[] frags = appName.split("\\s+");
		if(appName.contains(".jar")) {
			
			for(String s: frags) {
				if(s.endsWith(".jar")) {
					String[] jfrags = s.split("\\.");
					return jfrags[jfrags.length-1];
				}
			}
		} else {
			String className = frags[0];
			Class<?> clazz = Util.loadClassByName(className, null);
			if(clazz!=null) {
				return clazz.getSimpleName();
			}
		}
		
		
		return null;
	}
	
	

}
