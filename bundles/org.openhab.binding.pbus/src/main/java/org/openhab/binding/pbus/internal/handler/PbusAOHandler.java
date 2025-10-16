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
package org.openhab.binding.pbus.internal.handler;

import static org.openhab.binding.pbus.internal.PbusBindingConstants.*;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.pbus.internal.PbusChannelIdentifier;
import org.openhab.binding.pbus.internal.PbusPacket;
import org.openhab.binding.pbus.internal.config.PbusPositioningConfig;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link PbusAOHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author JWteK - Initial contribution
 */
@NonNullByDefault
public class PbusAOHandler extends PbusThingHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_2Y10S, THING_2Y10M, THING_2Y420);

    private @NonNullByDefault({}) PbusPositioningConfig config;

    public PbusAOHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        this.config = getConfigAs(PbusPositioningConfig.class);

        super.initialize();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {

        PbusBridgeHandler bridge = getPbusBridgeHandler();
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            return;
        }

        if (command instanceof RefreshType) {

            // Haal handstatus op van moduul
            byte addr = getModuleAddress().getAddress();
            PbusPacket packet = new PbusPacket(addr, HAND_STATUS_REQUEST, ALL_CHANNELS);
            bridge.sendPacket(packet.getBytes());

        } else if (command instanceof PercentType percentCommand) {

            // Stuur ON/OFF command naar moduleadres
            byte addr = getModuleAddress().getAddress();
            PbusPacket packet = new PbusPacket(addr, SET_VALUE, ALL_CHANNELS, percentCommand.byteValue());
            bridge.sendPacket(packet.getBytes());

        } else {
            logger.debug("The command '{}' is not supported by this handler.", command.getClass());
        }
    }

    @Override
    protected void performRefreshTick() {
        // nog niet geïmplementeerd
    }

    @Override
    public void onPacketReceived(byte[] packet) {

        if (packet[0] == PbusPacket.STX && packet.length >= 5) {
            byte address = packet[2];
            byte command = packet[4];

            if ((command == HAND_STATUS_ANSWER) && packet.length >= 8) {
                byte channel = packet[5];
                byte dimValue = packet[7];

                PbusChannelIdentifier pbusChannelIdentifier = new PbusChannelIdentifier(address, channel);
                PercentType state = new PercentType(dimValue);
                updateState(getModuleAddress().getChannelId(pbusChannelIdentifier), state);
            }
        }
    }
}
