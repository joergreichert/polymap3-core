/* 
 * polymap.org
 * Copyright 2009-2013, Polymap GmbH. All rights reserved.
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
 */
package org.polymap.core.project.operations;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.polymap.core.project.ILayer;
import org.polymap.core.project.ProjectRepository;
import org.polymap.core.qi4j.event.AbstractModelChangeOperation;

/**
 * 
 *
 * @author <a href="http://www.polymap.de">Falko Braeutigam</a>
 * @since 3.0
 */
public class RemoveLayerOperation
        extends AbstractModelChangeOperation {

    private List<ILayer>          layers = new ArrayList();

    public RemoveLayerOperation() {
        super( "[undefined]" );
    }


    public void init( ILayer layer ) {
        this.layers.add( layer );
        setLabel( '"' + layer.getLabel() + "\" l�schen" );
    }

    public void init( List<ILayer> _layers ) {
        this.layers.addAll( _layers );
        if (layers.size() > 1) {
            setLabel( layers.size() + " Ebenen l�schen" );
        } else {
            setLabel( '"' + layers.get( 0 ).getLabel() + "\" l�schen" );
        }
    }


    public IStatus doExecute( IProgressMonitor monitor, IAdaptable info )
            throws ExecutionException {
        try {
            ProjectRepository rep = ProjectRepository.instance();
            for (ILayer layer : layers) {
                layer.getMap().removeLayer( layer );
                rep.removeEntity( layer );
            }
        }
        catch (Throwable e) {
            throw new ExecutionException( e.getMessage(), e );
        }
        return Status.OK_STATUS;
    }

}
