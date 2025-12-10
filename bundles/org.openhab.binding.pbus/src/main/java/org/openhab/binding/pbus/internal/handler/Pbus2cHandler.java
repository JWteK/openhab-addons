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
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.pbus.internal.PbusPacket;
import org.openhab.binding.pbus.internal.config.PbusBasicConfig;
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
 * The {@link Pbus2cHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author JWteK - Initial contribution
 */
@NonNullByDefault
public class Pbus2cHandler extends PbusThingHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    // Things handled by this class
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_2C);

    private @Nullable PbusBasicConfig config;

    public Pbus2cHandler(Thing thing) {
        super(thing);
    }

    // Called by OH core when creating thing
    @Override
    public void initialize() {

        // Basic init
        super.initialize();

        // Get config
        config = getConfigAs(PbusBasicConfig.class);

        // Start refresh job if needed
        int interval = Math.max(0, config.refresh);
        logger.debug("Try to start Refresh job");
        startRefreshJob(interval);

        // If not exist create counter properties to store cuurent values
        Map<String, String> props = editProperties();
        props.putIfAbsent("port1LastValue", "0");
        props.putIfAbsent("port2LastValue", "0");
        updateProperties(props);

        // Get current values from counterproperties
        String p1 = getThing().getProperties().get("port1LastValue");
        if (p1 != null) {
            updateState("port1", new DecimalType(Long.parseLong(p1)));
        }

        String p2 = getThing().getProperties().get("port2LastValue");
        if (p2 != null) {
            updateState("port2", new DecimalType(Long.parseLong(p2)));
        }
    }

    // Called by OH core on refresh tick and manual refresh
    @Override
    protected void performRefreshTick() {

        // Check if bridge and handler OK
        PbusBridgeHandler bridge = getPbusBridgeHandler();
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            return;
        }

        // Request Counter status
        byte addr = getModuleAddress().getAddress();
        PbusPacket packet = new PbusPacket(addr, ANALOG_STATUS_REQUEST);
        bridge.sendPacket(packet.getBytes());
        logger.trace("Sent CouterStatusRequest packet to {}", addr);
    }

    // Called by OH core from UI or Rule
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {

        // Check if bridge and handler OK
        PbusBridgeHandler bridge = getPbusBridgeHandler();
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            return;
        }

        switch (command) {

            // Used to perform a manual refresh
            case RefreshType refreshType -> {
                performRefreshTick();
            }
            // Used to set initial counter value from rule or UI
            case DecimalType decimalType -> {

                String chId = channelUID.getId();

                // Set new value
                updateState(chId, new DecimalType(decimalType.longValue()));

                // Store same value in thing properties
                Map<String, String> props = editProperties();
                props.put(chId + "LastValue", Long.toString(decimalType.longValue()));
                updateProperties(props);
            }
            default -> logger.debug("The command '{}' is not supported by this handler.", command.getClass());
        }
    }

    // Called when a data package is arrived for this handler
    @Override
    public void onPacketReceived(byte[] packet) {

        byte address = packet[1];
        byte command = packet[3];

        // Check if module must be removed, remove module from listners and set to offline
        if ((packet.length == 7) & (command == MODULE_REMOVED)) {
            PbusBridgeHandler bridge = getPbusBridgeHandler();
            if (bridge != null) {
                // Remove from packetlistners
                bridge.unregisterPacketListener(address);
            }
            // Set to OffLine (Returns to OnLine after restart, so remove manualy)
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, "(Remove thing manualy)");
            return;
        }

        // Check if length is correct
        if (packet.length != 10) {
            logger.debug("onPacketReceived called with wrong length for {}", getThing().getUID());
            return;
        }

        // Check if command is correct
        if (command != ANALOG_STATUS_ANSWER) {
            logger.debug("onPacketReceived called for {} with wrong command", getThing().getUID());
            return;
        }

        // Get all channels from thing
        List<Channel> channels = getThing().getChannels();

        logger.debug("Received packet ({} bytes) for thing {} with {} channels", packet.length, getThing().getUID(),
                channels.size());

        // Loop through all channels
        for (Channel channel : channels) {

            ChannelUID chUid = channel.getUID();
            String chId = chUid.getId();

            long raw = 0;

            switch (chId) {
                case "port1" -> {
                    raw = ((packet[4] & 0xFFL) << 8) | (packet[5] & 0xFFL);
                }
                case "port2" -> {
                    raw = ((packet[6] & 0xFFL) << 8) | (packet[7] & 0xFFL);
                }
                default -> {
                    logger.debug("Unexpected channel {} in", chUid);
                    return;
                }
            }

            // Update only if channel is linked
            if (isLinked(chId)) {

                // Get the old value and add the new received value
                long oldValue = Long.parseLong(getThing().getProperties().getOrDefault(chId + "LastValue", "0"));
                long totalValue = oldValue + raw;

                // Update state and propertie
                updateState(chUid, new DecimalType(totalValue));

                Map<String, String> props = editProperties();
                props.put(chId + "LastValue", Long.toString(totalValue));
                updateProperties(props);

                logger.debug("Updated {} to {}", chUid, totalValue);
            } else {
                logger.debug("Channel {} not linked", chUid);
            }
        }
    }
}
