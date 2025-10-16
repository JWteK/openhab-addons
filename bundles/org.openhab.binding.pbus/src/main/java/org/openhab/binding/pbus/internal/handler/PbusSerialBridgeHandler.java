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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.TooManyListenersException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.pbus.internal.config.PbusSerialBridgeConfig;
import org.openhab.core.io.transport.serial.PortInUseException;
import org.openhab.core.io.transport.serial.SerialPort;
import org.openhab.core.io.transport.serial.SerialPortEvent;
import org.openhab.core.io.transport.serial.SerialPortEventListener;
import org.openhab.core.io.transport.serial.SerialPortIdentifier;
import org.openhab.core.io.transport.serial.SerialPortManager;
import org.openhab.core.io.transport.serial.UnsupportedCommOperationException;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link PbusSerialBridgeHandler} is the handler for a Pbus Serial-interface and connects it to
 * the framework.
 *
 * @author JWteK - Initial contribution
 */
@NonNullByDefault
public class PbusSerialBridgeHandler extends PbusBridgeHandler implements SerialPortEventListener {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private SerialPortManager serialPortManager;

    private @Nullable SerialPort serialPort;
    private @NonNullByDefault({}) PbusSerialBridgeConfig serialBridgeConfig;

    public PbusSerialBridgeHandler(Bridge pbusBridge, SerialPortManager serialPortManager) {
        super(pbusBridge);
        this.serialPortManager = serialPortManager;
    }

    @Override
    public void initialize() {
        this.serialBridgeConfig = getConfigAs(PbusSerialBridgeConfig.class);

        super.initialize();
    }

    @Override
    public void serialEvent(SerialPortEvent event) {
        logger.debug("(PbusSerialBridgeHandler) Serial port event");

        if (event.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
            readPackets();
        }
    }

    /**
     * Makes a connection to the Pbus system.
     *
     * @return True if the connection succeeded, false if the connection did not succeed.
     */
    @Override
    protected boolean connect() {
        // parse ports and if the port is found, initialize the reader
        SerialPortIdentifier portId = serialPortManager.getIdentifier(serialBridgeConfig.port);

        if (portId == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR, "Port is not known!");
            return false;
        }

        // initialize serial port
        try {

            // Open serial port with timeout of 2000ms
            SerialPort serialPort = portId.open(getThing().getUID().toString(), 2000);

            this.serialPort = serialPort;

            serialPort.setSerialPortParams(serialBridgeConfig.baudrate, SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
                    SerialPort.PARITY_NONE);

            serialPort.enableReceiveThreshold(1);
            serialPort.disableReceiveTimeout();

            OutputStream outputStream = serialPort.getOutputStream();
            InputStream inputStream = serialPort.getInputStream();

            if (outputStream != null && inputStream != null) {
                initializeStreams(outputStream, inputStream);

                serialPort.addEventListener(this);
                serialPort.notifyOnDataAvailable(true);

                updateStatus(ThingStatus.ONLINE);
                logger.debug("Bridge online on serial port {}", serialBridgeConfig.port);
                return true;
            }
        } catch (final IOException ex) {
            onConnectionLost();
            logger.debug("I/O error on serial port {}", serialBridgeConfig.port);
        } catch (PortInUseException e) {
            onConnectionLost();
            logger.debug("Port {} is in use", serialBridgeConfig.port);
        } catch (TooManyListenersException e) {
            onConnectionLost();
            logger.debug("Cannot attach listener to port {}", serialBridgeConfig.port);
        } catch (UnsupportedCommOperationException e) {
            logger.debug("Unsupported exception on port {}", serialBridgeConfig.port);
        }

        return false;
    }

    @Override
    protected void disconnect() {
        final SerialPort serialPort = this.serialPort;
        if (serialPort != null) {
            serialPort.removeEventListener();
            serialPort.close();
            this.serialPort = null;
        }

        super.disconnect();
    }
}
