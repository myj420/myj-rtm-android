package com.livedata.audioConvert;

public class AudioConvert {
    public static native byte[] convertAmrwbToWav(byte[] amrSrc, int[] status, int[] wavsize);

    static {
        System.loadLibrary("audio-convert");
    }
}

