package smart.home.entity;

public enum GarageDoorState {
    UNKNOWN,           // Initial state or lost tracking
    IDLE_CLOSED,       // Door is closed and not moving (~7W)
    IDLE_OPEN,         // Door is open and not moving (~7W)
    IDLE_PARTIAL,      // Door is partially open (stopped mid-operation)
    OPENING,           // Door is currently opening (25-119W)
    CLOSING,           // Door is currently closing (25-119W)
    STUCK_OPENING,     // Door got stuck while opening
    STUCK_CLOSING,     // Door got stuck while closing
    IDLE_WITH_LIGHT,   // Motor idle but light is on (~26-28W)
    STOPPED_PARTIAL    // Door was manually stopped during operation
}