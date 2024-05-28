/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024 mochibit.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.mochibit.defcon.lifecycle

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.threading.jobs.Schedulable

abstract class CycledObject : Lifecycled, Schedulable {
    private var destroyed = false;
    fun instantiate(async: Boolean) {
        this.start();
        if (async) {
            Defcon.instance.asyncRunnable.addWorkload(this);
        } else {
            Defcon.instance.scheduledRunnable.addWorkload(this);
        }
    }
    fun destroy() {
        this.stop();
        destroyed = true;
    }

    override fun compute() {
        this.update(1.0 / Defcon.instance.server.tps[0])
    }

    override fun shouldBeRescheduled(): Boolean {
        return !destroyed;
    }


}