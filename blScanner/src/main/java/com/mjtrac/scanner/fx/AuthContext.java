/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Scanner Driver — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.scanner.fx;

import com.mjtrac.scanner.entity.ScannerUser;
import org.springframework.stereotype.Component;

/** Holds the logged-in user for the lifetime of the process — replaces HttpSession. */
@Component
public class AuthContext {

    private ScannerUser currentUser;

    public ScannerUser getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(ScannerUser user) {
        this.currentUser = user;
    }

    public boolean isAdministrator() {
        return currentUser != null && "ADMINISTRATOR".equals(currentUser.getRole());
    }

    public void clear() {
        currentUser = null;
    }
}
