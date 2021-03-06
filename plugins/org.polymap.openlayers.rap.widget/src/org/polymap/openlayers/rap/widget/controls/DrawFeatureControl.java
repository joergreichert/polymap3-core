/*
 * polymap.org
 * Copyright (C) 2009-2015, Polymap GmbH. All rights reserved.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 */
package org.polymap.openlayers.rap.widget.controls;

import org.polymap.openlayers.rap.widget.handler.DrawFeatureHandler;
import org.polymap.openlayers.rap.widget.handler.Handler;
import org.polymap.openlayers.rap.widget.layers.VectorLayer;
import org.polymap.openlayers.rap.widget.util.Stringer;

/**
 * 
 *
 * @author <a href="http://www.polymap.de">Falko Br�utigam</a>
 * @since 3.0
 */
public class DrawFeatureControl 
        extends Control {
    
    /** Triggered when a feature has been added. */
    public final static String  EVENT_ADDED = "featureadded";

    private DrawFeatureHandler      handler;
    
    /**
     * 
     * @param layer
     * @param handler The handler to use to draw the feature.
     */
    public DrawFeatureControl( VectorLayer layer, DrawFeatureHandler handler ) {
        super.create( new Stringer( "new OpenLayers.Control.DrawFeature(", 
            layer.getJSObjRef(), ", ", handler.jsClassName(),
            // XXX FIX http://polymap.org/atlas/ticket/219
            // the event: this.events.triggerEvent('featureadded') is not fired for unknown reason
            // -> do triggerEvent() in timeout which seems to disarm a race cond
            ",{drawFeature: function(geometry) {",
                "var self = this; var args = arguments;",
                "setTimeout( function() {",
                    "OpenLayers.Control.DrawFeature.prototype.drawFeature.apply(self, args);",
                "}, 250 );",
            "}",
            "}",
            ");" ).toString() );
        
        addObjModCode( new Stringer( "OpenLayers.Util.extend(", getJSObjRef(), ", {",
            "activate: function() {",
                "OpenLayers.Control.DrawFeature.prototype.activate.apply(this, arguments);",
                "if (!this.keyboardHandler) {",
                    "this.keyboardCallbacks = { keydown: this.handleKeyDown };",
                    "this.keyboardHandler = new OpenLayers.Handler.Keyboard(this, this.keyboardCallbacks, {});",
                "}",
                "this.keyboardHandler.activate();",
            "},",
            "deactivate: function() {",
                "OpenLayers.Control.DrawFeature.prototype.deactivate.apply(this, arguments);",
                "this.keyboardHandler.deactivate();",
            "},",
            "destroy: function() {",
                "OpenLayers.Control.ModifyFeature.prototype.destroy.apply(this, arguments);",
                "if (this.keyboardHandler) {",
                    "this.keyboardHandler.destroy();",
                "}",
            "},",
            "handleKeyDown: function (evt) {",
                "var handled = false;",
                "switch (evt.keyCode) {",
                "case 90:",  // z
                    "if (evt.metaKey || evt.ctrlKey) {",
                        "this.undo();",
                        "handled = true;",
                    "}",
                    "break;",
                "case 89:", // y
                    "if (evt.metaKey || evt.ctrlKey) {",
                        "this.redo();",
                        "handled = true;",
                    "}",
                    "break;",
                "case 27:", // esc
                    "this.cancel();",
                    "handled = true;",
                    "break;",
                "}",
                "if (handled) {",
                    "OpenLayers.Event.stop(evt);",
                "}",
            "}",
            "});").toString() );
        
        handler.init( this );
        this.handler = handler;
    }

    
    public Handler getHandler() {
        return handler;
    }

    
    /**
     * Sets the new handler and destroys the old one.
     */
    public void changeHandler( DrawFeatureHandler newHandler ) {
        handler.deactivate();
        handler.destroy();
        
        handler = newHandler;
        handler.init( this );
        addObjModCode( new Stringer(
            getJSObjRef(), ".handler = new ", newHandler.jsClassName(), "(", 
                getJSObjRef(), ",",
                getJSObjRef(), ".callbacks,", 
                getJSObjRef(), ".handlerOptions);" ).toString() );
        handler.activate();
    }
    
    
//    public void addMode( int mode ) {
//        super.addObjModCode( "obj.mode |=  OpenLayers.Control.ModifyFeature." + mode2name( mode ) );
//    }
//
//    public void rmMode( int mode ) {
//        super.addObjModCode( "obj.mode &=  ~OpenLayers.Control.ModifyFeature." + mode2name( mode ) );
//    }

}
