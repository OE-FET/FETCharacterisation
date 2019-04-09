package Experiment;

import JISA.Control.ConfigStore;
import JISA.Control.Field;
import JISA.Control.SRunnable;
import JISA.Devices.MCSMU;
import JISA.Devices.SMU;
import JISA.Experiment.ResultList;
import JISA.GUI.*;
import JISA.Util;

import java.util.ArrayList;
import java.util.LinkedList;

/**
 * JISA Template Application.
 * <p>
 * Write the code you want to run when the application is run in the "run(...)" method.
 * <p>
 * If you are not going to use any GUI elements, remove "extends GUI" from the line below.
 */
public class Main extends GUI {

    // ==== DEFAULT VALUES =============================================================================================
    private static final double MIN_SD_VOLTAGE            = -5.0;
    private static final double MAX_SD_VOLTAGE            = -60;
    private static final int    STEPS_SD_VOLTAGE          = 2;
    private static final double MIN_G_VOLTAGE             = 0;
    private static final double MAX_G_VOLTAGE             = -60;
    private static final int    STEPS_G_VOLTAGE           = 61;
    private static final double CURRENT_LIMIT             = 1e-3;
    private static final int    AVERAGE_COUNT             = 5;
    private static final double DELAY_TIME                = 0.5;
    private static final double MIN_SD_VOLTAGE_OUTPUT     = 0.0;
    private static final double MAX_SD_VOLTAGE_OUTPUT     = -60.0;
    private static final int    STEPS_SD_VOLTAGE_OUTPUT   = 61;
    private static final double MIN_GATE_VOLTAGE_OUTPUT   = 0.0;
    private static final double MAX_GATE_VOLTAGE_OUTPUT   = -60.0;
    private static final int    STEPS_GATE_VOLTAGE_OUTPUT = 7;
    private static final double INTEGRATION_TIME          = 1D / 50D;

    // ==== Transfer Curve Fields and Results ==========================================================================
    private static Field<Double>  minGateT;
    private static Field<Double>  maxGateT;
    private static Field<Integer> gateStepsT;
    private static Field<Double>  minSDT;
    private static Field<Double>  maxSDT;
    private static Field<Integer> sdStepsT;
    private static Field<Double>  limitT;
    private static Field<Integer> countT;
    private static Field<Double>  delayT;
    private static Field<Double>  intTimeT;
    private static Field<String>  fileT;
    private static Field<Boolean> fourProbeT;
    private static ResultList     transferResults;

    // ==== Output Curve Fields and Results ============================================================================
    private static Field<Double>  minGateO;
    private static Field<Double>  maxGateO;
    private static Field<Integer> gateStepsO;
    private static Field<Double>  minSDO;
    private static Field<Double>  maxSDO;
    private static Field<Integer> sdStepsO;
    private static Field<Double>  limitO;
    private static Field<Integer> countO;
    private static Field<Double>  delayO;
    private static Field<Double>  intTimeO;
    private static Field<String>  fileO;
    private static ResultList     outputResults;

    // ==== Tabs GUI (main window) =====================================================================================
    private static Tabs tabs;

    private static boolean stopFlag = true;

    // ==== Connection Config Handles ==================================================================================
    private static InstrumentConfig<SMU> smu1;
    private static InstrumentConfig<SMU> smu2;
    private static InstrumentConfig<SMU> smu3;
    private static InstrumentConfig<SMU> smu4;

    // ==== SMUs =======================================================================================================
    private static SMUConfig smuSD;
    private static SMUConfig smuG;
    private static SMUConfig smu4P1;
    private static SMUConfig smu4P2;

    // ==== Config Storage and Set-Up ==================================================================================
    private static ConfigStore          config;
    private static ArrayList<SRunnable> smuConfigs = new ArrayList<>();
    private static ConfigGrid  connections;

    /**
     * Runs at start, this is where it all begins.
     *
     * @throws Exception Upon something going wrong
     */
    private static void run(String[] args) throws Exception {

        // Create or load up config file
        config = new ConfigStore("FETCharacterisation");

        // Create results storage
        transferResults = new ResultList("SD Voltage", "Gate Voltage", "Drain Current", "Leakage", "4PP 1", "4PP 2");
        transferResults.setUnits("V", "V", "A", "A", "V", "V");

        outputResults = new ResultList("SD Voltage", "Gate Voltage", "Drain Current", "Leakage");
        outputResults.setUnits("V", "V", "A", "A");

        // Create the tabs which we shall use as the main window (ie everything else gets added to this one way or another)
        tabs = new Tabs("FET Characterisation");

        // Create each section of our GUI
        createConnectionSection();
        createConfigSection();
        createTransferSection();
        createOutputSection();

        // Make sure the window is maximised and show it
        tabs.setMaximised(true);
        tabs.setExitOnClose(true);
        tabs.show();

    }

    /**
     * Creates the "Transfer Curve" tab in the GUI, for controlling and observing transfer curve measurements.
     *
     */
    private static void createTransferSection() {

        // Create config panels
        Fields params = new Fields("Experiment Parameters");
        Fields config = new Fields("Configuration");

        // Results displays
        Table       table         = new Table("Table of Results", transferResults);
        Plot        plot          = new Plot("Transfer Curve", "Gate Voltage [V]", "Drain Current [A]");
        SeriesGroup transferCurve = plot.watchList(transferResults, 1, 2, 0);

        transferCurve.showMarkers(false);

        // Put them all in a grid
        Grid transferGrid = new Grid("Transfer Curve", params, config, table, plot);

        // Add fields to panels, returning Field objects which allow use to query and set the value in each field
        minGateT = params.addDoubleField("Min Gate [V]");
        maxGateT = params.addDoubleField("Max Gate [V]");
        gateStepsT = params.addIntegerField("No. Steps");

        params.addSeparator();

        minSDT = params.addDoubleField("Min SD [V]");
        maxSDT = params.addDoubleField("Max SD [V]");
        sdStepsT = params.addIntegerField("No. Steps");

        countT = config.addIntegerField("Averaging Count");
        delayT = config.addDoubleField("Delay Time [s]");
        intTimeT = config.addDoubleField("Integration Time [s]");
        fileT = config.addFileSave("Output File");
        fourProbeT = config.addCheckBox("Four Probe Measurement?");

        // Set the default values
        minGateT.set(MIN_G_VOLTAGE);
        maxGateT.set(MAX_G_VOLTAGE);
        gateStepsT.set(STEPS_G_VOLTAGE);

        minSDT.set(MIN_SD_VOLTAGE);
        maxSDT.set(MAX_SD_VOLTAGE);
        sdStepsT.set(STEPS_SD_VOLTAGE);

        countT.set(AVERAGE_COUNT);
        delayT.set(DELAY_TIME);
        intTimeT.set(INTEGRATION_TIME);

        // Add toolbar buttons
        transferGrid.addToolbarButton("Start Transfer", Main::doTransfer);
        transferGrid.addToolbarButton("Stop Experiment", Main::stopExperiment);
        transferGrid.addToolbarButton("Clear", () -> {
            transferResults.clear();
            transferCurve.clear();
        });

        transferGrid.setNumColumns(2);
        tabs.add(transferGrid);

    }

    /**
     * Creates the "Output Curve" tab in the GUI, for controlling and observing output curve measurements.
     *
     */
    private static void createOutputSection() {

        // Create config panels
        Fields params = new Fields("Experiment Parameters");
        Fields config = new Fields("Configuration");

        minGateO = params.addDoubleField("Min Gate [V]");
        maxGateO = params.addDoubleField("Max Gate [V]");
        gateStepsO = params.addIntegerField("No. Steps");

        params.addSeparator();

        minSDO = params.addDoubleField("Min SD [V]");
        maxSDO = params.addDoubleField("Max SD [V]");
        sdStepsO = params.addIntegerField("No. Steps");

        countO = config.addIntegerField("Averaging Count");
        delayO = config.addDoubleField("Delay Time [s]");
        intTimeO = config.addDoubleField("Integration Time [s]");
        fileO = config.addFileSave("Output File");

        minGateO.set(MIN_GATE_VOLTAGE_OUTPUT);
        maxGateO.set(MAX_GATE_VOLTAGE_OUTPUT);
        gateStepsO.set(STEPS_GATE_VOLTAGE_OUTPUT);

        minSDO.set(MIN_SD_VOLTAGE_OUTPUT);
        maxSDO.set(MAX_SD_VOLTAGE_OUTPUT);
        sdStepsO.set(STEPS_SD_VOLTAGE_OUTPUT);

        countO.set(AVERAGE_COUNT);
        delayO.set(DELAY_TIME);
        intTimeO.set(INTEGRATION_TIME);

        Table       table       = new Table("Table of Results", outputResults);
        Plot        plot        = new Plot("Output Curve", "SD Voltage [V]", "Drain Current [A]");
        SeriesGroup outputCurve = plot.watchList(outputResults,0,2,1);
        Grid        grid        = new Grid("Output Curve", params, config, table, plot);

        outputCurve.showMarkers(false);

        grid.addToolbarButton("Start Output", Main::doOutput);
        grid.addToolbarButton("Stop Experiment", Main::stopExperiment);
        grid.addToolbarButton("Clear", () -> {
            outputResults.clear();
            outputCurve.clear();
        });

        grid.setNumColumns(2);

        tabs.add(grid);


    }

    /**
     * Creates the "Connection Config" tab in the GUI for configuring how to connect to each instrument.
     */
    private static void createConnectionSection() {

        // Create a ConfigGrid - a grid of Instrument Connection configuration panels
        connections = new ConfigGrid("Connection Config", config);
        connections.setNumColumns(2);

        // Add instruments to configure, returns GetSettable objects for the relevant instrument objects
        smu1 = connections.addInstrument("SMU 1", SMU.class);
        smu2 = connections.addInstrument("SMU 2", SMU.class);
        smu3 = connections.addInstrument("SMU 3", SMU.class);
        smu4 = connections.addInstrument("SMU 4", SMU.class);

        // Add this section to the tabs
        tabs.add(connections);

        // Attempt to connect with whatever config has been loaded from our config storage
        connections.connectAll();

    }

    /**
     * Creates the "Instrument Config" tab in the GUI for configuring which SMU channel should be used for what.
     */
    private static void createConfigSection() {

        smuSD = new SMUConfig("Source-Drain", "sdSMU", config, connections);
        smuG = new SMUConfig("Source-Gate", "sgSMU", config, connections);
        smu4P1 = new SMUConfig("Four-Point-Probe 1", "fpp1SMU", config, connections);
        smu4P2 = new SMUConfig("Four-Point-Probe 2", "fpp2SMU", config, connections);

        Grid grid = new Grid("Instrument Config", smuSD, smuG, smu4P1, smu4P2);
        grid.setNumColumns(2);
        grid.setGrowth(true, false);

        tabs.add(grid);

    }

    /**
     * Notifies all experiments to stop running
     */
    private static void stopExperiment() {

        if (stopFlag) {
            GUI.errorAlert("Warning", "Nothing to Stop", "There is nothing running to stop.");
        }

        stopFlag = true;
    }

    private static void applyChannelConfiguration() throws Exception {

        for (SRunnable r : smuConfigs) {
            r.run();
        }
    }

    /**
     * Performs a transfer curve characterisation, outputting the data to a CSV file.
     *
     * @throws Exception Upon something going wrong
     */
    private static void doTransfer() throws Exception {

        SMU smuSD = Main.smuSD.getSMU();
        SMU smuG  = Main.smuG.getSMU();
        SMU smu4P1 = Main.smu4P1.getSMU();
        SMU smu4P2 = Main.smu4P2.getSMU();

        // Make sure we have applied our config before running
        applyChannelConfiguration();

        // Do we want to use four-point-probe measurements?
        boolean useFourProbe = fourProbeT.get();

        // Create an array list to put errors in
        LinkedList<String> errors = new LinkedList<>();

        // Check that the source-drain and source-gate SMUs are connected and configured
        if (smuSD == null) {
            errors.add("The Source-Drain SMU is not configured.");
        }

        if (smuG == null) {
            errors.add("The Source-Gate SMU is not configured.");
        }

        // If we are using four-point-probe measurements, make sure the two 4PP SMUs are connected and configured
        if ((smu4P1 == null || smu4P2 == null) && useFourProbe) {
            errors.add("To perform four-point-probe measurements, the 4PP SMUs must be configured.");
        }

        // If stopFlag == false then another experiment must be currently running
        if (!stopFlag) {
            errors.add("Another experiment is already running.\n\nPlease wait until it has finished.");
        }

        // Get the value currently in the "Output File" text-box
        String outputFile = fileT.get();

        // Check that it isn't blank
        if (outputFile.trim().equals("")) {
            errors.add("You must specify a file to output to.");
        }

        // If the errors array has something in it, then we can't continue
        if (!errors.isEmpty()) {
            GUI.errorAlert("Error", "Could Not Start Experiment", String.join("\n\n", errors), 600);
            errors.clear();
            return;
        }

        // Set stopFlag to false so that the rest of the programme knows we're running
        stopFlag = false;

        // Get the values currently entered into the various parameter text-boxes
        double minVG        = minGateT.get();
        double maxVG        = maxGateT.get();
        int    stepsVG      = gateStepsT.get();
        double minSDV       = minSDT.get();
        double maxSDV       = maxSDT.get();
        int    stepsSDV     = sdStepsT.get();
        int    averageCount = countT.get();
        int    delayMSec    = (int) (delayT.get() * 1000); // Convert to milli-seconds
        double intTime      = intTimeT.get();

        // Create arrays of the voltages we are going to use
        double[] gateVoltages = Util.symArray(Util.makeLinearArray(minVG, maxVG, stepsVG));
        double[] sdVoltages   = Util.makeLinearArray(minSDV, maxSDV, stepsSDV);

        // Configure the Source-Drain SMU channel
        smuSD.turnOff();                                           // Make sure we're not outputting anything yet
        smuSD.setVoltage(minSDV);                                  // Source voltage, initial value
        smuSD.useAutoRanges();                                     // Use auto-ranging for both voltage and current
        smuSD.setAveraging(SMU.AMode.MEAN_REPEAT, averageCount);   // Use repeated-mean averaging with user-inputted n
        smuSD.useFourProbe(false);                                 // We only want 2-wire measurements
        smuSD.setIntegrationTime(intTime);                         // Set the integration time

        // Configure the Gate SMU channel
        smuG.turnOff();                                            // Make sure we're not outputting anything yet
        smuG.setVoltage(minVG);                                    // Source voltage, initial value
        smuG.useAutoRanges();                                      // Use auto-ranging for both voltage and current
        smuG.setAveraging(SMU.AMode.MEAN_REPEAT, averageCount);    // Set-up averaging
        smuG.useFourProbe(false);                                  // 2-wire measurements
        smuG.setIntegrationTime(intTime);                          // Set the integration time


        if (useFourProbe) {

            // Configure the 4PP-1 SMU channel to act as a voltmeter
            smu4P1.turnOff();                                         // Make sure we're not outputting yet
            smu4P1.setCurrent(0);                                     // We want to source 0 A of current
            smu4P1.useAutoRanges();                                   // Auto-ranging
            smu4P1.setAveraging(SMU.AMode.MEAN_REPEAT, averageCount); // Averaging mode and count
            smu4P1.useFourProbe(false);                               // 2-wire measurements
            smu4P1.setIntegrationTime(intTime);                       // Set the integration time
            smu4P1.setCurrentRange(10E-12);
            // Configure the 4PP-2 SMU channel to act as a voltmeter
            smu4P2.turnOff();                                         // Make sure we're not outputting yet
            smu4P2.setCurrent(0);                                     // We want to source 0 A of current
            smu4P2.setAveraging(SMU.AMode.MEAN_REPEAT, averageCount); // Averaging mode and count
            smu4P2.useFourProbe(false);                               // 2-wire measurements
            smu4P2.setIntegrationTime(intTime);                       // Set the integration time

            // Enable voltage probes (SMU channels)
            smu4P1.turnOn();
            smu4P2.turnOn();
        }

        // Enable the SMU channels
        smuSD.turnOn();
        smuG.turnOn();

        // mainLoop: is a "label", marking this for loop as being our "mainLoop" so that we can tell Java to break out of
        // this loop when needed
        mainLoop:
        for (double VSD : sdVoltages) {
            smuSD.setVoltage(VSD);
            for (double VG : gateVoltages) {
                smuG.setVoltage(VG);

                // Wait our delay time before measuring
                Thread.sleep(delayMSec);

                transferResults.addData(
                        VSD,
                        VG,
                        smuSD.getCurrent(),
                        smuG.getCurrent(),
                        useFourProbe ? smu4P1.getVoltage() : 0,
                        useFourProbe ? smu4P2.getVoltage() : 0
                );

                // If the stopFlag changes to true, then someone has pressed the Stop button
                if (stopFlag) {
                    break mainLoop; // Break out of mainLoop:
                }

            }

        }

        // Make sure all channels are turned back off
        smuSD.turnOff();
        smuG.turnOff();

        if (useFourProbe) {
            smu4P1.turnOff();
            smu4P2.turnOff();
        }

        // Output our data as a CSV file
        transferResults.output(outputFile);

        // Tell the user we're done
        GUI.infoAlert("Complete", stopFlag ? "Measurement Stopped" : "Measurement Complete", "Transfer curve data saved to:\n" + outputFile, 600);

        // Reset the stop flag
        stopFlag = true;

    }

    /**
     * Performs an output curve characterisation, outputting the data to a CSV file.
     *
     * @throws Exception Upon something going wrong
     */
    private static void doOutput() throws Exception {

        SMU smuSD = Main.smuSD.getSMU();
        SMU smuG  = Main.smuG.getSMU();
        SMU smu4P1 = Main.smu4P1.getSMU();
        SMU smu4P2 = Main.smu4P2.getSMU();

        // Run that config code we stored previously
        applyChannelConfiguration();

        LinkedList<String> errors = new LinkedList<>();

        // If the source-drain and/or gate SMU is not connected/configured then we can't continue
        if (smuSD == null) {
            errors.add("The Source-Drain SMU is not configured.");
        }

        if (smuG == null) {
            errors.add("The Source-Gate SMU is not configured.");
        }

        // If stopFlag == false then another measurement must already be running
        if (!stopFlag) {
            errors.add("Another experiment is already running.\n\nPlease wait until it has finished.");
        }

        // Get the file path entered into the output file text-box field
        String outputFile = fileO.get();

        // If it's blank then we can't continue
        if (outputFile.trim().equals("")) {
            errors.add("You must specify a file to output to.");
        }

        // If there is anything in the errors array, then we cannot continue
        if (!errors.isEmpty()) {
            GUI.errorAlert("Error", "Could Not Start Experiment", String.join("\n\n", errors), 600);
            errors.clear();
            return;
        }

        // Set the stopFlag to indicate we are now running
        stopFlag = false;

        // Get the values currently in the various configuration text-box fields
        double minVG        = minGateO.get();
        double maxVG        = maxGateO.get();
        int    stepsVG      = gateStepsO.get();
        double minSDV       = minSDO.get();
        double maxSDV       = maxSDO.get();
        int    stepsSDV     = sdStepsO.get();
        int    averageCount = countO.get();
        int    delayMSec    = (int) (delayO.get() * 1000); // Convert to milliseconds
        double intTime      = intTimeO.get();

        // Generate arrays of the voltage values we will use
        double[] gateVoltages = Util.makeLinearArray(minVG, maxVG, stepsVG);
        double[] sdVoltages   = Util.makeLinearArray(minSDV, maxSDV, stepsSDV);
        double[] gateVoltagesRev = Util.makeLinearArray(maxVG, minVG, stepsVG);
        double[] sdVoltagesRev   = Util.makeLinearArray(maxSDV, minSDV, stepsSDV);

        // Configure Source-Drain SMU
        smuSD.turnOff();                                         // Make sure we're not outputting yet
        smuSD.setVoltage(minSDV);                                // Source voltage, set initial value
        smuSD.useAutoRanges();                                   // Use auto-ranging for both quantities
        smuSD.setAveraging(SMU.AMode.MEAN_REPEAT, averageCount); // Set averaging to mean and user-defined count
        smuSD.useFourProbe(false);                               // We want 2-wire measurements
        smuSD.setIntegrationTime(intTime);                       // Set the integration time to what the user entered

        // Configure Source-Gate SMU
        smuG.turnOff();                                          // Make sure we're not outputting yet
        smuG.setVoltage(minVG);                                  // Source voltage, set initial value
        smuG.useAutoRanges();                                    // Use auto-ranging for both quantities
        smuG.setAveraging(SMU.AMode.MEAN_REPEAT, averageCount);  // Set averaging to mean and user-defined count
        smuG.useFourProbe(false);                                // We want 2-wire measurements
        smuG.setIntegrationTime(intTime);                        // Set the integration time to what the user entered

        // Enable outputs
        smuSD.turnOn();
        smuG.turnOn();

        // Label outer loop so we can break out of it if needed
        mainLoop:
        for (double VG : gateVoltages) {

            smuG.setVoltage(VG);

            for (double VSD : sdVoltages) {

                smuSD.setVoltage(VSD);

                // Wait our delay time before taking the measurement
                Thread.sleep(delayMSec);

                outputResults.addData(
                        VSD,
                        VG,
                        smuSD.getCurrent(),
                        smuG.getCurrent()
                );

                // If the stop flag has become true then the user has pressed the "Stop" button
                if (stopFlag) {
                    break mainLoop;
                }

            }
            for (double VSD : sdVoltagesRev) {

                smuSD.setVoltage(VSD);

                // Wait our delay time before taking the measurement
                Thread.sleep(delayMSec);

                outputResults.addData(
                        VSD,
                        VG,
                        smuSD.getCurrent(),
                        smuG.getCurrent()
                );

                // If the stop flag has become true then the user has pressed the "Stop" button
                if (stopFlag) {
                    break mainLoop;
                }

            }
        }

        // Turn output back off again
        smuSD.turnOff();
        smuG.turnOff();

        outputResults.output(outputFile);

        GUI.infoAlert("Complete", stopFlag ? "Measurement Stopped" : "Measurement Complete", "Output curve data saved to:\n" + outputFile, 600);
        stopFlag = true;

    }

    public static void main(String[] args) {

        try {
            run(args);
        } catch (Exception e) {
            GUI.errorAlert("Exception", "Exception Thrown", e.getMessage());
            Util.exceptionHandler(e);
        }

    }
}
