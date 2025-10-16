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
package org.openhab.binding.pbus.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link PbusSerialBridgeConfig} class represents the configuration of a Pbus Serial Bridge.
 *
 * @author JWteK - Initial contribution
 */
@NonNullByDefault
public class PbusSerialBridgeConfig {
    public String port = "";
    public int baudrate;
}
