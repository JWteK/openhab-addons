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
import org.openhab.binding.pbus.internal.PbusPacket;
import org.openhab.binding.pbus.internal.config.PbusRelayConfig;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Channel;
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
 * The {@link PbusDOHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author JWteK - Initial contribution
 */
@NonNullByDefault
public class PbusDOHandler extends PbusThingHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_2Q250, THING_2Q250M, THING_3QM3);

    public PbusDOHandler(Thing thing) {
        super(thing);
    }

    // Wordt aangeroepen door OH aanmaken van thing
    @Override
    public void initialize() {
        // Basis-initialisatie uitvoeren
        super.initialize();

        // Config ophalen
        PbusRelayConfig config = getConfigAs(PbusRelayConfig.class);

        // Haal interval en start refresh
        int interval = Math.max(0, config.refresh);
        logger.debug("Try to start Refresh job");
        startRefreshJob(interval);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {

        PbusBridgeHandler bridge = getPbusBridgeHandler();
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            return;
        }

        byte addr = getModuleAddress().getAddress();

        byte chan = switch (channelUID.getId()) {
            case "port1" -> 1;
            case "port2" -> 2;
            default -> 0;
        };

        switch (command) {
            case RefreshType refreshType -> {

                // Haal feedback op van moduul
                PbusPacket packet = new PbusPacket(addr, HAND_STATUS_REQUEST);
                bridge.sendPacket(packet.getBytes());

            }
            case OnOffType onOffType -> {

                // Stuur ON/OFF command naar moduleadres
                byte comm = switch (onOffType) {
                    case ON -> 1;
                    case OFF -> 0;
                };

                PbusPacket packet = new PbusPacket(addr, SWITCH_RELAY, chan, comm);
                bridge.sendPacket(packet.getBytes());

                logger.debug("Setting ON/OFF command output to state {}", comm);

            }
            case DecimalType decimalType -> {

                // Stuur stage command naar moduleadres
                byte comm = decimalType.byteValue();

                PbusPacket packet = new PbusPacket(addr, SET_VALUE, chan, comm);
                bridge.sendPacket(packet.getBytes());

                logger.debug("Setting 3-stage commandByte output to stage {}", comm);

            }
            default -> logger.debug("The command '{}' is not supported by this handler.", command.getClass());
        }
    }

    @Override
    protected void performRefreshTick() {

        // Bridge ophalen
        PbusBridgeHandler bridge = getPbusBridgeHandler();

        // Geen bridge = offline status
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            return;
        }

        // Statusrequest sturen naar moduleadres
        byte addr = getModuleAddress().getAddress();
        PbusPacket packet = new PbusPacket(addr, HAND_STATUS_REQUEST);

        bridge.sendPacket(packet.getBytes());
        logger.trace("Sent SensorStatusRequest packet to {}", addr);
    }

    @Override
    public void onPacketReceived(byte[] packet) {

        // Zoek het juiste channel
        Channel feedback = getThing().getChannel("hand");

        if (packet[0] == PbusPacket.STX && packet.length >= 5) {

            byte address = packet[1];
            byte command = packet[3];

            // Hand status voor beide kanalen tegelijk
            if (command == HAND_STATUS_ANSWER && packet.length >= 7) {

                int value = (packet[4] & 0xFF) << 8 | (packet[5] & 0xFF);

                // Bepaal bits (LSB = bit 0)
                boolean hand1 = (value & (1 << 0)) != 0;
                boolean hand2 = (value & (1 << 1)) != 0;

                boolean handStatus1 = (value & (1 << 8)) != 0;
                boolean handStatus2 = (value & (1 << 9)) != 0;

                boolean autoStatus1 = (value & (1 << 15)) != 0;
                boolean autoStatus2 = (value & (1 << 14)) != 0;

                // 1️⃣ Mode-channels bijwerken
                updateState(new ChannelUID(getThing().getUID(), "hand1"), new StringType(hand1 ? "HAND" : "AUTO"));
                updateState(new ChannelUID(getThing().getUID(), "hand2"), new StringType(hand2 ? "HAND" : "AUTO"));

                // 2️⃣ Status-channels bijwerken
                OnOffType port1State = hand1 ? (handStatus1 ? OnOffType.ON : OnOffType.OFF)
                        : (autoStatus1 ? OnOffType.ON : OnOffType.OFF);

                OnOffType port2State = hand2 ? (handStatus2 ? OnOffType.ON : OnOffType.OFF)
                        : (autoStatus2 ? OnOffType.ON : OnOffType.OFF);

                updateState(new ChannelUID(getThing().getUID(), "port1"), port1State);
                updateState(new ChannelUID(getThing().getUID(), "port2"), port2State);

            }
        }
    }
}
