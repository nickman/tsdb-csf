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
import java.net.URL;
import java.net.URLConnection;

/**
 * <p>Title: MemoryURLConnection</p>
 * <p>Description: A {@link URLConnection} implementation for in memory buffers. Copied from <a href="http://tika.apache.org/">Apache Tika</a></p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.utils.classload.memurl.MemoryURLConnection</code></p>
 */

public class MemoryURLConnection extends URLConnection {

	/** The content length header */
	public static final String CONTENT_LENGTH = "content-length";
	/** The content type header */
	public static final String CONTENT_TYPE = "content-type";
	/** The default content type value */
	public static final String TEXT_PLAIN = "text/plain";
	/** The last modified timestamp header */
	public static final String LAST_MODIFIED = "last-modified";
	
	/** The system property key for the property that specifies URL stream handler factory names */
	public static final String PKGS = "java.protocol.handler.pkgs";
	/** The package specification */
	public static final String MEM_URL_PKG = "com.heliosapm.shorthand.util.net";
    /** The buffered data */
    private final BufferManager.MemBuffer data;
    
    /**
     * Registers the Memory URL stream factory
     */
    public static void register() {
    	// -Djava.protocol.handler.pkgs=com.theice.clearing.eventcaster.io
//    	synchronized(System.getProperties()) {
//	    	String value = System.getProperty(PKGS);
//	    	if(value!=null) {
//	    		if(!value.contains(MEM_URL_PKG)) {
//	    			value = "|" + MEM_URL_PKG;
//	    		}
//	    	} else {
//	    		value = "MEM_URL_PKG";
//	    	}
//	    	System.setProperty(PKGS, value);
//    	}
    	URL.setURLStreamHandlerFactory(new MemoryURLStreamHandlerFactory());    	
    }

    /**
     * Creates a new MemoryURLConnection
     * @param url The URL that represents this connection
     * @param data The buffered data
     */
    public MemoryURLConnection(URL url, BufferManager.MemBuffer data) {
        super(url);
        this.data = data;
    }

    /**
     * {@inheritDoc}
     * @see java.net.URLConnection#connect()
     */
    @Override
    public void connect() {
    	/* No Op */
    }

    /**
     * {@inheritDoc}
     * @see java.net.URLConnection#getInputStream()
     */
    @Override
    public InputStream getInputStream() {
        return data.getInputStream();
    }
    
    /**
     * {@inheritDoc}
     * @see java.net.URLConnection#getLastModified()
     */
    @Override
    public long getLastModified() {
    	return data.getLastModifiedTime();
    }
    
    /**
     * {@inheritDoc}
     * @see java.net.URLConnection#getOutputStream()
     */
    @Override
    public OutputStream getOutputStream() throws IOException {    
    	return data.getOutputStream();
    }
    
    /**
     * {@inheritDoc}
     * @see java.net.URLConnection#getHeaderFieldDate(java.lang.String, long)
     */
    @Override
    public long getHeaderFieldDate(String name, long Default) {
    	if(LAST_MODIFIED.equalsIgnoreCase(name)) {
    		return data.getLastModifiedTime();
    	} 
    	return super.getHeaderFieldDate(name, Default);
    }


}
