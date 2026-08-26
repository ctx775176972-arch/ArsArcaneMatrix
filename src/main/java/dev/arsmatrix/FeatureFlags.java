package dev.arsmatrix;

/** Compile-time switches for features that are retained in source but not ready for release. */
public final class FeatureFlags {

    /** Keeps the registry id stable while allowing the rebuilt Arcane Hunting Grounds to ship. */
    public static final boolean ARCANE_ARENA = true;

    private FeatureFlags() {
    }
}
