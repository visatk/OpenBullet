#include <jni.h>
#include <algorithm>
#include <cctype>
#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

namespace {

bool containsCaseInsensitive(std::string_view haystack, std::string_view needle) {
    if (needle.empty()) return false;
    auto it = std::search(
        haystack.begin(), haystack.end(),
        needle.begin(), needle.end(),
        [](unsigned char a, unsigned char b) {
            return std::tolower(a) == std::tolower(b);
        }
    );
    return it != haystack.end();
}

uint64_t fnv1a64(std::string_view data) {
    uint64_t hash = 14695981039346656037ULL;
    for (unsigned char c : data) {
        hash ^= static_cast<uint64_t>(c);
        hash *= 1099511628211ULL;
    }
    return hash;
}

std::vector<std::string> toStringVector(JNIEnv* env, jobjectArray array) {
    std::vector<std::string> out;
    if (!array) return out;
    jsize len = env->GetArrayLength(array);
    out.reserve(static_cast<size_t>(len));
    for (jsize i = 0; i < len; ++i) {
        auto jstr = static_cast<jstring>(env->GetObjectArrayElement(array, i));
        if (jstr) {
            const char* chars = env->GetStringUTFChars(jstr, nullptr);
            if (chars) {
                out.emplace_back(chars);
                env->ReleaseStringUTFChars(jstr, chars);
            }
            env->DeleteLocalRef(jstr);
        }
    }
    return out;
}

}  // namespace

extern "C" {

// Returns: 1 = HIT (success), 2 = CUSTOM, 3 = FAIL, 0 = NONE/TO_CHECK
JNIEXPORT jint JNICALL
Java_com_example_nativeengine_NativeScannerBridge_nativeClassifyResponse(
    JNIEnv* env,
    jobject /* thiz */,
    jstring jBody,
    jobjectArray jSuccessKeys,
    jobjectArray jCustomKeys,
    jobjectArray jFailureKeys
) {
    if (!jBody) return 0;
    const char* bodyChars = env->GetStringUTFChars(jBody, nullptr);
    if (!bodyChars) return 0;
    std::string_view body(bodyChars);

    auto failureKeys = toStringVector(env, jFailureKeys);
    for (const auto& key : failureKeys) {
        if (containsCaseInsensitive(body, key)) {
            env->ReleaseStringUTFChars(jBody, bodyChars);
            return 3;
        }
    }

    auto successKeys = toStringVector(env, jSuccessKeys);
    for (const auto& key : successKeys) {
        if (containsCaseInsensitive(body, key)) {
            env->ReleaseStringUTFChars(jBody, bodyChars);
            return 1;
        }
    }

    auto customKeys = toStringVector(env, jCustomKeys);
    for (const auto& key : customKeys) {
        if (containsCaseInsensitive(body, key)) {
            env->ReleaseStringUTFChars(jBody, bodyChars);
            return 2;
        }
    }

    env->ReleaseStringUTFChars(jBody, bodyChars);
    return 0;
}

// Fast 64-bit FNV-1a hash + structural pair check
JNIEXPORT jlong JNICALL
Java_com_example_nativeengine_NativeScannerBridge_nativeFastHashLine(
    JNIEnv* env,
    jobject /* thiz */,
    jstring jLine
) {
    if (!jLine) return 0L;
    const char* chars = env->GetStringUTFChars(jLine, nullptr);
    if (!chars) return 0L;
    uint64_t hash = fnv1a64(std::string_view(chars));
    env->ReleaseStringUTFChars(jLine, chars);
    return static_cast<jlong>(hash);
}

JNIEXPORT jstring JNICALL
Java_com_example_nativeengine_NativeScannerBridge_nativeGetEngineInfo(
    JNIEnv* env,
    jobject /* thiz */
) {
    return env->NewStringUTF("NDK C++17 SIMD/Boyer-Moore Scanner Active");
}

}  // extern "C"
