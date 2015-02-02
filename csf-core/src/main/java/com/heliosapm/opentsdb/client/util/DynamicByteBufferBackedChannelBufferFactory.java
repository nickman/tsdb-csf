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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.jboss.netty.buffer.ChannelBufferFactory;

/**
 * <p>Title: DynamicByteBufferBackedChannelBufferFactory</p>
 * <p>Description: A factory for {@link DynamicByteBufferBackedChannelBuffer}s</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.util.DynamicByteBufferBackedChannelBufferFactory</code></p>
 */

public class DynamicByteBufferBackedChannelBufferFactory implements ChannelBufferFactory {

	/** The default extend percentage */
    public static final float DEFAULT_EXTEND = .5f;
    /** The default initial capacity */
    public static final int DEFAULT_INITIAL = 1024;
    /** The default byte order */
    public static final ByteOrder DEFAULT_ORDER = ByteOrder.nativeOrder();
    
    /** A default native byte order factory */
    public static final DynamicByteBufferBackedChannelBufferFactory DEFAULT_NATIVE = new DynamicByteBufferBackedChannelBufferFactory(DEFAULT_INITIAL, DEFAULT_EXTEND, DEFAULT_ORDER);
    /** A default non-native byte order factory */
    public static final DynamicByteBufferBackedChannelBufferFactory DEFAULT_NOT_NATIVE = new DynamicByteBufferBackedChannelBufferFactory(DEFAULT_INITIAL, DEFAULT_EXTEND, DEFAULT_ORDER.equals(ByteOrder.BIG_ENDIAN) ?  ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
    
    /**
     * Returns the default factory instance for the passed order
     * @param order The order to get a factory for. Returns native if null.
     * @return the requested order typed factory
     */
    public static DynamicByteBufferBackedChannelBufferFactory getInstance(final ByteOrder order) {
    	if(order==null) return DEFAULT_NATIVE;
    	return order.equals(DEFAULT_ORDER) ? DEFAULT_NATIVE : DEFAULT_NOT_NATIVE;
    }
    
    
    final int initialCapacity;
    final float extend;
    final ByteOrder byteOrder;
    
	/**
	 * Creates a new DynamicByteBufferBackedChannelBufferFactory
	 * @param initialCapacity The initial capacity of the created buffers
	 * @param extend The extend percentage of the created buffers
	 * @param byteOrder The byte order of the created buffers
	 */
	public DynamicByteBufferBackedChannelBufferFactory(final int initialCapacity, final float extend, final ByteOrder byteOrder) {		
		this.initialCapacity = initialCapacity;
		this.extend = extend;
		this.byteOrder = byteOrder;
	}
	
	/**
	 * Creates a new DynamicByteBufferBackedChannelBufferFactory with the native byte order
	 * @param initialCapacity The initial capacity of the created buffers
	 * @param extend The extend percentage of the created buffers
	 */
	public DynamicByteBufferBackedChannelBufferFactory(final int initialCapacity, final float extend) {		
		this(initialCapacity, extend, DEFAULT_ORDER);
	}
	
	/**
	 * Creates a new DynamicByteBufferBackedChannelBufferFactory with the native byte order and default extend
	 * @param initialCapacity The initial capacity of the created buffers
	 */
	public DynamicByteBufferBackedChannelBufferFactory(final int initialCapacity) {		
		this(initialCapacity, DEFAULT_EXTEND, DEFAULT_ORDER);
	}
	
	/**
	 * Creates a new DynamicByteBufferBackedChannelBufferFactory with the native byte order, default extend
	 * and default initial capacity.
	 */
	public DynamicByteBufferBackedChannelBufferFactory() {		
		this(DEFAULT_INITIAL, DEFAULT_EXTEND, DEFAULT_ORDER);
	}
	
	/**
	 * Creates a new DynamicByteBufferBackedChannelBuffer with the factory's configured defaults
	 * @return a new DynamicByteBufferBackedChannelBuffer
	 */
	public DynamicByteBufferBackedChannelBuffer getBuffer() {
		return getBuffer(byteOrder, initialCapacity, extend);
	}

	/**
	 * {@inheritDoc}
	 * @see org.jboss.netty.buffer.ChannelBufferFactory#getBuffer(int)
	 */
	@Override
	public DynamicByteBufferBackedChannelBuffer getBuffer(final int capacity) {
		return getBuffer(byteOrder, capacity, extend);
	}
	
	/**
	 * Creates a new DynamicByteBufferBackedChannelBuffer
	 * @param capacity The initial capacity
	 * @param extend The extend percentage
	 * @return a new DynamicByteBufferBackedChannelBuffer
	 */
	public DynamicByteBufferBackedChannelBuffer getBuffer(final int capacity, final float extend) {
		return getBuffer(byteOrder, capacity, extend);
	}
	
	/**
	 * Creates a new DynamicByteBufferBackedChannelBuffer
	 * @param order The byte order of the buffer to create
	 * @param capacity The initial capacity
	 * @param extend The extend percentage
	 * @return a new DynamicByteBufferBackedChannelBuffer
	 */
	public DynamicByteBufferBackedChannelBuffer getBuffer(final ByteOrder order, final int capacity, final float extend) {
		return new DynamicByteBufferBackedChannelBuffer(order, capacity, extend);
	}

	/**
	 * {@inheritDoc}
	 * @see org.jboss.netty.buffer.ChannelBufferFactory#getBuffer(java.nio.ByteOrder, int)
	 */
	@Override
	public DynamicByteBufferBackedChannelBuffer getBuffer(final ByteOrder endianness, final int capacity) {
		return new DynamicByteBufferBackedChannelBuffer(endianness, capacity, extend);
	}

	/**
	 * {@inheritDoc}
	 * @see org.jboss.netty.buffer.ChannelBufferFactory#getBuffer(byte[], int, int)
	 */
	@Override
	public DynamicByteBufferBackedChannelBuffer getBuffer(final byte[] array, final int offset, final int length) {
		return getBuffer(byteOrder, array, offset, length);
	}

	/**
	 * {@inheritDoc}
	 * @see org.jboss.netty.buffer.ChannelBufferFactory#getBuffer(java.nio.ByteOrder, byte[], int, int)
	 */
	@Override
	public DynamicByteBufferBackedChannelBuffer getBuffer(final ByteOrder endianness, final byte[] array, final int offset, final int length) {
		final DynamicByteBufferBackedChannelBuffer buff = getBuffer(endianness, length);
		buff.writeBytes(array, offset, length);
		return buff;		
	}

	/**
	 * {@inheritDoc}
	 * @see org.jboss.netty.buffer.ChannelBufferFactory#getBuffer(java.nio.ByteBuffer)
	 */
	@Override
	public DynamicByteBufferBackedChannelBuffer getBuffer(final ByteBuffer nioBuffer) {
		return getBuffer(nioBuffer, extend);
	}
	
	/**
	 * Creates a new DynamicByteBufferBackedChannelBuffer containing the content of the passed ByteBuffer
	 * @param nioBuffer The buffer to copy from
	 * @param extend The extend percentage
	 * @return the new DynamicByteBufferBackedChannelBuffer
	 */
	public DynamicByteBufferBackedChannelBuffer getBuffer(final ByteBuffer nioBuffer, final float extend) {
		final DynamicByteBufferBackedChannelBuffer buff = getBuffer(nioBuffer.order(), nioBuffer.limit(), extend);
		buff.writeBytes(nioBuffer);
		return buff;
	}
	

	/**
	 * {@inheritDoc}
	 * @see org.jboss.netty.buffer.ChannelBufferFactory#getDefaultOrder()
	 */
	@Override
	public ByteOrder getDefaultOrder() {
		return DEFAULT_ORDER;
	}

}
