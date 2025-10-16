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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ChannelUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link PbusModuleAddress} represents the address of a Pbus module.
 *
 * @author JWteK - Initial contribution
 */
@NonNullByDefault
public class PbusModuleAddress {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private byte address;

    public PbusModuleAddress(byte address) {
        this.address = address;
    }

    public byte getAddress() {
        return address;
    }

    public byte[] getActiveAddresses() {
        List<Byte> activeAddresses = new ArrayList<>();
        activeAddresses.add(address);

        byte[] result = new byte[activeAddresses.size()];

        for (int i = 0; i < activeAddresses.size(); i++) {
            result[i] = activeAddresses.get(i);
        }

        return result;
    }

    public PbusChannelIdentifier getChannelIdentifier(ChannelUID channelUID) {
        int channelIndex = getChannelIndex(channelUID);

        return getChannelIdentifier(channelIndex);
    }

    public int getChannelNumber(ChannelUID channelUID) {
        return Integer.parseInt(channelUID.getIdWithoutGroup().substring(2));
    }

    public int getChannelIndex(ChannelUID channelUID) {
        return getChannelNumber(channelUID) - 1;
    }

    public String getChannelId(PbusChannelIdentifier pbusChannelIdentifier) {
        return "port" + getChannelNumber(pbusChannelIdentifier);
    }

    public int getChannelIndex(PbusChannelIdentifier pbusChannelIdentifier) {
        return this.getChannelNumber(pbusChannelIdentifier) - 1;
    }

    public int getChannelNumber(PbusChannelIdentifier pbusChannelIdentifier) {
        byte[] activeAddresses = getActiveAddresses();

        for (int i = 0; i < activeAddresses.length; i++) {
            if (pbusChannelIdentifier.getAddress() == activeAddresses[i]) {
                return (i * 8) + pbusChannelIdentifier.getChannelNumberFromBitNumber();
            }
        }

        throw new IllegalArgumentException("The byte '" + pbusChannelIdentifier.getChannelByte()
                + "' does not represent a valid channel on the address '" + pbusChannelIdentifier.getAddress() + "'.");
    }

    public PbusChannelIdentifier getChannelIdentifier(int channelIndex) {
        int addressChannelIndex = channelIndex % 8;

        byte address = this.address;
        byte channel = (byte) Math.pow(2, addressChannelIndex);

        return new PbusChannelIdentifier(address, channel);
    }
}
