package thibautperrin.tetrano.packageGestionJeu;

import thibautperrin.tetrano.packageSurfaceGraphView.SurfaceGraphView;

/**
 * Created by Thibaut PERRIN on 15/08/2015.
 * This interface describe the object managing the game, which will be in
 * communication with the surfaceGraphView (the user interface).
 */
public interface InterfaceGameManagement {

    /**
     * When the screen orientation change, we have to build again the surface.
     * However the interface should not been deleted.
     * So, when the surface is built again, we have to rebuild the connection
     * between the srface and the interface of managing a game.
     * This method should just reset the field surface as the new surface.
     * @param surface : The surfaceGraphView wanted to build the connection.
     */
    void setSurfaceGraphView(SurfaceGraphView surface);

    /**
     * This method is called when the surfaceGraphView is ready.
     * the implementation of the interface can begin to send the
     * data describing what should be displayed by the surface.
     */
    void begin();

    /**
     * This method is called when the user has chosen a ring and put it on the
     * grid. This method verifies if the user can play in this location, that
     * mean if the location is free. This method considers that the ring is
     * played by the user (if possible) but wait the call to the method
     * postRingPlayedByUser to proceed to the next step.
     * @param ringNumber byte between 0 and 63 representing the ring that
     *                   the user tries to play.
     * @return true if the user can play here, false if he can't.
     */
    boolean userTriedPlaying(byte ringNumber);

    /**
     * This method is called when the user interface is updated for the ring
     * played by the user: We can proceed to the next step.
     */
    void postRingPlayedByUser();
}
