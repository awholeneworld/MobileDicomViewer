package gachon.dicomviewer;

public class ThreadState {

    // Thread has catch a OutOfMemoryError exception.
    public static final short OUT_OF_MEMORY = 0;

    // The thread is started.
    public static final short STARTED = 1;

    // The thread is finished.
    public static final short FINISHED = 2;

    // The thread progression update.
    public static final short PROGRESSION_UPDATE = 3;

    // An error occurred while the thread running that cannot be managed.
    public static final short UNCATCHABLE_ERROR_OCCURRED = 4;

    // An error occurred while the thread running that can be managed or ignored.
    public static final short CATCHABLE_ERROR_OCCURRED = 5;
}
