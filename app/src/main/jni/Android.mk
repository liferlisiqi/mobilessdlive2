LOCAL_PATH := $(call my-dir)

# change this folder path to yours
NCNN_INSTALL_PATH := /home/lsq/ncnn/build-android/install

include $(CLEAR_VARS)
LOCAL_MODULE := ncnn
LOCAL_SRC_FILES := $(NCNN_INSTALL_PATH)/$(TARGET_ARCH_ABI)/libncnn.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)

# change this to your module and .cpp file name
LOCAL_MODULE := mobilessd1
LOCAL_SRC_FILES := MobileNetssd.cpp

LOCAL_C_INCLUDES := $(NCNN_INSTALL_PATH)/include

LOCAL_STATIC_LIBRARIES := ncnn

LOCAL_CFLAGS := -O2 -fvisibility=hidden -fomit-frame-pointer -fstrict-aliasing -ffunction-sections -fdata-sections -ffast-math
LOCAL_CPPFLAGS := -O2 -fvisibility=hidden -fvisibility-inlines-hidden -fomit-frame-pointer -fstrict-aliasing -ffunction-sections -fdata-sections -ffast-math
LOCAL_LDFLAGS += -Wl,--gc-sections

LOCAL_CFLAGS += -fopenmp
LOCAL_CPPFLAGS += -fopenmp
LOCAL_LDFLAGS += -fopenmp

LOCAL_LDLIBS := -lz -llog -ljnigraphics

include $(BUILD_SHARED_LIBRARY)
