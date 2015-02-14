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

import static com.heliosapm.opentsdb.client.opentsdb.Constants.CHARSET;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBufferOutputStream;

import com.heliosapm.opentsdb.client.util.DynamicByteBufferBackedChannelBuffer;
import com.heliosapm.opentsdb.client.util.DynamicByteBufferBackedChannelBufferFactory;

/**
 * <p>Title: OffHeapFIFOFile</p>
 * <p>Description: An off-heap space to stream IO activity to avoid large heap allocations</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.OffHeapFIFOFile</code></p>
 */

public class OffHeapFIFOFile {
	/** The fully qualified file name */
	private final String fileName;
	/** The underlying file */
	private final File file;
	/** The off-heap transfer buffer */
	private final DynamicByteBufferBackedChannelBuffer ohbuff;
	/** Temporary compression buffer, allocated when needed */
	private DynamicByteBufferBackedChannelBuffer tmpBuffer = null;
	/** Transfer buffer for compression, allocated when needed */
	private byte[] transferBuff = null;
	
	/** The buffer factory for allocating composite buffers to stream reads and writes through offheap space */
    protected static final DynamicByteBufferBackedChannelBufferFactory bufferFactory = new DynamicByteBufferBackedChannelBufferFactory(4096);
	
	/** Static class logger */
	private static final Logger log = LogManager.getLogger(OffHeapFIFOFile.class);

	/** An empty InputStream array const */
	private static final InputStream[] EMPTY_IS_ARR = {};
	/** An empty File array const */
	private static final File[] EMPTY_FILE_ARR = {};
	
	/** The default transfer transfer byte array size */
	public static final int XFER_BUFF_SIZE = 2048;
	
	/** A map of all known OffHeapFIFOFiles keyed by the absolute file name */
	private static final Map<String, OffHeapFIFOFile> FILECACHE = new ConcurrentHashMap<String, OffHeapFIFOFile>();
	
	/**
	 * Acquires the OffHeapFIFOFile for the file
	 * @param file The file
	 * @return the OffHeapFIFOFile 
	 */
	public static OffHeapFIFOFile get(final File file) {
		return get(file.getParentFile(), file.getName());
	}
	
	/**
	 * Acquires the OffHeapFIFOFile for the passed directory and file name
	 * @param parentDir The directory to get the file in
	 * @param fileName The name of the file
	 * @return the OffHeapFIFOFile 
	 */
	public static OffHeapFIFOFile get(final File parentDir, final String fileName) {
		final File file = new File(parentDir, fileName);
		final String key = file.getAbsolutePath();
		OffHeapFIFOFile ff = FILECACHE.get(key);
		if(ff==null) {
			synchronized(FILECACHE) {
				ff = FILECACHE.get(key);
				if(ff==null) {
					ff = new OffHeapFIFOFile(parentDir, fileName);
					FILECACHE.put(key, ff);
				}
			}
		}
		return ff;
	}

	/**
	 * Creates a new OffHeapFIFOFile
	 * @param parentDir The parent directory
	 * @param fileName The file name
	 */
	private OffHeapFIFOFile(final File parentDir, final String fileName) {		
		file = new File(parentDir, fileName);
		if(!parentDir.exists()) {
			if(!parentDir.mkdirs()) throw new RuntimeException("Failed to create parent directory [" + parentDir + "]");
		}
		this.fileName = file.getName();
		if(!file.exists()) {
			try {
				if(!file.createNewFile()) throw new Exception();
			} catch (Exception ex) {
				throw new RuntimeException("Failed to create file [" + file + "]");
			}
		}
		if(file.length()==0) {
			initSize();
		}
		ohbuff = bufferFactory.getBuffer();
	}
	
	/**
	 * Adds an already existing file to the file cache
	 * @param file the already existing file 
	 */
	public static void existing(final File file) {
		if(file!=null) {
			if(file.exists()) {
				FILECACHE.put(file.getAbsolutePath(),  new OffHeapFIFOFile(file.getParentFile(), file.getName()));
			}
		}
	}
	
	
	
	/**
	 * Deletes this file and removes it from cache
	 * @return true if successfully deleted, false otherwise
	 */
	public boolean delete() {
		final boolean del = file.delete();
		if(del) FILECACHE.remove(file.getAbsolutePath());
		ohbuff.clean();
		if(tmpBuffer!=null) {
			tmpBuffer.clean();
			tmpBuffer = null;
		}
		transferBuff = null;	
		return del;		
	}
	
	
	void slimDown() {
		ohbuff.reset();
		DynamicByteBufferBackedChannelBuffer t = tmpBuffer;
		tmpBuffer = null;
		if(t!=null) {
			t.clean();
			t = null;
		}
		transferBuff = null;		
	}
	
	/**
	 * Returns the number of entries in the file
	 * @return the number of entries in the file
	 */
	public int getEntrySize() {
		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile(file, "r");
			if(raf.length()<4) return -1;
			raf.seek(0);
			return raf.readInt();
		} catch (Exception ex) {
			log.error("Failed to read entry size in FIFO file [" + this + "]:" + ex);
			return -1;
		} finally {
			if(raf!=null) try { raf.close(); } catch (Exception x) {/* No Op */}
		}		
	}
	
	/**
	 * Returns the underlying file
	 * @return the underlying file
	 */
	public File getFile() {
		return file;
	}
	
	/**
	 * Returns the number of entries in the file
	 * @param fc An existing file channel
	 * @return the number of entries in the file
	 */
	protected int getEntrySize(final FileChannel fc) {
		MappedByteBuffer buff = null;
		try {
			if(fc==null || fc.size()<4) return -1;
			buff = fc.map(MapMode.READ_ONLY, 0, 4);
			return buff.getInt(0);			
		} catch (Exception ex) {
			log.error("Failed to read entry size from [" + file + "]:" + ex);
			return -1;
		} finally {
			clean(buff);
		}
	}
	
	/**
	 * Writes the entry count to the file
	 * @param fc The file channel
	 * @param newSize The entry count to write
	 */
	protected void setEntrySize(final FileChannel fc, final int newSize) {
		MappedByteBuffer buff = null;
		try {
			if(fc==null || fc.size()<4) return;
			buff = fc.map(MapMode.READ_WRITE, 0, 4);
			buff.putInt(0, newSize);				
		} catch (Exception ex) {
			log.error("Failed to set entry size from [" + file + "]:" + ex);
			return;
		} finally {
			clean(buff);
		}		
	}
	
	/**
	 * Writes the entry count to the file
	 * @param raf The Random Access File
	 * @param newSize The entry count to write
	 */
	protected void setEntrySize(final RandomAccessFile raf, final int newSize) {		
		try {
			if(raf==null || raf.length()<4) return;
			raf.seek(0);
			raf.writeInt(newSize);
		} catch (Exception ex) {
			log.error("Failed to set entry size from [" + file + "]:" + ex);
			return;
		}		
	}
	
	
	/**
	 * Increments the entry count to the file
	 * @param fc The file channel
	 * @param delta The amount to increment by (pass negative value to decrement)
	 * @return The new value
	 */
	protected int incrEntrySize(final FileChannel fc, final int delta) {
		MappedByteBuffer buff = null;
		try {
			if(fc==null || fc.size()<4) return -1;
			buff = fc.map(MapMode.READ_WRITE, 0, 4);
			final int newSize = buff.getInt(0) + delta;
			buff.putInt(0, newSize);
			return newSize;
		} catch (Exception ex) {
			log.error("Failed to increment entry size from [" + file + "]:" + ex);
			return -1;
		} finally {
			clean(buff);
		}		
	}
	
	
	
	/**
	 * Writes a zero to the entry count field
	 */
	private void initSize() {
		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile(file, "rw");
			raf.setLength(4);
			raf.seek(0);
			raf.writeInt(0);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to initialize FIFO file [" + this + "]", ex);
		} finally {
			if(raf!=null) try { raf.close(); } catch (Exception x) {/* No Op */}
		}
	}
	
	/**
	 * Returns this file's name
	 * @return this file's name
	 */
	public String getFileName() {
		return file.getName();
	}
	
	/**
	 * Returns this file's size
	 * @return this file's size
	 */
	public long getFileSize() {
		return file.length();
	}
	
	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		int entryCount = -1;
		try {
			entryCount = getEntrySize();
		} catch (Exception ex) {/* No Op */}
		return new StringBuilder("OffHeapFIFOFile [").append(file.toString()).append("], Size:").append(file.length()).append(", Entries:").append(entryCount).toString();
	}
	
	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((file == null) ? 0 : file.hashCode());
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
		OffHeapFIFOFile other = (OffHeapFIFOFile) obj;
		if (file == null) {
			if (other.file != null)
				return false;
		} else if (!file.equals(other.file))
			return false;
		return true;
	}

	
	/**
	 * Returns the current size of the file in bytes
	 * @return the size of the file in bytes
	 */
	public synchronized long size() {
		try {
			return file.length();
		} catch (Exception ex) {
			throw new RuntimeException("Failed to read file size for [" + file + "]", ex);
		}
	}
	
	/**
	 * Returns the file space that will be allocated to write the passed json strings
	 * @param jsons The json strings to measure
	 * @return the number of file bytes required to write the passed strings
	 */
	public static long getWriteSize(final String...jsons) {
		if(jsons==null || jsons.length<1) return 0L;
		long cnt = 0;
		for(String json: jsons) {
			if(json==null || json.isEmpty()) continue;
			cnt += 4;
			cnt += json.getBytes(CHARSET).length;
		}
		return cnt;
		
	}
	
	/**
	 * Dumps the content of the file
	 * @param stream The output stream to write to
	 */
	public synchronized void dump(final OutputStream stream) {
		PrintStream p = null;
		try {
			p = new PrintStream(stream, true, CHARSET.name());
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("Unsupported Character Set [" + CHARSET.name() + "]", e);
		} 
		if(file.length()<1) return;
		int entrySize = getEntrySize(); 
		if(entrySize < 1) return;
		p.println("File Size:" + file.length());
		p.println("Entry Count:" + entrySize);
		p.println("======================");
		MappedByteBuffer buff = null;
		RandomAccessFile raf = null;
		FileChannel fc = null;
		final ChannelBuffer tmp = bufferFactory.getBuffer();
		int byteSize = -1;
		byte[] bytes = null;
		try {
			raf = new RandomAccessFile(file, "r");
			fc = raf.getChannel();
			buff = fc.map(MapMode.READ_ONLY, 4, fc.size()-4);
			while(buff.hasRemaining()) {
				tmp.clear();
				byteSize = buff.getInt();
				p.print("Size:" + byteSize + "-[");
				bytes = new byte[byteSize];
				buff.get(bytes);
				tmp.writeBytes(bytes);
				if(isGzipped(tmp)) {
					decompress(tmp);
				}
				//p.print(tmp.toString(CHARSET));
				p.println("]");
			}
			p.println("======================");
			p.println("File pos:" + fc.position() + ", Buff pos:" + buff.position() + ", Remaining:" + buff.remaining());
		} catch (Exception ex) {
			throw new RuntimeException("Failed to dump file [" + fileName + "]", ex);
		} finally {
			clean(buff);
			clean(tmp);
			if(raf!=null) try { raf.close(); } catch (Exception x) {/* No Op */}
			if(fc!=null) try { fc.close(); } catch (Exception x) {/* No Op */}
		}
	}
	
	
	/**
	 * Appends the passed stringy to the end of the file
	 * @param value The value to write
	 * @return The new size of the file in bytes, once the write is complete, or -1 if the resulting file will exceed {@link Integer#MAX_VALUE} bytes.
	 */
	public synchronized long write(final CharSequence value) {
		if(value==null || value.length()==0) return file.length();
		ohbuff.clear();
		ohbuff.writeBytes(value.toString().getBytes(CHARSET));		
		return write();		
	}
	
	/**
	 * Appends the contents of the passed buffer to the end of the file
	 * @param buff The buffer to write
	 * @return The new size of the file in bytes, once the write is complete, or -1 if the resulting file will exceed {@link Integer#MAX_VALUE} bytes.
	 */
	public synchronized long write(final ChannelBuffer buff) {
		if(buff==null || buff.readableBytes()==0) return file.length();
		ohbuff.clear();
		ohbuff.writeBytes(buff);		
		return write();		
	}
	
	
//	/**
//	 * Appends the passed metrics to the end of the file
//	 * @param metrics The metric set to write
//	 * @return The new size of the file in bytes, once the write is complete, or -1 if the resulting file will exceed {@link Integer#MAX_VALUE} bytes.
//	 */
//	public synchronized long write(final Set<OpenTsdbMetric> metrics) {
//		if(metrics==null || metrics.isEmpty()) return file.length();
//		ohbuff.clear();
//		final int metricsWritten = OpenTsdbMetric.writeToBuffer(metrics, ohbuff);
//		return write();
//	}	
	
	/**
	 * Writes the FIFO's buffer to the end of the file
	 * @return The new size of the file in bytes, once the write is complete, or -1 if the resulting file will exceed {@link Integer#MAX_VALUE} bytes.
	 */
	private long write() {
		RandomAccessFile raf = null;
		FileChannel fc = null;
		MappedByteBuffer buff = null;
		ByteBuffer bb = null;
		
		try {						
			if(!isGzipped(ohbuff)) {
				compress(ohbuff);
			}
			final int length = ohbuff.readableBytes();
			raf = new RandomAccessFile(file, "rw");
			final long pos = raf.length();
			if(pos + length > Integer.MAX_VALUE) return -1;
			final long newSize = pos + length + 4;
			fc = raf.getChannel();			
			fc.truncate(newSize);
			buff = fc.map(MapMode.READ_WRITE, pos, length + 4);
			buff.putInt(length);
			bb = ohbuff.toByteBuffer();
			buff.put(bb);			
			incrEntrySize(fc, 1);
			return newSize;
		} catch (Exception ex) {
			throw new RuntimeException("Failed to write to [" + fileName + "]", ex);
		} finally {
			if(raf!=null) try { raf.close(); } catch (Exception x) {/* No Op */}
			if(fc!=null) try { fc.close(); } catch (Exception x) {/* No Op */}
			clean(buff);
			clean(bb);
		}
	}
	
	/**
	 * Reads the specified number of entries, writes them to a temp file in the same dir and returns the file.
	 * @param recordsToRead The number of records to read
	 * @return an array of files
	 */
	public synchronized File[] extract(final int recordsToRead) {
		if(recordsToRead<1) return EMPTY_FILE_ARR;
		RandomAccessFile raf = null;
		FileChannel fc = null;
		MappedByteBuffer buff = null;
		final ByteBuffer sizeBuff = ByteBuffer.allocate(4);
		final Set<File> samples;
		int recordsRead = 0;
		int bytesToRead = -1;		
		long poz = 4;
		try {
			// ==============================================================================
			raf = new RandomAccessFile(file, "rw");
			final long fileSize = raf.length();			
			if(fileSize==0) return EMPTY_FILE_ARR;
			fc = raf.getChannel();
			final int entryCount = getEntrySize(fc);
			if(entryCount==0) return EMPTY_FILE_ARR;
			final int toRead = recordsToRead > entryCount ? entryCount : recordsToRead;
			samples = new LinkedHashSet<File>(toRead);
			ohbuff.clear();
			// ==============================================================================
			while(recordsRead < toRead) {
				fc.position(poz);				
				fc.read(sizeBuff);
				sizeBuff.rewind();
				bytesToRead = sizeBuff.getInt(0);
				poz += 4;
				fc.position(poz);
				samples.add(extract(fc, bytesToRead)); 
				poz += bytesToRead;
				fc.position(poz);
				ohbuff.clear();
				recordsRead++;
			}
			final int newEntryCount = entryCount-recordsRead;
			final long newFileSize = fileSize - poz + 4;
			
			buff = fc.map(MapMode.READ_WRITE, 4, fileSize-4);
			buff.load();
			buff.position((int)(poz-4));
//			System.out.println("Compacting between [" + buff.position() + "] and [" + buff.limit() + "]");
			buff.compact();			
			clean(buff);
			//fc.write((ByteBuffer)ByteBuffer.allocate(4).asIntBuffer().put(newEntryCount).flip(), 0);
			raf.seek(0);
			raf.writeInt(newEntryCount);			
//			setEntrySize(fc, newEntryCount);
			fc.truncate(newFileSize);
			return samples.toArray(new File[samples.size()]);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to extract from [" + fileName + "] at loop [" + recordsRead + "], byeSize: [" + bytesToRead + "]", ex);
		} finally {
			if(raf!=null) try { raf.close(); } catch (Exception x) {/* No Op */}
			if(fc!=null && fc.isOpen()) try { fc.close(); } catch (Exception x) {/* No Op */}
			clean(buff);
		}		
	}
	
	
	/**
	 * Extracts the content at the current position of the passed file channel
	 * @param fc The file channel to read from
	 * @param bytesToRead the number of bytes to read
	 * @return the new created file with the content
	 */
	protected File extract(final FileChannel fc, final int bytesToRead) {
		FileOutputStream fos = null;
		FileChannel outFc = null;
		try {
			File tmp = File.createTempFile("inprocess-" + file.getName().replace(".dat", ""), ".tmp", file.getParentFile());
			fos = new FileOutputStream(tmp);
			outFc = fos.getChannel();
			outFc.truncate(bytesToRead);
			long bytesWritten = fc.transferTo(fc.position(), bytesToRead, outFc);
			if(bytesWritten != bytesToRead) {
				log.error("Warning. Tmp File. Expected:" + bytesToRead + ", Actual:" + bytesWritten);
			} else {
				fc.position(fc.position() + bytesToRead);
			}
			return tmp;
		} catch (Exception ex) {
			throw new RuntimeException("Failed to extract", ex);
		} finally {
			if(fos!=null) try { fos.close(); } catch (Exception x) {/* No Op */}
			if(outFc!=null) try { outFc.close(); } catch (Exception x) {/* No Op */}
		}
	}
	
	
	/**
	 * Determines if the content of the passed buffer is GZipped
	 * @param buff The buffer to check
	 * @return true if the content of the passed buffer is GZipped, false otherwise
	 */
	public static boolean isGzipped(final ByteBuffer buff) {
		if(buff==null || buff.limit() <2) return false;
		int magic1 = (short)(buff.get(0) & 0xFF);
		int magic2 = (short)(buff.get(1) & 0xFF);
		return magic1 == 31 && magic2 == 139;
	}
	
	/**
	 * Determines if the content of the passed file is GZipped
	 * @param file The file to check
	 * @return true if the content of the passed file is GZipped, false otherwise
	 */
	public static boolean isGzipped(final File file) {
		if(file==null || !file.canRead()) return false;
		if(file.length()<8) return false;
		final int offset = MetricPersistence.FILE_NAME_PATTERN.matcher(file.getName()).matches() ? 8 : 0;
		FileInputStream fis = null;
		FileChannel fc = null;
		MappedByteBuffer buff = null;
		try {
			fis = new FileInputStream(file);
			fc = fis.getChannel();
			buff = fc.map(MapMode.READ_ONLY, offset, 2);
			return isGzipped(buff);
		} catch (Exception ex) {
			return false;
		} finally {
			if(fis!=null) try { fis.close(); } catch (Exception x) {/* No Op */}
			if(fc!=null) try { fc.close(); } catch (Exception x) {/* No Op */}
			clean(buff);
		}
	}
	
	
	
	
	/**
	 * Determines if the content of the passed buffer is GZipped
	 * @param buff The buffer to check
	 * @return true if the content of the passed buffer is GZipped, false otherwise
	 */
	public static boolean isGzipped(final ChannelBuffer buff) {		
		if(buff==null || buff.readableBytes() <2) return false;
		int magic1 = buff.getUnsignedByte(0), magic2 = buff.getUnsignedByte(1);
		return magic1 == 31 && magic2 == 139;
	}
	
	
	/**
	 * Initializes the compression/decompression buffers for this file
	 */
	private void initXpressionBuffers() {
		if(tmpBuffer==null) {
			tmpBuffer = bufferFactory.getBuffer();
		}
		tmpBuffer.clear();
		if(transferBuff == null) {
			transferBuff = new byte[XFER_BUFF_SIZE];
		}		
	}
	
	
	
	/**
	 * Calcs a double average incorporating a new value
	 * using <b><code>(prev_avg*cnt + newval)/(cnt+1)</code></b>
	 * @param prev_avg The pre-average
	 * @param cnt The pre-count
	 * @param newval The new value
	 * @return the average
	 */
	public static double avgd(double prev_avg, double cnt, double newval) {		
		return (prev_avg*cnt + newval)/(cnt+1);
	}
	
	/**
	 * Calculates a compression percentage
	 * @param size The original size
	 * @param csize The compressed size
	 * @return the percentage compression
	 */
	public static double perc(final double size, final double csize) {
		if(size==0 || csize==0) return 0;
		if(csize > size) {
			return 0;
		}
		return (size-csize)/size*100;
	}
	
	/**
	 * Updates the running average compression
	 * @param size The original size
	 * @param csize The compressed size
	 */
	protected void updateAvg(final int size, final int csize) {
		final double perc = perc(size, csize);
		compressionAverage[2] = perc;		
		if(compressionAverage[1]==0d) {
			compressionAverage[0] = perc;
		} else {
			compressionAverage[0] = avgd(compressionAverage[0], compressionAverage[1], perc); 
		}
		compressionAverage[1]++;		
	}
	
	/**
	 * Prints the compression statistics for this file
	 * @return the compression statistics for this file
	 */
	public String printCompressionStats() {
		return new StringBuilder("Compression Stats:\n\tAverage:").append(Math.round(compressionAverage[0])).append("%")				
				.append("\n\tLast:").append(Math.round(compressionAverage[2])).append("%")
				.append("\n\tCount:").append(new Double(compressionAverage[1]).longValue())
				.toString();
	}
	
	/** Compression Stats  (running avg, count, lastReading) */
	protected final double[] compressionAverage = new double[]{-1d, 0d, -1d};
	
	/**
	 * Returns this file's average compression rate
	 * @return this file's average compression rate
	 */
	public double getCompressionRate() {
		return compressionAverage[0];
	}

	
	
	/**
	 * Decompresses the passed buffer
	 * @param buffer The buffer to decompress
	 */
	private synchronized void decompress(final ChannelBuffer buffer) {
		initXpressionBuffers();
		decompress(buffer, tmpBuffer, transferBuff);
	}
	
	private synchronized int compress(final ChannelBuffer buffer) {
		final int size = buffer.readableBytes();
		initXpressionBuffers();
		compress(buffer, tmpBuffer, transferBuff);
		final int newSize = buffer.readableBytes();
		updateAvg(size, newSize);
		return newSize;
	}
	
	/**
	 * Decompresses the content in the passed buffer
	 * @param buffer The buffer to decompress
	 * @param tmpBuffer An optional work space buffer
	 * @param transferBuff An optional transfer byte array
	 */
	@SuppressWarnings("null")
	public static void decompress(final ChannelBuffer buffer, ChannelBuffer tmpBuffer, byte[] transferBuff) {
		if(buffer==null || buffer.readableBytes()==0) return;		
		if(transferBuff==null) transferBuff = new byte[XFER_BUFF_SIZE];
		final boolean cleanTmpBuff = tmpBuffer==null; 
		if(cleanTmpBuff) tmpBuffer = bufferFactory.getBuffer();
		ChannelBufferOutputStream cbos = new ChannelBufferOutputStream(tmpBuffer);
		ChannelBufferInputStream cbis = new ChannelBufferInputStream(buffer);		
		GZIPInputStream gos = null; 
		try {
			gos = new GZIPInputStream(cbis, 1024);			
			int bytesRead = -1;
			while((bytesRead = gos.read(transferBuff))!=-1) {
				cbos.write(transferBuff, 0, bytesRead);
			}
			cbos.flush();
			buffer.clear();
			buffer.writeBytes(tmpBuffer.toByteBuffer());
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
		} finally {
			if(cleanTmpBuff) clean(tmpBuffer);
			try { cbos.close(); } catch (Exception x) {/* No Op */}
			try { cbis.close(); } catch (Exception x) {/* No Op */}
			if(gos!=null) try { gos.close(); } catch (Exception x) {/* No Op */}
		}
	}
	
	
	
	/**
	 * Compresses the passed buffer
	 * @param buffer The buffer to compress
	 * @return the size of the new buffer
	 */
	public static int compress(final ChannelBuffer buffer, ChannelBuffer tmpBuffer, byte[] transferBuff) {
		if(buffer==null || buffer.readableBytes()==0) return -1;
		if(transferBuff==null) transferBuff = new byte[XFER_BUFF_SIZE];
		final boolean cleanTmpBuff = tmpBuffer==null; 
		if(cleanTmpBuff) tmpBuffer = bufferFactory.getBuffer();
		ChannelBufferOutputStream cbos = new ChannelBufferOutputStream(tmpBuffer);
		ChannelBufferInputStream cbis = new ChannelBufferInputStream(buffer);		
		GZIPOutputStream gos = null; 
		try {
			gos = new GZIPOutputStream(cbos, 1024);
			int bytesRead = -1;
			while((bytesRead = cbis.read(transferBuff))!=-1) {
				gos.write(transferBuff, 0, bytesRead);
			}
			gos.flush();
			gos.close();
			cbos.flush();
			buffer.clear();
			buffer.writeBytes(tmpBuffer);
			return buffer.readableBytes();
		} catch (Exception ex) {
			return -1;
		} finally {
			if(cleanTmpBuff) clean(tmpBuffer);
			try { cbos.close(); } catch (Exception x) {/* No Op */}
			try { cbis.close(); } catch (Exception x) {/* No Op */}
			if(gos!=null) try { gos.close(); } catch (Exception x) {/* No Op */}			
		}
	}
	
	/**
	 * Indicates if the metric entries are gzipped
	 * @return true if the metric entries are gzipped, false otherwise
	 */
	public boolean isGZipped() {
		return isGzipped(file);
	}

	
	/**
	 * Cleans an array of ChannelBuffers if they are instances of {@link DynamicByteBufferBackedChannelBuffer}
	 * @param buffs The array of buffers to clean
	 */
	public static void clean(final ChannelBuffer...buffs) {
		if(buffs!=null) {
			for(ChannelBuffer buff: buffs) {
				if(buff==null) continue;
				if(buff instanceof DynamicByteBufferBackedChannelBuffer) {
					((DynamicByteBufferBackedChannelBuffer)buff).clean();
				}
			}
		}
		
	}
	
	
	/**
	 * Manual deallocation of the memory allocated for direct byte buffers.
	 * Does nothing if the cleaner class and methods were not reflected successfully,
	 * the passed buffer is null or not a DirectByteBuffer, 
	 * or if the clean invocation fails.
	 * @param buffs The buffers to clean
	 */
	public static void clean(final ByteBuffer... buffs) {
		if(buffs!=null) {
			for(ByteBuffer buff: buffs) {
				if(buff==null) continue;
				if(directByteBuffClass!=null && buff!=null && directByteBuffClass.isInstance(buff)) {
					try {
						Object cleaner = getCleanerMethod.invoke(buff);
						if(cleaner!=null) {
							cleanMethod.invoke(cleaner);					
						}
						return;
					} catch (Throwable t) {
						t.printStackTrace(System.err);
						/* No Op */
					}
				}
				if(Constants.IS_WIN) log.error("Uncleaned MappedByteBuffer on Windows !!!!");				
			}
		}
	}
	
	/** The direct byte buff class */
	private static final Class<?> directByteBuffClass;
	/** The direct byte buff class cleaner accessor method*/
	private static final Method getCleanerMethod;
	/** The clean method in cleaner */
	private static final Method cleanMethod;
	
	static {
		Class<?> clazz = null;
		Class<?> cleanerClazz = null;
		Method m = null;
		Method cm = null;
		try {
			clazz = Class.forName("java.nio.DirectByteBuffer", true, ClassLoader.getSystemClassLoader());
			m = clazz.getDeclaredMethod("cleaner");
			m.setAccessible(true);
			cleanerClazz = Class.forName("sun.misc.Cleaner", true, ClassLoader.getSystemClassLoader());
			cm = cleanerClazz.getDeclaredMethod("clean");
			cm.setAccessible(true);
		} catch (Throwable x) {
			clazz = null;
			m = null;
			cm = null;
			log.error("Failed to initialize DirectByteBuffer Cleaner:" + x + "\n\tNon-Fatal, will continue");
		}
		directByteBuffClass = clazz;
		getCleanerMethod = m;
		cleanMethod = cm;
	}
	
	
	
	public static void main(String[] args) {
		Logger log = LogManager.getLogger("FIFOFileTest");
		log.info("Fifo File Test");
		final Random random = new Random(System.currentTimeMillis());
		
		File dir = new File(System.getProperty("user.home") + File.separator + ".tsdb-metrics");
		if(!dir.exists()) {
			if(!dir.mkdirs()) {
				log.error("Failed to create test dir");
				return;
			}
		}
		for(File x: dir.listFiles()) {
			if(x.length()>4 && MetricPersistence.FILE_NAME_PATTERN.matcher(x.getName()).matches()) {
				OffHeapFIFOFile ff = new OffHeapFIFOFile(x.getParentFile(), x.getName());
//				ff.dump(System.out);
				while(ff.getEntrySize()>0) {
					File[] fs = ff.extract(1);
					System.out.println(fs[0].getName() + ", size: " + fs[0].length());
				}
				
			}
		}
	}
	

}
