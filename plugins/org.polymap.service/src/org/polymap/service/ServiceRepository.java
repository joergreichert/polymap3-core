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
package org.polymap.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.qi4j.api.unitofwork.NoSuchEntityException;
import org.polymap.core.model.AssocCollection;
import org.polymap.core.operation.OperationSupport;
import org.polymap.core.project.IMap;
import org.polymap.core.qi4j.Qi4jPlugin;
import org.polymap.core.qi4j.QiModule;
import org.polymap.core.qi4j.QiModuleAssembler;
import org.polymap.service.model.ServiceListComposite;

/**
 * Factory and repository for the domain model artifacts.
 * 
 * @author <a href="http://www.polymap.de">Falko Braeutigam</a>
 * @version POLYMAP3 ($Revision$)
 * @since 3.0
 */
public class ServiceRepository
        extends QiModule
        implements org.polymap.core.model.Module {

    private static Log log = LogFactory.getLog( ServiceRepository.class );


    /**
     * Get or create the repository for the current user session.
     */
    public static final ServiceRepository instance() {
        return (ServiceRepository)Qi4jPlugin.Session.instance().module( ServiceRepository.class );
    }


    class WaitingAtomicReference<T>
            extends AtomicReference<T> {
        
        public T waitAndGet() {
            T ref = get();
            if (ref == null) {
                synchronized (this) {
                    while ((ref = get()) == null) {
                        try { wait( 1000 ); } catch (InterruptedException e) { }
                    }
                    return get();
                }
            }
            return ref;
        }
        
        public void setAndNotify( T value ) {
            set( value );
            synchronized (this) {
                notifyAll();
            }
        }
    }
    
    
    // instance *******************************************

    private WaitingAtomicReference<ServiceListComposite>   serviceList = new WaitingAtomicReference();
    
    private OperationSaveListener               operationListener;
    

    protected ServiceRepository( final QiModuleAssembler assembler ) {
        super( assembler );
        
        operationListener = new OperationSaveListener();
        OperationSupport.instance().addOperationSaveListener( operationListener );

        serviceList.setAndNotify( uow.get( ServiceListComposite.class, "serviceList" ) );
    }
    
    
    protected void done() {
        if (operationListener != null) {
            OperationSupport.instance().removeOperationSaveListener( operationListener );
            operationListener = null;
        }
    }

    
    protected void legacyRemoveServices() {
        ServiceListComposite tempServiceList = serviceList.get();        
        Iterator<IProvidedService> it = tempServiceList.services().iterator();
        IProvidedService service = null;
        while (it.hasNext()) {
            try {
                service = it.next();
                service.getMap();
            }
            catch (NoSuchEntityException e) {
                log.info( "Map is no longer found for service: " + e.identity() );
                if (service == null) {
                    it.remove();
                }
                else {
                    //tempServiceList.removeService( service );
                    it.remove();
                    removeService( service );
                }
            }
        }
    }
    
    
    public IProvidedService findService( IMap map, Class cl ) {
        List<IProvidedService> services = findServices( map, cl );
        if (services.size() > 1) {
            throw new IllegalStateException( "" );
        }
        else if (services.isEmpty()) {
            return null;
        }
        else {
            return services.get( 0 );
        }
    }

    
    public List<IProvidedService> findServices( IMap map, Class cl ) {
        List<IProvidedService> result = new ArrayList();
        for (IProvidedService service : serviceList.waitAndGet().getServices()) {
            try {
                if (service.getMapId().equals( map.id() )
                        && service.getServiceType().equals( cl )) {
                    result.add( service );
                }
            }
            catch (NoSuchEntityException e) {
                // the IMap of the service is no longer found
                log.info( "Map is no longer found for service: " + service.getPathSpec() );
            }
        }
        return result;
    }
    
    
    public void addService( IProvidedService service ) { 
        serviceList.waitAndGet().addService( service );
    }
    
    
    public void removeService( IProvidedService service ) { 
        serviceList.waitAndGet().removeService( service );
        removeEntity( service );
    }
    
    
    public Collection<IProvidedService> allServices() {
        AssocCollection<IProvidedService> services = serviceList.waitAndGet().getServices();
        List<IProvidedService> result = new ArrayList();
        for (IProvidedService service : services) {
            // check if the IMap is still there
            try {
                service.getMap();
                result.add( service );
            }
            catch (NoSuchEntityException e) {
                // the IMap of the service is no longer found
                log.info( "Map is no longer found for service: " + service.getPathSpec() );
                // FIXME delete this entity on IMap delete
            }
        }
        return result;
    }


    public <T> T newOperation( Class<T> type ) {
        T result = assembler.getModule().transientBuilderFactory().newTransient( type );
        return result;
    }

//    public void fireModelChangedEvent( Object source, String propName, Object oldValue, Object newValue) {
//        PropertyChangeEvent event = new PropertyChangeEvent( source, propName, oldValue, newValue ); 
//        for (Object l : propChangeListeners.getListeners()) {
//            ((PropertyChangeListener)l).propertyChange( event );
//        }
//    }
    
}