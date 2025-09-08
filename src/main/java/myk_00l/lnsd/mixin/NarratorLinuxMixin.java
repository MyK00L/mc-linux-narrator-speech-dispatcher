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

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import com.mojang.text2speech.NarratorLinux;
import com.mojang.text2speech.Narrator;
import com.sun.jna.Pointer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mixin(value = NarratorLinux.class, remap = false)
public abstract class NarratorLinuxMixin {
	@Mutable @Shadow @Final
	private AtomicInteger executionBatch;
	@Mutable @Shadow @Final
	private ExecutorService executor;

	// Speech-dispatcher process reference for interruption
	private Process currentSpeechProcess = null;

	private static final String MOD_ID = "linux-narrator-speech-dispatcher";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private void stopCurrentSpeech() {
		if(currentSpeechProcess!=null && currentSpeechProcess.isAlive()) {
			currentSpeechProcess = null;
			List<String> command = new ArrayList<>();
			command.add("spd-say");
			command.add("-N");
			command.add("minecraft-narrator");
			command.add("-C");
			try {
				ProcessBuilder pb = new ProcessBuilder(command);
				pb.start();
			} catch (Exception e) {
				LOGGER.warn("exception: {}", e); 
			}
		}
	}

	/** overwrite function @reason because @author me */
	@Overwrite(remap = false)
	public void say(String msg, boolean interrupt, float volume) {
		if (interrupt) {
			this.clear();
		}
		try {
			List<String> command = new ArrayList<>();
			command.add("spd-say");
			command.add("-N");
			command.add("minecraft-narrator");
			command.add("-w");
			int spdVolume = Math.round((volume * 200) - 100);
			command.add("-i");
			command.add(String.valueOf(spdVolume));
			command.add(msg);
			ProcessBuilder pb = new ProcessBuilder(command);
			currentSpeechProcess = pb.start();
		} catch (Exception e) {
			LOGGER.error("Failed to execute spd-say command. Is speech-dispatcher installed?", e);
		}
	}
	/** overwrite function @reason because @author me */
	@Overwrite(remap = false)
	public void clear() {
		stopCurrentSpeech();
	}
	/** overwrite function @reason because @author me */
	@Overwrite(remap = false)
	public void destroy() {
		stopCurrentSpeech();
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
		LOGGER.info("NarratorLinux initialized successfully without flite dependencies");
	}
}

