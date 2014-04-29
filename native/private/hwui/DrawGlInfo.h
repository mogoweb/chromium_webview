/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef ANDROID_HWUI_DRAW_GL_INFO_H
#define ANDROID_HWUI_DRAW_GL_INFO_H

namespace android {
namespace uirenderer {

/**
 * Structure used by OpenGLRenderer::callDrawGLFunction() to pass and
 * receive data from OpenGL functors.
 */
struct DrawGlInfo {
    // Input: current clip rect
    int clipLeft;
    int clipTop;
    int clipRight;
    int clipBottom;

    // Input: current width/height of destination surface
    int width;
    int height;

    // Input: is the render target an FBO
    bool isLayer;

    // Input: current transform matrix, in OpenGL format
    float transform[16];

    // Output: dirty region to redraw
    float dirtyLeft;
    float dirtyTop;
    float dirtyRight;
    float dirtyBottom;

    /**
     * Values used as the "what" parameter of the functor.
     */
    enum Mode {
        // Indicates that the functor is called to perform a draw
        kModeDraw,
        // Indicates the the functor is called only to perform
        // processing and that no draw should be attempted
        kModeProcess
    };

    /**
     * Values used by OpenGL functors to tell the framework
     * what to do next.
     */
    enum Status {
        // The functor is done
        kStatusDone = 0x0,
        // The functor is requesting a redraw (the clip rect
        // used by the redraw is specified by DrawGlInfo.)
        // The rest of the UI might redraw too.
        kStatusDraw = 0x1,
        // The functor needs to be invoked again but will
        // not redraw. Only the functor is invoked again
        // (unless another functor requests a redraw.)
        kStatusInvoke = 0x2,
        // DisplayList actually issued GL drawing commands.
        // This is used to signal the HardwareRenderer that the
        // buffers should be flipped - otherwise, there were no
        // changes to the buffer, so no need to flip. Some hardware
        // has issues with stale buffer contents when no GL
        // commands are issued.
        kStatusDrew = 0x4
    };
}; // struct DrawGlInfo

}; // namespace uirenderer
}; // namespace android

#endif // ANDROID_HWUI_DRAW_GL_INFO_H
