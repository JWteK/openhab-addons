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

import static org.openhab.binding.pbus.internal.PbusBindingConstants.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.pbus.internal.handler.Pbus2cHandler;
import org.openhab.binding.pbus.internal.handler.PbusAIHandler;
import org.openhab.binding.pbus.internal.handler.PbusAOHandler;
import org.openhab.binding.pbus.internal.handler.PbusBridgeHandler;
import org.openhab.binding.pbus.internal.handler.PbusDIHandler;
import org.openhab.binding.pbus.internal.handler.PbusDOHandler;
import org.openhab.binding.pbus.internal.handler.PbusNetworkBridgeHandler;
import org.openhab.binding.pbus.internal.handler.PbusSerialBridgeHandler;
import org.openhab.core.io.transport.serial.SerialPortManager;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link PbusHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author JWteK - Initial contribution
 */
@NonNullByDefault
@Component(service = ThingHandlerFactory.class, configurationPid = "binding.pbus")
public class PbusHandlerFactory extends BaseThingHandlerFactory {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final SerialPortManager serialPortManager;

    @Activate
    public PbusHandlerFactory(final @Reference SerialPortManager serialPortManager,
            final @Reference ItemRegistry itemRegistry) {
        this.serialPortManager = serialPortManager;
    }

    // Bij nieuw thing kijk of deze wordt ondersteund. Zo ja wordt, door systeem, createHamdler aangeroepen
    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_BRIDGE_UIDS.contains(thingTypeUID) || SUPPORTED_THING_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();
        ThingHandler thingHandler = null;

        if (SUPPORTED_BRIDGE_UIDS.contains(thingTypeUID)) {
            PbusBridgeHandler pbusBridgeHandler;
            if (thingTypeUID.equals(BRIDGE_NETWORK)) {
                pbusBridgeHandler = new PbusNetworkBridgeHandler((Bridge) thing);
            } else {
                pbusBridgeHandler = new PbusSerialBridgeHandler((Bridge) thing, serialPortManager);
            }
            thingHandler = pbusBridgeHandler;

        } else if (Pbus2cHandler.SUPPORTED_THING_TYPES.contains(thingTypeUID)) {
            thingHandler = new Pbus2cHandler(thing);
        } else if (PbusDIHandler.SUPPORTED_THING_TYPES.contains(thingTypeUID)) {
            thingHandler = new PbusDIHandler(thing);
        } else if (PbusAIHandler.SUPPORTED_THING_TYPES.contains(thingTypeUID)) {
            thingHandler = new PbusAIHandler(thing);
        } else if (PbusDOHandler.SUPPORTED_THING_TYPES.contains(thingTypeUID)) {
            thingHandler = new PbusDOHandler(thing);
        } else if (PbusAOHandler.SUPPORTED_THING_TYPES.contains(thingTypeUID)) {
            thingHandler = new PbusAOHandler(thing);
        }

        return thingHandler;
    }
}
