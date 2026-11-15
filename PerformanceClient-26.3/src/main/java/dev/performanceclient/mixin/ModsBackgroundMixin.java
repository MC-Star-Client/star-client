package dev.performanceclient.mixin;

import dev.performanceclient.gui.GlassModsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Unterdrückt Vanillas Hintergrund (inkl. Blur) des inneren Mod-Menu-Screens, während GlassModsScreen ihn ins Glas-Panel zeichnet.
 * Sonst würde der Hintergrund das Panel überdecken und Minecraft meldet "Can only blur once per frame". Greift nur für genau diesen
 * Screen (GlassModsScreen#suppressBackground); alle anderen Screens bleiben unverändert. Die Zielklasse von Mod Menu als String
 * (nur falls ModsScreen den Hintergrund selbst überschreibt). Passt kein Methodenname, passiert nichts (require = 0).
 */
@Mixin(targets = {
    "net.minecraft.client.gui.screens.Screen",
    "com.terraformersmc.modmenu.gui.ModsScreen"
})
public abstract class ModsBackgroundMixin {
    @Inject(method = "/^(extract|render)(Background|BlurredBackground|TransparentBackground|MenuBackground|Panorama)$/",
        at = @At("HEAD"), cancellable = true, require = 0)
    private void performanceclient$noInnerBackground(CallbackInfo ci) {
        if (GlassModsScreen.suppressBackground((Object) this)) ci.cancel();
    }
}
