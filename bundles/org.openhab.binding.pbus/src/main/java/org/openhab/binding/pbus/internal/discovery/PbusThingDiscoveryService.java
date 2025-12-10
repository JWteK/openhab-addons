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
package org.openhab.binding.pbus.internal.discovery;

import static org.openhab.binding.pbus.internal.PbusBindingConstants.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.pbus.internal.PbusModule;
import org.openhab.binding.pbus.internal.PbusModuleAddress;
import org.openhab.binding.pbus.internal.PbusPacket;
import org.openhab.binding.pbus.internal.PbusPacketListener;
import org.openhab.binding.pbus.internal.handler.PbusBridgeHandler;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * If the user scans manually for things this
 * {@link PbusThingDiscoveryService}
 * is used to return Pbus Modules as things to the framework.
 *
 * @author JWteK - Initial contribution
 */
@NonNullByDefault
public class PbusThingDiscoveryService extends AbstractDiscoveryService
        implements DiscoveryService, ThingHandlerService, PbusPacketListener {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private Map<Byte, PbusModule> pbusModules = new HashMap<>();

    private @Nullable PbusBridgeHandler pbusBridgeHandler;

    public PbusThingDiscoveryService() {
        super(SUPPORTED_THING_UIDS, SEARCH_TIME);
    }

    @Override
    public void deactivate() {
        removeOlderResults(Instant.now());

        final PbusBridgeHandler pbusBridgeHandler = this.pbusBridgeHandler;
        if (pbusBridgeHandler != null) {
            pbusBridgeHandler.clearDefaultPacketListener();
        }
    }

    // Scan ... Send Module type reguest to all 64 adresses (Progress bar time in SEARCH_TIME constant)
    @Override
    protected void startScan() {

        final PbusBridgeHandler pbusBridgeHandler = this.pbusBridgeHandler;

        for (int i = 1; i <= 64; i++) {

            PbusPacket packet = new PbusPacket((byte) i, MODULE_TYPE_REQUEST);

            if (pbusBridgeHandler != null) {
                pbusBridgeHandler.sendPacket(packet.getBytes());
            }
        }
    }

    @Override
    // Called when receiving a not existing adress - Register new thing
    public void onPacketReceived(byte[] packet) {

        byte address = packet[1];
        byte command = packet[3];

        // Check if length is correct
        if (packet.length != 7) {
            logger.debug("onPacketReceived called to register with wrong length");
            return;
        }

        if (command != MODULE_TYPE_ANSWER) {
            logger.error("Packet has wrong command '{}' instead of {}", String.format("%02X", command),
                    String.format("%02X", MODULE_TYPE_ANSWER));
            return;
        }

        handleModuleTypeCommand(packet, address);
    }

    private void handleModuleTypeCommand(byte[] packet, byte address) {
        byte moduleType = packet[4];

        PbusModule pbusModule = switch (moduleType) {
            default -> null;
            case MODULE_2C -> new PbusModule(new PbusModuleAddress(address), moduleType, THING_2C);
            case MODULE_2D20 -> new PbusModule(new PbusModuleAddress(address), moduleType, THING_2D20);
            case MODULE_2R1K -> new PbusModule(new PbusModuleAddress(address), moduleType, THING_2R1K);
            case MODULE_2Y10 -> new PbusModule(new PbusModuleAddress(address), moduleType, THING_2Y10);
            case MODULE_2U10 -> new PbusModule(new PbusModuleAddress(address), moduleType, THING_2U10);
            case MODULE_2Y10M -> new PbusModule(new PbusModuleAddress(address), moduleType, THING_2Y10M);
            case MODULE_2P100 -> new PbusModule(new PbusModuleAddress(address), moduleType, THING_2P100);
            case MODULE_2Y420 -> new PbusModule(new PbusModuleAddress(address), moduleType, THING_2Y420);
            case MODULE_2I25 -> new PbusModule(new PbusModuleAddress(address), moduleType, THING_2I25);
            case MODULE_4D20 -> new PbusModule(new PbusModuleAddress(address), moduleType, THING_4D20);
            case MODULE_2P1K -> new PbusModule(new PbusModuleAddress(address), moduleType, THING_2P1K);
            case MODULE_2I420 -> new PbusModule(new PbusModuleAddress(address), moduleType, THING_2I420);
            case MODULE_2Q250 -> new PbusModule(new PbusModuleAddress(address), moduleType, THING_2Q250);
            case MODULE_2Q250M -> new PbusModule(new PbusModuleAddress(address), moduleType, THING_2Q250M);
            case MODULE_2D42 -> new PbusModule(new PbusModuleAddress(address), moduleType, THING_2D42);
            case MODULE_3QM3 -> new PbusModule(new PbusModuleAddress(address), moduleType, THING_3QM3);
            case MODULE_2D250 -> new PbusModule(new PbusModuleAddress(address), moduleType, THING_2D250);
        };

        if (pbusModule != null) {
            registerPbusModule(address, pbusModule);
        } else {
            logger.error("Packet has wrong module type '{}'", String.format("%02X", moduleType));

        }
    }

    protected void registerPbusModule(byte address, PbusModule pbusModule) {
        pbusModules.put(address, pbusModule);
        notifyDiscoveredPbusModule(pbusModule);
    }

    private void notifyDiscoveredPbusModule(PbusModule pbusModule) {
        final PbusBridgeHandler pbusBridgeHandler = this.pbusBridgeHandler;
        if (pbusBridgeHandler != null) {

            ThingUID bridgeUID = pbusBridgeHandler.getThing().getUID();

            DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(pbusModule.getThingUID(bridgeUID))
                    .withThingType(pbusModule.getThingTypeUID()).withProperties(pbusModule.getProperties())
                    .withRepresentationProperty("address").withBridge(bridgeUID).withLabel(pbusModule.getLabel())
                    .build();

            thingDiscovered(discoveryResult);
        }
    }

    @Override
    public void setThingHandler(@Nullable ThingHandler handler) {
        if (handler instanceof PbusBridgeHandler) {
            final PbusBridgeHandler pbusBridgeHandler = (PbusBridgeHandler) handler;
            this.pbusBridgeHandler = pbusBridgeHandler;
            pbusBridgeHandler.setDefaultPacketListener(this);
        }
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return this.pbusBridgeHandler;
    }
}
