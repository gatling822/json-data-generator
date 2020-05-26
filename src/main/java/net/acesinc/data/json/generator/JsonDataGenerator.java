/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.acesinc.data.json.generator;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.acesinc.data.json.generator.config.SimulationConfig;
import net.acesinc.data.json.generator.config.JSONConfigReader;
import net.acesinc.data.json.generator.log.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pulsar.client.api.PulsarClientException;
import org.eclipse.paho.client.mqttv3.MqttException;

/**
 *
 * @author andrewserff
 */
public class JsonDataGenerator {

    private static final Logger log = LogManager.getLogger(JsonDataGenerator.class);

    private SimulationRunner simRunner;
    private String simConfigFile;

    public JsonDataGenerator(String simConfigString) {
        simConfigFile = simConfigString;
        try {
            log.debug("Creating Simulation Runner using Simulation Config [ " + simConfigString + " ]");
            SimulationConfig simConfig = getSimConfig();
            EventLoggerFactory.getInstance().configure(simConfig);
            List<EventLogger> loggers = new ArrayList<>();
            for (Map<String, Object> elProps : simConfig.getProducers()) {
                String elType = (String) elProps.get("type");
                try {
                    EventLogger logger = EventLoggerFactory.getInstance().getEventLogger(elType, elProps);
                    loggers.add(logger);
                } catch (Exception e) {
                    e.printStackTrace();
                    log.error(e);
                }
            }
            if (loggers.isEmpty()) {
                throw new IllegalArgumentException("You must configure at least one Producer in the Simulation Config");
            }
            simRunner = new SimulationRunner(simConfig, loggers);
        } catch (IOException ex) {
            log.error("Error getting Simulation Config [ " + simConfigString + " ]", ex);
        }
    }

    public void startRunning() {
        simRunner.startSimulation();
    }

    public void stopRunning() {
        simRunner.stopSimulation();
    }

    private SimulationConfig getSimConfig() throws IOException {
        return JSONConfigReader.readConfig(this.getClass().getClassLoader().getResourceAsStream(simConfigFile), SimulationConfig.class);
    }

    public boolean isRunning() {
        return simRunner.isRunning();
    }

    public static void main(String[] args) {
        String simConfig = "defaultSimConfig.json";
        if (args.length > 0) {
            simConfig = args[0];
            log.info("Overriding Simulation Config file from command line to use [ " + simConfig + " ]");
        }

        final JsonDataGenerator gen = new JsonDataGenerator(simConfig);

        final Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                log.info("Shutdown Hook Invoked.  Shutting Down Loggers");
                gen.stopRunning();
                try {
                    mainThread.join();
                } catch (InterruptedException ex) {
                    //oh well
                }
            }
        });

        gen.startRunning();
        while (gen.isRunning()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                //wakie wakie!
            }
        }

    }

    /**
     * @return the simConfigFile
     */
    public String getSimConfigFile() {
        return simConfigFile;
    }

    /**
     * @param simConfigFile the simConfigFile to set
     */
    public void setSimConfigFile(String simConfigFile) {
        this.simConfigFile = simConfigFile;
    }

}
