/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Counter — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.counter.fx;

import com.mjtrac.counter.entity.CounterUser;
import org.springframework.stereotype.Component;

/** Holds the logged-in user for the lifetime of the process — replaces HttpSession. */
@Component
public class AuthContext {

    private CounterUser currentUser;

    public CounterUser getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(CounterUser user) {
        this.currentUser = user;
    }

    public boolean isAdmin() {
        return currentUser != null && currentUser.getRoles().contains(CounterUser.Role.ADMIN);
    }

    public boolean isCounterOperator() {
        return currentUser != null && (currentUser.getRoles().contains(CounterUser.Role.ADMIN)
            || currentUser.getRoles().contains(CounterUser.Role.COUNTER_OPERATOR));
    }

    public boolean isViewer() {
        return currentUser != null && (currentUser.getRoles().contains(CounterUser.Role.ADMIN)
            || currentUser.getRoles().contains(CounterUser.Role.VIEWER));
    }

    public void clear() {
        currentUser = null;
    }
}
