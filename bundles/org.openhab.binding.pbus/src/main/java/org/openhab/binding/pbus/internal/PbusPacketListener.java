/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.pbus.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link PbusPacketListener} is notified when a Pbus packet for
 * the listener's address is sent on the bus.
 *
 * @author JWteK - Initial contribution
 */
@NonNullByDefault
public interface PbusPacketListener {
    /**
     * This method is called whenever the state of the given relay has changed.
     *
     * @param packet The bytes of the received packet.
     */
    void onPacketReceived(byte[] packet);
}
