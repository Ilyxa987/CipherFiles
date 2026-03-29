#include <jni.h>
#include <string>
#include <fstream>
#include <vector>
#include <cstring>
#include <openssl/evp.h>
#include <openssl/rand.h>
#include <openssl/err.h>
#include <android/log.h>

char errorBuffer[256];

void handleOpenSSLError() {
    ERR_error_string_n(ERR_get_error(), errorBuffer, sizeof(errorBuffer));
    __android_log_print(ANDROID_LOG_ERROR, "CipherFiles", "OpenSSL error: %s", errorBuffer);
}

bool readFile(const std::string& filePath, std::vector<unsigned char>& data) {
    std::ifstream file(filePath, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        return false;
    }

    std::streamsize size = file.tellg();
    file.seekg(0, std::ios::beg);

    data.resize(size);
    if (!file.read(reinterpret_cast<char*>(data.data()), size)) {
        return false;
    }

    file.close();
    return true;
}

bool writeFile(const std::string& filePath, const std::vector<unsigned char>& data) {
    std::ofstream file(filePath, std::ios::binary);
    if (!file.is_open()) {
        __android_log_print(ANDROID_LOG_ERROR, "CipherFiles", "Cannot open file: %s", filePath.c_str());
        return false;
    }

    file.write(reinterpret_cast<const char*>(data.data()), data.size());
    file.close();
    return true;
}

bool aesEncrypt(const std::vector<unsigned char>& plaintext,
                const std::vector<unsigned char>& key,
                std::vector<unsigned char>& ciphertext,
                std::vector<unsigned char>& iv) {

    iv.resize(16);
    if (RAND_bytes(iv.data(), iv.size()) != 1) {
        handleOpenSSLError();
        return false;
    }

    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();

    if (!ctx) {
        return false;
    }

    if (EVP_EncryptInit_ex(ctx, EVP_aes_256_cbc(), nullptr, key.data(), iv.data()) != 1) {
        EVP_CIPHER_CTX_free(ctx);
        handleOpenSSLError();
        return false;
    }

    ciphertext.resize(plaintext.size() + EVP_CIPHER_CTX_block_size(ctx));

    int outLen = 0;
    int tempLen = 0;

    if (EVP_EncryptUpdate(ctx, ciphertext.data(), &outLen,
                          plaintext.data(), plaintext.size()) != 1) {
        EVP_CIPHER_CTX_free(ctx);
        handleOpenSSLError();
        return false;
    }

    if (EVP_EncryptFinal_ex(ctx, ciphertext.data() + outLen, &tempLen) != 1) {
        EVP_CIPHER_CTX_free(ctx);
        handleOpenSSLError();
        return false;
    }

    outLen += tempLen;
    ciphertext.resize(outLen);

    EVP_CIPHER_CTX_free(ctx);
    return true;
}

bool aesDecrypt(const std::vector<unsigned char>& ciphertext,
                const std::vector<unsigned char>& key,
                const std::vector<unsigned char>& iv,
                std::vector<unsigned char>& plaintext) {

    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
    if (!ctx) {
        return false;
    }

    if (EVP_DecryptInit_ex(ctx, EVP_aes_256_cbc(), nullptr, key.data(), iv.data()) != 1) {
        EVP_CIPHER_CTX_free(ctx);
        handleOpenSSLError();
        return false;
    }

    plaintext.resize(ciphertext.size());

    int outLen = 0;
    int tempLen = 0;

    if (EVP_DecryptUpdate(ctx, plaintext.data(), &outLen,
                          ciphertext.data(), ciphertext.size()) != 1) {
        EVP_CIPHER_CTX_free(ctx);
        handleOpenSSLError();
        return false;
    }

    if (EVP_DecryptFinal_ex(ctx, plaintext.data() + outLen, &tempLen) != 1) {
        EVP_CIPHER_CTX_free(ctx);
        handleOpenSSLError();
        return false;
    }

    outLen += tempLen;
    plaintext.resize(outLen);

    EVP_CIPHER_CTX_free(ctx);
    return true;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_cipherfiles_MainActivity_encryptFile(
        JNIEnv* env,
        jobject /* this */,
        jstring sourcePath,
        jstring destPath,
        jbyteArray keyBytes
        ) {
    const char* source = env->GetStringUTFChars(sourcePath, nullptr);
    const char* dest = env->GetStringUTFChars(destPath, nullptr);

    jsize keyLen = env->GetArrayLength(keyBytes);
    std::vector<unsigned char> key(keyLen);
    env->GetByteArrayRegion(keyBytes, 0, keyLen, reinterpret_cast<jbyte*>(key.data()));

    std::vector<unsigned char> plaintext;
    if (!readFile(source, plaintext)) {
        env->ReleaseStringUTFChars(sourcePath, source);
        env->ReleaseStringUTFChars(destPath, dest);
        return JNI_FALSE;
    }

    std::vector<unsigned char> ciphertext;
    std::vector<unsigned char> iv;

    if (!aesEncrypt(plaintext, key, ciphertext, iv)) {
        env->ReleaseStringUTFChars(sourcePath, source);
        env->ReleaseStringUTFChars(destPath, dest);
        return JNI_FALSE;
    }

    std::vector<unsigned char> output;
    output.insert(output.end(), iv.begin(), iv.end());
    output.insert(output.end(), ciphertext.begin(), ciphertext.end());

    bool result = writeFile(dest, output);

    env->ReleaseStringUTFChars(sourcePath, source);
    env->ReleaseStringUTFChars(destPath, dest);

    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_cipherfiles_MainActivity_decryptFile(
        JNIEnv* env,
        jobject /* this */,
        jstring sourcePath,
        jstring destPath,
        jbyteArray keyBytes
        ) {

    const char* source = env->GetStringUTFChars(sourcePath, nullptr);
    const char* dest = env->GetStringUTFChars(destPath, nullptr);

    jsize keyLen = env->GetArrayLength(keyBytes);
    std::vector<unsigned char> key(keyLen);
    env->GetByteArrayRegion(keyBytes, 0, keyLen, reinterpret_cast<jbyte*>(key.data()));

    std::vector<unsigned char> encryptedData;
    if (!readFile(source, encryptedData)) {
        env->ReleaseStringUTFChars(sourcePath, source);
        env->ReleaseStringUTFChars(destPath, dest);
        return JNI_FALSE;
    }

    if (encryptedData.size() < 16) {
        env->ReleaseStringUTFChars(sourcePath, source);
        env->ReleaseStringUTFChars(destPath, dest);
        return JNI_FALSE;
    }

    std::vector<unsigned char> iv(encryptedData.begin(), encryptedData.begin() + 16);
    std::vector<unsigned char> ciphertext(encryptedData.begin() + 16, encryptedData.end());

    std::vector<unsigned char> plaintext;

    if (!aesDecrypt(ciphertext, key, iv, plaintext)) {
        env->ReleaseStringUTFChars(sourcePath, source);
        env->ReleaseStringUTFChars(destPath, dest);
        return JNI_FALSE;
    }

    bool result = writeFile(dest, plaintext);

    env->ReleaseStringUTFChars(sourcePath, source);
    env->ReleaseStringUTFChars(destPath, dest);

    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_cipherfiles_MainActivity_getFileSize(
        JNIEnv* env,
        jobject /* this */,
        jstring filePath
        ) {
    const char* path = env->GetStringUTFChars(filePath, nullptr);

    std::ifstream file(path, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        env->ReleaseStringUTFChars(filePath, path);
        return -1;
    }

    jlong size = file.tellg();
    file.close();

    env->ReleaseStringUTFChars(filePath, path);
    return size;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_cipherfiles_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}