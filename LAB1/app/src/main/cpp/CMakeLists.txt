cmake_minimum_required(VERSION 3.22.1)
project("fclient")

add_library(
    fclient
    SHARED
    native-lib.cpp
)

# Подключение MbedTLS (если библиотека собрана)
if(EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libmbedcrypto.so)
    add_library(mbedcrypto SHARED IMPORTED)
    set_target_properties(mbedcrypto PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libmbedcrypto.so)
    target_include_directories(fclient PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/../../../libs/mbedtls/mbedtls/include)
    target_link_libraries(fclient mbedcrypto)
endif()

find_library(log-lib log)

target_link_libraries(
    fclient
    ${log-lib}
)