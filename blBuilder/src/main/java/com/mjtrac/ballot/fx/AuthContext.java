/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Ballot System — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.ballot.fx;

import com.mjtrac.ballot.model.User;
import org.springframework.stereotype.Component;

/** Holds the logged-in user for the lifetime of the process — replaces HttpSession. */
@Component
public class AuthContext {

    private User currentUser;

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public boolean isAdmin() {
        return currentUser != null && currentUser.getRoles().contains(User.Role.ADMIN);
    }

    public boolean isDataEntry() {
        return currentUser != null && (currentUser.getRoles().contains(User.Role.ADMIN)
            || currentUser.getRoles().contains(User.Role.DATA_ENTRY));
    }

    public boolean isPrinter() {
        return currentUser != null && (currentUser.getRoles().contains(User.Role.ADMIN)
            || currentUser.getRoles().contains(User.Role.PRINTER));
    }

    public void clear() {
        currentUser = null;
    }
}
