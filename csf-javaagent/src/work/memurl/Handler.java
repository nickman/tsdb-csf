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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>Title: Handler</p>
 * <p>Description: A {@link URLStreamHandler} implementation for in memory buffers. Copied from <a href="http://tika.apache.org/">Apache Tika</a></p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.utils.classload.memurl.Handler</code></p>
 */

public class Handler extends URLStreamHandler {
    /** Serial number factory for creating unique arbitrary URLs */
    private static final AtomicInteger counter = new AtomicInteger();
    /** The prefix of the mem url */
    public static final String URLFORMAT = "mem://localhost/";

    
    /**
     * {@inheritDoc}
     * @see java.net.URLStreamHandler#setURL(java.net.URL, java.lang.String, java.lang.String, int, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    protected void setURL(URL u, String protocol, String host, int port,
            String authority, String userInfo, String path,
            String query, String ref) {    	
    	if(path==null || path.trim().isEmpty()) {
    		path = "" + counter.incrementAndGet();
    	}
    	if(host==null || host.trim().isEmpty()) {
    		host = "localhost";
    	}
    	super.setURL(u, protocol, host, port, authority, userInfo, path, query, ref);
    	u.hashCode();
    	BufferManager.getInstance().registerMemBuffer(u);
    }
    

    
    /** Tracks the thread when evaluating {@link #equals(URL, URL)} to prevent an endless loop and stackoverflow */
    private static final ThreadLocal<Thread> equalsThread = new ThreadLocal<Thread>();
    
    /**
     * {@inheritDoc}
     * @see java.net.URLStreamHandler#equals(java.net.URL, java.net.URL)
     */
    @Override
    protected boolean equals(URL u1, URL u2) {
    	
    	if(u1==null || u2==null) return false;
    	return u1.toString().equals(u2.toString());
////    	String p1 = u1.getProtocol(), p2 = u2.getProtocol();
////    	if(!"mem".equalsIgnoreCase(p1) || !"mem".equalsIgnoreCase(p2)) return false;
////    	try {    		
////    		if(equalsThread.get()!=null) {
////    			// this means that we're in a recursive call, so delegate to super.
////    			return super.equals(u1, u2);
////    		}
////			equalsThread.set(Thread.currentThread());
////    		MemBuffer m1 = BufferManager.getInstance().getMemBuffer(u1), m2 = BufferManager.getInstance().getMemBuffer(u2);
////    		return m1.equals(m2);
//    	} catch (Exception ex) {
//    		return false;
//    	} finally {
//    		equalsThread.remove();
//    	}
    }
    
    /**
     * Creates a new Handler
     * @param fileName The optional file name to be appended to the URL
     * @return the new Handler
     */
    public static URL createURL(String fileName) {
    	return createURL(fileName, -1);
    }
    
    /**
     * Creates a new Handler
     * @param initialCapacity The initial capacity of the buffer
     * @return the new Handler
     */
    public static URL createURL(int initialCapacity) {
    	return createURL(null, initialCapacity);
    }
    
    /**
     * Creates a new Handler
     * @return the new Handler
     */
    public static URL createURL() {
    	return createURL(null, -1);
    }
    
    
    /**
     * Creates a new Handler
     * @param fileName The optional file name to be appended to the URL
     * @param initialCapacity The initial capacity of the buffer
     * @return the new Handler
     */
    public static URL createURL(String fileName, int initialCapacity) {
        try {
        	String _fileName = "/" + ((fileName==null || fileName.trim().isEmpty()) ? "" + counter.incrementAndGet() : fileName.trim()); 
            StringBuilder b = new StringBuilder(URLFORMAT).append(_fileName);            
            return new URL(b.toString());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     * @see java.net.URLStreamHandler#openConnection(java.net.URL)
     */
    @Override
    protected URLConnection openConnection(URL u) throws IOException {
    	try {
    		return new MemoryURLConnection(u, BufferManager.getInstance().getMemBuffer(u));
    	} catch (Exception ex) {
    		throw new IOException("Unknown URL: " + u);
    	}
    }

}
