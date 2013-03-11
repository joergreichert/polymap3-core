package net.refractions.udig.catalog.ui;

import org.eclipse.swt.widgets.Display;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.refractions.udig.catalog.CatalogPlugin;
import net.refractions.udig.catalog.ICatalog;
import net.refractions.udig.catalog.IGeoResource;
import net.refractions.udig.catalog.IResolve;
import net.refractions.udig.catalog.IResolveChangeEvent;
import net.refractions.udig.catalog.IResolveChangeListener;
import net.refractions.udig.catalog.IService;
import net.refractions.udig.catalog.ui.internal.Messages;

import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.ui.PlatformUI;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;

import org.polymap.core.runtime.UIJob;


/**
 * Provides a threaded Tree content provider for IResolve.
 * <p>
 * Responsibilities:
 * <ul>
 * <li>Rooted by a catalog
 * <li>Ensure that calls to members are dispatched in a non ui thread
 * </ul>
 * </p>
 * 
 * @author jgarnett
 * @since 0.6.0
 */
public class AbstractResolveContentProvider
        implements IResolveChangeListener {

    /**
     * Map of threads used to resolve associated IResolves.
     * <p/>
     * Waking up the thread will cause the associated IResolve to be refreshed.
     * </p>
     * Here an {@link IdentityHashMap} is used because the containsKey otherwise
     * doesn't look deep enough in the object to correctly deal with multilevel
     * services.
     */
    private Map<IResolve,Update>    jobs = new IdentityHashMap<IResolve,Update>();

    /**
     * Captures parent child relationships.
     * <p/>
     * Here an {@link IdentityHashMap} is used because the containsKey otherwise
     * doesn't look deep enough in the object to correctly deal with multilevel
     * services.
     */
    protected final Map<IResolve, List<IResolve>> structure = new IdentityHashMap<IResolve, List<IResolve>>();

    /** Root of this tree, often a ICatalog. */
    protected ICatalog              catalog;
    protected List<IResolve>        list;
    protected Viewer                viewer;
    private Display                 display;

    public AbstractResolveContentProvider() {
        super();
    }

    /**
     * Catalog has changed!
     * <p>
     * Will only start up a thread to update the structure if:
     * <ul>
     * <li>We care about this resolve
     * <li>We are not already running a thread
     * </ul>
     * </p>
     * This will allow us to "ignore" events generated by the process up inspecting the resolve
     * being updated.
     * </p>
     * 
     * @param event
     */
    public void changed( IResolveChangeEvent event ) {
    	if (jobs==null) {
    		// we are disposed
    		if (catalog!=null) {
    			catalog.removeCatalogListener(this);
    		}
    		return;
    	}
        if (event.getType() != IResolveChangeEvent.Type.POST_CHANGE) {
            return;
        }
        IResolve resolve = event.getResolve();
        if (jobs.containsKey(resolve)) {
            update(resolve);
        }
    }

    /**
     * Called by thread to client code to get the content refreshed.
     * <p>
     * This call will not update structure, just appearance.
     * </p>
     * 
     * @param resolve
     */
    public void refresh( final IResolve resolve ) {
        if (PlatformUI.getWorkbench().isClosing()) {
            return;
        }
        display.asyncExec( new Runnable(){
        	public void run() {
        		if (viewer instanceof TreeViewer) {
    				TreeViewer treeViewer = (TreeViewer) viewer;
    				treeViewer.refresh(resolve, true);
    			}else{
        			viewer.refresh();
    			}
        	}
        });
        
//        PlatformGIS.asyncInDisplayThread(object, true);
    }

    /**
     * Update appearance and structure.
     * <p/>
     * Note: this will spawn a thread to fetch the required information.
     */
    public void update( final IResolve resolve ) {
        if (resolve == null) {
            return;
        }
        if (resolve.getIdentifier() == null) {
            // System.out.println( "Got an resolve with out an id "+ resolve);
        }
        
        // run the thread, unless it is already running...
        // (this will cause any change events generated by the thread
        // to be ignored).
        Update job = jobs.get( resolve );
        if (job != null) {
            if (job.getState() == Job.RUNNING) {
                // thread will already report back to structure
                // Note: thread should end with a async update to the
                // assoicated element
            } 
            else {
                // We had a thread but it stopped - must be do to an error?
                job.cancelAndInterrupt();

                job = new Update( resolve );
                jobs.put( resolve, job );
            }
        } 
        else {
            // request a structure update
            job = new Update( resolve );
            jobs.put( resolve, job );
        }
    }

    /**
     * Notifies this content provider that the given viewer's input has been switched to a different
     * element.
     * <p>
     * A typical use for this method is registering the content provider as a listener to changes on
     * the new input (using model-specific means), and deregistering the viewer from the old input.
     * In response to these change notifications, the content provider should update the viewer (see
     * the add, remove, update and refresh methods on the viewers).
     * </p>
     * <p>
     * The viewer should not be updated during this call, as it might be in the process of being
     * disposed.
     * </p>
     * 
     * @param viewer the viewer
     * @param oldInput the old input element, or <code>null</code> if the viewer did not
     *        previously have an input
     * @param newInput the new input element, or <code>null</code> if the viewer does not have an
     *        input
     */
    @SuppressWarnings("unchecked")
    public void inputChanged( Viewer newViewer, Object oldInput, Object newInput ) {
        if (oldInput == newInput) {
            return;
        }
        viewer = newViewer;
        display = viewer.getControl().getDisplay();
        assert display != null : "Called outside UIThread.";
        
        if (catalog != null || list != null) {
            CatalogPlugin.removeListener(this);
        }
        catalog = newInput instanceof ICatalog ? (ICatalog) newInput : null;
        list = newInput instanceof List ? (List<IResolve>) newInput : null;
        
        if (catalog != null || list != null) {
            CatalogPlugin.addListener(this);
        }
    }

    public void dispose() {
        if (jobs != null) {
            for( IResolve resolve : jobs.keySet() ) {
                Job job = jobs.get(resolve);
                if (job!=null && job.getState()==Job.RUNNING) {
                    job.cancel();
                }
            }
            if(catalog!=null)
            	catalog.removeCatalogListener(this);
            
            CatalogPlugin.getDefault().getLocalCatalog().removeCatalogListener(this);
            jobs.clear();
            jobs = null;
        }
        if (structure != null) {
            for( IResolve resolve : structure.keySet() ) {
                List<IResolve> children = structure.get(resolve);
                if( children!=null )
                    children.clear();
            }
            structure.clear();
        }
    }


    /**
     * Thread for updating structure
     * <p>
     * Note: thread notify the system that the element has requires an update
     */
    class Update
            extends UIJob {
        
        private IResolve        resolve;
        private Throwable       exception;
        
        Update( IResolve target ) {
            super( Messages.get( "ResolveContentProvider_connecting" ) + ": " + target );
            this.resolve = target;
            setUser( true );
            schedule();
        }
        
        public Throwable getException() {
            return exception;
        }

        /**
         * Update strucuture, Thread will be notified if more updates are required.
         * <p>
         * Note: We also need to let ourselves be interrupted
         */
        @Override
        protected void runWithException( IProgressMonitor monitor ) throws Exception {
            try {
                System.out.println( getClass().getSimpleName() + ": " + resolve.getIdentifier()); //$NON-NLS-1$

                List<IResolve> children = new ArrayList();

                // force connect to backend resource here in the job, so that just
                // the job is blocked and not the UIThread of the label providers
                List<UIJob> infoJobs = new ArrayList();
                for (final IResolve child : resolve.members( monitor )) {
                    children.add( child );
                    
                    // separate jobs to fetch info to speed up operation
                    UIJob infoJob = new UIJob( Messages.get( "ResolveContentProvider_connecting" ) + ": " + child ) {
                        protected void runWithException( IProgressMonitor _monitor ) throws Exception {
                            if (child instanceof IService) {
                                ((IService)child).getInfo( _monitor );
                            }
                            else if (child instanceof IGeoResource) {
                                ((IGeoResource)child).getInfo( _monitor );
                            }
                        }
                    };
                    infoJobs.add( infoJob );
                    infoJob.schedule();
                }
                
                // wait for infoJobs
                UIJob.joinJobs( infoJobs );
                
                structure.put( resolve, children );
            } 
            catch (Exception e) {
                // could not get children
                e.printStackTrace();
                structure.put( resolve, null );
                exception = e;
            }
            refresh( resolve );
        }

    }

}