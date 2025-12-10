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

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link PbusBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author JWteK - Initial contribution
 */
@NonNullByDefault
public class PbusBindingConstants {

    public static final String BINDING_ID = "pbus";

    // Time of progress bar in Open hab Scan
    public static final int SEARCH_TIME = 10;

    // bridges
    public static final ThingTypeUID BRIDGE_SERIAL = new ThingTypeUID(BINDING_ID, "serialbridge");
    public static final ThingTypeUID BRIDGE_NETWORK = new ThingTypeUID(BINDING_ID, "networkbridge");

    // thing types
    public static final ThingTypeUID THING_2C = new ThingTypeUID(BINDING_ID, "2c");
    public static final ThingTypeUID THING_2D20 = new ThingTypeUID(BINDING_ID, "2d20");
    public static final ThingTypeUID THING_2R1K = new ThingTypeUID(BINDING_ID, "2r1k");
    public static final ThingTypeUID THING_2Y10 = new ThingTypeUID(BINDING_ID, "2y10");
    public static final ThingTypeUID THING_2U10 = new ThingTypeUID(BINDING_ID, "2u10");
    public static final ThingTypeUID THING_2Y10M = new ThingTypeUID(BINDING_ID, "2y10m");
    public static final ThingTypeUID THING_2P100 = new ThingTypeUID(BINDING_ID, "2p100");
    public static final ThingTypeUID THING_2Y420 = new ThingTypeUID(BINDING_ID, "2y420");
    public static final ThingTypeUID THING_2I25 = new ThingTypeUID(BINDING_ID, "2i25");
    public static final ThingTypeUID THING_4D20 = new ThingTypeUID(BINDING_ID, "4d20");
    public static final ThingTypeUID THING_2P1K = new ThingTypeUID(BINDING_ID, "2p1k");
    public static final ThingTypeUID THING_2I420 = new ThingTypeUID(BINDING_ID, "2i420");
    public static final ThingTypeUID THING_2Q250 = new ThingTypeUID(BINDING_ID, "2q250");
    public static final ThingTypeUID THING_2Q250M = new ThingTypeUID(BINDING_ID, "2q250m");
    public static final ThingTypeUID THING_2D42 = new ThingTypeUID(BINDING_ID, "2d42");
    public static final ThingTypeUID THING_3QM3 = new ThingTypeUID(BINDING_ID, "3qm3");
    public static final ThingTypeUID THING_2D250 = new ThingTypeUID(BINDING_ID, "2d250");

    // thing type sets
    public static final Set<ThingTypeUID> SUPPORTED_BRIDGE_UIDS = Set.of(BRIDGE_SERIAL, BRIDGE_NETWORK);

    public static final Set<ThingTypeUID> SUPPORTED_THING_UIDS = Set.of(THING_2C, THING_2D20, THING_2R1K, THING_2Y10,
            THING_2U10, THING_2Y10M, THING_2P100, THING_2Y420, THING_2I25, THING_4D20, THING_2P1K, THING_2I420,
            THING_2Q250, THING_2Q250M, THING_2D42, THING_3QM3, THING_2D250);

    // Pbus module types
    public static final byte MODULE_2C = 0x00;
    public static final byte MODULE_2D20 = 0x01;
    public static final byte MODULE_2R1K = 0x02;
    public static final byte MODULE_2Y10 = 0x03;
    public static final byte MODULE_2U10 = 0x06;
    public static final byte MODULE_2Y10M = 0x07;
    public static final byte MODULE_2P100 = 0x0A;
    public static final byte MODULE_2Y420 = 0x0B;
    public static final byte MODULE_2I25 = 0x0E;
    public static final byte MODULE_4D20 = 0x11;
    public static final byte MODULE_2P1K = 0x16;
    public static final byte MODULE_2I420 = 0x1A;
    public static final byte MODULE_2Q250 = 0x1D;
    public static final byte MODULE_2Q250M = 0x20;
    public static final byte MODULE_2D42 = 0x21;
    public static final byte MODULE_3QM3 = 0x28;
    public static final byte MODULE_2D250 = 0x31;

    // Pbus commands
    public static final byte MODULE_TYPE_REQUEST = (byte) 0x11;
    public static final byte MODULE_TYPE_ANSWER = (byte) 0x21;
    public static final byte MODULE_REMOVED = (byte) 0x31;

    public static final byte DIGITAL_STATUS_REQUEST = (byte) 0x12;
    public static final byte DIGITAL_STATUS_ANSWER = (byte) 0x22;

    public static final byte ANALOG_STATUS_REQUEST = (byte) 0x13;
    public static final byte ANALOG_STATUS_ANSWER = (byte) 0x23;

    public static final byte FEEDBACK_REQUEST = (byte) 0x14;
    public static final byte FEEDBACK_ANSWER = (byte) 0x24;

    public static final byte SWITCH_RELAY = (byte) 0x41;

    public static final byte SET_VALUE = (byte) 0x42;
}
