package myk_00l.lnsd.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import com.mojang.text2speech.NarratorLinux;
import com.sun.jna.Pointer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageManager;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

@Mixin(value = NarratorLinux.class, remap = false)
public abstract class NarratorLinuxMixin {
	@Mutable @Shadow @Final
	private AtomicInteger executionBatch;
	@Mutable @Shadow @Final
	private ExecutorService executor;

	// private static final String MOD_ID = "linux-narrator-speech-dispatcher";
	// private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** overwrite function @reason because @author me */
	@Overwrite(remap = false)
	public void say(String msg, boolean interrupt, float volume) {
		// unnecessary as speech-dispatcher automatically cancels on new dialogue
		//if(interrupt) {
		// TODO: add -S flag
		//}
		String lang = Minecraft.getInstance().getLanguageManager().getSelected().replace('_','-');
		String vol = String.valueOf(Math.round((volume * 200) - 100));
		ProcessBuilder pb = new ProcessBuilder("spd-say", "-N", "minecraft-narrator", "-i", vol, "-l", lang, msg);
		try {
			pb.start();
		} catch (Exception e) {
			// LOGGER.error("Failed to execute spd-say command. Is speech-dispatcher installed?", e);
		}
	}
	/** overwrite function @reason because @author me */
	@Overwrite(remap = false)
	public void clear() {
		ProcessBuilder pb = new ProcessBuilder("spd-say", "-N", "minecraft-narrator", "-C");
		try {
			pb.start();
		} catch (Exception e) {}
	}
	/** overwrite function @reason because @author me */
	@Overwrite(remap = false)
	public void destroy() {
		clear();
	}

	// disable loading native libs
	@Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/text2speech/NarratorLinux$FliteLibrary;loadNative()V"), remap = false)
	private void skipFliteLibraryLoad() {
	}
	@Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/text2speech/NarratorLinux$FliteLibrary$CmuUsKal16;loadNative()V"), remap = false)
	private void skipFliteVoiceLibraryLoad() {
	}
	@Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/text2speech/NarratorLinux$FliteLibrary;flite_init()I"), remap = false)
	private int skipFliteInit() {
		return 0;
	}
	@Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/text2speech/NarratorLinux$FliteLibrary$CmuUsKal16;register_cmu_us_kal16(Ljava/lang/String;)Lcom/sun/jna/Pointer;"), remap = false)
	private Pointer skipVoiceRegistration(String param) {
		return new Pointer(1L);
	}
	@Inject(method = "<init>", at = @At("RETURN"), remap = false)
	private void onConstructorSuccess(CallbackInfo ci) {
		this.executor.shutdownNow();
		this.executor = null;
		this.executionBatch = null;
		// LOGGER.info("NarratorLinux initialized successfully without flite dependencies");
	}
}

