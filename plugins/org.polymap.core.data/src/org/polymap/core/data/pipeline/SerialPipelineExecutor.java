/* 
 * polymap.org
 * Copyright 2009, Polymap GmbH, and individual contributors as indicated
 * by the @authors tag.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 *
 * $Id$
 */

package org.polymap.core.data.pipeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.refractions.udig.catalog.IService;

import org.polymap.core.project.ILayer;
import org.polymap.core.project.IMap;

/**
 * Executes the processors in serial order inside the calling thread. 
 *
 * @author <a href="http://www.polymap.de">Falko Braeutigam</a>
 *         <li>20.10.2009: created</li>
 * @version POLYMAP3 ($Revision$)
 * @since 3.0
 */
public class SerialPipelineExecutor
        implements PipelineExecutor {

    private static final Log log = LogFactory.getLog( SerialPipelineExecutor.class );

    private Pipeline            pipe;
    
    /** The handler to send the result to, or null if a non-chunked execute() call was used. */
    private ResponseHandler     handler;
    
    private ProcessorRequest    request;
    
    private List<SerialContext> contexts = new ArrayList();
    
    private boolean             isEop;
    
    
    public void execute( Pipeline pipe0, ProcessorRequest request0, ResponseHandler handler0 )
            throws Exception {
        this.pipe = pipe0;
        this.request = request0;
        this.handler = handler0;
        
        // create contexts
        int i = 0;
        for (PipelineProcessor proc : pipe) {
            SerialContext context = new SerialContext( proc, i++ );
            contexts.add( context );
        }
        // first request
        contexts.get( 0 ).requests.add( request );
        
        // processing loop
        isEop = false;
        while (!isEop) {
            boolean matched = false;
            for (Iterator<SerialContext> it=contexts.iterator(); it.hasNext() && !matched; ) {
                SerialContext context = it.next();
                
                // process request
                if (!context.requests.isEmpty()) {
                    ProcessorRequest r = context.requests.remove( 0 );
                    context.proc.processRequest( r, context );
                    matched = true;
                }
                // process response
                else if (!context.responses.isEmpty()) {
                    ProcessorResponse r = context.responses.remove( 0 );
                    context.proc.processResponse( r, context );
                    matched = true;
                }
            }
            if (!matched) {
                throw new RuntimeException( "No context with pending requests/responses found." );                
            }
        }
    }

    
    public void handleResponse( ProcessorResponse r ) {
        // FIXME this try/catch must be done somewhere else
        try {
            if (r != ProcessorResponse.EOP) {
                handler.handle( r );
            }
            else {
                isEop = true;
            }
        }
        catch (Exception e) {
            throw new RuntimeException( e );
        }
    }

    
    /**
     * The processor context used by {@link SerialPipelineExecutor}.
     * 
     * @author <a href="http://www.polymap.de">Falko Braeutigam</a>
     *         <li>20.10.2009: created</li>
     */
    protected class SerialContext
            implements ProcessorContext {

        int                     pipePos;
        
        PipelineProcessor       proc;
        
        /** Pending request for this processor. */
        List<ProcessorRequest>  requests = new LinkedList();

        /** Pending request for this processor. */
        List<ProcessorResponse> responses = new LinkedList();
        
        /** The processor specific data. */
        Map                     procData = new HashMap();
        
        boolean                 contextEop = false;
        
        
        public SerialContext( PipelineProcessor proc, int pipePos ) {
            this.pipePos = pipePos;
            this.proc = proc;
        }

        public Object put( String key, Object data ) {
            return procData.put( key, data );
        }

        public Object get( String key ) {
            return procData.get( key );
        }

        public IMap getMap() {
            return pipe.getMap();
        }

        public Set<ILayer> getLayers() {
            return pipe.getLayers();
        }
        
        public IService getService() {
            return pipe.getService();
        }

        public void sendRequest( ProcessorRequest r ) {
            contexts.get( pipePos+1 ).requests.add( r );
        }

        public void sendResponse( ProcessorResponse r ) {
            if (pipePos > 0) {
                if (r != ProcessorResponse.EOP) {
                    contexts.get( pipePos-1 ).responses.add( r );
                }
                else {
                    // send EOP 
                    contexts.get( pipePos-1 ).responses.add( r );
                    // close context!?
                }
            }
            else {
                handleResponse( r );
            }
        }
        
    }

}
