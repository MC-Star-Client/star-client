package dev.performanceclient.features;
import java.util.*;
/** Feature registry is intentionally explicit about whether the game integration actually exists. */
public final class FeatureCatalog {
    public enum Stage { ACTIVE, LOCAL_ONLY, PLANNED }
    public record Feature(String group,String name,Stage stage,boolean fairPlay) {}
    private static final List<Feature> ALL=List.of(
        new Feature("performance","frame-csv",Stage.ACTIVE,true),
        new Feature("performance","renderpearl-gpu-probe",Stage.ACTIVE,true),
        new Feature("performance","worker-meshing",Stage.LOCAL_ONLY,true),
        new Feature("performance","region-buffer-allocation",Stage.LOCAL_ONLY,true),
        new Feature("performance","faster-chunk-draw",Stage.PLANNED,true),
        new Feature("performance","gpu-occlusion-hiz",Stage.PLANNED,true),
        new Feature("comfort","movable-hud-layout",Stage.LOCAL_ONLY,true),
        new Feature("comfort","zoom",Stage.PLANNED,true),
        new Feature("comfort","freelook",Stage.PLANNED,true),
        new Feature("comfort","minimap",Stage.PLANNED,true),
        new Feature("comfort","waypoints-file",Stage.LOCAL_ONLY,true),
        new Feature("comfort","hide-names",Stage.PLANNED,true),
        new Feature("pvp","keystrokes",Stage.LOCAL_ONLY,true),
        new Feature("pvp","cps",Stage.LOCAL_ONLY,true),
        new Feature("pvp","pixel-crosshair",Stage.LOCAL_ONLY,true),
        new Feature("pvp","animation-sounds",Stage.PLANNED,true),
        new Feature("pvp","visual-hitboxes",Stage.PLANNED,false),
        new Feature("management","local-profiles",Stage.ACTIVE,true),
        new Feature("management","modrinth-curseforge-manager",Stage.PLANNED,true),
        new Feature("management","crash-assistant",Stage.LOCAL_ONLY,true),
        new Feature("extra","replay",Stage.PLANNED,true),
        new Feature("extra","screenshots",Stage.PLANNED,true),
        new Feature("extra","discord-integration",Stage.PLANNED,true),
        new Feature("extra","pixel-player-png",Stage.LOCAL_ONLY,true),
        new Feature("extra","cosmetics",Stage.PLANNED,true),
        new Feature("extra","localization",Stage.LOCAL_ONLY,true)
    );
    public static List<Feature> all(){return ALL;}
    private FeatureCatalog(){}
}
