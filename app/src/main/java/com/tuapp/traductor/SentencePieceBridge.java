package com.tuapp.traductor;

final class SentencePieceBridge {
    static { System.loadLibrary("sentencepiece_jni"); }
    static native long load(String path);
    static native String[] encode(long handle, String text);
    static native String decode(long handle, String[] tokens);
    static native void release(long handle);
    private SentencePieceBridge() {}
}


