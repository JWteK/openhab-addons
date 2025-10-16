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
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.pbus.internal.PbusPacket;
import org.openhab.binding.pbus.internal.config.Pbus2cConfig;
import org.openhab.core.library.types.DecimalType;
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

    /** Ondersteunde sensortypen (thing types) */
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_2C);

    /** Configuratie voor de 2C module */
    private @Nullable Pbus2cConfig config;

    public Pbus2cHandler(Thing thing) {
        super(thing);
    }

    // Round given number to 2 decimal places
    public static double rnd(double num, int dec) {
        return Math.round(num * Math.pow(10, dec)) / Math.pow(10, dec);
    }

    // Wordt aangeroepen door OH aanmaken van thing
    @Override
    public void initialize() {
        // Basis-initialisatie uitvoeren
        super.initialize();

        // Config ophalen
        this.config = getConfigAs(Pbus2cConfig.class);

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
        PbusPacket packet = new PbusPacket(addr, COUNTER_STATUS_REQUEST);

        bridge.sendPacket(packet.getBytes());
        logger.trace("Sent CouterStatusRequest packet to {}", addr);
    }

    @Override
    public void onPacketReceived(byte[] packet) {

        // Controleren of het commando klopt
        if (packet[3] != COUNTER_STATUS_ANSWER) {
            logger.debug("onPacketReceived called for {} with wrong command", getThing().getUID());
            return;
        }

        // Controleren of de lengte klopt
        if (packet.length != 11) {
            logger.debug("onPacketReceived called for {} with wrong length", getThing().getUID());
            return;
        }

        logger.debug("onPacketReceived called for {}", getThing().getUID());

        // Kanaal bepalen (1 of 2)
        int channel = packet[4];

        // Counterwaarde (4 bytes samenvoegen als unsigned long)
        long raw = ((packet[5] & 0xFFL) << 24) | ((packet[6] & 0xFFL) << 16) | ((packet[7] & 0xFFL) << 8)
                | (packet[8] & 0xFFL);

        // Afhankelijk van het kanaal de juiste berekening doen
        switch (channel) {
            case 1 -> {

                // Maak Channel UID voor counter 1
                ChannelUID cntr1 = new ChannelUID(thing.getUID(), "port1");

                // Kanaal 1: pulsen per unit bepalen (standaard of custom) en afronding
                double ppu = config.port1Ppu != 0 ? config.port1Ppu : config.port1PpuCustom;
                int round = config.port1Round;

                // Waarde berekenen en afronden
                double value = rnd(raw / ppu, round);

                // State updaten en loggen
                updateState(cntr1, new DecimalType(value));
                logger.debug("Updated {} to {}", cntr1, String.format("%." + round + "f", value));
            }
            case 2 -> {

                // Maak Channel UID voor counter 2
                ChannelUID cntr2 = new ChannelUID(thing.getUID(), "port2");

                // Kanaal 2:pulsen per unit bepalen (standaard of custom) en afronding
                double ppu = config.port2Ppu != 0 ? config.port2Ppu : config.port2PpuCustom;
                int round = config.port2Round;

                // Waarde berekenen en afronden
                double value = rnd(raw / ppu, round);

                // State updaten en loggen
                updateState(cntr2, new DecimalType(value));
                logger.debug("Updated {} to {}", cntr2, String.format("%." + round + "f", value));
            }

            default -> {
                // Onbekend kanaal → waarschuwing loggen
                logger.warn("Unexpected channel {} in COUNTER_STATUS_ANSWER", channel);
            }
        }
    }
}
