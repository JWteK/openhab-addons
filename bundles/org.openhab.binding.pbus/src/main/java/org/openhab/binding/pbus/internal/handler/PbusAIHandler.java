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

import static org.openhab.binding.pbus.internal.PbusBindingConstants.SENSOR_STATUS_ANSWER;
import static org.openhab.binding.pbus.internal.PbusBindingConstants.SENSOR_STATUS_REQUEST;
import static org.openhab.binding.pbus.internal.PbusBindingConstants.THING_2I25;
import static org.openhab.binding.pbus.internal.PbusBindingConstants.THING_2I420;
import static org.openhab.binding.pbus.internal.PbusBindingConstants.THING_2P100;
import static org.openhab.binding.pbus.internal.PbusBindingConstants.THING_2P1K;
import static org.openhab.binding.pbus.internal.PbusBindingConstants.THING_2R1K;
import static org.openhab.binding.pbus.internal.PbusBindingConstants.THING_2U10;

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.pbus.internal.PbusPacket;
import org.openhab.binding.pbus.internal.config.PbusSensorConfig;
import org.openhab.core.library.types.DecimalType;
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
 * The {@link PbusAIHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author JWteK - Initial contribution
 */
@NonNullByDefault
public class PbusAIHandler extends PbusThingHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /** Ondersteunde sensortypen (thing types) */
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_2R1K, THING_2U10, THING_2P100,
            THING_2I25, THING_2P1K, THING_2I420);

    public PbusAIHandler(Thing thing) {
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

                case "Number": {
                    // Elke poort heeft 2 bytes in packet[4 + (chIdx * 2)] en packet[5 + (chIdx * 2)]
                    int byteIndex = 4 + (chIdx * 2);
                    if (byteIndex + 1 < packet.length) {

                        // Combineer 2 bytes (high + low)
                        int analogValue = ((packet[byteIndex] & 0xFF) << 8) | (packet[byteIndex + 1] & 0xFF);

                        // Zet bit 15 op 0
                        analogValue = analogValue & 0x7FFF;

                        // Schuif de 13 bits waarde 2 plaatsen op en voeg een 0 in
                        analogValue = analogValue >>> 2;

                        // Als Moduul een 2r1k (12 bits) is dan 1 plaats extra opschuiven zodat alles 13 bits wordt
                        if (uid.toString().contains("2r1k")) {
                            analogValue = analogValue >>> 1;
                        }

                        updateState(uid, new DecimalType(analogValue));
                        logger.debug("Updated Number channel {} (bytes[{}-{}]) → {}", uid, byteIndex, byteIndex + 1,
                                analogValue);

                    } else {
                        logger.warn("Packet too short for Number port {}", chIdx);
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
