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
 * Een generieke klasse die elk type Pbus-pakket kan bouwen.
 * Je geeft gewoon het adres en de data-bytes mee.
 *
 * Voorbeeld:
 * new PbusPacket(addr, DIGITAL_STATUS_REQUEST, channel);
 * new PbusPacket(addr, COMMAND_POSITION, channel, percent, speedHi, speedLo);
 *
 * @author JWteK - Initial contribution
 */
@NonNullByDefault
public class PbusPacket {

    public static final byte STX = 0x0F;
    public static final byte ETX = 0x04;

    private final byte address;
    private final byte[] dataBytes;

    public PbusPacket(byte address, byte... dataBytes) {
        this.address = address;
        this.dataBytes = dataBytes;
    }

    /** Bouwt het volledige Pbus-pakket inclusief STX, ETX en CRC-byte */
    public byte[] getBytes() {
        int len = dataBytes.length;
        byte[] packet = new byte[5 + len];

        packet[0] = STX;
        packet[1] = address;
        packet[2] = (byte) len;

        System.arraycopy(dataBytes, 0, packet, 3, len);

        packet[3 + len] = computeCRCByte(packet);
        packet[4 + len] = ETX;

        return packet;
    }

    /** Bereken CRC-byte */
    public static byte computeCRCByte(byte[] packet) {
        int crc = 0;
        for (int i = 0; i < packet.length - 2; i++) {
            crc = (crc + (packet[i] & 0xFF)) & 0xFF;
        }
        return (byte) (0x100 - crc);
    }
}
