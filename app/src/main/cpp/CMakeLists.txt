# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)
include(FetchContent)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("cipherfiles")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
set(OPENSSL_DIR ${CMAKE_SOURCE_DIR}/jniLibs/${ANDROID_ABI})
message("${OPENSSL_DIR}")
if(EXISTS ${OPENSSL_DIR}/lib/libcrypto_1_1.so)
    message(STATUS "OpenSSL found for ${ANDROID_ABI}")

    # Добавляем OpenSSL как IMPORTED библиотеки
    add_library(crypto SHARED IMPORTED)
    set_target_properties(crypto PROPERTIES
            IMPORTED_LOCATION ${OPENSSL_DIR}/lib/libcrypto_1_1.so
    )

    add_library(ssl SHARED IMPORTED)
    set_target_properties(ssl PROPERTIES
            IMPORTED_LOCATION ${OPENSSL_DIR}/lib/libssl_1_1.so
    )

    set(OPENSSL_LIBS crypto ssl)
    set(OPENSSL_INCLUDES ${OPENSSL_DIR}/include)
else()
    message(FATAL_ERROR "OpenSSL not found for ${ANDROID_ABI}")
endif()

# In this top level CMakeLists.txt, ${CMAKE_PROJECT_NAME} is used to define
# the target library name; in the sub-module's CMakeLists.txt, ${PROJECT_NAME}
# is preferred for the same purpose.
#
# In order to load a library into your app from Java/Kotlin, you must call
# System.loadLibrary() and pass the name of the library defined here;
# for GameActivity/NativeActivity derived applications, the same library name must be
# used in the AndroidManifest.xml file.
add_library(${CMAKE_PROJECT_NAME} SHARED
        # List C/C++ source files with relative paths to this CMakeLists.txt.
        native-lib.cpp)

# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(${CMAKE_PROJECT_NAME}
        # List libraries link to the target library
        android
        log
        ${OPENSSL_LIBS})

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${OPENSSL_INCLUDES}
)