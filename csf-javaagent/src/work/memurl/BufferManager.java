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
package com.heliosapm.utils.classload.memurl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.ObjectName;

import com.google.common.cache.RemovalNotification;
import com.heliosapm.utils.jmx.JMXHelper;
import com.heliosapm.utils.unsafe.UnsafeAdapter;
import com.heliosapm.utils.url.URLHelper;

/**
 * <p>Title: BufferManager</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.utils.classload.memurl.BufferManager</code></p>
 */

public class BufferManager implements BufferManagerMBean, Runnable {
	/** The singleton instance */
	private static volatile BufferManager instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	
	/** The BufferManager's JMX object name */
	private static final ObjectName OBJECT_NAME = JMXHelper.objectName(new StringBuilder(BufferManager.class.getPackage().getName()).append(":service=").append(BufferManager.class.getSimpleName()));
	
	private final Map<URL, BufferReference> buffers = new ConcurrentHashMap<URL, BufferReference>();
	private final ReferenceQueue<ByteBuffer> bufferRefQueue = new ReferenceQueue<ByteBuffer>();
	
	private final Thread refQueueCleaner;
	private final AtomicBoolean factoryRegistered = new AtomicBoolean(false);
    
    
	/** The mem URL protocol */
	public static final String MEM_PROTOCOL = "mem";
	/** The mem URL prefix */
	public static final String URL_PREFIX = "mem://localhost/";
	
	/**
	 * Acquires the BufferManager singleton instance
	 * @return the BufferManager singleton instance
	 */
	public static BufferManager getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new BufferManager();
				}
			}
		}
		return instance;
	}
	
   class BufferReference extends WeakReference<ByteBuffer> {
        final URL url;        
		/**
		 * Creates a new BufferReference
		 * @param url The URL assigned to the buffer
		 * @param bb The buffer
		 */
		public BufferReference(final URL url, final ByteBuffer bb) {
			super(bb, bufferRefQueue);
			this.url= url;			
		}
    }
	
	
	public void run() {
		while(true) {
			try {
				final BufferReference w = (BufferReference)bufferRefQueue.remove();
				final URL url = w.url;
				if(url!=null) {
					buffers.remove(url);
				}
			} catch (Exception ex) {
				if(Thread.interrupted()) Thread.interrupted();
				continue;
			}
		}
	}
	
	/**
	 * Creates a new BufferManager
	 */
	private BufferManager() {
		refQueueCleaner = new Thread(this, "BufferManagerRefQueueCleaner");
		refQueueCleaner.setDaemon(true);
		refQueueCleaner.start();
		register();
	}
	
    /**
     * Registers the Memory URL stream factory
     */
    public void register() {
    	if(factoryRegistered.compareAndSet(false, true)) {
    		URL.setURLStreamHandlerFactory(new MemoryURLStreamHandlerFactory());
    	}
    }	
    
	
	/**
	 * Creates a new un-registered MemBuffer
	 * @param initialCapacity The initial capacity of the buffer to create. If 0 or less, the buffer will have no initial capacity.
	 * @param url the URL this mem buffer is being created for
	 * @return a new un-registered MemBuffer
	 */
	protected MemBuffer newMemBuffer(int initialCapacity, URL url) {
		return new MemBuffer(initialCapacity, MemBuffer.DEFAULT_NEXT_SEG, url);
	}
	
	
	/**
	 * Registers a MemBuffer to associate with the passed mem URL
	 * @param url The mem URL to register with
	 * @param buffer The MemBuffer to register. If this is null, a new MemBuffer will be created.
	 * @return The registered MemBuffer
	 */
	MemBuffer registerMemBuffer(final URL url) {
		validateMemUrl(url);
		try {
			MemBuffer mb = references.get(url.toString(), new Callable<MemBuffer>(){
				public MemBuffer call() {
					log("Registered MemBuffer for [%s]", url);
					return new MemBuffer(getInitialCapacity(url),  getNextSeg(url), url);
				}
			});		
			
			return mb;
		} catch (java.util.concurrent.ExecutionException ex) {
			throw new RuntimeException("Failed to create new MemBuffer for URL [" + url + "]", ex);
		}
	}
	
    /**
     * Parses the initial capacity argument from a URL query segment
     * @param url the URL to get the query segment from
     * @return the initial capacity or -1
     */
    protected int getInitialCapacity(URL url) {
    	String val = getParameterValue(url, "ic");
    	if(val==null) return MemBuffer.DEFAULT_SIZE;
    	return Integer.parseInt(val.trim());
    }    
    
    /**
     * Parses the next seg allocation argument from a URL query segment
     * @param url the URL to get the query segment from
     * @return the next seg allocation or -1
     */
    public int getNextSeg(URL url) {
    	String val = getParameterValue(url, "ns");
    	if(val==null) return MemBuffer.DEFAULT_NEXT_SEG;
    	return Integer.parseInt(val.trim());
    }        
    
	

	
	public void unregister(URL url) {
		references.invalidate(url);
	}
	
	
	/**
	 * Validates the passed URL to ensure that it is not null and has a <code>mem</code> protocol
	 * @param url The URL to validate
	 */
	public void validateMemUrl(URL url) {
		if(url==null) throw new IllegalArgumentException("The passed URL was null", new Throwable());
		if(!MEM_PROTOCOL.equalsIgnoreCase(url.getProtocol())) throw new IllegalArgumentException("The passed URL [" + url + "] did not have a mem protocol", new Throwable());		
	}
	

	
	/**
	 * Returns the MemBuffer associated to the passed URL
	 * @param url The URL to get the associated MemBuffer for
	 * @return the associated MemBuffer
	 */
	public MemBuffer getMemBuffer(URL url) {
		return references.getIfPresent(url.toString());
	}
	
	/**
	 * Returns the MemBuffer associated to the passed URL
	 * @param url The URL to get the associated MemBuffer for
	 * @return the associated MemBuffer
	 */
	public MemBuffer getMemBuffer(String url) {
		return references.getIfPresent(url);
	}
	

	
	/**
	 * Returns the URL created for the passed URL suffix
	 * @param bufferName The URL suffix for the URL
	 * @return the MemBuffer URL
	 */
	@Override
	public URL getMemBufferURL(String bufferName) {
		return URLHelper.toURL(URL_PREFIX + bufferName);
	}
	
	
    /**
     * Parses the named query parameter from a URL query segment
     * @param url the URL to get the query segment from
     * @param paramName The name of the parameter to get
     * @return the value of the named query parameter or null if it was not found
     */
    public String getParameterValue(URL url, String paramName) {
    	if(url==null || paramName==null || paramName.trim().isEmpty()) return null;
    	paramName = paramName.trim();
    	String query = url.getQuery();
    	if(query==null || query.trim().isEmpty()) return null;
    	String[] frags = query.trim().split("&");
    	for(String pair: frags) {
    		pair = pair.trim().toLowerCase();
    		String delim = paramName + "=";
    		int index = pair.indexOf(delim);
    		if(index!=-1) {
    			return pair.substring(index + delim.length());
    		}
    	}
    	return null;
    }    
	
	

    
    /**
     * Returns the number of allocated and registered MemBuffers
     * @return the number of allocated and registered MemBuffers
     */
    @Override
	public int getMemBufferCount() {
    	return (int)references.size();
    }
    
    /**
     * {@inheritDoc}
     * @see com.theice.clearing.eventcaster.io.mem.BufferManagerMBean#printKeys()
     */
    @Override
    public Set<String> printKeys() {
    	return new HashSet<String>(references.asMap().keySet());
    }
    
    /**
     * {@inheritDoc}
     * @see com.theice.clearing.eventcaster.io.mem.BufferManagerMBean#getMemBufferExpirations()
     */
    @Override
    public long getMemBufferExpirations() {
    	return MemBuffer.getFinalizationCount();
    }

	/**
	 * {@inheritDoc}
	 * @see com.google.common.cache.RemovalListener#onRemoval(com.google.common.cache.RemovalNotification)
	 */
	@Override
	public void onRemoval(RemovalNotification<String, MemBuffer> notification) {
		log("WeakRefed out a mem url [%s] on account of [%s]", notification.getKey().toString(), notification.getCause().name());
		
	}
	
	/**
	 * Simple out formatted logger
	 * @param fmt The format of the message
	 * @param args The message arguments
	 */
	public static void log(String fmt, Object...args) {
		System.out.println(String.format(fmt, args));
	}

	
	static class MemBuffer implements DeallocatingAction {
		/** The memory buffer pointer */
		private long address;
		/** The offset of the last byte of data written in to the buffer */
		private long size;
		/** The total size of the memory currently allocated under the address */
		private long capacity;
		
		/** The buffer input stream */
		InputStream is;
		/** The timestamp of the last data change to this buffer */
		private long lastChange = System.currentTimeMillis();
		/** The URL this mem buffer was created for */
		private final URL memUrl;
		
		private static final AtomicLong instances = new AtomicLong();
		private static final AtomicLong highWater = new AtomicLong();
		
		
		
		public String toString() {
			return String.format("[%s] size:%s  capacity:%s", memUrl.toString(), size, capacity);
		}
		/** The size increment of the memory allocation when the space is exhausted */
		private final int nextSegSize;
		
		private final AtomicLong addressUpdater;
		
		
		/** A counter of the number of MemBuffer finalizations */
		private static final AtomicLong fCount = new AtomicLong(0L);
		
		/** The default size of the initial memory space */
		public static final int DEFAULT_SIZE = 1024;
		/** The default size to grow the memory space by when space is exhausted */
		public static final int DEFAULT_NEXT_SEG = 1024;
		
		/**
		 * Returns the count of MemBuffers that have been finalized
		 * @return the count of MemBuffers that have been finalized
		 */
		public static long getFinalizationCount() {
			return fCount.get();
		}
		
		/**
		 * Creates a new MemBuffer
		 * @param initialSize The initial memory size
		 * @param nextSegSize The size of the next segment allocated when space is exhausted
		 * @param memUrl The URL this mem buffer was created for
		 */
		public MemBuffer(int initialSize, int nextSegSize, URL memUrl) {
			this.memUrl = memUrl;
			this.nextSegSize = nextSegSize;
			address = UnsafeAdapter.allocateMemory(initialSize);
			size = 0;
			capacity = initialSize;
			addressUpdater = RunnableReferenceQueue.getInstance().buildPhantomReference(this, address); 		
			highWater.set(instances.incrementAndGet());
			System.out.println("\n\t#### Created MemBuffer for [" + memUrl + "] --> [" + System.identityHashCode(this) + "] ####");
		}
		
		/**
		 * Creates a new Record of the defalt initial size and growth segment size
		 * @param memUrl The URL this mem buffer was created for
		 */
		public MemBuffer(URL memUrl) {
			this(DEFAULT_SIZE, DEFAULT_NEXT_SEG, memUrl);
		}
		
		/**
		 * Writes the passed byte to the current offset in the buffer
		 * @param b The byte to write
		 * @throws IOException thrown if the position specified is greater than the current size
		 */
		void write(byte b) throws IOException {
			if(capacity-size<10) {
				resize();
			}
			UnsafeAdapter.putByte(null, address + size, b);
			size++;
			lastChange = System.currentTimeMillis();
		}
		
		/**
		 * Writes the passed byte array to the current offset in the buffer
		 * @param arr The byte array to write
		 * @throws IOException thrown if the position specified is greater than the current size
		 */
		void write(byte[] arr) throws IOException {
			if(arr==null) throw new IllegalArgumentException("The passed byte array was null");
			if(capacity-size<(arr.length + 10)) {
				resize(Math.max(DEFAULT_NEXT_SEG, arr.length + 10));
			}
			UnsafeAdapter.copyMemory(arr, UnsafeAdapter.BYTE_ARRAY_OFFSET, null, address + size, arr.length);
			lastChange = System.currentTimeMillis();
			size += arr.length;
		}
		
		/**
		 * Reads a byte from the allocated memory at the specified offset
		 * @param pos The offset off the address to read from
		 * @return The read byte
		 * @throws IOException thrown if the position specified is greater than the current size
		 */
		byte read(long pos) throws IOException {
			if(pos<0) throw new IllegalArgumentException("Invalid position [" + pos + "]");
			if(pos>size) throw new IOException("Invalid position [" + pos + "] for buffer of size ["  + size + "].");
			return UnsafeAdapter.getByte(address + pos);
		}
		
		/**
		 * Reads the allocated memory at the specified offset into the passed byte array
		 * @param pos The offset off the address to read from
		 * @param arr The aray to write into
		 * @return The number of bytes read
		 * @throws IOException thrown if the position specified is greater than the current size
		 */
		int read(long pos, byte[] arr) throws IOException {
			if(pos<0) throw new IllegalArgumentException("Invalid position [" + pos + "]");
			if(pos>size) throw new IOException("Invalid position [" + pos + "] for buffer of size ["  + size + "].");		
			if(size==pos) return 0;		
			int maxBytes = (int)size-(int)pos;
			int bytesToRead = (int)(Math.min(maxBytes, arr.length));
			UnsafeAdapter.copyMemory(null, address + pos, arr, UnsafeAdapter.BYTE_ARRAY_OFFSET, bytesToRead);
			return bytesToRead;
		}
		
		/**
		 * Reads the allocated memory at the specified offset into the passed byte array
		 * @param pos The offset off the address to read from
		 * @param arr The aray to write into
		 * @param off The offset in the array to start writing at
		 * @param len the number of bytes to write into the array
		 * @return the number of bytes written
		 * @throws IOException
		 */
		int read(long pos, byte[] arr, int off, int len) throws IOException {
			if(pos<0) throw new IllegalArgumentException("Invalid position [" + pos + "]");
			if(pos>size) throw new IOException("Invalid position [" + pos + "] for buffer of size ["  + size + "].");
			if(size==pos) return 0;
			int maxBytes = (int)size-(int)pos;
			int bytesToRead = (int)(Math.min(maxBytes, len));
			UnsafeAdapter.copyMemory(null, address + pos, arr, UnsafeAdapter.BYTE_ARRAY_OFFSET + off, bytesToRead);
			return bytesToRead;				
		}
		
		/**
		 * Grows the allocated memory space by {@link #nextSegSize} bytes.
		 */
		private void resize() {
			address = UnsafeAdapter.reallocateMemory(address, capacity + nextSegSize);
			capacity += nextSegSize; 
			addressUpdater.set(address);
		}
		
		/**
		 * Grows the allocated memory space by the passed size
		 * @param increase The number of bytes to increase the allocated memory by
		 */
		private void resize(int increase) {
			address = UnsafeAdapter.reallocateMemory(address, capacity + increase);
			capacity += increase;
			addressUpdater.set(address);
		}
		
		
		/**
		 * Returns the timestamp of the last modification to this record's data
		 * @return the timestamp of the last modification to this record's data
		 */
		public long getLastModifiedTime() {
			return lastChange;
		}
		
		/**
		 * Returns the buffer's output stream
		 * @return the buffer's output stream
		 */
		public OutputStream getOutputStream() {
			return new MemBufferOutputStream(this);
		}
		
		/**
		 * Returns the buffer's input stream
		 * @return the buffer's intput stream
		 */
		public InputStream getInputStream() {
			return new MemBufferInputStream(this);
		}

		/**
		 * {@inheritDoc}
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (int) (address ^ (address >>> 32));
			return result;
		}

		/**
		 * {@inheritDoc}
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			MemBuffer other = (MemBuffer) obj;
			if (address != other.address)
				return false;
			return true;
		}

		/**
		 * Returns the current number of instances 
		 * @return the current number of instances
		 */
		public static long getInstances() {
			return instances.get();
		}
		
		/**
		 * Returns the highwater instance count
		 * @return the highwater instance count
		 */
		public static long getHighwaterInstances() {
			return highWater.get();
		}
		
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.shorthand.util.ref.DeallocatingAction#getAction()
		 */
		@Override
		public Runnable getAction() {
			return action;
		}
		
		private final Runnable action = newAction(instances, fCount);
		
		private static Runnable newAction(final AtomicLong inst, final AtomicLong fcnt) {
			return new Runnable() {
				@Override
				public void run() {
					instances.decrementAndGet();
					fCount.incrementAndGet();
				}				
			};
		}

		/**
		 * Returns the total size of the allocated memory in bytes
		 * @return the allocated memory size
		 */
		public long getSize() {
			return size;
		}

	}


}
