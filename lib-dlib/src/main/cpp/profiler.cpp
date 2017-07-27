#include <my/profiler.h>
#include <android/log.h>

#define LOGI(...) \
  ((void)__android_log_print(ANDROID_LOG_INFO, "profiler-jni:", __VA_ARGS__))

Profiler::Profiler() {
    // DO NOTHING.
}

void Profiler::start() {
    timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    mTimespecs.push(now);
//    LOGI("%d elements in the stack.", mTimespecs.size());
}

Profiler::~Profiler() {
    // DO NOTHING.
}

double Profiler::stopAndGetInterval() {
    // Pop the previous time spec.
    timespec prev = mTimespecs.top();
    mTimespecs.pop();

//    LOGI("%d elements in the stack.", mTimespecs.size());

    // Get the current time spec.
    timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);

    return ((double) (now.tv_sec - prev.tv_sec) * 1000L +
            (double) (now.tv_nsec - prev.tv_nsec) / 1000000L);
}
