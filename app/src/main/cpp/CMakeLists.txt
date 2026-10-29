cmake_minimum_required(VERSION 3.22)
project(visor_turco_sentencepiece LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 17)
set(SPM_BUILD_TEST OFF CACHE BOOL "" FORCE)
set(SPM_ENABLE_SHARED OFF CACHE BOOL "" FORCE)
set(SPM_ENABLE_TCMALLOC OFF CACHE BOOL "" FORCE)
if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/third_party/sentencepiece/CMakeLists.txt")
  message(FATAL_ERROR "Falta third_party/sentencepiece; ejecutar scripts/prepare-third-party.sh")
endif()
add_subdirectory(third_party/sentencepiece)
add_library(sentencepiece_jni SHARED sentencepiece_jni.cpp)
target_link_libraries(sentencepiece_jni PRIVATE sentencepiece-static log)


