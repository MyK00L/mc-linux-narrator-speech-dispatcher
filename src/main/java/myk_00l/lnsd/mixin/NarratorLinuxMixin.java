package myk_00l.lnsd.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
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
   
   @Shadow
   private AtomicInteger executionBatch;
   
   @Shadow
   private ExecutorService executor;
   
   // Our own executor service in case the original one fails to initialize
   private ExecutorService backupExecutor;
   
   // Speech-dispatcher process reference for interruption
   private volatile Process currentSpeechProcess = null;
   
   private static final String MOD_ID = "linux-narrator-speech-dispatcher";
   private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

   /**
    * Call speech-dispatcher to speak the text
    */
   private void callSpeechDispatcher(String text, float volume) {
      try {
         // Build the spd-say command
         List<String> command = new ArrayList<>();
         command.add("spd-say");
         
         // Wait for completion to avoid overlapping speech
         command.add("-w");
         
         // Set a unique application name to avoid conflicts with screen readers
         command.add("-N");
         command.add("minecraft-narrator");
         
         // Set priority to ensure it gets heard
         command.add("-P");
         command.add("message");
         
         // Set volume (speech-dispatcher uses -100 to 100, we convert from 0.0-1.0)
         if (volume != 1.0f) {
            int spdVolume = Math.round((volume * 200) - 100); // Convert 0.0-1.0 to -100 to 100
            spdVolume = Math.max(-100, Math.min(100, spdVolume)); // Clamp to valid range
            command.add("-i");
            command.add(String.valueOf(spdVolume));
         }
         
         // Add the text to speak (escape any special characters)
         command.add(text);
         
         // LOGGER.debug("Speaking with speech-dispatcher: '{}'", text);
         
         // Execute the command
         ProcessBuilder pb = new ProcessBuilder(command);
         pb.redirectErrorStream(true); // Redirect stderr to stdout
         
         currentSpeechProcess = pb.start();
         
         // Wait for the process to complete
         int exitCode = currentSpeechProcess.waitFor();
         if (exitCode != 0) {
            LOGGER.warn("spd-say exited with code: {} for text: '{}'", exitCode, text);
         }
         
      } catch (IOException e) {
         LOGGER.error("Failed to execute spd-say command. Is speech-dispatcher installed?", e);
         LOGGER.info("Fallback text: " + text); // Fallback to logging
      } catch (InterruptedException e) {
         LOGGER.debug("Speech interrupted for text: '{}'", text);
         // Thread.currentThread().interrupt();
      } catch (Exception e) {
         LOGGER.error("Unexpected error in speech-dispatcher for text: '{}'", text, e);
      } finally {
         currentSpeechProcess = null;
      }
   }

   /**
    * Stop current speech output
    */
   private void stopCurrentSpeech() {
      Process process = currentSpeechProcess;
      if (process != null && process.isAlive()) {
         process.destroyForcibly();
         // LOGGER.debug("Stopped current speech process");
      }
      
      // Also try to cancel all messages from our application
      try {
         ProcessBuilder pb = new ProcessBuilder("spd-say", "-C", "-N", "minecraft-narrator");
         Process stopProcess = pb.start();
         stopProcess.waitFor();
         // LOGGER.debug("Cancelled all minecraft-narrator messages");
      } catch (Exception e) {
         // LOGGER.debug("Could not cancel speech-dispatcher messages: {}", e.getMessage());
      }
   }

   /**
    * @reason Skip flite library loading to prevent crashes when flite is not installed
    * @author MyK_00L
    */
   @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/text2speech/NarratorLinux$FliteLibrary;loadNative()V"), remap = false)
   private void skipFliteLibraryLoad() {
      // Do nothing - skip flite library loading
      LOGGER.info("Skipped flite library loading");
   }

   /**
    * @reason Skip flite voice library loading to prevent crashes when flite is not installed
    * @author MyK_00L
    */
   @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/text2speech/NarratorLinux$FliteLibrary$CmuUsKal16;loadNative()V"), remap = false)
   private void skipFliteVoiceLibraryLoad() {
      // Do nothing - skip flite voice library loading
      LOGGER.info("Skipped flite voice library loading");
   }

   /**
    * @reason Skip flite initialization to prevent crashes when flite is not installed
    * @author MyK_00L
    */
   @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/text2speech/NarratorLinux$FliteLibrary;flite_init()I"), remap = false)
   private int skipFliteInit() {
      // Return success code (0) without actually initializing flite
      LOGGER.info("Skipped flite initialization");
      return 0;
   }

   /**
    * @reason Skip voice registration to prevent crashes when flite is not installed
    * @author MyK_00L
    */
   @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/text2speech/NarratorLinux$FliteLibrary$CmuUsKal16;register_cmu_us_kal16(Ljava/lang/String;)Lcom/sun/jna/Pointer;"), remap = false)
   private Pointer skipVoiceRegistration(String param) {
      // Return a non-null dummy pointer to avoid initialization failure
      LOGGER.info("Skipped voice registration");
      return new Pointer(1L);
   }

   /**
    * @reason Log successful initialization without flite
    * @author MyK_00L
    */
   @Inject(method = "<init>", at = @At("RETURN"), remap = false)
   private void onConstructorSuccess(CallbackInfo ci) {
      LOGGER.info("NarratorLinux initialized successfully without flite dependencies");
   }

   /**
    * @reason Replace flite TTS with speech-dispatcher
    * @author MyK_00L
    */
   @Overwrite(remap = false)
   public void say(String msg, boolean interrupt, float volume) {
      LOGGER.info("Narrator say() called with: '{}', interrupt: {}, volume: {}, stack trace:\n{}", msg, interrupt, volume, Thread.currentThread().getStackTrace());
      
      // Ensure we have a working executor service
      final ExecutorService workingExecutor;
      if (this.executor != null) {
         workingExecutor = this.executor;
      } else {
         if (backupExecutor == null) {
            backupExecutor = Executors.newSingleThreadExecutor();
         }
         workingExecutor = backupExecutor;
      }
      
      // Ensure we have a working execution batch counter
      final AtomicInteger workingBatch;
      if (this.executionBatch != null) {
         workingBatch = this.executionBatch;
      } else {
         workingBatch = new AtomicInteger();
      }
      
      if (interrupt) {
         this.clear();
      }
      
      final int thisBatch = workingBatch.get();
      
      // Alternative approach: speak the entire message at once instead of splitting
      // This might work better with screen readers
      if (!msg.trim().isEmpty()) {
         // LOGGER.info("Speaking entire message: '{}'", msg.trim());
         workingExecutor.submit(() -> {
            if (thisBatch >= workingBatch.get()) {
               callSpeechDispatcher(msg.trim(), volume);
            }
         });
      }
      
      /* Original approach - split into sentences (comment out if the above works better)
      Arrays.stream(msg.split("[,.:;/\"()\\[\\]{}!?\\\\]+"))
          .filter(x -> !x.isBlank())
          .forEach(unit -> {
             LOGGER.info("Speaking unit: '{}'", unit.trim());
             workingExecutor.submit(() -> {
                if (thisBatch >= workingBatch.get()) {
                   // Use speech-dispatcher instead of flite
                   callSpeechDispatcher(unit.trim(), volume);
                }
             });
          });
      */
   }
   
   /**
    * @reason Handle clear operation safely even if original fields are null
    * @author MyK_00L
    */
   @Overwrite(remap = false)
   public void clear() {
      // Stop current speech when clearing
      stopCurrentSpeech();
      
      if (this.executionBatch != null) {
         this.executionBatch.incrementAndGet();
      }
   }
   
   /**
    * @reason Handle destroy operation safely
    * @author MyK_00L
    */
   @Overwrite(remap = false)
   public void destroy() {
      // Stop any ongoing speech
      stopCurrentSpeech();
      
      if (this.executor != null) {
         this.executor.shutdownNow();
      }
      if (this.backupExecutor != null) {
         this.backupExecutor.shutdownNow();
      }
   }

}

