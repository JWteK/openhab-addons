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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.pbus.internal.PbusPacket;
import org.openhab.binding.pbus.internal.config.PbusAOConfig;
import org.openhab.core.library.types.PercentType;
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
 * The {@link PbusAOHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author JWteK - Initial contribution
 */
@NonNullByDefault
public class PbusAOHandler extends PbusThingHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    // Things handled by this class
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_2Y10, THING_2Y10M, THING_2Y420);

    private @NonNullByDefault({}) PbusAOConfig config;

    private @Nullable ScheduledFuture<?> levelTimer;

    public PbusAOHandler(Thing thing) {
        super(thing);
    }

    // Called by OH core when creating thing
    @Override
    public void initialize() {

        // Run Initialize of ThingHandler
        super.initialize();

        config = getConfigAs(PbusAOConfig.class);

        // Start refresh job if needed
        int interval = Math.max(0, config.refresh);
        logger.debug("Try to start Refresh job");
        startRefreshJob(interval);
    }

    @Override
    public void dispose() {
        if (levelTimer != null) {
            levelTimer.cancel(false);
            levelTimer = null;
        }
        // Run dispose of ThingHandler
        super.dispose();
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

        // Request feedback status
        byte addr = getModuleAddress().getAddress();
        PbusPacket packet = new PbusPacket(addr, FEEDBACK_REQUEST);
        bridge.sendPacket(packet.getBytes());
        logger.debug("Sent Feedback Request packet to {}", addr);
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
            case PercentType percent -> {

                byte addr = getModuleAddress().getAddress();
                int delay = config.debounce;
                byte percVal = (byte) Math.min(240, Math.max(0, Math.round(percent.byteValue() * 2.4f)));

                // Cancel running tast
                if (levelTimer != null) {
                    levelTimer.cancel(false);
                }

                // Make scheduled task (Code betweem {} is task)
                levelTimer = scheduler.schedule(() -> {

                    PbusPacket packet = new PbusPacket(addr, SET_VALUE, percVal);
                    bridge.sendPacket(packet.getBytes());

                }, delay, TimeUnit.MILLISECONDS);

            }
            default -> logger.debug("The command '{}' is not supported by this handler.", command.getClass());

        }
    }

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
        if (packet.length != 8) {
            logger.debug("onPacketReceived called with wrong length for {}", getThing().getUID());
            return;
        }

        // Check if command is correct
        if (command != FEEDBACK_ANSWER) {
            logger.debug("onPacketReceived called for {} with wrong command", getThing().getUID());
            return;
        }

        List<Channel> channels = getThing().getChannels();

        logger.debug("Received packet ({} bytes) for thing {} with {} channels", packet.length, getThing().getUID(),
                channels.size());

        // Get values from received data
        int feedback1 = (packet[4] & 0xFF);
        int feedback2 = (packet[5] & 0xFF);

        // Mode (Bit 4) 0=AUTO 1=HAND
        boolean mode1 = (feedback1 & 16) != 0;
        boolean mode2 = (feedback2 & 16) != 0;

        // Loop through all channels
        for (Channel channel : channels) {

            ChannelUID chUid = channel.getUID();
            String chId = chUid.getId();

            switch (chId) {
                case "mode1" -> {
                    updateState(chUid, new StringType(mode1 ? "HAND" : "AUTO"));
                }
                case "mode2" -> {
                    updateState(chUid, new StringType(mode2 ? "HAND" : "AUTO"));
                }
            }
        }
    }
}
