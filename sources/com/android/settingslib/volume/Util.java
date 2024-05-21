package com.android.settingslib.volume;

import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.telephony.TelephonyManager;
import android.widget.TextView;
import java.util.Objects;
/* loaded from: classes3.dex */
public class Util {
    private static final int[] AUDIO_MANAGER_FLAGS = {1, 16, 4, 2, 8, 2048, 128, 4096, 1024};
    private static final String[] AUDIO_MANAGER_FLAG_NAMES = {"SHOW_UI", "VIBRATE", "PLAY_SOUND", "ALLOW_RINGER_MODES", "REMOVE_SOUND_AND_VIBRATE", "SHOW_VIBRATE_HINT", "SHOW_SILENT_HINT", "FROM_KEY", "SHOW_UI_WARNINGS"};

    public static String logTag(Class<?> c) {
        String tag = "vol." + c.getSimpleName();
        return tag.length() < 23 ? tag : tag.substring(0, 23);
    }

    public static String mediaMetadataToString(MediaMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        return metadata.getDescription().toString();
    }

    public static String playbackInfoToString(MediaController.PlaybackInfo info) {
        if (info == null) {
            return null;
        }
        String type = playbackInfoTypeToString(info.getPlaybackType());
        String vc = volumeProviderControlToString(info.getVolumeControl());
        return String.format("PlaybackInfo[vol=%s,max=%s,type=%s,vc=%s],atts=%s", Integer.valueOf(info.getCurrentVolume()), Integer.valueOf(info.getMaxVolume()), type, vc, info.getAudioAttributes());
    }

    public static String playbackInfoTypeToString(int type) {
        if (type != 1) {
            if (type == 2) {
                return "REMOTE";
            }
            return "UNKNOWN_" + type;
        }
        return "LOCAL";
    }

    public static String playbackStateStateToString(int state) {
        if (state != 0) {
            if (state != 1) {
                if (state != 2) {
                    if (state == 3) {
                        return "STATE_PLAYING";
                    }
                    return "UNKNOWN_" + state;
                }
                return "STATE_PAUSED";
            }
            return "STATE_STOPPED";
        }
        return "STATE_NONE";
    }

    public static String volumeProviderControlToString(int control) {
        if (control != 0) {
            if (control != 1) {
                if (control == 2) {
                    return "VOLUME_CONTROL_ABSOLUTE";
                }
                return "VOLUME_CONTROL_UNKNOWN_" + control;
            }
            return "VOLUME_CONTROL_RELATIVE";
        }
        return "VOLUME_CONTROL_FIXED";
    }

    public static String playbackStateToString(PlaybackState playbackState) {
        if (playbackState == null) {
            return null;
        }
        return playbackStateStateToString(playbackState.getState()) + " " + playbackState;
    }

    public static String audioManagerFlagsToString(int value) {
        return bitFieldToString(value, AUDIO_MANAGER_FLAGS, AUDIO_MANAGER_FLAG_NAMES);
    }

    protected static String bitFieldToString(int value, int[] values, String[] names) {
        if (value == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if ((values[i] & value) != 0) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(names[i]);
            }
            value &= ~values[i];
        }
        if (value != 0) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append("UNKNOWN_");
            sb.append(value);
        }
        return sb.toString();
    }

    private static CharSequence emptyToNull(CharSequence str) {
        if (str == null || str.length() == 0) {
            return null;
        }
        return str;
    }

    public static boolean setText(TextView tv, CharSequence text) {
        if (Objects.equals(emptyToNull(tv.getText()), emptyToNull(text))) {
            return false;
        }
        tv.setText(text);
        return true;
    }

    public static boolean isVoiceCapable(Context context) {
        TelephonyManager telephony = (TelephonyManager) context.getSystemService("phone");
        return telephony != null && telephony.isVoiceCapable();
    }
}
