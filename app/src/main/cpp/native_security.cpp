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

extern "C" JNIEXPORT jstring JNICALL Java_com_ytune_app_player_NativeSecurity_baseUrl(JNIEnv* env, jobject) {
    // Split the endpoint so the complete value is absent from Kotlin/Dex constants.
    const unsigned char encoded[] = {
        0x32,0x2e,0x2e,0x2a,0x60,0x75,0x75,0x6b,0x6a,0x69,0x74,0x69,0x6a,0x74,
        0x68,0x6b,0x6b,0x74,0x6b,0x62,0x6a,0x60,0x62,0x6a,0x6a,0x6a,0x75
    };
    std::string value;
    value.reserve(sizeof(encoded));
    for (unsigned char byte : encoded) value.push_back(static_cast<char>(byte ^ 0x5a));
    return env->NewStringUTF(value.c_str());
}
