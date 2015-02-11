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
package com.heliosapm.opentsdb.client.opentsdb;

import static com.heliosapm.opentsdb.client.opentsdb.ConfigurationReader.conf;
import static com.heliosapm.opentsdb.client.opentsdb.Constants.DEFAULT_OFFLINE_DIR;
import static com.heliosapm.opentsdb.client.opentsdb.Constants.PROP_OFFLINE_DIR;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;

import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: MetricPersistence</p>
 * <p>Description: Offline storage for metrics for use while the OpenTSDB endpoint is null</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.MetricPersistence</code></p>
 */

public class MetricPersistence implements FilenameFilter  {
	/** The singleton instance */
	private static volatile MetricPersistence instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	/** Instance logger */
	private final Logger log = LogManager.getLogger(getClass());
	/** The directory where persistence files are cached */
	private final File persistDir;
	/** The relative file name pattern */
	public static final String FILE_NAME_TEMPLATE = "offlinemetrics%s.dat";
	/** Regex to match offline file names */
	public static final Pattern FILE_NAME_PATTERN = Pattern.compile("offlinemetrics(\\d+)\\.dat", Pattern.CASE_INSENSITIVE);
	/** The tmp file name prefix */
	public static final String TMP_FILE_PREFIX = "inprocess-offlinemetrics";
	/** The tmp file extension */
	public static final String TMP_FILE_EXT = ".tmp";
	
	
	
	/** The file name index */
	private final AtomicLong fileNameIndex = new AtomicLong();
	/** The current file to write to */
	protected final AtomicReference<OffHeapFIFOFile> currentOfflineFile = new AtomicReference<OffHeapFIFOFile>(null); 
	
	/** A counter of successful metric collection flushes */
	protected final AtomicLong flushSuccessCounter = new AtomicLong(0);
	/** A counter of failed metric collection flushes */
	protected final AtomicLong flushFailedCounter = new AtomicLong(0);
	/** A counter of bad content metric collection flushes */
	protected final AtomicLong flushBadContentCounter = new AtomicLong(0);
	
	

	/** Lock file RAF */
	protected RandomAccessFile lockFileRaf = null;
	/** Lock file file channel */
	protected FileChannel lockFileFc = null;
	/** The lock file */
	protected File lockFile = null;
	/** The lock file lock */
	protected FileLock lockFileLock = null;
	
	
	/** Comparator to allow files to be sorted by the index */
	static final NaturalOrderComparator NOC = new NaturalOrderComparator();
	
	
	
	

	/**
	 * Acquires the MetricPersistence singleton instance
	 * @return the MetricPersistence singleton instance
	 */
	public static MetricPersistence getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new MetricPersistence(); 
				}
			}
		}
		return instance;
	}
	
	/**
	 * Returns the current offline file name
	 * @return the current offline file name, or null if there is no current file
	 */
	public String getCurrentOfflineFile() {
		OffHeapFIFOFile ff = currentOfflineFile.get();
		if(ff!=null) {
			return ff.getFileName();
		}
		return null;
	}
	
	/**
	 * Returns the current offline file size
	 * @return the current offline file size, or -1L if there is no current file
	 */
	public long getCurrentOfflineFileSize() {
		OffHeapFIFOFile ff = currentOfflineFile.get();
		if(ff!=null) {
			return ff.getFileSize();
		}
		return -1L;
	}
	
	/**
	 * Returns the current offline file entry count
	 * @return the current offline file entry count, or -1 if there is no current file
	 */
	public int getCurrentOfflineFileEntries() {
		OffHeapFIFOFile ff = currentOfflineFile.get();
		if(ff!=null) {
			return ff.getEntrySize();
		}
		return -1;
	}
	
	/**
	 * Returns the current offline file compression rate
	 * @return the current offline file compression rate, or -1d if there is no current file
	 */
	public double getCurrentOfflineFileCompressionRate() {
		OffHeapFIFOFile ff = currentOfflineFile.get();
		if(ff!=null) {
			return ff.getCompressionRate();
		}
		return -1d;		
	}
	
	/**
	 * Indicates if the current offline file is compressed
	 * @return true if the current offline file is compressed, false if not or there is no current file
	 */
	public boolean isCurrentOfflineFileCompressed() {
		OffHeapFIFOFile ff = currentOfflineFile.get();
		if(ff!=null) {
			return ff.isGZipped();
		}
		return false;				
	}

	
	
	/**
	 * Returns the offline directory
	 * @return the offline directory
	 */
	public File getOfflineDir() {
		return persistDir;
	}
	
	/**
	 * Returns the total number of offline files
	 * @return the total number of offline files
	 */
	public int getOfflineFileCount() {
		return persistDir.listFiles(this).length;
	}
	
	/**
	 * Returns the total number of offline entries
	 * @return the total number of offline entries
	 */
	public int getOfflineEntryCount() {
		int entries = 0;
		for(File ff: persistDir.listFiles(this)) {
			if(ff.length()>=4) {
				entries += getEntryCount(ff);
			}
		}
		return entries;
	}
	
	/**
	 * Returns the number of entries in the passed offline file
	 * @param f The offline file
	 * @return the entry count
	 */
	public int getEntryCount(final File f) {
		FileInputStream fis = null;
		FileChannel fc = null;
		try {
			fis = new FileInputStream(f);
			fc = fis.getChannel();
			return fc.map(MapMode.READ_ONLY, 0, 4).getInt();
		} catch (Exception x) {
			return 0;
		} finally {
			if(fis!=null) try { fis.close(); } catch (Exception x) {/* No Op */}
			if(fc!=null) try { fc.close(); } catch (Exception x) {/* No Op */}
		}
	}
	
	
	private static final FilenameFilter TMPFILENAME_FILTER = new FilenameFilter() {		
		@Override
		public boolean accept(File dir, String name) {
			if(name!=null) {
				return name.startsWith(TMP_FILE_PREFIX) && name.endsWith(TMP_FILE_EXT);
			}
			return false;
			
		}
	};
	
	/**
	 * Creates a new MetricPersistence
	 */
	private MetricPersistence() {
		File f = initOfflineStorage();
		if(f==null || !lockPersisenceDirectory(f)) {
			persistDir = null;
			System.err.println("WARNING: Metric buffering is disabled. Failed to lock directory [" + f + "]");			
		} else {
			persistDir = f;
		}
		if(persistDir!=null) {
			updateIndex();		
		}
		purgeTmpFiles();
		
		if(lockFileLock!=null) {
			Util.sdhook(new Runnable(){
				@Override
				public void run() {
					purgeTmpFiles();
					if(lockFileLock!=null) try { lockFileLock.release(); } catch (Exception x) {/* No Op */}
					if(lockFileRaf!=null) try { lockFileRaf.close(); } catch (Exception x) {/* No Op */}
					if(lockFileFc!=null) try { lockFileFc.close(); } catch (Exception x) {/* No Op */}
					if(lockFile!=null) lockFile.delete();					
				}
			});
		}
		log.info("Metric Persistence Started in [" + persistDir + "]");
	}
	
	
	private void purgeTmpFiles() {
		try {
			for(File f: persistDir.listFiles(TMPFILENAME_FILTER)) {
				f.delete();
			}
		} catch (Exception x) {/* No Op */}		
	}
	
	/**
	 * Saves a JSON collection of metrics offline
	 * @param buff The buffer containing the json metric collection to save
	 */
	public void offline(final ChannelBuffer buff) {
		if(persistDir==null || buff==null || buff.readableBytes()==0) {
			return;
		}
		OffHeapFIFOFile offlineFile = currentOfflineFile.get();
		if(offlineFile==null) {
			synchronized(currentOfflineFile) {
				offlineFile = currentOfflineFile.get();
				if(offlineFile==null) {
					offlineFile = OffHeapFIFOFile.get(persistDir, String.format(FILE_NAME_TEMPLATE, fileNameIndex.incrementAndGet()));
					currentOfflineFile.set(offlineFile);
				}
			}
		}
		int contentSize = buff.readableBytes();
		long ffsize = offlineFile.size();
		long max = ffsize + contentSize + 4;
		if(max > Integer.MAX_VALUE) {			// TODO: custom max file size
			offlineFile = rollOfflineFile();
		}
		ffsize = offlineFile.write(buff);
		if(ffsize==-1L) {
			log.error("Unexpected overflow writing to offline [{}]", offlineFile);
		}
	}
	
	private final ThreadFactory threadFactory = new ThreadFactory() {
		final AtomicLong serial = new AtomicLong(0L);
		@Override
		public Thread newThread(final Runnable r) {
			final Thread t = new Thread(r, "FlushToServerThread#" + serial.incrementAndGet());
			t.setDaemon(true);
			return t;
		}
	};
	
	
	/**
	 * Attempts to flush all buffered metrics to the TSDB server
	 * @param poster the metrics poster to send with
	 * TODO: delete file when down to zero entries and <header-size> file size
	 */
	public synchronized void flushToServer(final HttpMetricsPoster poster) {
		final long start = System.currentTimeMillis();
		final OffHeapFIFOFile  current = rollOfflineFile();
		final int maxConcurrent = poster.getMaxConcurrentFlushes();
		final int entries = getOfflineEntryCount();
		if(entries==0) return;
		final ArrayBlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<Runnable>(entries);
		final ThreadPoolExecutor tpe = new ThreadPoolExecutor(maxConcurrent, maxConcurrent, 5, TimeUnit.SECONDS, workQueue, threadFactory, new ThreadPoolExecutor.AbortPolicy());
		tpe.prestartAllCoreThreads();
		log.warn("Max concurrent flushes: {}", maxConcurrent);
		final long limitTimeoutMs = (poster.getConnectionTimeout() + poster.getRequestTimeout()) * maxConcurrent;
		outerloop:
		for(final File f: persistDir.listFiles(this)) {
			OffHeapFIFOFile ff = null;
			try {
				ff = OffHeapFIFOFile.get(f);
				if(current.equals(ff)) continue;
				while(ff.getEntrySize()>0) {
					if(poster.isHardDown()) break outerloop;
					final File[] tmpFile = ff.extract(1);
					if(tmpFile.length==1) {
						try {
							tpe.execute(new Runnable(){
								@Override
								public void run() {									
									try {
										log.debug("[{}] Sending: {}", Thread.currentThread(), tmpFile[0].getName());
										final CountDownLatch latch = new CountDownLatch(1);
										poster.send(tmpFile[0], new  CompletionCallback<Integer>(){
											final AtomicBoolean callbackCalled = new AtomicBoolean(false);
											@Override
											public void onComplete(final Integer completionValue) {
												if(callbackCalled.compareAndSet(false, true)) {
													if(completionValue!=null) {
														if(completionValue==1) flushFailedCounter.incrementAndGet();
														else if(completionValue==2) flushBadContentCounter.incrementAndGet();
														else if(completionValue==3) flushSuccessCounter.incrementAndGet();
													}		
													latch.countDown();
												}
											}							
										});
										try {
											if(!latch.await(limitTimeoutMs, TimeUnit.MILLISECONDS)) {
												log.error("Timed out waiting for file flush");
											} 
										} catch (InterruptedException iex) {
											log.error("Thread interrupted while waiting on file flush", iex);
										}
									} finally {
										log.warn("[{}] Sent: {}", Thread.currentThread(), tmpFile[0].getName());
									}
								}
							});
						} catch (Exception ex) {
							log.error("Inner Loop Exception", ex);
						}
					}
				}
			} catch (Exception ex) {
				log.error("Outer Loop Exception", ex);
			} finally {
				ff.delete();
			}
		}
		tpe.shutdown();
		try {
			if(!tpe.awaitTermination(30, TimeUnit.SECONDS)) {
				log.error("Timed out waiting for flush completion");
			} else {
				log.info("\n\n\t==============================================================\n\tOffline Flush Complete\n\tElapsed: {} ms.\n\tEntries: {}\n\t==============================================================\n", System.currentTimeMillis() - start, entries);
			}
		} catch (InterruptedException iex) {
			log.error("Thread interrupted while waiting on flush completion", iex);
		} finally {
			try { tpe.shutdownNow(); } catch (Exception x) {/* No Op */}
		}
	}
	
	public String reportFileSummary() {
		StringBuilder b = new StringBuilder();
		for(File f: persistDir.listFiles(this)) {
			
		}
		return b.toString();
	}
	
	
	/**
	 * Rolls the offline file
	 * @return the new offline file
	 */
	protected OffHeapFIFOFile rollOfflineFile() {
		synchronized(currentOfflineFile) {
			OffHeapFIFOFile offlineFile = OffHeapFIFOFile.get(persistDir, String.format(FILE_NAME_TEMPLATE, fileNameIndex.incrementAndGet()));
			final OffHeapFIFOFile priorFile = currentOfflineFile.getAndSet(offlineFile);
			if(priorFile!=null) priorFile.slimDown();
			return offlineFile;
		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see java.io.FilenameFilter#accept(java.io.File, java.lang.String)
	 */
	@Override
	public boolean accept(File dir, String name) {
		return FILE_NAME_PATTERN.matcher(name).matches();
	}

	
	
	/**
	 * Finds the highest offline file index
	 */
	protected void updateIndex() {
		TreeSet<File> pFiles = new TreeSet<File>(NOC);
		Collections.addAll(pFiles, persistDir.listFiles(this));
		for(Iterator<File> iter = pFiles.iterator(); iter.hasNext();) {
			File f = iter.next();
			if(f.length() <= 4 || f.getName().startsWith("metrics-inprocess-")) {
				if(f.delete()) {
					iter.remove();
				}
			}
		}
		if(!pFiles.isEmpty()) {
			for(File f: pFiles) {
				OffHeapFIFOFile.existing(f);
			}
			File lastFile = pFiles.last();
			Matcher m = FILE_NAME_PATTERN.matcher(lastFile.getName());
			if(m.matches()) {
				try {
					long idx = Long.parseLong(m.group(1));
					fileNameIndex.set(idx);
					log.info("Found {} Persisted Metric Files. Last Index was {}. Next file will be {}", pFiles.size(), idx, String.format(FILE_NAME_TEMPLATE, idx+1));
				} catch (Exception x) {
					log.warn("Failed to get last index of persisted files. Starting from 0:{}", x);
				}
			}			
		}		
	}
	
	/**
	 * Tests a few different configs for a good offline data store directory, returning the first that works.
	 * If none are found, offline metrics storage will be disabled 
	 * @return the directory to store to
	 */
	protected File initOfflineStorage() {
		String offlineDir = conf(PROP_OFFLINE_DIR, DEFAULT_OFFLINE_DIR);		
		String appName = OpenTsdb.getInstance().getAppName();
		File f = doOffline(offlineDir + File.separator + appName);
		if(f!=null) return f;
		f = doOffline(DEFAULT_OFFLINE_DIR + File.separator + appName);
		if(f!=null) return f;
		f = doOffline(new File(System.getProperty("java.io.tmpdir") + File.separator + ".tsdb-metrics"  + File.separator + appName).getAbsolutePath());
		if(f==null) {
			log.error("\n\n\t!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n\tFailed to create any of the offline metric stores. Offline storage disabled\n\t!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n\n");
		}
		return f;
	}
	
	/**
	 * Attempts to lock the persistence directory by locking a file called {@link Constants#LOCK_FILE_NAME} in the passed directory.
	 * If the file cannot be locked, or cannot be created, will return false.
	 * If the file does not exist, it is created. The current PID is then written to the file.
	 * The lock is kept until the JVM terminates. 
	 * @param dir The persistence directory
	 * @return true if locked, false otherwise
	 */
	protected boolean lockPersisenceDirectory(final File dir) {
		if(dir==null || !dir.exists() || !dir.isDirectory()) {
			System.err.println("Failed to lock directory [" + dir + "]. Not found or not directory");
			return false;
		}
		try {
			lockFile = new File(dir, Constants.LOCK_FILE_NAME);
			if(lockFile.exists() && lockFile.isDirectory()) {
				System.err.println("Failed to lock directory [" + dir + "]. tsdb.lock is a directory.");
				return false;				
			}
			boolean fileExists = lockFile.exists(); 
			if(fileExists) {
				if(!lockFile.createNewFile()) {
					if(lockFile.exists()) {
						fileExists = true;
					} else {
						System.err.println("Failed to lock directory [" + dir + "]. Could not create file [" + lockFile + "]");
						return false;
					}
				}
			}
			final byte[] PIDBYTES = Constants.SPID.getBytes(Charset.defaultCharset());
			FileLock tmpLock = null;
			try {
				lockFileRaf = new RandomAccessFile(lockFile, "rw");
				lockFileFc = lockFileRaf.getChannel();
				tmpLock = lockFileFc.tryLock(0, 0, false);
				if(tmpLock==null) {
					System.err.println("Failed to lock directory [" + dir + "]. Could not lock file [" + lockFile + "]");
					return false;					
				}
				lockFileFc.truncate(PIDBYTES.length);
				lockFileFc.write(ByteBuffer.wrap(PIDBYTES), 0);
				lockFileLock = lockFileFc.tryLock(0, PIDBYTES.length, true);
//				tmpLock.close();
				return true;
			} catch (Exception ex) {
				if(lockFileRaf!=null) try { lockFileRaf.close(); } catch (Exception x) {/* No Op */}
				if(lockFileFc!=null) try { lockFileFc.close(); } catch (Exception x) {/* No Op */}
//				if(tmpLock!=null) try { tmpLock.close();} catch (Exception x) {/* No Op */}
				throw ex;
			} 
		} catch (Exception x) {
			System.err.println("Failed to lock directory [" + dir + "]. Lock failed:" + x);
			return false;
		}
	}
	
	/**
	 * Tests a single offline data store directory pattern
	 * @param name The file pattern to test
	 * @return The directory plus file if successful, null otherwise 
	 */
	protected File doOffline(final String name) {
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
			if(testFile!=null) testFile.delete();
		}
		
	}
	

	/**
	 NaturalOrderComparator.java -- Perform 'natural order' comparisons of strings in Java.
	 Copyright (C) 2003 by Pierre-Luc Paour <natorder@paour.com>

	 Based on the C version by Martin Pool, of which this is more or less a straight conversion.
	 Copyright (C) 2000 by Martin Pool <mbp@humbug.org.au>

	 This software is provided 'as-is', without any express or implied
	 warranty.  In no event will the authors be held liable for any damages
	 arising from the use of this software.

	 Permission is granted to anyone to use this software for any purpose,
	 including commercial applications, and to alter it and redistribute it
	 freely, subject to the following restrictions:

	 1. The origin of this software must not be misrepresented; you must not
	 claim that you wrote the original software. If you use this software
	 in a product, an acknowledgment in the product documentation would be
	 appreciated but is not required.
	 2. Altered source versions must be plainly marked as such, and must not be
	 misrepresented as being the original software.
	 3. This notice may not be removed or altered from any source distribution.
	 */
	public static class NaturalOrderComparator implements Comparator<Object>
	{
	    int compareRight(String a, String b)
	    {
	        int bias = 0;
	        int ia = 0;
	        int ib = 0;

	        // The longest run of digits wins. That aside, the greatest
	        // value wins, but we can't know that it will until we've scanned
	        // both numbers to know that they have the same magnitude, so we
	        // remember it in BIAS.
	        for (;; ia++, ib++)
	        {
	            char ca = charAt(a, ia);
	            char cb = charAt(b, ib);

	            if (!Character.isDigit(ca) && !Character.isDigit(cb))
	            {
	                return bias;
	            }
	            else if (!Character.isDigit(ca))
	            {
	                return -1;
	            }
	            else if (!Character.isDigit(cb))
	            {
	                return +1;
	            }
	            else if (ca < cb)
	            {
	                if (bias == 0)
	                {
	                    bias = -1;
	                }
	            }
	            else if (ca > cb)
	            {
	                if (bias == 0)
	                    bias = +1;
	            }
	            else if (ca == 0 && cb == 0)
	            {
	                return bias;
	            }
	        }
	    }

	    @Override
		public int compare(Object o1, Object o2)
	    {
	        String a = o1.toString();
	        String b = o2.toString();

	        int ia = 0, ib = 0;
	        int nza = 0, nzb = 0;
	        char ca, cb;
	        int result;

	        while (true)
	        {
	            // only count the number of zeroes leading the last number compared
	            nza = nzb = 0;

	            ca = charAt(a, ia);
	            cb = charAt(b, ib);

	            // skip over leading spaces or zeros
	            while (Character.isSpaceChar(ca) || ca == '0')
	            {
	                if (ca == '0')
	                {
	                    nza++;
	                }
	                else
	                {
	                    // only count consecutive zeroes
	                    nza = 0;
	                }

	                ca = charAt(a, ++ia);
	            }

	            while (Character.isSpaceChar(cb) || cb == '0')
	            {
	                if (cb == '0')
	                {
	                    nzb++;
	                }
	                else
	                {
	                    // only count consecutive zeroes
	                    nzb = 0;
	                }

	                cb = charAt(b, ++ib);
	            }

	            // process run of digits
	            if (Character.isDigit(ca) && Character.isDigit(cb))
	            {
	                if ((result = compareRight(a.substring(ia), b.substring(ib))) != 0)
	                {
	                    return result;
	                }
	            }

	            if (ca == 0 && cb == 0)
	            {
	                // The strings compare the same. Perhaps the caller
	                // will want to call strcmp to break the tie.
	                return nza - nzb;
	            }

	            if (ca < cb)
	            {
	                return -1;
	            }
	            else if (ca > cb)
	            {
	                return +1;
	            }

	            ++ia;
	            ++ib;
	        }
	    }

	    char charAt(String s, int i)
	    {
	        if (i >= s.length())
	        {
	            return 0;
	        }
			return s.charAt(i);
	    }
	}


	/**
	 * Returns the count of successful metric collection flushes
	 * @return the count of successful metric collection flushes
	 */
	public long getFlushSuccessCounter() {
		return flushSuccessCounter.get();
	}

	/**
	 * Returns the count of failed metric collection flushes
	 * @return the count of failed metric collection flushes
	 */
	public long getFlushFailedCounter() {
		return flushFailedCounter.get();
	}
	
	/**
	 * Returns the count of bad content metric collection flushes
	 * @return the count of bad content collection flushes
	 */
	public long getFlushBadContentCounter() {
		return flushBadContentCounter.get();
	}	
	
	
	

}
