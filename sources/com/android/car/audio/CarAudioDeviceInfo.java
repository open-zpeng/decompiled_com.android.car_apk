package com.android.car.audio;

import android.media.AudioDeviceInfo;
import android.media.AudioDevicePort;
import android.media.AudioGain;
import android.media.AudioGainConfig;
import android.media.AudioManager;
import android.util.Slog;
import com.android.car.CarLog;
import com.android.internal.util.Preconditions;
import java.io.PrintWriter;
/* loaded from: classes3.dex */
class CarAudioDeviceInfo {
    private final AudioDeviceInfo mAudioDeviceInfo;
    private final int mBusNumber;
    private final int mChannelCount;
    private int mCurrentGain;
    private final int mDefaultGain;
    private final int mEncodingFormat;
    private final int mMaxGain;
    private final int mMinGain;
    private final int mSampleRate;

    /* JADX INFO: Access modifiers changed from: package-private */
    public static int parseDeviceAddress(String address) {
        String[] words = address.split("_");
        int addressParsed = -1;
        if (words[0].toLowerCase().startsWith("bus")) {
            try {
                addressParsed = Integer.parseInt(words[0].substring(3));
            } catch (NumberFormatException e) {
            }
        }
        if (addressParsed < 0) {
            return -1;
        }
        return addressParsed;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public CarAudioDeviceInfo(AudioDeviceInfo audioDeviceInfo) {
        this.mAudioDeviceInfo = audioDeviceInfo;
        this.mBusNumber = parseDeviceAddress(audioDeviceInfo.getAddress());
        this.mSampleRate = getMaxSampleRate(audioDeviceInfo);
        this.mEncodingFormat = getEncodingFormat(audioDeviceInfo);
        this.mChannelCount = getMaxChannels(audioDeviceInfo);
        AudioGain audioGain = getAudioGain();
        AudioGain audioGain2 = (AudioGain) Preconditions.checkNotNull(audioGain, "No audio gain on device port " + audioDeviceInfo);
        this.mDefaultGain = audioGain2.defaultValue();
        this.mMaxGain = audioGain2.maxValue();
        this.mMinGain = audioGain2.minValue();
        this.mCurrentGain = -1;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public AudioDeviceInfo getAudioDeviceInfo() {
        return this.mAudioDeviceInfo;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public AudioDevicePort getAudioDevicePort() {
        return this.mAudioDeviceInfo.getPort();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int getBusNumber() {
        return this.mBusNumber;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int getDefaultGain() {
        return this.mDefaultGain;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int getMaxGain() {
        return this.mMaxGain;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int getMinGain() {
        return this.mMinGain;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int getSampleRate() {
        return this.mSampleRate;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int getEncodingFormat() {
        return this.mEncodingFormat;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int getChannelCount() {
        return this.mChannelCount;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void setCurrentGain(int gainInMillibels) {
        if (gainInMillibels < this.mMinGain) {
            gainInMillibels = this.mMinGain;
        } else if (gainInMillibels > this.mMaxGain) {
            gainInMillibels = this.mMaxGain;
        }
        AudioGain audioGain = getAudioGain();
        if (audioGain == null) {
            Slog.e(CarLog.TAG_AUDIO, "getAudioGain() returned null.");
            return;
        }
        AudioGainConfig audioGainConfig = audioGain.buildConfig(1, audioGain.channelMask(), new int[]{gainInMillibels}, 0);
        if (audioGainConfig == null) {
            Slog.e(CarLog.TAG_AUDIO, "Failed to construct AudioGainConfig");
            return;
        }
        int r = AudioManager.setAudioPortGain(getAudioDevicePort(), audioGainConfig);
        if (r == 0) {
            this.mCurrentGain = gainInMillibels;
            return;
        }
        Slog.e(CarLog.TAG_AUDIO, "Failed to setAudioPortGain: " + r);
    }

    private int getMaxSampleRate(AudioDeviceInfo info) {
        int[] sampleRates = info.getSampleRates();
        if (sampleRates == null || sampleRates.length == 0) {
            return 48000;
        }
        int sampleRate = sampleRates[0];
        for (int i = 1; i < sampleRates.length; i++) {
            if (sampleRates[i] > sampleRate) {
                sampleRate = sampleRates[i];
            }
        }
        return sampleRate;
    }

    private int getEncodingFormat(AudioDeviceInfo info) {
        return 2;
    }

    private int getMaxChannels(AudioDeviceInfo info) {
        int numChannels = 1;
        int[] channelMasks = info.getChannelMasks();
        if (channelMasks == null) {
            return 1;
        }
        for (int channelMask : channelMasks) {
            int currentNumChannels = Integer.bitCount(channelMask);
            if (currentNumChannels > numChannels) {
                numChannels = currentNumChannels;
            }
        }
        return numChannels;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public AudioGain getAudioGain() {
        AudioGain[] gains;
        AudioDevicePort audioPort = getAudioDevicePort();
        if (audioPort != null && audioPort.gains().length > 0) {
            for (AudioGain audioGain : audioPort.gains()) {
                if ((audioGain.mode() & 1) != 0) {
                    return checkAudioGainConfiguration(audioGain);
                }
            }
            return null;
        }
        return null;
    }

    private AudioGain checkAudioGainConfiguration(AudioGain audioGain) {
        Preconditions.checkArgument(audioGain.maxValue() >= audioGain.minValue());
        Preconditions.checkArgument(audioGain.defaultValue() >= audioGain.minValue() && audioGain.defaultValue() <= audioGain.maxValue());
        Preconditions.checkArgument((audioGain.maxValue() - audioGain.minValue()) % audioGain.stepValue() == 0);
        Preconditions.checkArgument((audioGain.defaultValue() - audioGain.minValue()) % audioGain.stepValue() == 0);
        return audioGain;
    }

    public String toString() {
        return "bus number: " + this.mBusNumber + " address: " + this.mAudioDeviceInfo.getAddress() + " sampleRate: " + getSampleRate() + " encodingFormat: " + getEncodingFormat() + " channelCount: " + getChannelCount() + " currentGain: " + this.mCurrentGain + " maxGain: " + this.mMaxGain + " minGain: " + this.mMinGain;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void dump(String indent, PrintWriter writer) {
        writer.printf("%sCarAudioDeviceInfo Bus(%d: %s)\n ", indent, Integer.valueOf(this.mBusNumber), this.mAudioDeviceInfo.getAddress());
        writer.printf("%s\tsample rate / encoding format / channel count: %d %d %d\n", indent, Integer.valueOf(getSampleRate()), Integer.valueOf(getEncodingFormat()), Integer.valueOf(getChannelCount()));
        writer.printf("%s\tGain values (min / max / default/ current): %d %d %d %d\n", indent, Integer.valueOf(this.mMinGain), Integer.valueOf(this.mMaxGain), Integer.valueOf(this.mDefaultGain), Integer.valueOf(this.mCurrentGain));
    }
}
