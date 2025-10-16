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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link PbusModule} represents a generic module and its basic properties
 * in the Pbus system.
 *
 * @author JWteK - Initial contribution
 */
@NonNullByDefault
public class PbusModule {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final HashMap<Integer, String[]> channelNames = new HashMap<>();

    private PbusModuleAddress moduleAddress;
    private byte moduleType;
    private ThingTypeUID thingTypeUID;

    public PbusModule(PbusModuleAddress moduleAddress, byte moduleType, ThingTypeUID thingTypeUID) {
        this.moduleAddress = moduleAddress;
        this.moduleType = moduleType;
        this.thingTypeUID = thingTypeUID;
    }

    public byte getModuleType() {
        return moduleType;
    }

    public PbusModuleAddress getModuleAddress() {
        return moduleAddress;
    }

    public String getAddress() {
        return String.format("%d", moduleAddress.getAddress());
    }

    public ThingTypeUID getThingTypeUID() {
        return this.thingTypeUID;
    }

    public ThingUID getThingUID(ThingUID bridgeUID) {
        return new ThingUID(getThingTypeUID(), bridgeUID, getAddress());
    }

    public String getLabel() {
        return getThingTypeUID() + " (Address " + getAddress() + ")";
    }

    protected String getChannelName(int channelIndex) {
        StringBuilder channelNameBuilder = new StringBuilder();

        Integer key = channelIndex - 1; // autoboxing werkt in Java 5
        String[] parts = channelNames.get(key);

        if (parts != null) {
            int limit = Math.min(parts.length, 3);
            for (int i = 0; i < limit; i++) {
                channelNameBuilder.append(parts[i]);
            }
        }

        return channelNameBuilder.toString();
    }

    public Map<String, Object> getProperties() {
        Map<String, Object> properties = new TreeMap<>();

        properties.put("address", getAddress());

        Integer[] keys = channelNames.keySet().toArray(new Integer[0]);
        Arrays.sort(keys);

        for (Integer key : keys) {
            String channelName = getChannelName(key);
            if (channelName.length() > 0) {
                properties.put("Port" + key, channelName);
            }
        }

        return properties;
    }
}
