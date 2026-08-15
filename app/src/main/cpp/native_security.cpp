#include <jni.h>
#include <string>
#include <chrono>
#include <sstream>
#include <functional>

extern "C" JNIEXPORT jstring JNICALL Java_com_ytune_app_player_NativeSecurity_requestId(JNIEnv* env, jobject, jstring seed) {
    const char* raw = env->GetStringUTFChars(seed, nullptr);
    auto now = std::chrono::high_resolution_clock::now().time_since_epoch().count();
    std::ostringstream out;
    out << std::hex << now << '-' << std::hash<std::string>{}(raw);
    env->ReleaseStringUTFChars(seed, raw);
    return env->NewStringUTF(out.str().c_str());
}
