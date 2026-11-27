package dev.performanceclient;
import dev.performanceclient.benchmark.*;
import dev.performanceclient.config.ClientPreferences;
import dev.performanceclient.gui.PerformanceScreen;
import dev.performanceclient.hud.FrametimeGraph;
import dev.performanceclient.profile.*;
import dev.performanceclient.performance.StageOneControl;
import dev.performanceclient.performance.StageOneControl.Feature;
import dev.performanceclient.renderer.adapter.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.nio.file.Path;
import java.io.IOException;
/** Aggregates benign client tools; experimental renderer remains DISABLED. */
public final class RuntimeController {
    public static final RuntimeController INSTANCE=new RuntimeController();
    private final Minecraft263Adapter adapter=new ConservativeMinecraft263Adapter();
    private final Path config=FabricLoader.getInstance().getConfigDir();
    public final ProfileManager profiles=new ProfileManager(config);
    public final ClientPreferences preferences=new ClientPreferences(config);
    public final BenchmarkService benchmark=new BenchmarkService(config);
    public final FrametimeGraph graph=new FrametimeGraph();
    private boolean initialized;
    private BenchmarkService.Mode nextBenchMode=BenchmarkService.Mode.VANILLA;
    private long rebuildCallsAtStart, entityCallsAtStart;
    private long rebuildActionsAtStart, entityActionsAtStart;
    private volatile String benchmarkHudText = "";
    private long benchmarkHudUntilNanos;
    private long ticks;
    private FrameRecorder.Result lastResult;
    private RuntimeController(){}
    public void initialize(){
        if(initialized)return;initialized=true;
        preferences.load();StageOneControl.INSTANCE.configure(preferences);profiles.initialize();
        profiles.select(preferences.get("profile","auto"));
        profiles.reload(PlatformInfo.detect(),GpuIdentity.from(adapter.device()));
    }
    public void frame(long nanos,long cpuNanos){
        graph.record(nanos);
        benchmark.onFrame(nanos,cpuNanos);
        if (benchmark.running() && benchmark.expired()) {
            finishBenchmark(true);  // exactly one minute on monotonic clock, rounded to next frame
        } else if (benchmark.running() && ticks % 12 == 0) {
            int left = benchmark.remainingSeconds();
            int warmup = benchmark.warmupRemaining();
            String phase = warmup > 0 ? "warmup " + warmup
                : "measured " + benchmark.measuredFrames();
            benchmarkHudText = String.format(java.util.Locale.ROOT,
                "F9 %s | %02d:%02d remaining | %s",
                benchmark.mode(), left/60, left%60, phase);
        }
        if(++ticks==1){
            Minecraft263Adapter.DeviceDescriptor d=adapter.device();
            profiles.reload(PlatformInfo.detect(),GpuIdentity.from(d));
            PerformanceClient.LOGGER.info("GPU backend={} vendor={} renderer={} driver={}",d.rawBackend(),d.vendor(),d.gpu(),d.driver());
        } // no disk I/O on subsequent frames
    }
    public Minecraft263Adapter.DeviceDescriptor device(){return adapter.device();}
    public String profileName(){return profiles.activeName();}
    public String runStatus(){return benchmark.running()?"recording "+benchmark.mode():"idle (next "+nextBenchMode+")";}
    public String stageOneMode(){return StageOneControl.INSTANCE.mode().name();}
    /** Read by GuiBenchmarkStatusMixin after vanilla GUI extraction. No allocation per draw. */
    public String benchmarkHudText(){
        return benchmark.running() || System.nanoTime() < benchmarkHudUntilNanos ? benchmarkHudText : "";
    }
    /** HUD-Modus: "simple" = nur FPS, "true" = FPS + ms + max, "false" = aus. Alte Einstellung "hud" (true/false) wird übernommen. */
    public String hudMode(){
        String mode=preferences.get("hud.mode","");
        if(mode.equals("simple")||mode.equals("true")||mode.equals("false"))return mode;
        return preferences.enabled("hud",true)?"true":"false";
    }
    public String hudModeLabel(){
        return switch(hudMode()){
            case "simple"->"Simple (nur FPS)";
            case "true"->"true (FPS + ms + max)";
            default->"false (aus)";
        };
    }
    /** Reihenfolge: Simple -> true -> false -> Simple. */
    public void cycleHudMode(){
        preferences.put("hud.mode",switch(hudMode()){case "simple"->"true";case "true"->"false";default->"simple";});
    }
    /** FPS-HUD ein/aus (Linksklick auf "Mods"): beim Einschalten kommt der zuletzt benutzte Modus zurück (Standard: true). */
    public void toggleHud(){
        String mode=hudMode();
        if(mode.equals("false")){
            String last=preferences.get("hud.mode.last","true");
            preferences.put("hud.mode",last.equals("simple")?"simple":"true");
        }else{
            preferences.put("hud.mode.last",mode);
            preferences.put("hud.mode","false");
        }
    }
    /** Farbe des Strichs oben am FPS-HUD (Weiß bei 0 % Helligkeit, gewählte GUI-Farbe bei 100 %). */
    public int hudStripeArgb(){return preferences.enabled("hud.stripe.on",true)?PerformanceScreen.hudStripeArgb(preferences):0;}
    /** Strich oben am FPS-HUD an/aus (Menü "FPS Einstellungen"). */
    public boolean hudStripeOn(){return preferences.enabled("hud.stripe.on",true);}
    public void toggleHudStripe(){preferences.toggle("hud.stripe.on",true);}
    /** Hintergrund des FPS-HUDs an/aus (Menü "FPS Einstellungen"); die Deckkraft bleibt gespeichert. */
    public boolean hudBackgroundOn(){return preferences.enabled("hud.bg.on",true);}
    public void toggleHudBackground(){preferences.toggle("hud.bg.on",true);}
    /** Anzeigeumfang: Einfach (nur FPS) oder Voll (FPS + ms + max). Gilt auch, wenn die Anzeige gerade aus ist (wirkt beim nächsten Einschalten). */
    public boolean hudDetailFull(){
        String mode=hudMode();
        if(mode.equals("false"))mode=preferences.get("hud.mode.last","true");
        return !mode.equals("simple");
    }
    public void toggleHudDetail(){
        String next=hudDetailFull()?"simple":"true";
        if(hudMode().equals("false"))preferences.put("hud.mode.last",next);else preferences.put("hud.mode",next);
    }
    /** Hintergrund des FPS-HUDs; Alpha 0 = nicht zeichnen. */
    public int hudBackgroundArgb(){return hudBackgroundOn()?PerformanceScreen.hudBackgroundArgb(preferences):0;}
    /** Immer sichtbare Mini-HUD-Zeile (FPS / Frametime). Leer, wenn im Menü ausgeschaltet. */
    public String hudText(){
        String mode=hudMode();
        if(mode.equals("false"))return "";
        float fps=graph.averageFps();
        if(fps<=0f)return "";
        if(mode.equals("simple"))return String.format(java.util.Locale.ROOT,"%.0f FPS",fps);
        return String.format(java.util.Locale.ROOT,"%.0f FPS | %.1f ms | max %.1f ms",fps,graph.last(),graph.worst());
    }
    // ------------------------------------------------------------------ CPS (Klicks pro Sekunde), Einstellungen wie bei der FPS-Anzeige
    private final dev.performanceclient.pvp.ClickCounter cpsLeft=new dev.performanceclient.pvp.ClickCounter();
    private final dev.performanceclient.pvp.ClickCounter cpsRight=new dev.performanceclient.pvp.ClickCounter();
    private boolean cpsLeftDown,cpsRightDown,cpsPollFailed;
    /** Pro Frame: zählt Klicks als Flanke "Taste gedrückt" (nur im Spiel ohne offenes Menü). Nichts wird automatisiert. */
    private void pollClicks(){
        if(cpsPollFailed)return;
        try{
            Minecraft mc=Minecraft.getInstance();
            boolean l=false,r=false;
            if(mc.gui.screen()==null&&mc.player!=null){l=mc.options.keyAttack.isDown();r=mc.options.keyUse.isDown();}
            long now=System.nanoTime();
            if(l&&!cpsLeftDown)cpsLeft.click(now);
            if(r&&!cpsRightDown)cpsRight.click(now);
            cpsLeftDown=l;cpsRightDown=r;
        }catch(Throwable t){
            cpsPollFailed=true;
            PerformanceClient.LOGGER.warn("CPS counting disabled: {}",t.toString());
        }
    }
    /** CPS-Anzeige ein/aus (Linksklick auf die Kachel \"CPS\" im Mods-Menü). */
    public boolean cpsOn(){return preferences.enabled("cps.on",true);}
    public void toggleCps(){preferences.toggle("cps.on",true);}
    public boolean cpsStripeOn(){return preferences.enabled("cps.stripe.on",true);}
    public void toggleCpsStripe(){preferences.toggle("cps.stripe.on",true);}
    public boolean cpsBackgroundOn(){return preferences.enabled("cps.bg.on",true);}
    public void toggleCpsBackground(){preferences.toggle("cps.bg.on",true);}
    /** Anzeigeumfang: Einfach (nur Links-Klicks) oder Voll (Links | Rechts). */
    public boolean cpsDetailFull(){return preferences.enabled("cps.detail.full",false);}
    public void toggleCpsDetail(){preferences.toggle("cps.detail.full",false);}
    public int cpsBackgroundArgb(){return cpsBackgroundOn()?PerformanceScreen.cpsBackgroundArgb(preferences):0;}
    public int cpsStripeArgb(){return cpsStripeOn()?PerformanceScreen.cpsStripeArgb(preferences):0;}
    /** CPS-HUD-Zeile; leer, wenn ausgeschaltet. */
    public String cpsText(){
        if(!cpsOn())return "";
        long now=System.nanoTime();
        int left=cpsLeft.cps(now);
        if(!cpsDetailFull())return left+" CPS";
        return left+" | "+cpsRight.cps(now)+" CPS";
    }
    // ------------------------------------------------------------------ Theme der Anzeigen (FPS, CPS, Keystrokes): folgt dem globalen Menü-Design
    /** Das Theme der Anzeigen (FPS, CPS, Keystrokes) folgt dem globalen Menü-Design (Client-Design an = Client-Theme); es gibt keine eigene Umschaltung je Mod mehr. */
    public boolean themeClient(String mod){
        return !PerformanceScreen.globalVanillaDesign();
    }
    // ------------------------------------------------------------------ Keystrokes (WASD, Maus, Leertaste), Einstellungen wie bei FPS/CPS
    public boolean keysOn(){return preferences.enabled("keys.on",true);}
    public void toggleKeys(){preferences.toggle("keys.on",true);}
    public boolean keysStripeOn(){return preferences.enabled("keys.stripe.on",true);}
    public void toggleKeysStripe(){preferences.toggle("keys.stripe.on",true);}
    public boolean keysBackgroundOn(){return preferences.enabled("keys.bg.on",true);}
    public void toggleKeysBackground(){preferences.toggle("keys.bg.on",true);}
    /** Anzeigeumfang: Einfach (nur WASD) oder Voll (WASD + Maustasten + Leertaste). */
    public boolean keysDetailFull(){return preferences.enabled("keys.detail.full",false);}
    public void toggleKeysDetail(){preferences.toggle("keys.detail.full",false);}
    public int keysBackgroundArgb(){return keysBackgroundOn()?PerformanceScreen.modBackgroundArgb(preferences,"keys"):0;}
    public int keysStripeArgb(){return keysStripeOn()?PerformanceScreen.modStripeArgb(preferences,"keys"):0;}
    // ------------------------------------------------------------------ Custom Crosshair (Linksklick auf die Kachel = ein/aus, Rechtsklick = Einstellungen)
    public boolean crosshairOn(){return dev.performanceclient.pvp.CrosshairSettings.on(preferences);}
    public void toggleCrosshair(){preferences.toggle("crosshair.on",true);}
    /** Pro Frame aufgerufen: öffnet das Menü, wenn die registrierte Taste gedrückt wurde. */
    public void pollKeys(){
        pollClicks();
        if(KeyBindings.consumeMenuClick()){
            Screen current=Minecraft.getInstance().gui.screen();
            if(current==null)openClientMenu();
        }
    }
    private void showBenchmarkMessage(String message, int seconds){
        benchmarkHudText = message;
        benchmarkHudUntilNanos = System.nanoTime() + seconds * 1_000_000_000L;
        PerformanceClient.LOGGER.info("Benchmark status: {}", message);
    }
    public void setStageOneMode(StageOneControl.Mode mode){
        if (!benchmark.running()) StageOneControl.INSTANCE.setMode(mode);
    }
    public void worldChanged(){
        if (benchmark.running()) {
            benchmark.cancel();
            PhaseProfiler.INSTANCE.abortRun();
            StageOneControl.INSTANCE.setMode(StageOneControl.Mode.CLIENT);
            showBenchmarkMessage("F9 STOPPED | world changed | no CSV written", 8);
            PerformanceClient.LOGGER.warn("Benchmark aborted: world changed during sample capture");
        }
    }
    public void toggleFeature(Feature feature){
        boolean next=!StageOneControl.INSTANCE.configured(feature);
        StageOneControl.INSTANCE.setConfigured(feature,next);
        preferences.put("stage1."+feature.name().toLowerCase(java.util.Locale.ROOT),String.valueOf(next));
    }
    public String lastSummary(){
        if(lastResult==null)return "No completed benchmark";
        return String.format(java.util.Locale.ROOT,"%.1f FPS / 1%% %.1f / P99 %.2f ms",lastResult.avgFps(),lastResult.onePercentLow(),lastResult.p99Ms());
    }
    /** F9 A/B collection: 60-second wall clock, manual route (still not reproducible). */
    public void toggleBenchmark(){
        if (benchmark.running()) {
            finishBenchmark(false);
            return;
        }
        Minecraft mc=Minecraft.getInstance();
        if (mc.level==null || !mc.isLocalServer()) {
            showBenchmarkMessage("F9 requires singleplayer world", 8);
            return;
        }
        StageOneControl.INSTANCE.setMode(nextBenchMode==BenchmarkService.Mode.VANILLA
            ? StageOneControl.Mode.VANILLA : StageOneControl.Mode.CLIENT);
        try {
            rebuildCallsAtStart=StageOneControl.INSTANCE.hookCalls(Feature.REBUILD);
            entityCallsAtStart=StageOneControl.INSTANCE.hookCalls(Feature.ENTITY);
            rebuildActionsAtStart=StageOneControl.INSTANCE.appliedCalls(Feature.REBUILD);
            entityActionsAtStart=StageOneControl.INSTANCE.appliedCalls(Feature.ENTITY);
            benchmark.begin(nextBenchMode,300);
            PhaseProfiler.INSTANCE.startRun();
            showBenchmarkMessage("F9 " + nextBenchMode + " | 01:00 remaining | warmup 300", 10);
            PerformanceClient.LOGGER.warn("Manual benchmark: world seed and camera route are NOT verified or fixed");
        } catch (IllegalStateException ex) {
            StageOneControl.INSTANCE.setMode(StageOneControl.Mode.CLIENT);
            showBenchmarkMessage("F9 NOT STARTED | " + shortError(ex), 8);
            PerformanceClient.LOGGER.warn("Benchmark start failed",ex);
        }
    }
    private void finishBenchmark(boolean automatic){
        if (!benchmark.running()) return;
        BenchmarkService.Mode finishedMode = benchmark.mode();
        try {
            lastResult=benchmark.end(automatic);
            PhaseProfiler.Snapshot profile = PhaseProfiler.INSTANCE.finishRun();
            Path phaseFile = PhaseCsvWriter.append(benchmark.file(), finishedMode, benchmark.lastRunNumber(), profile);
            PerformanceClient.LOGGER.info("CPU-phase measurements saved to {} ({} measured frames)", phaseFile, profile.frames());
                nextBenchMode=(nextBenchMode==BenchmarkService.Mode.VANILLA)
                    ?BenchmarkService.Mode.CLIENT:BenchmarkService.Mode.VANILLA;
                long rebuildCalls=StageOneControl.INSTANCE.hookCalls(Feature.REBUILD)-rebuildCallsAtStart;
                long entityCalls=StageOneControl.INSTANCE.hookCalls(Feature.ENTITY)-entityCallsAtStart;
                long rebuildActions=StageOneControl.INSTANCE.appliedCalls(Feature.REBUILD)-rebuildActionsAtStart;
                long entityActions=StageOneControl.INSTANCE.appliedCalls(Feature.ENTITY)-entityActionsAtStart;
                showBenchmarkMessage(String.format(java.util.Locale.ROOT,
                    "F9 SAVED %s%s %.1f FPS | R=%d E=%d",
                    finishedMode, automatic ? " AUTO 60s" : "", lastResult.avgFps(), rebuildActions, entityActions), 10);
                PerformanceClient.LOGGER.info("Benchmark saved to {}: {}", benchmark.file(),lastSummary());
                PerformanceClient.LOGGER.info("Stage-1 diagnostics [{}] REBUILD calls={} actions={}; ENTITY calls={} actions={}",
                    finishedMode,rebuildCalls,rebuildActions,entityCalls,entityActions);
                if (finishedMode == BenchmarkService.Mode.CLIENT && rebuildActions==0 && entityActions==0)
                    PerformanceClient.LOGGER.warn("CLIENT run caused ZERO stage-1 actions. Similar FPS is expected; no improvement is demonstrated.");
                if (rebuildCalls==0 || entityCalls==0)
                    PerformanceClient.LOGGER.warn("Stage-1 hooks not both observed; check whether Mixins were applied.");
            } catch(IOException | IllegalStateException ex) {
                PhaseProfiler.INSTANCE.abortRun();
                showBenchmarkMessage("F9 FAILED | " + shortError(ex), 10);
                PerformanceClient.LOGGER.warn("Benchmark cancelled/failed",ex);
            } finally {
                StageOneControl.INSTANCE.setMode(StageOneControl.Mode.CLIENT);
            }
    }
    private static String shortError(Exception ex){
        String message=ex.getMessage();
        return message==null ? ex.getClass().getSimpleName() : message;
    }
    /** R-Shift-Menü (Mods / Einstellungen). "Einstellungen" öffnet das bisherige Client-Menü (openMenu). */
    public void openClientMenu(){
        Minecraft mc=Minecraft.getInstance();
        Screen previous=mc.gui.screen();
        if(previous instanceof dev.performanceclient.gui.GlassClientMenuScreen){previous.onClose();return;}   // erneutes Drücken (F8) schließt das Menü
        mc.gui.setScreen(new dev.performanceclient.gui.GlassClientMenuScreen(previous));
    }
    public void openMenu(){
        Minecraft mc=Minecraft.getInstance();
        Screen previous=mc.gui.screen();
        mc.gui.setScreen(new PerformanceScreen(previous));
    }
    public void chooseProfile(String name){
        preferences.put("profile",name);profiles.select(name);
        profiles.reload(PlatformInfo.detect(),GpuIdentity.from(adapter.device()));
    }
}
