import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Enhanced PvP State Machine with timeout protection, error recovery, and comprehensive state tracking.
 * 
 * Features:
 * - Timeout protection for all state transitions
 * - Automatic error recovery and retry mechanisms
 * - Thread-safe state management
 * - Comprehensive state history tracking
 * - Event-driven architecture with listeners
 * - Graceful degradation on failures
 * - Configurable timeout and retry policies
 */
public class ImprovedPvpStateMachine {
    private static final Logger LOGGER = Logger.getLogger(ImprovedPvpStateMachine.class.getName());
    
    /**
     * Represents the various states in the PvP lifecycle.
     */
    public enum State {
        IDLE("Idle - Waiting for match"),
        MATCHMAKING("Matchmaking - Searching for opponent"),
        MATCH_FOUND("Match Found - Opponent located"),
        PREPARING("Preparing - Loading game resources"),
        COUNTDOWN("Countdown - Pre-match timer"),
        IN_PROGRESS("In Progress - Active combat"),
        PAUSED("Paused - Match temporarily halted"),
        ENDED("Ended - Match completed"),
        ERROR("Error - Recovery required"),
        DISCONNECTED("Disconnected - Network issue");
        
        private final String description;
        
        State(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Represents events that trigger state transitions.
     */
    public enum Event {
        START_MATCHMAKING,
        MATCH_FOUND,
        MATCH_FAILED,
        PREPARE_COMPLETE,
        PREPARE_FAILED,
        COUNTDOWN_START,
        COUNTDOWN_COMPLETE,
        MATCH_START,
        MATCH_END,
        PAUSE,
        RESUME,
        DISCONNECT,
        RECONNECT,
        ERROR_OCCURRED,
        RECOVERY_COMPLETE,
        FORCE_END,
        TIMEOUT
    }
    
    /**
     * Configuration for timeout and retry policies.
     */
    public static class StateMachineConfig {
        private final Map<State, Duration> stateTimeouts;
        private final int maxRetries;
        private final Duration retryDelay;
        private final boolean enableAutoRecovery;
        private final int maxHistorySize;
        
        public static class Builder {
            private final Map<State, Duration> stateTimeouts = new EnumMap<>(State.class);
            private int maxRetries = 3;
            private Duration retryDelay = Duration.ofSeconds(2);
            private boolean enableAutoRecovery = true;
            private int maxHistorySize = 100;
            
            public Builder() {
                // Default timeouts for each state
                stateTimeouts.put(State.MATCHMAKING, Duration.ofMinutes(5));
                stateTimeouts.put(State.MATCH_FOUND, Duration.ofSeconds(30));
                stateTimeouts.put(State.PREPARING, Duration.ofSeconds(60));
                stateTimeouts.put(State.COUNTDOWN, Duration.ofSeconds(10));
                stateTimeouts.put(State.IN_PROGRESS, Duration.ofMinutes(30));
                stateTimeouts.put(State.PAUSED, Duration.ofMinutes(10));
                stateTimeouts.put(State.ERROR, Duration.ofMinutes(2));
                stateTimeouts.put(State.DISCONNECTED, Duration.ofMinutes(5));
            }
            
            public Builder setStateTimeout(State state, Duration timeout) {
                stateTimeouts.put(state, timeout);
                return this;
            }
            
            public Builder setMaxRetries(int maxRetries) {
                this.maxRetries = maxRetries;
                return this;
            }
            
            public Builder setRetryDelay(Duration retryDelay) {
                this.retryDelay = retryDelay;
                return this;
            }
            
            public Builder setEnableAutoRecovery(boolean enableAutoRecovery) {
                this.enableAutoRecovery = enableAutoRecovery;
                return this;
            }
            
            public Builder setMaxHistorySize(int maxHistorySize) {
                this.maxHistorySize = maxHistorySize;
                return this;
            }
            
            public StateMachineConfig build() {
                return new StateMachineConfig(this);
            }
        }
        
        private StateMachineConfig(Builder builder) {
            this.stateTimeouts = new EnumMap<>(builder.stateTimeouts);
            this.maxRetries = builder.maxRetries;
            this.retryDelay = builder.retryDelay;
            this.enableAutoRecovery = builder.enableAutoRecovery;
            this.maxHistorySize = builder.maxHistorySize;
        }
        
        public Duration getTimeout(State state) {
            return stateTimeouts.getOrDefault(state, Duration.ofMinutes(5));
        }
        
        public int getMaxRetries() {
            return maxRetries;
        }
        
        public Duration getRetryDelay() {
            return retryDelay;
        }
        
        public boolean isAutoRecoveryEnabled() {
            return enableAutoRecovery;
        }
        
        public int getMaxHistorySize() {
            return maxHistorySize;
        }
    }
    
    /**
     * Represents a state transition in the history.
     */
    public static class StateTransition {
        private final State fromState;
        private final State toState;
        private final Event triggeringEvent;
        private final Instant timestamp;
        private final boolean successful;
        private final String details;
        
        public StateTransition(State fromState, State toState, Event triggeringEvent, 
                              boolean successful, String details) {
            this.fromState = fromState;
            this.toState = toState;
            this.triggeringEvent = triggeringEvent;
            this.timestamp = Instant.now();
            this.successful = successful;
            this.details = details;
        }
        
        public State getFromState() { return fromState; }
        public State getToState() { return toState; }
        public Event getTriggeringEvent() { return triggeringEvent; }
        public Instant getTimestamp() { return timestamp; }
        public boolean isSuccessful() { return successful; }
        public String getDetails() { return details; }
        
        @Override
        public String toString() {
            return String.format("[%s] %s -> %s (via %s) %s - %s",
                timestamp, fromState, toState, triggeringEvent,
                successful ? "SUCCESS" : "FAILED", details);
        }
    }
    
    /**
     * Listener interface for state change events.
     */
    public interface StateChangeListener {
        void onStateChanged(State oldState, State newState, Event triggeringEvent);
        void onTimeout(State state, Duration elapsed);
        void onError(State state, Throwable error);
        void onRecovery(State recoveredState);
        void onTransitionFailed(State fromState, State toState, Event event, Throwable error);
    }
    
    /**
     * Abstract base for state handlers with timeout and error handling.
     */
    public abstract static class StateHandler {
        protected final ImprovedPvpStateMachine machine;
        
        public StateHandler(ImprovedPvpStateMachine machine) {
            this.machine = machine;
        }
        
        public void onEnter(State previousState, Event triggeringEvent) throws Exception {
            // Default implementation - override as needed
        }
        
        public void onExit(State nextState, Event triggeringEvent) throws Exception {
            // Default implementation - override as needed
        }
        
        public void onTimeout() {
            // Default timeout handler
            LOGGER.warning(String.format("State %s timed out in handler", machine.getCurrentState()));
        }
        
        public void onError(Throwable error) {
            // Default error handler
            LOGGER.log(Level.SEVERE, String.format("Error in state %s", machine.getCurrentState()), error);
        }
    }
    
    // Core state machine fields
    private volatile State currentState;
    private volatile State previousState;
    private final ReentrantLock stateLock = new ReentrantLock();
    private final Map<State, StateHandler> stateHandlers = new EnumMap<>(State.class);
    private final ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor(
        r -> {
            Thread t = new Thread(r, "PvP-Timeout-Handler");
            t.setDaemon(true);
            return t;
        }
    );
    private final ExecutorService recoveryExecutor = Executors.newSingleThreadExecutor(
        r -> {
            Thread t = new Thread(r, "PvP-Recovery-Handler");
            t.setDaemon(true);
            return t;
        }
    );
    
    // Configuration and tracking
    private final StateMachineConfig config;
    private final Map<State, Instant> stateEntryTimes = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<StateTransition> transitionHistory = new ConcurrentLinkedQueue<>();
    private final CopyOnWriteArrayList<StateChangeListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isRecovering = new AtomicBoolean(false);
    private final AtomicBoolean isDisposed = new AtomicBoolean(false);
    
    // Timeout tracking
    private ScheduledFuture<?> currentTimeoutFuture;
    private volatile int consecutiveFailures = 0;
    private volatile Throwable lastError;
    
    /**
     * Creates a new PvP state machine with default configuration.
     */
    public ImprovedPvpStateMachine() {
        this(new StateMachineConfig.Builder().build());
    }
    
    /**
     * Creates a new PvP state machine with custom configuration.
     */
    public ImprovedPvpStateMachine(StateMachineConfig config) {
        this.config = config;
        this.currentState = State.IDLE;
        this.previousState = State.IDLE;
        this.stateEntryTimes.put(State.IDLE, Instant.now());
        
        // Register default state handlers
        registerDefaultHandlers();
        
        LOGGER.info("PvP State Machine initialized with state: " + currentState);
    }
    
    private void registerDefaultHandlers() {
        // Handlers can be overridden by users of this class
        stateHandlers.put(State.MATCHMAKING, new MatchmakingHandler(this));
        stateHandlers.put(State.PREPARING, new PreparingHandler(this));
        stateHandlers.put(State.COUNTDOWN, new CountdownHandler(this));
        stateHandlers.put(State.IN_PROGRESS, new InProgressHandler(this));
        stateHandlers.put(State.ERROR, new ErrorHandler(this));
        stateHandlers.put(State.DISCONNECTED, new DisconnectedHandler(this));
    }
    
    /**
     * Registers a custom state handler for a specific state.
     */
    public void registerStateHandler(State state, StateHandler handler) {
        stateHandlers.put(state, handler);
    }
    
    /**
     * Adds a listener for state change events.
     */
    public void addStateChangeListener(StateChangeListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Removes a listener.
     */
    public void removeStateChangeListener(StateChangeListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Transitions to a new state based on an event.
     */
    public boolean transition(Event event) {
        if (isDisposed.get()) {
            LOGGER.warning("Cannot transition - state machine is disposed");
            return false;
        }
        
        stateLock.lock();
        try {
            State targetState = determineTargetState(currentState, event);
            
            if (targetState == null) {
                LOGGER.warning(String.format("Invalid transition: %s --%s--> ?", currentState, event));
                notifyTransitionFailed(currentState, null, event, 
                    new IllegalStateException("Invalid transition for event: " + event));
                return false;
            }
            
            return performTransition(targetState, event);
        } finally {
            stateLock.unlock();
        }
    }
    
    /**
     * Determines the target state for a given current state and event.
     */
    private State determineTargetState(State current, Event event) {
        switch (current) {
            case IDLE:
                if (event == Event.START_MATCHMAKING) return State.MATCHMAKING;
                break;
                
            case MATCHMAKING:
                if (event == Event.MATCH_FOUND) return State.MATCH_FOUND;
                if (event == Event.MATCH_FAILED) return State.ERROR;
                if (event == Event.TIMEOUT) return State.ERROR;
                break;
                
            case MATCH_FOUND:
                if (event == Event.PREPARE_COMPLETE) return State.PREPARING;
                if (event == Event.PREPARE_FAILED) return State.ERROR;
                if (event == Event.TIMEOUT) return State.ERROR;
                break;
                
            case PREPARING:
                if (event == Event.PREPARE_COMPLETE) return State.COUNTDOWN;
                if (event == Event.PREPARE_FAILED) return State.ERROR;
                if (event == Event.TIMEOUT) return State.ERROR;
                break;
                
            case COUNTDOWN:
                if (event == Event.COUNTDOWN_COMPLETE) return State.IN_PROGRESS;
                if (event == Event.ERROR_OCCURRED) return State.ERROR;
                if (event == Event.TIMEOUT) return State.ERROR;
                break;
                
            case IN_PROGRESS:
                if (event == Event.PAUSE) return State.PAUSED;
                if (event == Event.MATCH_END) return State.ENDED;
                if (event == Event.DISCONNECT) return State.DISCONNECTED;
                if (event == Event.ERROR_OCCURRED) return State.ERROR;
                break;
                
            case PAUSED:
                if (event == Event.RESUME) return State.IN_PROGRESS;
                if (event == Event.MATCH_END) return State.ENDED;
                if (event == Event.TIMEOUT) return State.ERROR;
                break;
                
            case ERROR:
                if (event == Event.RECOVERY_COMPLETE) return State.IDLE;
                if (event == Event.FORCE_END) return State.ENDED;
                break;
                
            case DISCONNECTED:
                if (event == Event.RECONNECT) return previousState != State.DISCONNECTED ? previousState : State.IDLE;
                if (event == Event.FORCE_END) return State.ENDED;
                if (event == Event.TIMEOUT) return State.ERROR;
                break;
                
            case ENDED:
                if (event == Event.START_MATCHMAKING) return State.MATCHMAKING;
                break;
        }
        
        // Allow same-state transitions for certain events
        if (event == Event.ERROR_OCCURRED && current != State.ERROR) {
            return State.ERROR;
        }
        
        return null;
    }
    
    /**
     * Performs the actual state transition with error handling.
     */
    private boolean performTransition(State targetState, Event triggeringEvent) {
        State oldState = currentState;
        boolean success = false;
        String details = "";
        
        try {
            // Exit current state
            StateHandler oldHandler = stateHandlers.get(oldState);
            if (oldHandler != null) {
                oldHandler.onExit(targetState, triggeringEvent);
            }
            
            // Cancel existing timeout
            cancelTimeout();
            
            // Update state
            previousState = oldState;
            currentState = targetState;
            stateEntryTimes.put(targetState, Instant.now());
            
            // Enter new state
            StateHandler newHandler = stateHandlers.get(targetState);
            if (newHandler != null) {
                newHandler.onEnter(oldState, triggeringEvent);
            }
            
            // Reset failure counter on successful transition
            if (oldState != targetState) {
                consecutiveFailures = 0;
            }
            
            // Set up timeout for new state
            scheduleTimeout(targetState);
            
            success = true;
            details = "Transition completed successfully";
            
            // Notify listeners
            notifyStateChanged(oldState, targetState, triggeringEvent);
            
            LOGGER.info(String.format("State transition: %s -> %s (via %s)", 
                oldState, targetState, triggeringEvent));
            
        } catch (Exception e) {
            details = "Transition failed: " + e.getMessage();
            LOGGER.log(Level.SEVERE, String.format("Failed to transition from %s to %s", 
                oldState, targetState), e);
            
            // Attempt recovery
            handleTransitionFailure(oldState, targetState, triggeringEvent, e);
            
        } finally {
            // Record transition in history
            recordTransition(oldState, currentState, triggeringEvent, success, details);
        }
        
        return success;
    }
    
    /**
     * Handles a failed transition attempt.
     */
    private void handleTransitionFailure(State fromState, State toState, 
                                        Event event, Throwable error) {
        consecutiveFailures++;
        lastError = error;
        
        notifyTransitionFailed(fromState, toState, event, error);
        
        // Attempt auto-recovery if enabled
        if (config.isAutoRecoveryEnabled() && consecutiveFailures <= config.getMaxRetries()) {
            scheduleRecovery();
        } else if (consecutiveFailures > config.getMaxRetries()) {
            LOGGER.severe("Maximum retry attempts exceeded. Forcing error state.");
            forceState(State.ERROR);
        }
    }
    
    /**
     * Schedules automatic recovery after a failure.
     */
    private void scheduleRecovery() {
        if (isRecovering.compareAndSet(false, true)) {
            recoveryExecutor.submit(() -> {
                try {
                    LOGGER.info("Attempting automatic recovery...");
                    Thread.sleep(config.getRetryDelay().toMillis());
                    
                    // Retry the last transition or reset to safe state
                    if (currentState == State.ERROR) {
                        resetToSafeState();
                    }
                    
                    isRecovering.set(false);
                    notifyRecovery(currentState);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    isRecovering.set(false);
                    LOGGER.warning("Recovery interrupted");
                }
            });
        }
    }
    
    /**
     * Resets the state machine to a safe state (IDLE).
     */
    public void resetToSafeState() {
        stateLock.lock();
        try {
            cancelTimeout();
            
            State oldState = currentState;
            currentState = State.IDLE;
            stateEntryTimes.put(State.IDLE, Instant.now());
            
            consecutiveFailures = 0;
            lastError = null;
            
            notifyStateChanged(oldState, State.IDLE, Event.RECOVERY_COMPLETE);
            LOGGER.info("State machine reset to safe state: IDLE");
            
        } finally {
            stateLock.unlock();
        }
    }
    
    /**
     * Forces the state machine into a specific state (emergency use).
     */
    public void forceState(State state) {
        stateLock.lock();
        try {
            cancelTimeout();
            
            State oldState = currentState;
            currentState = state;
            stateEntryTimes.put(state, Instant.now());
            
            if (state == State.ERROR) {
                consecutiveFailures++;
            }
            
            notifyStateChanged(oldState, state, Event.FORCE_END);
            LOGGER.warning(String.format("Forced state change: %s -> %s", oldState, state));
            
        } finally {
            stateLock.unlock();
        }
    }
    
    /**
     * Schedules a timeout for the current state.
     */
    private void scheduleTimeout(State state) {
        Duration timeout = config.getTimeout(state);
        
        if (timeout != null && !timeout.isZero() && !timeout.isNegative()) {
            cancelTimeout();
            
            currentTimeoutFuture = timeoutExecutor.schedule(() -> {
                handleTimeout(state);
            }, timeout.toMillis(), TimeUnit.MILLISECONDS);
            
            LOGGER.fine(String.format("Timeout scheduled for state %s: %s", state, timeout));
        }
    }
    
    /**
     * Handles a state timeout.
     */
    private void handleTimeout(State state) {
        stateLock.lock();
        try {
            Duration elapsed = Duration.between(stateEntryTimes.get(state), Instant.now());
            
            LOGGER.warning(String.format("State %s timed out after %s", state, elapsed));
            
            // Notify listeners
            listeners.forEach(listener -> {
                try {
                    listener.onTimeout(state, elapsed);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error in timeout listener", e);
                }
            });
            
            // Execute state-specific timeout handler
            StateHandler handler = stateHandlers.get(state);
            if (handler != null) {
                handler.onTimeout();
            }
            
            // Trigger timeout event
            transition(Event.TIMEOUT);
            
        } finally {
            stateLock.unlock();
        }
    }
    
    /**
     * Cancels any pending timeout.
     */
    private void cancelTimeout() {
        if (currentTimeoutFuture != null && !currentTimeoutFuture.isDone()) {
            currentTimeoutFuture.cancel(false);
            currentTimeoutFuture = null;
        }
    }
    
    /**
     * Records a state transition in history.
     */
    private void recordTransition(State from, State to, Event event, 
                                 boolean successful, String details) {
        StateTransition transition = new StateTransition(from, to, event, successful, details);
        transitionHistory.add(transition);
        
        // Limit history size
        while (transitionHistory.size() > config.getMaxHistorySize()) {
            transitionHistory.poll();
        }
    }
    
    /**
     * Notifies all listeners of a state change.
     */
    private void notifyStateChanged(State oldState, State newState, Event event) {
        listeners.forEach(listener -> {
            try {
                listener.onStateChanged(oldState, newState, event);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in state change listener", e);
            }
        });
    }
    
    /**
     * Notifies all listeners of a failed transition.
     */
    private void notifyTransitionFailed(State fromState, State toState, 
                                       Event event, Throwable error) {
        listeners.forEach(listener -> {
            try {
                listener.onTransitionFailed(fromState, toState, event, error);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in transition failed listener", e);
            }
        });
    }
    
    /**
     * Notifies all listeners of a recovery event.
     */
    private void notifyRecovery(State recoveredState) {
        listeners.forEach(listener -> {
            try {
                listener.onRecovery(recoveredState);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in recovery listener", e);
            }
        });
    }
    
    /**
     * Records an error in the current state.
     */
    public void recordError(Throwable error) {
        this.lastError = error;
        
        listeners.forEach(listener -> {
            try {
                listener.onError(currentState, error);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in error listener", e);
            }
        });
        
        LOGGER.log(Level.SEVERE, String.format("Error recorded in state %s", currentState), error);
    }
    
    // ========== Getters and Status Methods ==========
    
    public State getCurrentState() {
        return currentState;
    }
    
    public State getPreviousState() {
        return previousState;
    }
    
    public Duration getTimeInCurrentState() {
        Instant entryTime = stateEntryTimes.get(currentState);
        return entryTime != null ? Duration.between(entryTime, Instant.now()) : Duration.ZERO;
    }
    
    public boolean isInState(State state) {
        return currentState == state;
    }
    
    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }
    
    public Throwable getLastError() {
        return lastError;
    }
    
    public boolean isRecovering() {
        return isRecovering.get();
    }
    
    public StateMachineConfig getConfig() {
        return config;
    }
    
    /**
     * Returns an immutable snapshot of the transition history.
     */
    public StateTransition[] getTransitionHistory() {
        return transitionHistory.toArray(new StateTransition[0]);
    }
    
    /**
     * Returns the number of transitions in history.
     */
    public int getHistorySize() {
        return transitionHistory.size();
    }
    
    /**
     * Checks if the state machine can transition based on the event.
     */
    public boolean canTransition(Event event) {
        return determineTargetState(currentState, event) != null;
    }
    
    /**
     * Disposes the state machine, releasing all resources.
     */
    public void dispose() {
        if (isDisposed.compareAndSet(false, true)) {
            cancelTimeout();
            timeoutExecutor.shutdownNow();
            recoveryExecutor.shutdownNow();
            
            try {
                if (!timeoutExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOGGER.warning("Timeout executor did not terminate gracefully");
                }
                if (!recoveryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOGGER.warning("Recovery executor did not terminate gracefully");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warning("Interrupted during shutdown");
            }
            
            listeners.clear();
            stateHandlers.clear();
            transitionHistory.clear();
            stateEntryTimes.clear();
            
            LOGGER.info("PvP State Machine disposed");
        }
    }
    
    @Override
    protected void finalize() throws Throwable {
        try {
            if (!isDisposed.get()) {
                LOGGER.warning("State machine was not properly disposed");
                dispose();
            }
        } finally {
            super.finalize();
        }
    }
    
    // ========== Default State Handlers ==========
    
    private static class MatchmakingHandler extends StateHandler {
        public MatchmakingHandler(ImprovedPvpStateMachine machine) {
            super(machine);
        }
        
        @Override
        public void onEnter(State previousState, Event triggeringEvent) {
            LOGGER.info("Starting matchmaking process...");
        }
    }
    
    private static class PreparingHandler extends StateHandler {
        public PreparingHandler(ImprovedPvpStateMachine machine) {
            super(machine);
        }
        
        @Override
        public void onEnter(State previousState, Event triggeringEvent) {
            LOGGER.info("Preparing match resources...");
        }
    }
    
    private static class CountdownHandler extends StateHandler {
        public CountdownHandler(ImprovedPvpStateMachine machine) {
            super(machine);
        }
        
        @Override
        public void onEnter(State previousState, Event triggeringEvent) {
            LOGGER.info("Starting countdown...");
        }
        
        @Override
        public void onTimeout() {
            LOGGER.warning("Countdown timed out!");
            super.onTimeout();
        }
    }
    
    private static class InProgressHandler extends StateHandler {
        public InProgressHandler(ImprovedPvpStateMachine machine) {
            super(machine);
        }
        
        @Override
        public void onEnter(State previousState, Event triggeringEvent) {
            LOGGER.info("Match in progress!");
        }
    }
    
    private static class ErrorHandler extends StateHandler {
        public ErrorHandler(ImprovedPvpStateMachine machine) {
            super(machine);
        }
        
        @Override
        public void onEnter(State previousState, Event triggeringEvent) {
            LOGGER.severe("Entered error state - recovery required");
        }
    }
    
    private static class DisconnectedHandler extends StateHandler {
        public DisconnectedHandler(ImprovedPvpStateMachine machine) {
            super(machine);
        }
        
        @Override
        public void onEnter(State previousState, Event triggeringEvent) {
            LOGGER.warning("Connection lost - attempting to reconnect...");
        }
    }
    
    @Override
    public String toString() {
        return String.format("ImprovedPvpStateMachine{current=%s, previous=%s, timeInState=%s, failures=%d}",
            currentState, previousState, getTimeInCurrentState(), consecutiveFailures);
    }
}
