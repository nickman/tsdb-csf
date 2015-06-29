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
package com.heliosapm.opentsdb.client.logging;

import static com.heliosapm.opentsdb.client.opentsdb.ConfigurationReader.conf;

import java.io.File;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.nio.charset.Charset;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.appender.SocketAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.net.ssl.SslConfiguration;
import org.apache.logging.log4j.spi.LoggerContext;

import com.heliosapm.opentsdb.client.name.AgentName;
import com.heliosapm.opentsdb.client.opentsdb.Constants;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: LoggingConfiguration</p>
 * <p>Description: Centralized logging config and boot time configuration</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.logging.LoggingConfiguration</code></p>
 */

public class LoggingConfiguration {
	/** The singleton instance */
	private static volatile LoggingConfiguration instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	/** The metric root directory where the boot logging occurs */
	private final File metricsRootDir;
	/** The logger context */
	private final LoggerContext lctx;
	
	/** The inner logger context */
	private org.apache.logging.log4j.core.LoggerContext bootContext = null;
	/** Instance logger */
	private final Logger log;

	/** The boot systime */
	static final long SYSTIME = System.currentTimeMillis();
	/** The JVM's PID */
	public static final String SPID = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
	/** The JVM's host name according to the RuntimeMXBean */
	public static final String HOST = ManagementFactory.getRuntimeMXBean().getName().split("@")[1];
	
	/** The default offline metric persistence file pattern */
	public static final String DEFAULT_OFFLINE_DIR = System.getProperty("user.home") + File.separator + ".tsdb-metrics";
	/** The system property config name for the offline metric persistence file-system directory */
	public static final String PROP_OFFLINE_DIR = "tsdb.logging.dir";
	
	
	
	/** The boot file prefix  (pid, host, current-time-millis) */
	static final String BOOT_LOG_FILE_PREFIX = String.format("tsdb-csf-%s-%s-boot-%s", SPID, HOST, SYSTIME);
	
	/**
	 * Acquires the LoggingConfiguration singleton instance
	 * @return the LoggingConfiguration singleton instance
	 */
	public static LoggingConfiguration getInstance() {		
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					try {
						final AgentName an = AgentName.getInstance();
						final ClassLoader cl = Thread.currentThread().getContextClassLoader(); 
						final InputStream is = cl.getResourceAsStream("log4j/tsdb-csf-log4j2.xml");
						System.err.println("\n\t====================================================\n\n\tLoggingConfiguration CLASSLOADER: [" + cl + "]\n\tIS: [" + is + "]\n\t=====================================\n");
						
						Configurator.initialize(cl, new ConfigurationSource(is));
					} catch (Exception ex) {
						ex.printStackTrace(System.err);
					}
					instance = new LoggingConfiguration();
				}
			}
		}
		return instance;
	}
	
	static {
		System.setProperty("Log4jContextSelector", "org.apache.logging.log4j.core.selector.BasicContextSelector");
		File f = initOfflineStorage();
		System.setProperty(PROP_OFFLINE_DIR, f.getAbsolutePath());
		System.setProperty("tsdb.logging.pid", SPID);
		System.setProperty("tsdb.logging.host", HOST);
		System.setProperty("tsdb.logging.systime", "" + SYSTIME);
		
	}
	
	private LoggingConfiguration() {
		final String appName = AgentName.getInstance().getAppName();
		final String hostName = AgentName.getInstance().getHostName();		
		metricsRootDir = initOfflineStorage();		
		//bootConfig();		
		lctx = LogManager.getContext(true);
		log = LogManager.getLogger(getClass());
		Thread t = new Thread("BootLoggerCleanUp") {
			public void run() {
				final FilenameFilter fnf = new FilenameFilter() {
					@Override
					public boolean accept(File dir, String name) {						
						return metricsRootDir.equals(dir)
							&& name.startsWith(BOOT_LOG_FILE_PREFIX);
					}
				};
				for(File f: metricsRootDir.listFiles(fnf)) {
					if(f.length()==0) f.delete();
				}
			}
		};
		Util.sdhook(t);
		SocketAppender sa = SocketAppender.createAppender(
      "127.0.0.1", //@PluginAttribute("host") final String host,
      "1588", //@PluginAttribute(value = "port", defaultInt = 0) final int port,
      "UDP", //@PluginAttribute("protocol") final String protocolStr,
      (SslConfiguration)null, //@PluginElement("SSL") final SslConfiguration sslConfig,
//      "5000", //@PluginAliases("reconnectionDelay") // deprecated  @PluginAttribute(value = "reconnectionDelayMillis", defaultInt = 0) final int reconnectionDelayMillis,
      "5000", //@PluginAttribute("reconnectionDelayMillis") final String delayMillis,
      "true", //@PluginAttribute("immediateFail") final String immediateFail,
      "METRICS", //@PluginAttribute("name") final String name,
      "true", //@PluginAttribute("immediateFlush") final String immediateFlush,
      "false", //@PluginAttribute("ignoreExceptions") final String ignore,
      PatternLayout.createLayout("%m%n", null, null, Charset.defaultCharset(), false, true, null, null), //@PluginElement("Layout") Layout<? extends Serializable> layout,
      (Filter)null, //@PluginElement("Filter") final Filter filter,
      (String)null, //@PluginAttribute("advertise") final String advertise, 
      (Configuration)null //@PluginConfiguration final Configuration config
        );
//		SyslogAppender appender = SyslogAppender.createAppender(        
//        "127.0.0.1", //@PluginAttribute("host") final String host,
//        1589, //@PluginAttribute(value = "port", defaultInt = 0) final int port,
//        "UDP", //@PluginAttribute("protocol") final String protocolStr,
//        (SslConfiguration)null, //@PluginElement("SSL") final SslConfiguration sslConfig,
//        5000, //@PluginAliases("reconnectionDelay") // deprecated  @PluginAttribute(value = "reconnectionDelayMillis", defaultInt = 0) final int reconnectionDelayMillis,
//        true, //@PluginAttribute(value = "immediateFail", defaultBoolean = true) final boolean immediateFail,
//        "SYSLOG", //@PluginAttribute("name") final String name,
//        true, //@PluginAttribute(value = "immediateFlush", defaultBoolean = true) final boolean immediateFlush,
//        false, //@PluginAttribute(value = "ignoreExceptions", defaultBoolean = true) final boolean ignoreExceptions,
//        Facility.LOCAL7, //@PluginAttribute(value = "facility", defaultString = "LOCAL0") final Facility facility,
//        "App", //@PluginAttribute("id") final String id,
//        18060, //@PluginAttribute(value = "enterpriseNumber", defaultInt = Rfc5424Layout.DEFAULT_ENTERPRISE_NUMBER) final int enterpriseNumber,
//        false, //@PluginAttribute(value = "includeMdc", defaultBoolean = true) final boolean includeMdc,
//        "mdc", //@PluginAttribute("mdcId") final String mdcId,
//        "csf", //@PluginAttribute("mdcPrefix") final String mdcPrefix,
//        "csf-event", //@PluginAttribute("eventPrefix") final String eventPrefix,
//        false, //@PluginAttribute(value = "newLine", defaultBoolean = false) final boolean newLine,
//        (String)null, //@PluginAttribute("newLineEscape") final String escapeNL,
//        appName, //@PluginAttribute("appName") final String appName,
//        "Audit", //@PluginAttribute("messageId") final String msgId,
//        (String)null, //@PluginAttribute("mdcExcludes") final String excludes,
//        (String)null, // @PluginAttribute("mdcIncludes") final String includes,
//        (String)null, //@PluginAttribute("mdcRequired") final String required,
//        "RFC5424", //@PluginAttribute("format") final String format,
//        (Filter)null, //@PluginElement("Filter") final Filter filter,
//        (Configuration)null, //@PluginConfiguration final Configuration config,
//        Charset.defaultCharset(), //@PluginAttribute(value = "charset", defaultString = "UTF-8") final Charset charsetName,
//        (String)null, //@PluginAttribute("exceptionPattern") final String exceptionPattern,
//        (LoggerFields[])null, //@PluginElement("LoggerFields") final LoggerFields[] loggerFields,
//        false //@PluginAttribute(value = "advertise", defaultBoolean = false) final boolean advertise
//        );
//		org.apache.logging.log4j.core.Logger rootLogger = (org.apache.logging.log4j.core.Logger)LogManager.getRootLogger();
////		appender.start();
//		sa.start();
//		rootLogger.addAppender(sa);
//		rootLogger.addAppender(appender);
		
		log.info("Boot Logger Initialized: [{}@{}]", appName, hostName);
	}
//  @PluginAttribute("host") final String host,
//  @PluginAttribute(value = "port", defaultInt = 0) final int port,
//  @PluginAttribute("protocol") final String protocolStr,
//  @PluginElement("SSL") final SslConfiguration sslConfig,
//  @PluginAliases("reconnectionDelay") // deprecated
//  @PluginAttribute(value = "reconnectionDelayMillis", defaultInt = 0) final int reconnectionDelayMillis,
//  @PluginAttribute(value = "immediateFail", defaultBoolean = true) final boolean immediateFail,
//  @PluginAttribute("name") final String name,
//  @PluginAttribute(value = "immediateFlush", defaultBoolean = true) final boolean immediateFlush,
//  @PluginAttribute(value = "ignoreExceptions", defaultBoolean = true) final boolean ignoreExceptions,
//  @PluginAttribute(value = "facility", defaultString = "LOCAL0") final Facility facility,
//  @PluginAttribute("id") final String id,
//  @PluginAttribute(value = "enterpriseNumber", defaultInt = Rfc5424Layout.DEFAULT_ENTERPRISE_NUMBER) final int enterpriseNumber,
//  @PluginAttribute(value = "includeMdc", defaultBoolean = true) final boolean includeMdc,
//  @PluginAttribute("mdcId") final String mdcId,
//  @PluginAttribute("mdcPrefix") final String mdcPrefix,
//  @PluginAttribute("eventPrefix") final String eventPrefix,
//  @PluginAttribute(value = "newLine", defaultBoolean = false) final boolean newLine,
//  @PluginAttribute("newLineEscape") final String escapeNL,
//  @PluginAttribute("appName") final String appName,
	
//  @PluginAttribute("messageId") final String msgId,
//  @PluginAttribute("mdcExcludes") final String excludes,
//  @PluginAttribute("mdcIncludes") final String includes,
//  @PluginAttribute("mdcRequired") final String required,
//  @PluginAttribute("format") final String format,
//  @PluginElement("Filter") final Filter filter,
//  @PluginConfiguration final Configuration config,
//  @PluginAttribute(value = "charset", defaultString = "UTF-8") final Charset charsetName,
//  @PluginAttribute("exceptionPattern") final String exceptionPattern,
//  @PluginElement("LoggerFields") final LoggerFields[] loggerFields,
//  @PluginAttribute(value = "advertise", defaultBoolean = false) final boolean advertise) {
	
	
	public void initAppLogging(final AgentName agentName) {
		// ${sys:tsdb.logging.dir}/tsdb-csf-${sys:tsdb.logging.pid}-${sys:tsdb.logging.host}-boot-${sys:tsdb.logging.systime}.log
		InputStream is = null;
		try {
			URL configUrl = LoggingConfiguration.class.getResource("/log4j/tsdb-csf-log4j2.xml");
			org.apache.logging.log4j.core.LoggerContext c = (org.apache.logging.log4j.core.LoggerContext)LogManager.getContext(true);
        	c.setConfigLocation(configUrl.toURI());
        	bootContext = (org.apache.logging.log4j.core.LoggerContext)LogManager.getContext(true);
        	
			Logger logLog = LogManager.getLogger(LoggingConfiguration.class);
			logLog.info("tsdb-csf Logging Service configured from [{}]", configUrl);
			// Delete the PID log if we get here.
			final String pidLogFile = String.format("tsdb-csf-%s-%s-boot-%s.log", Constants.SPID, HOST, SYSTIME);
			final File pidFile = new File(metricsRootDir, pidLogFile);
			if(pidFile.exists()) {
				if(!pidFile.delete()) {
					Util.sdhook(pidFile);
					logLog.info("PID Log File: [{}] was still locked. Adding to delete on exit queue.", pidFile);
				} else {
					logLog.info("Deleted PID Log File: [{}]", pidFile);
				}
			}
			// e.g.  Deleted PID Log File: [/home/nwhitehead/.tsdb-metrics/tsdb-csf-23969-hserval-boot-1422391526795.log]
		} catch (Exception ex) {
			System.err.println("Failed to initialize tsdb-csf logging");
			ex.printStackTrace(System.err);
		} finally {
			if(is!=null) try { is.close(); } catch (Exception x) {/* No Op */}
		}
	}
	
	/**
	 * Tests a few different configs for a good offline data store directory, returning the first that works.
	 * If none are found, offline metrics storage will be disabled 
	 * @return the directory to store to
	 */
	@SuppressWarnings("null")
	protected static File initOfflineStorage() {
		boolean gotConfiguredDir = true;
		String offlineDir = conf(PROP_OFFLINE_DIR, DEFAULT_OFFLINE_DIR);		
		File f = doOffline(offlineDir);
		if(f!=null) return f;
		gotConfiguredDir = false;
		f = doOffline(DEFAULT_OFFLINE_DIR);
		if(f!=null) return f;
		f = doOffline(new File(System.getProperty("java.io.tmpdir") + File.separator + ".tsdb-metrics").getAbsolutePath());
		if(f==null) {
			System.err.println("\n\n\t!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n\t[" + LoggingConfiguration.class.getName() + "]\n\tFailed to create any of the offline metric stores. Offline storage disabled\n\t!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n\n");
		} else {
			if(!gotConfiguredDir) {
				System.out.println("tsdb-csf Persistence Directory:" + f.getAbsolutePath());
			}
		}
		System.setProperty(PROP_OFFLINE_DIR, f.getAbsolutePath().replace(File.separator + File.separator, File.separator));
		return f;
	}
	
	/**
	 * Tests a single offline data store directory pattern
	 * @param name The file pattern to test
	 * @return The directory plus file if successful, null otherwise 
	 */
	protected static File doOffline(final String name) {
		if(name==null) return null;
		File d = new File(name);
		if(!d.exists()) {
			if(!d.mkdirs()) {
				return null;
			}
		}
		File testFile = null;
		try {
			
			testFile = File.createTempFile("offlinemetrictest", "", d);
			if(testFile.canRead() && testFile.canWrite()) {
				return d;
			}
			return null;
		} catch (Exception ex) {
			return null;
		} finally {
			if(testFile!=null) {
				if(!testFile.delete()) {
					Util.sdhook(testFile);
				}
			}
		}
		
	}
	
	
}
