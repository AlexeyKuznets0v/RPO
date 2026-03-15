#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <thread>
#include <android/log.h>

#define LOG_TAG "fclient"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

JavaVM* gJvm = nullptr;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* pjvm, void* reserved) {
    gJvm = pjvm;
    return JNI_VERSION_1_6;
}

JNIEnv* getEnv(bool& detach) {
    JNIEnv* env = nullptr;
    int status = gJvm->GetEnv((void**)&env, JNI_VERSION_1_6);
    detach = false;
    if (status == JNI_EDETACHED) {
        status = gJvm->AttachCurrentThread(&env, nullptr);
        if (status < 0) {
            return nullptr;
        }
        detach = true;
    }
    return env;
}

void releaseEnv(bool detach, JNIEnv* env) {
    if (detach && env != nullptr) {
        gJvm->DetachCurrentThread();
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_fclient_MainActivity_transaction(JNIEnv *xenv, jobject xthiz, jbyteArray xtrd) {
    // Создаем глобальные ссылки для использования в другом потоке
    jobject thiz = xenv->NewGlobalRef(xthiz);
    jbyteArray trd = (jbyteArray)xenv->NewGlobalRef(xtrd);

    std::thread t([thiz, trd] {
        bool detach = false;
        JNIEnv *env = getEnv(detach);

        // ❗ КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: не используем env, если он null
        if (env == nullptr) {
            // В реальном проекте: __android_log_print(...)
            env = nullptr; // явное указание, что env недоступен
            // Примечание: глобальные ссылки здесь не освобождаются,
            // но это предотвращает краш. В продакшене нужна лучшая обработка.
            return;
        }

        jclass cls = env->GetObjectClass(thiz);

        jmethodID id = env->GetMethodID(cls, "enterPin", "(ILjava/lang/String;)Ljava/lang/String;");
        if (id == nullptr) {
            LOGD("Method enterPin not found!");
            env->DeleteGlobalRef(thiz);
            env->DeleteGlobalRef(trd);
            releaseEnv(detach, env);
            return;
        }

        uint8_t* p = (uint8_t*)env->GetByteArrayElements(trd, nullptr);
        jsize sz = env->GetArrayLength(trd);

        // Проверка формата TRD
        if ((sz != 9) || (p[0] != 0x9F) || (p[1] != 0x02) || (p[2] != 0x06)) {
            LOGD("Invalid TRD format");
            env->ReleaseByteArrayElements(trd, (jbyte *)p, JNI_ABORT);
            env->DeleteGlobalRef(thiz);
            env->DeleteGlobalRef(trd);
            releaseEnv(detach, env);
            return;
        }

        // Преобразование суммы из BCD в строку
        char buf[13];
        for (int i = 0; i < 6; i++) {
            uint8_t n = *(p + 3 + i);
            buf[i*2] = ((n & 0xF0) >> 4) + '0';
            buf[i*2 + 1] = (n & 0x0F) + '0';
        }
        buf[12] = 0x00;

        jstring jamount = env->NewStringUTF(buf);
        int ptc = 3;
        bool success = false;

        while (ptc > 0) {
            LOGD("C++: calling enterPin with ptc=%d, amount=%s", ptc, env->GetStringUTFChars(jamount, nullptr));

            jstring pin = (jstring) env->CallObjectMethod(thiz, id, ptc, jamount);

            if (env->ExceptionCheck()) {
                env->ExceptionDescribe();
                env->ExceptionClear();
                break;
            }

            if (pin != nullptr) {
                const char *utf = env->GetStringUTFChars(pin, nullptr);
                LOGD("C++: received pin='%s'", utf != nullptr ? utf : "null");

                if ((utf != nullptr) && (strlen(utf) == 4) && (strcmp(utf, "1234") == 0)) {
                    success = true;
                    LOGD("C++: PIN correct!");
                    env->ReleaseStringUTFChars(pin, utf);
                    env->DeleteLocalRef(pin);
                    break;
                }

                LOGD("C++: PIN incorrect, attempts left: %d", ptc - 1);

                if (utf != nullptr) {
                    env->ReleaseStringUTFChars(pin, utf);
                }
                env->DeleteLocalRef(pin);
            }

            ptc--;  // Уменьшаем попытки
        }

        // Вызов transactionResult
        jmethodID resultId = env->GetMethodID(cls, "transactionResult", "(Z)V");
        if (resultId != nullptr) {
            env->CallVoidMethod(thiz, resultId, success ? JNI_TRUE : JNI_FALSE);
        }

        // Очистка ресурсов
        env->ReleaseByteArrayElements(trd, (jbyte *)p, JNI_ABORT);
        env->DeleteLocalRef(jamount);
        env->DeleteGlobalRef(thiz);
        env->DeleteGlobalRef(trd);
        releaseEnv(detach, env);
    });

    t.detach();
    return JNI_TRUE;
}

// Заглушки для других методов
extern "C" JNIEXPORT jint JNICALL
Java_com_example_fclient_MainActivity_initRng(JNIEnv*, jclass) {
    return 0;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_fclient_MainActivity_randomBytes(JNIEnv* env, jclass, jint count) {
    jbyteArray result = env->NewByteArray(count);
    jbyte* bytes = env->GetByteArrayElements(result, nullptr);
    for (int i = 0; i < count; i++) bytes[i] = (jbyte)(i % 256);
    env->ReleaseByteArrayElements(result, bytes, 0);
    return result;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_fclient_MainActivity_encrypt(JNIEnv* env, jclass, jbyteArray, jbyteArray data) {
    jsize len = env->GetArrayLength(data);
    jbyteArray result = env->NewByteArray(len);
    env->SetByteArrayRegion(result, 0, len, env->GetByteArrayElements(data, nullptr));
    return result;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_fclient_MainActivity_decrypt(JNIEnv* env, jclass, jbyteArray, jbyteArray data) {
    jsize len = env->GetArrayLength(data);
    jbyteArray result = env->NewByteArray(len);
    env->SetByteArrayRegion(result, 0, len, env->GetByteArrayElements(data, nullptr));
    return result;
}