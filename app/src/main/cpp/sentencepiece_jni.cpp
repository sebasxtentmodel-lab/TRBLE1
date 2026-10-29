#include <jni.h>
#include <string>
#include <vector>
#include "sentencepiece_processor.h"

using sentencepiece::SentencePieceProcessor;
static std::string toUtf(JNIEnv* env, jstring js) {
    if (!js) return "";
    const char* ptr = env->GetStringUTFChars(js, nullptr);
    std::string out = ptr ? ptr : "";
    if (ptr) env->ReleaseStringUTFChars(js, ptr);
    return out;
}
static void throwJava(JNIEnv* env, const std::string& msg) {
    jclass klass = env->FindClass("java/lang/IllegalStateException");
    if (klass) env->ThrowNew(klass, msg.c_str());
}
extern "C" JNIEXPORT jlong JNICALL
Java_com_tuapp_traductor_SentencePieceBridge_load(JNIEnv* env, jclass, jstring path) {
    auto* processor = new SentencePieceProcessor();
    auto status = processor->Load(toUtf(env, path));
    if (!status.ok()) {
        std::string msg = status.ToString();
        delete processor;
        throwJava(env, msg);
        return 0;
    }
    return reinterpret_cast<jlong>(processor);
}
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_tuapp_traductor_SentencePieceBridge_encode(JNIEnv* env, jclass, jlong handle, jstring source) {
    auto* processor = reinterpret_cast<SentencePieceProcessor*>(handle);
    if (!processor) { throwJava(env,"SentencePiece no preparado"); return nullptr; }
    std::vector<std::string> pieces;
    auto status = processor->Encode(toUtf(env, source), &pieces);
    if (!status.ok()) { throwJava(env,status.ToString()); return nullptr; }
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(pieces.size()), stringClass, nullptr);
    for (jsize i=0;i<static_cast<jsize>(pieces.size());i++) {
        jstring s = env->NewStringUTF(pieces[i].c_str());
        env->SetObjectArrayElement(result, i, s);
        env->DeleteLocalRef(s);
    }
    return result;
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_tuapp_traductor_SentencePieceBridge_decode(JNIEnv* env, jclass, jlong handle, jobjectArray tokens) {
    auto* processor = reinterpret_cast<SentencePieceProcessor*>(handle);
    if (!processor) { throwJava(env,"SentencePiece no preparado"); return nullptr; }
    std::vector<std::string> pieces;
    jsize count = env->GetArrayLength(tokens);
    for (jsize i=0;i<count;i++) {
        jstring s = static_cast<jstring>(env->GetObjectArrayElement(tokens, i));
        pieces.push_back(toUtf(env, s));
        env->DeleteLocalRef(s);
    }
    std::string output;
    auto status = processor->Decode(pieces, &output);
    if (!status.ok()) { throwJava(env,status.ToString()); return nullptr; }
    return env->NewStringUTF(output.c_str());
}
extern "C" JNIEXPORT void JNICALL
Java_com_tuapp_traductor_SentencePieceBridge_release(JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<SentencePieceProcessor*>(handle);
}


