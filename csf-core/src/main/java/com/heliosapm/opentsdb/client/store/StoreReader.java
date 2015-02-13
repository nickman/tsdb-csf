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

package com.heliosapm.opentsdb.client.store;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

/**
 * <p>Title: StoreReader</p>
 * <p>Description: Defines the layout of MetricStore files</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.store.StoreReader</code></p>
 */

public enum StoreReader implements IStoreReaderFactory {
	/** The v1 file reader */
	READER_v1 (1, new IStoreReaderFactory(){public IReader_V1 getReader() { return new Reader_V1(); }});
	
	private StoreReader(final int version, final IStoreReaderFactory reader) {
		this.version = version;
		this.reader = reader;
	}
	
	/** The MetricStore format version */
	public final int version;
	/** The reader for this version */
	final IStoreReaderFactory reader;
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.store.IStoreReaderFactory#getReader()
	 */
	@Override
	public IReader_V1 getReader() { return reader.getReader(); }
	
	
	/** The prefix for the reader enum */
	public static final String READER_PREFIX = "READER_v";
	/** The first int of the magic number */
	public static final int MAGIC_NUMBER_1 = 3102;
	/** The second int of the magic number */
	public static final int MAGIC_NUMBER_2 = 43170;
	/** The magic number as a string */
	public static final String MAGIC_STRING = Integer.toHexString(MAGIC_NUMBER_1) + Integer.toHexString(MAGIC_NUMBER_2); 
	
	
	/** Some syntactic sugar */
	private static final StoreReader[] values = StoreReader.values();
	
	/**
	 * Reads the header from the passed file in the form of a memory mapped byte buffer
	 * @param storeFile The MetricStore file
	 * @param mode The map mode
	 * @param keepOpen if true, the internal random access file and file channel will be kept open, otherwise they will be closed.
	 * If an exception is thrown, they wil both be closed.
	 * @return the mapped byte buffer
	 */
	@SuppressWarnings("resource")
	public static IReader_V1 loadHeader(final File storeFile, final MapMode mode, final boolean keepOpen) {
		if(storeFile==null) throw new IllegalArgumentException("The passed file was null");
		if(mode==null) throw new IllegalArgumentException("The passed Map mode was null");
		if(storeFile.canRead()) throw new IllegalArgumentException("The passed file [" + storeFile + "] cannot be read");
		RandomAccessFile raf = null;
		FileChannel fc = null;
		try {
			raf = new RandomAccessFile(storeFile, MapMode.READ_ONLY==mode ? "r" : "rw");
			raf.seek(0);
			int m1 = raf.readInt(), m2 = raf.readInt(), v = raf.readInt();
			if(v<1) throw new RuntimeException("Read invalid MetricStore format version [" + v + "] in file [" + storeFile + "]. File may be corrupt");
			raf.seek(0);
			String magic = Integer.toHexString(m1) + Integer.toHexString(m2);
			if(!MAGIC_STRING.equals(magic)) throw new RuntimeException("Invalid magic number for MetricStore file [" + magic + "]. Expected [" + MAGIC_STRING + "]");
			try {
				IReader_V1 ir = StoreReader.valueOf(READER_PREFIX + v).getReader();
				ir.setFileChannel(fc);
				ir.setRandomAccessFile(raf);
				return ir;				
			} catch (Exception x) {
				throw new Exception("Invalid MetricStore version [" + v + "]. Latest supported version is [" + values[values.length-1].version + "]. Are you running the latest version ?");
			}
		} catch (Exception ex) {
			if(raf!=null) try { raf.close(); } catch (Exception x) {/* No Op */}
			if(fc!=null) try { fc.close(); } catch (Exception x) {/* No Op */}			
			throw new RuntimeException("Failed to load header from file [" + storeFile + "]", ex);
		} finally {
			if(!keepOpen) {
				if(raf!=null) try { raf.close(); } catch (Exception x) {/* No Op */}
				if(fc!=null) try { fc.close(); } catch (Exception x) {/* No Op */}
			}
		}		
	}
	
//	/**
//	 * Reads the header from the passed file channel in the form of a memory mapped byte buffer
//	 * @param storeFileChannel The file channel pointing to a MetricStore file
//	 * @param mode The map mode
//	 * @return the mapped byte buffer
//	 */
//	public static MappedByteBuffer loadHeader(final FileChannel storeFileChannel, final MapMode mode) {
//		if(storeFileChannel==null) throw new IllegalArgumentException("The passed file channel was null");
//		if(mode==null) throw new IllegalArgumentException("The passed Map mode was null");
//		if(!storeFileChannel.isOpen()) throw new RuntimeException("The passed file channel was not open");
//		try {
//			return storeFileChannel.map(mode, OFFSET, LENGTH);
//		} catch (Exception ex) {
//			throw new RuntimeException("Failed to read header from file channel", ex);
//		}
//		
//	}
	



}
