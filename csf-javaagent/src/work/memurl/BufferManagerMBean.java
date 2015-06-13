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

import java.net.URL;
import java.util.Set;

/**
 * <p>Title: BufferManagerMBean</p>
 * <p>Description: JMX MBean interface for {@link BufferManager}</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.utils.classload.memurl.BufferManagerMBean</code></p>
 */

public interface BufferManagerMBean {
    /**
     * Returns the number of allocated and registered MemBuffers
     * @return the number of allocated and registered MemBuffers
     */
    public int getMemBufferCount();
    
    /**
     * Returns a set of the stringified URLs in the MemBuffer cache
     * @return a set of the stringified URLs in the MemBuffer cache
     */
    public Set<String> printKeys();
    
    /**
     * Returns the number of MemBuffers that have been expired due to their associated mem URL being garbage collected
     * @return the number of MemBuffers that have been expired
     */
    public long getMemBufferExpirations();
    
	/**
	 * Returns the URL created for the passed URL suffix
	 * @param bufferName The URL suffix for the URL
	 * @return the MemBuffer URL
	 */
	public URL getMemBufferURL(String bufferName);    
    
//    /**
//     * Returns the total number of MemBuffer instances that have been destroyed
//     * @return the total number of MemBuffer instances that have been destroyed
//     */
//    public long getMemBufferDestroys();
    
//    /**
//     * Returns the highwater number of MemBuffer instances
//     * @return the highwater number of MemBuffer instances
//     */
//    public long getMemBufferInstanceHighwater();


}
