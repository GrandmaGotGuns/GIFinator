set(IMPORT_DIR ${CMAKE_SOURCE_DIR}/../jniLibs)

# FFmpeg include file
include_directories(${IMPORT_DIR}/${ANDROID_ABI}/include)

# Codec library
add_library(avcodec SHARED IMPORTED)
set_target_properties(avcodec PROPERTIES IMPORTED_LOCATION ${IMPORT_DIR}/${ANDROID_ABI}/libavcodec.so)

# Filter library
add_library(avfilter SHARED IMPORTED)
set_target_properties(avfilter PROPERTIES IMPORTED_LOCATION ${IMPORT_DIR}/${ANDROID_ABI}/libavfilter.so)

# File format libraries
add_library(avformat SHARED IMPORTED)
set_target_properties(avformat PROPERTIES IMPORTED_LOCATION ${IMPORT_DIR}/${ANDROID_ABI}/libavformat.so)

# Tool library
add_library(avutil SHARED IMPORTED)
set_target_properties(avutil PROPERTIES IMPORTED_LOCATION ${IMPORT_DIR}/${ANDROID_ABI}/libavutil.so)

# Resampling library
add_library(swresample SHARED IMPORTED)
set_target_properties(swresample PROPERTIES IMPORTED_LOCATION ${IMPORT_DIR}/${ANDROID_ABI}/libswresample.so)

# Video scaling library
add_library(swscale SHARED IMPORTED)
set_target_properties(swscale PROPERTIES IMPORTED_LOCATION ${IMPORT_DIR}/${ANDROID_ABI}/libswscale.so)

# Link the libraries with your native library
target_link_libraries(
        # Replace "<Your-Native-Library>" with your native library name
        native-lib
        ${log-lib}
        android
        avcodec
        avfilter
        avformat
        avutil
        swresample
        swscale
)
