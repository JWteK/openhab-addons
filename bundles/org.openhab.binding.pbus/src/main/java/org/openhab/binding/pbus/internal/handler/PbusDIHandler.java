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
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.pbus.internal.PbusPacket;
import org.openhab.binding.pbus.internal.config.PbusBasicConfig;
import org.openhab.core.library.types.OnOffType;
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

    // Things handled by this class
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_2D20, THING_4D20, THING_2D42,
            THING_2D250);

    private @Nullable PbusBasicConfig config;

    public PbusDIHandler(Thing thing) {
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

        // Request Input status
        byte addr = getModuleAddress().getAddress();
        PbusPacket packet = new PbusPacket(addr, DIGITAL_STATUS_REQUEST);
        bridge.sendPacket(packet.getBytes());
        logger.trace("Sent DIGITAL_STATUS_REQUEST packet to {}", addr);
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

            case RefreshType refreshType -> {
                performRefreshTick();
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
        if (command != DIGITAL_STATUS_ANSWER) {
            logger.debug("onPacketReceived called for {} with wrong command", getThing().getUID());
            return;
        }

        // Get all channels from thing
        List<Channel> channels = getThing().getChannels();

        logger.debug("Received packet ({} bytes) for thing {} with {} channels", packet.length, getThing().getUID(),
                channels.size());

        // loop through all channels
        for (int chIdx = 0; chIdx < channels.size(); chIdx++) {

            Channel channel = channels.get(chIdx);
            ChannelUID chUid = channel.getUID();

            String itemType = channel.getAcceptedItemType();
            switch (itemType) {

                case "Contact": {
                    // Each channel is 1 byte in packet[4...]
                    int newValue = packet[4 + chIdx] & 1;

                    OpenClosedType state = (newValue == 0) ? OpenClosedType.OPEN : OpenClosedType.CLOSED;
                    updateState(chUid, state);
                    logger.debug("Updated Contact channel {} → {}", chUid, state);
                    break;
                }

                case "Switch": {
                    // Each channel is 1 byte in packet[4 + chIdx]
                    int newValue = packet[4 + chIdx] & 1;

                    OnOffType state = (newValue == 0) ? OnOffType.OFF : OnOffType.ON;
                    updateState(chUid, state);
                    logger.debug("Updated Switch channel {} → {}", chUid, state);
                    break;
                }

                case null:
                    logger.warn("No Item Type for channel {}", chUid);
                    break;

                default:
                    logger.warn("Unsupported Item Type '{}' for channel {}", itemType, chUid);
                    break;
            }
        }
    }
}
