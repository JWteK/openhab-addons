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
 * The {@link PbusAOConfig} class represents the configuration of a Pbus Analog Output module.
 * This is a extension of the refresh config
 *
 * Used to limit the amount of messages send when using a slider or simular
 *
 * @author JWteK - Initial contribution
 */
@NonNullByDefault
public class PbusAOConfig extends PbusBasicConfig {
    public int debounce;
}
