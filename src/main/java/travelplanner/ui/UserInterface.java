package travelplanner.ui;

import travelplanner.model.TripRequest;

/**
 * What the User agent needs from a user interface. The console version implements it now;
 * a Swing GUI can implement the same interface later without changing UserAgent.
 *
 * Threading rules (important for a GUI):
 *  - The show* methods and close() are called from the JADE agent thread.
 *    A Swing implementation must hand the work to the Swing thread with
 *    SwingUtilities.invokeLater(...).
 *  - Listener methods are called from the UI's own thread (console thread or Swing thread).
 *    UserAgent turns them into behaviours, so the UI never touches agent internals directly.
 */
public interface UserInterface {

    /** Callbacks from the UI to the agent. */
    interface Listener {
        /** The user wants this request sent to the Planner. */
        void onSubmit(TripRequest request);

        /** The user closed the UI. */
        void onQuit();
    }

    /** Opens the UI. Must return quickly; the UI runs on its own thread. */
    void start(Listener listener);

    /** Intermediate progress for a request (e.g. the Planner AGREEd). */
    void showStatus(String requestId, String message);

    /** Final successful answer for a request. */
    void showResult(String requestId, String result);

    /** Final failure for a request (REFUSE, FAILURE, NOT_UNDERSTOOD, unreachable planner). */
    void showError(String requestId, String error);

    /** Closes the UI and frees its resources. */
    void close();
}
