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
package com.heliosapm.opentsdb.client.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.netty.buffer.AbstractChannelBuffer;
import org.jboss.netty.buffer.ByteBufferBackedChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.ChannelBuffers;

import com.heliosapm.opentsdb.client.opentsdb.OffHeapFIFOFile;

/**
 * <p>Title: DynamicByteBufferBackedChannelBuffer</p>
 * <p>Description: This is an attempt to implement a ChannelBuffer with the characteristics of off-heap using ByteBuffers, 
 * dynamic so the space can be expanded as needed but with cleaner buffer implications so backing buffers can be procedurally cleaned when
 * we know we're done with them.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.util.DynamicByteBufferBackedChannelBuffer</code></p>
 */

public class DynamicByteBufferBackedChannelBuffer extends AbstractChannelBuffer {
    /** The current buffer */
    private ByteBuffer buffer;
    /** The buffer's byte order */
    private final ByteOrder order;
    /** The buffer's current capacity */
    private final AtomicInteger capacity;
    /** The percentage of current capacity to extend by when extending */
    private final float extend;
    /** The preserved initial size the buffer will reset to */
    private final int initialSize;
    /** A counter of allocated instances */
    private static final AtomicLong allocatedInstances = new AtomicLong();
    /** A counter of allocated memory */
    private static final AtomicLong allocatedMemory = new AtomicLong();
    
    
    /**
     * Returns the total allocated memory in bytes
     * @return the total allocated memory in bytes
     */
    public static long getAllocatedMemory() {
    	return allocatedMemory.get();
    }
    
    /**
     * Returns the total number of allocated instances
     * @return the total number of allocated instances
     */
    public static long getAllocatedInstances() {
    	return allocatedInstances.get();
    }
    
    
    /** The default extend percentage */
    public static final float DEFAULT_EXTEND = .5f;
    /** The default initial capacity */
    public static final int DEFAULT_INITIAL = 1024;
    

    /**
     * Creates a new DynamicByteBufferBackedChannelBuffer
     * @param order The byte order
     * @param initialSize The initial size
     * @param extendSize The extend percentage
     */
    public DynamicByteBufferBackedChannelBuffer(final ByteOrder order, final int initialSize, final float extendSize) {
    	this.initialSize = initialSize;
    	this.capacity = new AtomicInteger(0);
    	this.capacity.set(initialSize);
    	this.extend = extendSize;
    	buffer = ByteBuffer.allocateDirect(initialSize);
    	buffer.order(order);
    	this.order = order;
    	allocatedInstances.incrementAndGet();
    	allocatedMemory.addAndGet(this.capacity.get());
    }
    
    
    
    /**
     * THIS IS THE KEY
     * {@inheritDoc}
     * @see org.jboss.netty.buffer.AbstractChannelBuffer#ensureWritableBytes(int)
     */
    
    @Override
    public void ensureWritableBytes(int minWritableBytes) {        
    	synchronized(capacity) {
    		if (minWritableBytes <= writableBytes()) {
    			return;
    		}
            final int currentCapacity = capacity.get();
            final int minNewCapacity = writerIndex() + minWritableBytes;
            int increment = (int) (capacity.get() * extend);        
            while (increment + currentCapacity < minNewCapacity) {
            	increment += (int) (increment * extend);
                // https://github.com/netty/netty/issues/258
                if (increment == 0) {
                    throw new IllegalStateException("Maximum size of 2gb exceeded");
                }
            }
            int newCapacity = increment + currentCapacity;
            if(capacity.compareAndSet(currentCapacity, newCapacity)) {
    	        ByteBuffer newBuffer = ByteBuffer.allocateDirect(newCapacity);
    	        newBuffer.put((ByteBuffer)buffer.duplicate().rewind());
    	        ByteBuffer oldBuffer = buffer; 
    	        buffer = newBuffer;
    	        clean(oldBuffer);
    	        allocatedMemory.addAndGet(increment);
            }    		
    	}        
    }
    
    private static void clean(final ByteBuffer buff) {    	
    	OffHeapFIFOFile.clean(buff);
    }
    
    /**
     * Deallocates the underlying buffer.
     * <b>Caution!</b>. Cleaned buffers are toxic.
     */
    public void clean() {
		final int cap = capacity.getAndSet(-1);
		if(cap!=-1) {
			clean(buffer);
			allocatedInstances.decrementAndGet();
			allocatedMemory.addAndGet((-1 * cap));
		}
    }
    
    /**
     * Resets the buffer back to it's initial size, discarding any held data.
     */
    public void reset() {
    	final int cap = capacity.getAndSet(initialSize)-initialSize;
    	ByteBuffer oldBuffer = buffer; 
    	buffer = ByteBuffer.allocateDirect(initialSize);
    	clean(oldBuffer);
    	allocatedMemory.addAndGet((-1 * cap));
    }
    
    /**
     * {@inheritDoc}
     * @see java.lang.Object#finalize()
     */
    @Override
    protected void finalize() throws Throwable {
    	clean();
    	super.finalize();
    }
    
    
//    public void ensureWritableBytes(int writableBytes) {
//        if (writableBytes > writableBytes()) {
//            throw new IndexOutOfBoundsException("Writable bytes exceeded: Got "
//                    + writableBytes + ", maximum is " + writableBytes());
//        }
//    }
    
    
    

  private DynamicByteBufferBackedChannelBuffer(final DynamicByteBufferBackedChannelBuffer buffer) {
      this.buffer = buffer.buffer;
      this.extend = buffer.extend;      
      order = buffer.order;
      this.initialSize = buffer.initialSize;
      capacity = buffer.capacity;
      setIndex(buffer.readerIndex(), buffer.writerIndex());
  }
    
    @Override
	public ChannelBuffer duplicate() {
        return new DynamicByteBufferBackedChannelBuffer(this);
    }
    
    
    
    @Override
    public void writeByte(int value) {
        ensureWritableBytes(1);
        super.writeByte(value);
    }

    @Override
    public void writeShort(int value) {
        ensureWritableBytes(2);
        super.writeShort(value);
    }

    @Override
    public void writeMedium(int value) {
        ensureWritableBytes(3);
        super.writeMedium(value);
    }

    @Override
    public void writeInt(int value) {
        ensureWritableBytes(4);
        super.writeInt(value);
    }

    @Override
    public void writeLong(long value) {
        ensureWritableBytes(8);
        super.writeLong(value);
    }

    @Override
    public void writeBytes(byte[] src, int srcIndex, int length) {
        ensureWritableBytes(length);
        super.writeBytes(src, srcIndex, length);
    }

    @Override
    public void writeBytes(ChannelBuffer src, int srcIndex, int length) {
        ensureWritableBytes(length);
        super.writeBytes(src, srcIndex, length);
    }

    @Override
    public void writeBytes(ByteBuffer src) {
        ensureWritableBytes(src.remaining());
        super.writeBytes(src);
    }

    @Override
    public int writeBytes(InputStream in, int length) throws IOException {
        ensureWritableBytes(length);
        return super.writeBytes(in, length);
    }

    @Override
    public int writeBytes(ScatteringByteChannel in, int length)
            throws IOException {
        ensureWritableBytes(length);
        return super.writeBytes(in, length);
    }

    @Override
    public void writeZero(int length) {
        ensureWritableBytes(length);
        super.writeZero(length);
    }
    
    

    /**
     * {@inheritDoc}
     * @see org.jboss.netty.buffer.ChannelBuffer#factory()
     */
    @Override
	public ChannelBufferFactory factory() {
    	return DynamicByteBufferBackedChannelBufferFactory.getInstance(order());
    }

    @Override
	public boolean isDirect() {
        return buffer.isDirect();
    }

    @Override
	public ByteOrder order() {
        return order;
    }

    @Override
	public int capacity() {
        return capacity.get();
    }

    @Override
	public boolean hasArray() {
        return buffer.hasArray();
    }

    @Override
	public byte[] array() {
        return buffer.array();
    }

    @Override
	public int arrayOffset() {
        return buffer.arrayOffset();
    }

    @Override
	public byte getByte(int index) {
        return buffer.get(index);
    }

    @Override
	public short getShort(int index) {
        return buffer.getShort(index);
    }

    @Override
	public int getUnsignedMedium(int index) {
        return  (getByte(index)     & 0xff) << 16 |
                (getByte(index + 1) & 0xff) <<  8 |
                getByte(index + 2) & 0xff;
    }

    @Override
	public int getInt(int index) {
        return buffer.getInt(index);
    }

    @Override
	public long getLong(int index) {
        return buffer.getLong(index);
    }

    @Override
	public void getBytes(int index, ChannelBuffer dst, int dstIndex, int length) {
        if (dst instanceof DynamicByteBufferBackedChannelBuffer) {
        	DynamicByteBufferBackedChannelBuffer bbdst = (DynamicByteBufferBackedChannelBuffer) dst;
            ByteBuffer data = bbdst.buffer.duplicate();

            data.limit(dstIndex + length).position(dstIndex);
            getBytes(index, data);
        } else if (buffer.hasArray()) {
            dst.setBytes(dstIndex, buffer.array(), index + buffer.arrayOffset(), length);
        } else {
            dst.setBytes(dstIndex, this, index, length);
        }
    }

    @Override
	public void getBytes(int index, byte[] dst, int dstIndex, int length) {
        ByteBuffer data = buffer.duplicate();
        try {
            data.limit(index + length).position(index);
        } catch (IllegalArgumentException e) {
            throw new IndexOutOfBoundsException("Too many bytes to read - Need "
                    + (index + length) + ", maximum is " + data.limit());
        }
        data.get(dst, dstIndex, length);
    }

    @Override
	public void getBytes(int index, ByteBuffer dst) {
        ByteBuffer data = buffer.duplicate();
        int bytesToCopy = Math.min(capacity() - index, dst.remaining());
        try {
            data.limit(index + bytesToCopy).position(index);
        } catch (IllegalArgumentException e) {
            throw new IndexOutOfBoundsException("Too many bytes to read - Need "
                    + (index + bytesToCopy) + ", maximum is " + data.limit());
        }
        dst.put(data);
    }

    @Override
	public void setByte(int index, int value) {
        buffer.put(index, (byte) value);
    }

    @Override
	public void setShort(int index, int value) {
        buffer.putShort(index, (short) value);
    }

    @Override
	public void setMedium(int index, int   value) {
        setByte(index,     (byte) (value >>> 16));
        setByte(index + 1, (byte) (value >>>  8));
        setByte(index + 2, (byte) value);
    }

    @Override
	public void setInt(int index, int   value) {
        buffer.putInt(index, value);
    }

    @Override
	public void setLong(int index, long  value) {
        buffer.putLong(index, value);
    }

    @Override
	public void setBytes(int index, ChannelBuffer src, int srcIndex, int length) {
        if (src instanceof DynamicByteBufferBackedChannelBuffer) {
        	DynamicByteBufferBackedChannelBuffer bbsrc = (DynamicByteBufferBackedChannelBuffer) src;
            ByteBuffer data = bbsrc.buffer.duplicate();

            data.limit(srcIndex + length).position(srcIndex);
            setBytes(index, data);
        } else if (buffer.hasArray()) {
            src.getBytes(srcIndex, buffer.array(), index + buffer.arrayOffset(), length);
        } else {
            src.getBytes(srcIndex, this, index, length);
        }
    }

    @Override
	public void setBytes(int index, byte[] src, int srcIndex, int length) {
        ByteBuffer data = buffer.duplicate();
        data.limit(index + length).position(index);
        data.put(src, srcIndex, length);
    }

    @Override
	public void setBytes(int index, ByteBuffer src) {
        ByteBuffer data = buffer.duplicate();
        data.limit(index + src.remaining()).position(index);
        data.put(src);
    }

    @Override
	public void getBytes(int index, OutputStream out, int length) throws IOException {
        if (length == 0) {
            return;
        }

        if (buffer.hasArray()) {
            out.write(
                    buffer.array(),
                    index + buffer.arrayOffset(),
                    length);
        } else {
            byte[] tmp = new byte[length];
            ((ByteBuffer) buffer.duplicate().position(index)).get(tmp);
            out.write(tmp);
        }
    }

    @Override
	public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        if (length == 0) {
            return 0;
        }

        return out.write((ByteBuffer) buffer.duplicate().position(index).limit(index + length));
    }

    @Override
	public int setBytes(int index, InputStream in, int length)
            throws IOException {

        int readBytes = 0;

        if (buffer.hasArray()) {
            index += buffer.arrayOffset();
            do {
                int localReadBytes = in.read(buffer.array(), index, length);
                if (localReadBytes < 0) {
                    if (readBytes == 0) {
                        return -1;
                    } else {
                        break;
                    }
                }
                readBytes += localReadBytes;
                index += localReadBytes;
                length -= localReadBytes;
            } while (length > 0);
        } else {
            byte[] tmp = new byte[length];
            int i = 0;
            do {
                int localReadBytes = in.read(tmp, i, tmp.length - i);
                if (localReadBytes < 0) {
                    if (readBytes == 0) {
                        return -1;
                    } else {
                        break;
                    }
                }
                readBytes += localReadBytes;
                i += readBytes;
            } while (i < tmp.length);
            ((ByteBuffer) buffer.duplicate().position(index)).put(tmp);
        }

        return readBytes;
    }

    @Override
	public int setBytes(int index, ScatteringByteChannel in, int length)
            throws IOException {

        ByteBuffer slice = (ByteBuffer) buffer.duplicate().limit(index + length).position(index);
        int readBytes = 0;

        while (readBytes < length) {
            int localReadBytes;
            try {
                localReadBytes = in.read(slice);
            } catch (ClosedChannelException e) {
                localReadBytes = -1;
            }
            if (localReadBytes < 0) {
                if (readBytes == 0) {
                    return -1;
                } else {
                    return readBytes;
                }
            }
            if (localReadBytes == 0) {
                break;
            }
            readBytes += localReadBytes;
        }

        return readBytes;
    }

    @Override
	public ByteBuffer toByteBuffer(int index, int length) {
        if (index == 0 && length == capacity()) {
            return buffer.duplicate().order(order());
        } else {
            return ((ByteBuffer) buffer.duplicate().position(
                    index).limit(index + length)).slice().order(order());
        }
    }

    @Override
	public ChannelBuffer slice(int index, int length) {
        if (index == 0 && length == capacity()) {
            ChannelBuffer slice = duplicate();
            slice.setIndex(0, length);
            return slice;
        } else {
            if (index >= 0 && length == 0) {
                return ChannelBuffers.EMPTY_BUFFER;
            }
            return new ByteBufferBackedChannelBuffer(
                    ((ByteBuffer) buffer.duplicate().position(
                            index).limit(index + length)).order(order()));
        }
    }


    @Override
	public ChannelBuffer copy(int index, int length) {
        ByteBuffer src;
        try {
            src = (ByteBuffer) buffer.duplicate().position(index).limit(index + length);
        } catch (IllegalArgumentException e) {
            throw new IndexOutOfBoundsException("Too many bytes to read - Need "
                    + (index + length));
        }

        ByteBuffer dst = buffer.isDirect() ? ByteBuffer.allocateDirect(length) : ByteBuffer.allocate(length);
        dst.put(src);
        dst.order(order());
        dst.clear();
        return new ByteBufferBackedChannelBuffer(dst);
    }
	

}
