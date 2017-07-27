// Copyright (c) 2017-present boyw165
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
//    The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
//    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

#ifndef COM_MY_JNI_PROFILER_H
#define COM_MY_JNI_PROFILER_H

#include <stack>
#include <time.h>

/**
 * An util class to profile the performance of code.
 * <br/>
 * Usage:
 * <pre>
 * // Init profiler.
 * Profile profiler;
 *
 * // Start profiling
 * profiler.start();
 *
 * // Run your code...
 *
 * // Stop and calculate the interval in milliseconds.
 * double interval = profiler.stopAndGetInterval();
 * </pre>
 */
class Profiler {
private:

    std::stack<timespec> mTimespecs;

public:

    Profiler();

    ~Profiler();

    /**
     * Start profiling. The profiler put a monotonic time-stamp to the stack.
     */
    void start();

    /**
     * Stop profiling and pop the time-stamp, then return the interval in
     * milliseconds.
     */
    double stopAndGetInterval();
};


#endif //COM_MY_JNI_PROFILER_H
