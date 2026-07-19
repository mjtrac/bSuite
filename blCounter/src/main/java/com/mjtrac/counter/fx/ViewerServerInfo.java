/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Counter — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.counter.fx;

import org.springframework.stereotype.Component;

/**
 * Holds the resolved local port of the embedded Viewer web server (bound to
 * server.port=0 — an OS-assigned free port — and captured once the servlet
 * container has actually started). The Viewer screen's WebView targets
 * http://localhost:{port}/viewer/ using this.
 */
@Component
public class ViewerServerInfo {

    private volatile int port = -1;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isReady() {
        return port > 0;
    }
}
