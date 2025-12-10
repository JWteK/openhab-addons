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

    // Things handled by this class
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_2Q250, THING_2Q250M, THING_3QM3);

    private @Nullable PbusBasicConfig config;

    public PbusDOHandler(Thing thing) {
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

        // Request feedback status
        byte addr = getModuleAddress().getAddress();
        PbusPacket packet = new PbusPacket(addr, FEEDBACK_REQUEST);
        bridge.sendPacket(packet.getBytes());
        logger.trace("Sent Feedback Request packet to {}", addr);
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

        byte addr = getModuleAddress().getAddress();

        byte chan = switch (channelUID.getId()) {
            case "port1" -> 1;
            case "port2" -> 2;
            default -> 0;
        };

        switch (command) {

            case RefreshType refreshType -> {
                performRefreshTick();
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
            String modType = chUid.getThingUID().toString().split(":")[1];
            String chId = chUid.getId();

            switch (chId) {
                case "port1" -> {
                    if (modType.equals("2q250m")) {

                        // Get value (0..1) of bit 0
                        boolean Status1 = (feedback1 & 1) != 0;
                        updateState(chUid, (Status1 ? OnOffType.ON : OnOffType.OFF));
                    }
                    if (modType.equals("3qm3")) {

                        // Get values (0..3) of bit 0+1
                        int Status1 = (feedback1 & 3);
                        updateState(chUid, new DecimalType(Status1));
                    }
                }
                case "port2" -> {
                    if (modType.equals("2q250m")) {

                        // Get value (0..1) of bit 0
                        boolean Status2 = (feedback2 & 1) != 0;
                        updateState(chUid, (Status2 ? OnOffType.ON : OnOffType.OFF));
                    }
                }
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
