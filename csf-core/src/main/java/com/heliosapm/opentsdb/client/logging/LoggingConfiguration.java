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
import java.util.Date;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.ConsoleAppender.Target;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.RollingFileManager;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;
import org.apache.logging.log4j.core.filter.ThresholdFilter;
import org.apache.logging.log4j.core.layout.PatternLayout;
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
		log.info("Boot Logger Initialized");
	}
	
	
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
