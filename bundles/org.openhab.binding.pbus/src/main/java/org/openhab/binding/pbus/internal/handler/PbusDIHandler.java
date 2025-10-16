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

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.pbus.internal.PbusPacket;
import org.openhab.binding.pbus.internal.config.PbusSensorConfig;
import org.openhab.core.library.types.OpenClosedType;
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
 * The {@link PbusDIHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author JWteK - Initial contribution
 */
@NonNullByDefault
public class PbusDIHandler extends PbusThingHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /** Ondersteunde sensortypen (thing types) */
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_2D20, THING_4D20, THING_2D42,
            THING_2D250);

    public PbusDIHandler(Thing thing) {
        super(thing);
    }

    // Wordt aangeroepen door OH aanmaken van thing
    @Override
    public void initialize() {
        // Basis-initialisatie uitvoeren
        super.initialize();

        // Config ophalen
        PbusSensorConfig config = getConfigAs(PbusSensorConfig.class);

        // Haal interval en start refresh
        int interval = Math.max(0, config.refresh);
        logger.debug("Try to start Refresh job");
        startRefreshJob(interval);
    }

    // Wordt aangeroepen door OH refresh of commando vanuit UI of rules
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {

        // Alleen RefreshType commando’s afhandelen
        if (!(command instanceof RefreshType)) {
            return;
        }
        performRefreshTick();
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
        PbusPacket packet = new PbusPacket(addr, SENSOR_STATUS_REQUEST);

        bridge.sendPacket(packet.getBytes());
        logger.trace("Sent SensorStatusRequest packet to {}", addr);
    }

    /**
     * Wordt aangeroepen wanneer er een pakket met data is ontvangen.
     * Elk kanaal wordt bijgewerkt afhankelijk van het type:
     */
    // TODO: Misschien de waarden van de digitale ingangen in 1 byte inlezen
    @Override
    public void onPacketReceived(byte[] packet) {

        // Controleren of het commando klopt
        if (packet[3] != SENSOR_STATUS_ANSWER) {
            logger.debug("onPacketReceived called for {} with wrong command", getThing().getUID());
            return;
        }

        List<Channel> channels = getThing().getChannels();

        logger.debug("Received packet ({} bytes) for thing {} with {} channels", packet.length, getThing().getUID(),
                channels.size());

        for (int chIdx = 0; chIdx < channels.size(); chIdx++) {

            Channel channel = channels.get(chIdx);
            ChannelUID uid = channel.getUID();
            String itemType = channel.getAcceptedItemType();

            switch (itemType) {

                case "Contact": {
                    // Elke poort heeft één byte in packet[4 + chIdx]
                    int byteIndex = 4 + chIdx;
                    if (byteIndex < packet.length) {
                        byte value = packet[byteIndex];
                        OpenClosedType state = (value == 0) ? OpenClosedType.OPEN : OpenClosedType.CLOSED;
                        updateState(uid, state);
                        logger.debug("Updated Contact channel {} (byte[{}]={}) → {}", uid, byteIndex, value, state);
                    } else {
                        logger.warn("Packet too short for Contact port {}", chIdx);
                    }
                    break;
                }

                case null:
                    logger.warn("No Itemtype for channel {}", uid);
                    break;

                default:
                    logger.warn("Unsupported item type '{}' for channel {}", itemType, uid);
                    break;
            }
        }
    }
}
