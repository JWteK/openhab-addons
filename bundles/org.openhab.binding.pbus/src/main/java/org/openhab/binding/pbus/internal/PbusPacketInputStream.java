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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link PbusPacketInputStream} is a wrapper around an InputStream that
 * aggregates bytes from the input stream to meaningful packets in the Pbus system.
 *
 * @author JWteK - Initial contribution
 */
@NonNullByDefault
public class PbusPacketInputStream {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public InputStream inputStream;

    private ArrayList<Byte> currentData = new ArrayList<Byte>();
    private @Nullable Byte currentSTX = null;
    private @Nullable Byte currentAddress = null;
    private @Nullable Byte currentDataLength = null;
    private @Nullable Byte currentChecksum = null;

    public String toHex(byte val) {
        return String.format("%02X", val);
    }

    public PbusPacketInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public byte[] readPacket() throws IOException {
        logger.debug("Listen for new packacge");
        int currentDataByte;

        // Lees de seriele data [0-255] totdat er niets meer in de buffer staat [-1]
        while ((currentDataByte = inputStream.read()) > -1) {

            // Lees pakket
            if (currentSTX == null) {
                if (((byte) currentDataByte) == PbusPacket.STX) {
                    currentSTX = (byte) currentDataByte;
                } else {
                    resetCurrentState();
                    logger.error("Packet with invalid start byte: {}", toHex((byte) currentDataByte));
                }

            } else if (currentAddress == null) {
                currentAddress = (byte) currentDataByte;

            } else if (currentDataLength == null && currentDataByte <= 8) {
                currentDataLength = (byte) currentDataByte;
            } else if (currentDataLength == null) {
                currentDataLength = 1;
                currentData.add((byte) currentDataByte);
            } else if (currentData.size() < currentDataLength) {
                currentData.add((byte) currentDataByte);

            } else if (currentChecksum == null) {
                currentChecksum = (byte) currentDataByte;
                byte[] packet = getCurrentPacket();
                byte expectedChecksum = PbusPacket.computeCRCByte(packet);

                if (currentChecksum != expectedChecksum) {
                    resetCurrentState();
                    logger.error("Packet with invalid checksum received: {} instead of {}", toHex(currentChecksum),
                            toHex(expectedChecksum));
                }
            } else if (((byte) currentDataByte) == PbusPacket.ETX) {
                byte[] packet = getCurrentPacket();
                resetCurrentState();
                logger.warn("Packet received");
                return packet;
            } else {
                resetCurrentState();
                logger.error("Packet with invalid ETX received: {}", toHex((byte) currentDataByte));
            }
        }
        return new byte[0];
    }

    public void close() throws IOException {
        inputStream.close();
    }

    protected byte[] getCurrentPacket() {
        if (currentDataLength != null && currentSTX != null && currentAddress != null && currentChecksum != null) {

            byte[] packet = new byte[5 + currentDataLength];

            packet[0] = currentSTX;
            packet[1] = currentAddress;
            packet[2] = currentDataLength;

            for (int i = 0; i < currentDataLength; i++) {
                packet[3 + i] = currentData.get(i);
            }

            packet[3 + currentDataLength] = currentChecksum;
            packet[4 + currentDataLength] = PbusPacket.ETX;

            return packet;
        }

        return new byte[0];
    }

    protected void resetCurrentState() throws IOException {
        currentSTX = null;
        currentAddress = null;
        currentDataLength = null;
        currentData = new ArrayList<>();
        currentChecksum = null;
        while (inputStream.available() > 0) {
            inputStream.read();
        }
    }
}
