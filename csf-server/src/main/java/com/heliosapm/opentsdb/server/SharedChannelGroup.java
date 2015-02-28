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
package com.heliosapm.opentsdb.server;

import java.net.SocketAddress;
import java.util.Collection;
import java.util.Iterator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;

/**
 * <p>Title: SharedChannelGroup</p>
 * <p>Description: A netty channel group managed as a singleton so it can be accessed anywhere.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.server.SharedChannelGroup</code></p>
 */
public class SharedChannelGroup implements ChannelGroup, ChannelFutureListener {
	/** The singleton instance */
	private static volatile SharedChannelGroup instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	/** Instance logger */
	protected final Logger log = LogManager.getLogger(getClass());

	
	/** The core channel group */
	private ChannelGroup channelGroup = new DefaultChannelGroup("Netty Ajax Server Channel Group");
	
	/**
	 * Retrieves the SharedChannelGroup singleton instance
	 * @return the SharedChannelGroup singleton instance
	 */
	public static SharedChannelGroup getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new SharedChannelGroup();
				}
			}
		}
		return instance;
	}
	
	private SharedChannelGroup() {
		
	}
	
	/**
	 * ChannelFutureListener impl that removes Channels from the group when they close.
	 * {@inheritDoc}
	 * @see org.jboss.netty.channel.ChannelFutureListener#operationComplete(org.jboss.netty.channel.ChannelFuture)
	 */
	@Override
	public void operationComplete(ChannelFuture f) throws Exception {
		if(f.isDone()) {
			remove(f.getChannel());
		}
	}
	
	
	/**
	 * Adds a channel to this group 
	 * @param channel The channel to add
	 * @return true if the channel was not already in the group
	 * @see java.util.Set#add(java.lang.Object)
	 */
	@Override
	public boolean add(Channel channel) {				
		boolean isNew =  channelGroup.add(channel);
		if(isNew) {
			log.info("Adding Channel From [" + channel.getPipeline().getLast().getClass().getSimpleName() + "/\t" + channel.getId() + "]");
		} else {
			log.info("Channel From [" + channel.getPipeline().getLast().getClass().getSimpleName() + "/\t" + channel.getId() + "] already registered");
		}
		return isNew;
	}
	
	/**
	 * Removes a channel from the ChannelGroup
	 * @param channnel The channel to remove
	 * @return true if the channel was present and was removed
	 * @see java.util.Set#remove(java.lang.Object)
	 */
	public boolean remove(Channel channel) {
		return channelGroup.remove(channel);
	}	
	

	/**
	 * {@inheritDoc}
	 * @see java.util.Set#remove(java.lang.Object)
	 */
	@Override
	public boolean remove(Object obj) {
		if(obj!=null && obj instanceof Channel) {
			return remove((Channel)obj);
		} 
		return false;
	}
	/**
	 * @return
	 * @see java.util.Set#size()
	 */
	@Override
	public int size() {
		return channelGroup.size();
	}

	/**
	 * @return
	 * @see java.util.Set#isEmpty()
	 */
	@Override
	public boolean isEmpty() {
		return channelGroup.isEmpty();
	}

	/**
	 * @param o
	 * @return
	 * @see java.util.Set#contains(java.lang.Object)
	 */
	@Override
	public boolean contains(Object o) {
		return channelGroup.contains(o);
	}

	/**
	 * @param o
	 * @return
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(ChannelGroup o) {
		return channelGroup.compareTo(o);
	}

	/**
	 * @return
	 * @see org.jboss.netty.channel.group.ChannelGroup#getName()
	 */
	@Override
	public String getName() {
		return channelGroup.getName();
	}

	/**
	 * @param id
	 * @return
	 * @see org.jboss.netty.channel.group.ChannelGroup#find(java.lang.Integer)
	 */
	@Override
	public Channel find(Integer id) {
		return channelGroup.find(id);
	}

	/**
	 * @return
	 * @see java.util.Set#iterator()
	 */
	@Override
	public Iterator<Channel> iterator() {
		return channelGroup.iterator();
	}

	/**
	 * @param interestOps
	 * @return
	 * @see org.jboss.netty.channel.group.ChannelGroup#setInterestOps(int)
	 */
	@Override
	public ChannelGroupFuture setInterestOps(int interestOps) {
		return channelGroup.setInterestOps(interestOps);
	}

	/**
	 * @return
	 * @see java.util.Set#toArray()
	 */
	@Override
	public Object[] toArray() {
		return channelGroup.toArray();
	}

	/**
	 * @param readable
	 * @return
	 * @see org.jboss.netty.channel.group.ChannelGroup#setReadable(boolean)
	 */
	@Override
	public ChannelGroupFuture setReadable(boolean readable) {
		return channelGroup.setReadable(readable);
	}

	/**
	 * @param message
	 * @return
	 * @see org.jboss.netty.channel.group.ChannelGroup#write(java.lang.Object)
	 */
	@Override
	public ChannelGroupFuture write(Object message) {
		return channelGroup.write(message);
	}

	/**
	 * @param a
	 * @return
	 * @see java.util.Set#toArray(T[])
	 */
	@Override
	public <T> T[] toArray(T[] a) {
		return channelGroup.toArray(a);
	}

	/**
	 * @param message
	 * @param remoteAddress
	 * @return
	 * @see org.jboss.netty.channel.group.ChannelGroup#write(java.lang.Object, java.net.SocketAddress)
	 */
	@Override
	public ChannelGroupFuture write(Object message, SocketAddress remoteAddress) {
		return channelGroup.write(message, remoteAddress);
	}

	/**
	 * @return
	 * @see org.jboss.netty.channel.group.ChannelGroup#disconnect()
	 */
	@Override
	public ChannelGroupFuture disconnect() {
		return channelGroup.disconnect();
	}

	/**
	 * @return
	 * @see org.jboss.netty.channel.group.ChannelGroup#unbind()
	 */
	@Override
	public ChannelGroupFuture unbind() {
		return channelGroup.unbind();
	}

	/**
	 * @return
	 * @see org.jboss.netty.channel.group.ChannelGroup#close()
	 */
	@Override
	public ChannelGroupFuture close() {
		return channelGroup.close();
	}




	/**
	 * @param c
	 * @return
	 * @see java.util.Set#containsAll(java.util.Collection)
	 */
	@Override
	public boolean containsAll(Collection<?> c) {
		return channelGroup.containsAll(c);
	}

	/**
	 * @param c
	 * @return
	 * @see java.util.Set#addAll(java.util.Collection)
	 */
	@Override
	public boolean addAll(Collection<? extends Channel> c) {
		return channelGroup.addAll(c);
	}

	/**
	 * @param c
	 * @return
	 * @see java.util.Set#retainAll(java.util.Collection)
	 */
	@Override
	public boolean retainAll(Collection<?> c) {
		return channelGroup.retainAll(c);
	}

	/**
	 * @param c
	 * @return
	 * @see java.util.Set#removeAll(java.util.Collection)
	 */
	@Override
	public boolean removeAll(Collection<?> c) {
		return channelGroup.removeAll(c);
	}

	/**
	 * 
	 * @see java.util.Set#clear()
	 */
	@Override
	public void clear() {
		channelGroup.clear();
	}

	/**
	 * @param o
	 * @return
	 * @see java.util.Set#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object o) {
		return channelGroup.equals(o);
	}

	/**
	 * @return
	 * @see java.util.Set#hashCode()
	 */
	@Override
	public int hashCode() {
		return channelGroup.hashCode();
	}
}
