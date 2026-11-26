// JNI bridge between Kotlin and whisper.cpp.
// Returns token-level results as a compact little-endian byte buffer so that
// multi-byte UTF-8 characters (ê, ô, ë ...) split across tokens survive intact.

#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

#include "whisper.h"

#define TAG "MetingsWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

static std::atomic<bool> g_abort(false);
static std::atomic<int> g_progress(0);

static bool abort_cb(void *) { return g_abort.load(); }

static void progress_cb(struct whisper_context *, struct whisper_state *, int progress, void *) {
    g_progress.store(progress);
}

static void put_i32(std::vector<uint8_t> &buf, int32_t v) {
    uint8_t b[4];
    std::memcpy(b, &v, 4);
    buf.insert(buf.end(), b, b + 4);
}

static void put_f32(std::vector<uint8_t> &buf, float v) {
    uint8_t b[4];
    std::memcpy(b, &v, 4);
    buf.insert(buf.end(), b, b + 4);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_bigoke_metings_engine_WhisperNative_initContext(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    LOGI("init %s -> %p", path, ctx);
    env->ReleaseStringUTFChars(modelPath, path);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_com_bigoke_metings_engine_WhisperNative_freeContext(JNIEnv *, jobject, jlong ptr) {
    auto *ctx = reinterpret_cast<whisper_context *>(ptr);
    if (ctx) whisper_free(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_com_bigoke_metings_engine_WhisperNative_requestAbort(JNIEnv *, jobject, jboolean value) {
    g_abort.store(value == JNI_TRUE);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_bigoke_metings_engine_WhisperNative_progress(JNIEnv *, jobject) {
    return g_progress.load();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_bigoke_metings_engine_WhisperNative_systemInfo(JNIEnv *env, jobject) {
    return env->NewStringUTF(whisper_print_system_info());
}

// Record layout per token: int32 t0 (10 ms units), int32 t1, int32 segment, float prob,
// int32 byteLength, bytes[byteLength]
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_bigoke_metings_engine_WhisperNative_transcribe(
        JNIEnv *env, jobject, jlong ptr, jfloatArray samples, jstring language,
        jint threads, jint beamSize, jstring prompt) {
    auto *ctx = reinterpret_cast<whisper_context *>(ptr);
    if (!ctx) return nullptr;

    const char *lang = env->GetStringUTFChars(language, nullptr);
    const char *promptChars = env->GetStringUTFChars(prompt, nullptr);
    std::string langStr(lang);
    std::string promptStr(promptChars);
    env->ReleaseStringUTFChars(language, lang);
    env->ReleaseStringUTFChars(prompt, promptChars);

    whisper_full_params params = whisper_full_default_params(
            beamSize > 1 ? WHISPER_SAMPLING_BEAM_SEARCH : WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_special = false;
    params.print_timestamps = false;
    params.translate = false;
    params.no_context = false;
    params.token_timestamps = true;
    params.suppress_blank = true;
    params.suppress_nst = true;
    params.beam_search.beam_size = beamSize > 1 ? beamSize : 1;
    params.greedy.best_of = 5;
    params.language = langStr.c_str();
    params.detect_language = false;
    if (!promptStr.empty()) params.initial_prompt = promptStr.c_str();
    params.progress_callback = progress_cb;
    params.abort_callback = abort_cb;

    g_progress.store(0);

    jsize n = env->GetArrayLength(samples);
    jfloat *data = env->GetFloatArrayElements(samples, nullptr);
    int rc = whisper_full(ctx, params, data, n);
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
    if (rc != 0) {
        LOGI("whisper_full failed: %d", rc);
        return nullptr;
    }

    const whisper_token eot = whisper_token_eot(ctx);
    std::vector<uint8_t> buf;
    buf.reserve(1 << 16);
    const int nSeg = whisper_full_n_segments(ctx);
    for (int i = 0; i < nSeg; ++i) {
        const int nTok = whisper_full_n_tokens(ctx, i);
        for (int j = 0; j < nTok; ++j) {
            whisper_token_data td = whisper_full_get_token_data(ctx, i, j);
            if (td.id >= eot) continue;  // special / timestamp tokens
            const char *text = whisper_full_get_token_text(ctx, i, j);
            const int len = text ? (int) std::strlen(text) : 0;
            put_i32(buf, (int32_t) td.t0);
            put_i32(buf, (int32_t) td.t1);
            put_i32(buf, i);
            put_f32(buf, td.p);
            put_i32(buf, len);
            if (len > 0) buf.insert(buf.end(), text, text + len);
        }
    }
    g_progress.store(100);

    jbyteArray out = env->NewByteArray((jsize) buf.size());
    if (!buf.empty()) {
        env->SetByteArrayRegion(out, 0, (jsize) buf.size(), reinterpret_cast<const jbyte *>(buf.data()));
    }
    return out;
}
