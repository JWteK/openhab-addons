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

import static org.openhab.binding.pbus.internal.PbusBindingConstants.DIGITAL_STATUS_REQUEST;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.pbus.internal.PbusModuleAddress;
import org.openhab.binding.pbus.internal.PbusPacket;
import org.openhab.binding.pbus.internal.PbusPacketListener;
import org.openhab.binding.pbus.internal.config.PbusThingConfig;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base ThingHandler for all Pbus handlers.
 *
 * @author JWteK - Initial contribution
 */
@NonNullByDefault
public abstract class PbusThingHandler extends BaseThingHandler implements PbusPacketListener {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /** Configuratie van dit Thing (wordt gevuld tijdens initialize()) */
    protected @Nullable PbusThingConfig pbusThingConfig;

    /** Bridge handler: koppeling naar het fysieke Pbus netwerk */
    private @Nullable PbusBridgeHandler pbusBridgeHandler;

    /** Moduleadres van dit device (gebaseerd op config) */
    private @Nullable PbusModuleAddress pbusModuleAddress;

    /** Scheduler voor periodieke refresh jobs */
    protected @Nullable ScheduledFuture<?> refreshJob;

    public PbusThingHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing {}", getThing().getUID());

        // Configuratie laden uit UI/YAML
        this.pbusThingConfig = getConfigAs(PbusThingConfig.class);

        // Ophalen bridge (kan null zijn)
        Bridge bridge = getBridge();

        // Thing initialiseren obv status van de bridge
        initializeThing(bridge == null ? ThingStatus.OFFLINE : bridge.getStatus());

        // Labels en states van channels initialiseren
        initializeChannelNames();
        initializeChannelStates();
    }

    @Override
    public void handleRemoval() {
        // Bij verwijderen: listeners deregistreren bij bridge
        if (pbusBridgeHandler != null && pbusModuleAddress != null) {
            for (byte address : pbusModuleAddress.getActiveAddresses()) {
                pbusBridgeHandler.unregisterPacketListener(address);
            }
        }
        super.handleRemoval();
    }

    @Override
    public void dispose() {
        // Scheduler netjes stoppen
        if (refreshJob != null && !refreshJob.isCancelled()) {
            refreshJob.cancel(true);
            logger.debug("Cancelled refresh job for {}", getThing().getUID());
        }
        super.dispose();
    }

    @Override
    public void channelLinked(ChannelUID channelUID) {
        // BaseThninHandler throws a refresh for every linked channel. To avoid refresh, this override
        logger.debug("Channel {} is linked", channelUID);
    }

    @Override
    public void channelUnlinked(ChannelUID channelUID) {
        // BaseThninHandler does nothing this is just for info
        logger.debug("Channel {} is unlinked", channelUID);
    }

    /**
     * Start een periodieke scheduler die {@link #performRefreshTick()} aanroept.
     */
    protected void startRefreshJob(int intervalSeconds) {

        if (refreshJob != null && !refreshJob.isCancelled()) {
            logger.debug("Refresh already running for {}", getThing().getUID());
            return;
        }

        if (intervalSeconds == 0) {
            logger.debug("Refresh disabled (interval = 0) for {}", getThing().getUID());
            return;
        }

        if (intervalSeconds == 1) {

            scheduler.execute(() -> {
                try {
                    performRefreshTick();
                } catch (Exception e) {
                    logger.warn("Exception during initial refresh for {}", getThing().getUID(), e);
                }
            });

            logger.debug("Initial refresh send (interval = 1) for {}", getThing().getUID());

            return;
        }

        // Start new refresh job
        refreshJob = scheduler.scheduleWithFixedDelay(() -> {
            try {
                performRefreshTick();
            } catch (Exception e) {
                logger.warn("Exception during refresh for {}", getThing().getUID(), e);
            }
        }, 0, intervalSeconds, TimeUnit.SECONDS);

        logger.debug("Refresh job ({}s) started for {}", intervalSeconds, getThing().getUID());
    }

    /** Door subklassen te implementeren: refresh-gedrag (bijv. statusrequest sturen). */
    protected abstract void performRefreshTick();

    /**
     * Retourneert het moduleadres. Gooit exception als dit nog niet gezet is.
     */
    protected PbusModuleAddress getModuleAddress() {
        return java.util.Objects.requireNonNull(pbusModuleAddress, "PbusModuleAddress is not initialized yet.");
    }

    /**
     * Update het label van een bestaand channel met een nieuwe naam.
     */
    protected void updateChannelLabel(ChannelUID channelUID, String channelName) {
        Channel current = thing.getChannel(channelUID.getId());
        if (current == null)
            return;

        // Description mag niet null zijn, fallback op lege string
        String desc = current.getDescription();
        if (desc == null) {
            desc = "";
        }

        // Nieuw channel object opbouwen met zelfde config, maar nieuw label
        Channel updated = ChannelBuilder.create(channelUID, current.getAcceptedItemType())
                .withConfiguration(current.getConfiguration()).withDefaultTags(current.getDefaultTags())
                .withDescription(desc).withKind(current.getKind()).withLabel(channelName)
                .withProperties(current.getProperties()).withType(current.getChannelTypeUID()).build();

        // Thing updaten: oud channel eruit, nieuwe erin
        ThingBuilder builder = editThing();
        builder.withoutChannel(channelUID).withChannel(updated);
        updateThing(builder.build());
    }

    /**
     * Initialisatie van Thing incl. registratie bij de bridge.
     */
    private void initializeThing(ThingStatus bridgeStatus) {
        this.pbusModuleAddress = createPbusModuleAddress();

        if (this.pbusModuleAddress == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR, "Address is not known!");
            return;
        }

        logger.debug("Thing {} initialized with address {}", getThing().getUID(),
                String.format("%d", pbusModuleAddress.getAddress()));

        // Koppel status van bridge door naar dit Thing
        PbusBridgeHandler bridgeHandler = getPbusBridgeHandler();
        if (bridgeHandler != null) {
            if (bridgeStatus == ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            }
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }

    /**
     * Maakt een moduleadres op basis van configuratie.
     */
    protected @Nullable PbusModuleAddress createPbusModuleAddress() {
        if (pbusThingConfig != null) {
            byte address = Byte.parseByte(pbusThingConfig.address);
            return new PbusModuleAddress(address);
        }
        return null;
    }

    /**
     * Past kanaallabels aan obv configuratie.
     */
    private void initializeChannelNames() {
        for (Channel channel : getThing().getChannels()) {
            String id = channel.getUID().getIdWithoutGroup();

            // Als er in config een label is opgegeven, overschrijven
            if (getConfig().containsKey(id)) {
                String newLabel = getConfig().get(id).toString();
                if (!newLabel.equals(channel.getLabel())) {
                    updateChannelLabel(channel.getUID(), newLabel);
                }
            }
        }
    }

    /**
     * Vraagt initiële status van het device op via de bridge.
     */
    private void initializeChannelStates() {
        if (pbusBridgeHandler != null) {

            // Statusrequest sturen naar moduleadres
            byte addr = getModuleAddress().getAddress();
            PbusPacket packet = new PbusPacket(addr, DIGITAL_STATUS_REQUEST);
        }
    }

    /**
     * Haalt of initialiseert de bridge handler en registreert listeners.
     */
    protected synchronized @Nullable PbusBridgeHandler getPbusBridgeHandler() {

        if (pbusBridgeHandler != null)
            return pbusBridgeHandler;

        Bridge bridge = getBridge();
        if (bridge == null)
            return null;

        ThingHandler handler = bridge.getHandler();
        if (handler instanceof PbusBridgeHandler bridgeHandler) {
            this.pbusBridgeHandler = bridgeHandler;

            // Listener registreren voor actieve adressen
            if (pbusModuleAddress != null) {
                for (byte address : pbusModuleAddress.getActiveAddresses()) {
                    bridgeHandler.registerPacketListener(address, this);
                }
            }
        }
        return pbusBridgeHandler;
    }
}
