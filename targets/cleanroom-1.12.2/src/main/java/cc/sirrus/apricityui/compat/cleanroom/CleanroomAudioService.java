package cc.sirrus.apricityui.compat.cleanroom;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.media.DecodedAudio;
import com.sighs.apricityui.spi.AuiAudioService;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

import java.nio.ByteBuffer;
import java.util.function.DoubleSupplier;

/**
 * Cleanroom 1.12 audio bridge. The loader exposes the LWJGL2 OpenAL ABI
 * (the runtime remaps the {@code org.lwjgl} package), so it must share the
 * already-created Minecraft context instead of using the LWJGL3 ALC handles
 * used by newer targets.
 */
public final class CleanroomAudioService implements AuiAudioService {
    private final DoubleSupplier masterVolume;
    private volatile boolean unavailableLogged;

    private CleanroomAudioService(DoubleSupplier masterVolume) {
        this.masterVolume = masterVolume == null ? () -> 1.0 : masterVolume;
    }

    public static CleanroomAudioService create(DoubleSupplier masterVolume) {
        return new CleanroomAudioService(masterVolume);
    }

    @Override public float masterVolume() {
        try { return (float) Math.max(0.0, Math.min(1.0, masterVolume.getAsDouble())); }
        catch (Throwable ignored) { return 1.0f; }
    }

    @Override public synchronized AudioBufferHandle createBuffer(DecodedAudio audio) {
        if (audio == null || !available()) return null;
        try {
            int format = audio.channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
            ByteBuffer pcm = BufferUtils.createByteBuffer(audio.pcm.length);
            pcm.put(audio.pcm).flip();
            int id = AL10.alGenBuffers();
            AL10.alBufferData(id, format, pcm, audio.sampleRate);
            if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                AL10.alDeleteBuffers(id);
                return null;
            }
            return new Buffer(id, audio.durationSeconds);
        } catch (Throwable error) {
            ApricityUI.LOGGER.warn("[AUI Audio] Cleanroom OpenAL buffer creation failed", error);
            return null;
        }
    }

    @Override public synchronized AudioChannel openChannel(AudioBufferHandle handle) {
        if (!(handle instanceof Buffer buffer) || buffer.destroyed || !available()) return null;
        try {
            int source = AL10.alGenSources();
            AL10.alSourcei(source, AL10.AL_BUFFER, buffer.id);
            if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                AL10.alDeleteSources(source);
                return null;
            }
            return new Channel(source, buffer);
        } catch (Throwable error) {
            ApricityUI.LOGGER.warn("[AUI Audio] Cleanroom OpenAL source creation failed", error);
            return null;
        }
    }

    private boolean available() {
        boolean available;
        try { available = AL.isCreated(); }
        catch (Throwable ignored) { available = false; }
        if (!available && !unavailableLogged) {
            unavailableLogged = true;
            ApricityUI.LOGGER.warn("[AUI Audio] Minecraft OpenAL context is unavailable; audio disabled");
        }
        return available;
    }

    private static final class Buffer implements AudioBufferHandle {
        final int id;
        final double duration;
        boolean destroyed;
        Buffer(int id, double duration) { this.id = id; this.duration = duration; }
        @Override public double durationSeconds() { return duration; }
        @Override public synchronized void destroy() {
            if (destroyed) return;
            destroyed = true;
            try { AL10.alDeleteBuffers(id); } catch (Throwable ignored) { }
        }
    }

    private static final class Channel implements AudioChannel {
        final int source;
        final Buffer buffer;
        boolean destroyed;
        Channel(int source, Buffer buffer) { this.source = source; this.buffer = buffer; }
        @Override public synchronized void play() { if (!destroyed) AL10.alSourcePlay(source); }
        @Override public synchronized void pause() { if (!destroyed) AL10.alSourcePause(source); }
        @Override public synchronized void stop() {
            if (destroyed) return;
            AL10.alSourceStop(source);
            AL10.alSourcei(source, AL11.AL_SAMPLE_OFFSET, 0);
        }
        @Override public synchronized void seekSeconds(double seconds) {
            if (destroyed) return;
            double clamped = Math.max(0, Math.min(seconds, buffer.duration));
            int frequency = AL10.alGetBufferi(buffer.id, AL10.AL_FREQUENCY);
            AL10.alSourcei(source, AL11.AL_SAMPLE_OFFSET, (int) (clamped * Math.max(1, frequency)));
        }
        @Override public synchronized double positionSeconds() {
            if (destroyed) return 0;
            int frequency = AL10.alGetBufferi(buffer.id, AL10.AL_FREQUENCY);
            double position = AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET) / (double) Math.max(1, frequency);
            return Math.max(0, Math.min(position, buffer.duration));
        }
        @Override public synchronized void setVolume(float volume) {
            if (!destroyed) AL10.alSourcef(source, AL10.AL_GAIN, Math.max(0, Math.min(1, volume)));
        }
        @Override public synchronized boolean isPlaying() {
            return !destroyed && AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING;
        }
        @Override public synchronized void destroy() {
            if (destroyed) return;
            destroyed = true;
            try {
                AL10.alSourceStop(source);
                AL10.alSourcei(source, AL10.AL_BUFFER, 0);
                AL10.alDeleteSources(source);
            } catch (Throwable ignored) { }
        }
    }
}
