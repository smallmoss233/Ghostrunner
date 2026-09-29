package ghostrunner.api;

public interface GhostrunnerStamina {
    float ghostrunner$getStamina();
    void ghostrunner$setStamina(float value);
    void ghostrunner$consumeStamina(float amount);
    boolean ghostrunner$isAirDashUsed();
    void ghostrunner$setAirDashUsed(boolean used);

    // ============ 格挡 ============
    boolean ghostrunner$isBlocking();
    void ghostrunner$setBlocking(boolean blocking);
}