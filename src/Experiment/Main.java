package Experiment;

import JISA.Addresses.*;
import JISA.Control.*;
import JISA.Devices.*;
import JISA.Experiment.*;
import JISA.GUI.*;
import JISA.Util;
import JISA.VISA.*;

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
    private static SetGettable<Double>  minGateT;
    private static SetGettable<Double>  maxGateT;
    private static SetGettable<Integer> gateStepsT;
    private static SetGettable<Double>  minSDT;
    private static SetGettable<Double>  maxSDT;
    private static SetGettable<Integer> sdStepsT;
    private static SetGettable<Double>  limitT;
    private static SetGettable<Integer> countT;
    private static SetGettable<Double>  delayT;
    private static SetGettable<Double>  intTimeT;
    private static SetGettable<String>  fileT;
    private static SetGettable<Boolean> fourProbeT;
    private static ResultList           transferResults;

    // ==== Output Curve Fields and Results ============================================================================
    private static SetGettable<Double>  minGateO;
    private static SetGettable<Double>  maxGateO;
    private static SetGettable<Integer> gateStepsO;
    private static SetGettable<Double>  minSDO;
    private static SetGettable<Double>  maxSDO;
    private static SetGettable<Integer> sdStepsO;
    private static SetGettable<Double>  limitO;
    private static SetGettable<Integer> countO;
    private static SetGettable<Double>  delayO;
    private static SetGettable<Double>  intTimeO;
    private static SetGettable<String>  fileO;
    private static ResultList           outputResults;

    // ==== Tabs GUI (main window) =====================================================================================
    private static Tabs tabs;

    private static boolean stopFlag = true;

    // ==== Connection Config Handles ==================================================================================
    private static InstrumentConfig<SMU> smu1;
    private static InstrumentConfig<SMU> smu2;
    private static InstrumentConfig<SMU> smu3;
    private static InstrumentConfig<SMU> smu4;

    // ==== SMUs =======================================================================================================
    private static SMU smuSD  = null;
    private static SMU smuG   = null;
    private static SMU smu4P1 = null;
    private static SMU smu4P2 = null;

    // ==== Config Storage and Set-Up ==================================================================================
    private static ConfigStore config;
    private static Runnable    doConfig;

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
        tabs.show();

    }

    /**
     * Creates the "Transfer Curve" tab in the GUI, for controlling and observing transfer curve measurements.
     *
     * @throws Exception Upon something going wrong
     */
    private static void createTransferSection() throws Exception {

        // Create config panels
        Fields params = new Fields("Experiment Parameters");
        Fields config = new Fields("Configuration");

        // Results displays
        Table table = new Table("Table of Results", transferResults);
        Plot  plot  = new Plot("Transfer Curve", transferResults, 1, 2, 0);

        plot.showMarkers(false);

        // Put them all in a grid
        Grid transferGrid = new Grid("Transfer Curve", params, config, table, plot);

        // Add fields to panels, returning SetGettable objects which allow use to query and set the value in each field
        minGateT = params.addDoubleField("Min Gate [V]");
        maxGateT = params.addDoubleField("Max Gate [V]");
        gateStepsT = params.addIntegerField("No. Steps");

        minSDT = params.addDoubleField("Min SD [V]");
        maxSDT = params.addDoubleField("Max SD [V]");
        sdStepsT = params.addIntegerField("No. Steps");

        limitT = config.addDoubleField("Current Limit [A]");
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

        limitT.set(CURRENT_LIMIT);
        countT.set(AVERAGE_COUNT);
        delayT.set(DELAY_TIME);
        intTimeT.set(INTEGRATION_TIME);

        // Add toolbar buttons
        transferGrid.addToolbarButton("Start Transfer", Main::doTransfer);
        transferGrid.addToolbarButton("Stop Experiment", Main::stopExperiment);
        transferGrid.addToolbarButton("Clear", transferResults::clear);

        transferGrid.setNumColumns(2);
        tabs.addTab(transferGrid);

    }

    /**
     * Creates the "Output Curve" tab in the GUI, for controlling and observing output curve measurements.
     *
     * @throws Exception Upon something going wrong
     */
    private static void createOutputSection() throws Exception {

        // Create config panels
        Fields params = new Fields("Experiment Parameters");
        Fields config = new Fields("Configuration");

        minGateO = params.addDoubleField("Min Gate [V]");
        maxGateO = params.addDoubleField("Max Gate [V]");
        gateStepsO = params.addIntegerField("No. Steps");

        minSDO = params.addDoubleField("Min SD [V]");
        maxSDO = params.addDoubleField("Max SD [V]");
        sdStepsO = params.addIntegerField("No. Steps");

        limitO = config.addDoubleField("Current Limit [A]");
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

        limitO.set(CURRENT_LIMIT);
        countO.set(AVERAGE_COUNT);
        delayO.set(DELAY_TIME);
        intTimeO.set(INTEGRATION_TIME);

        Table table = new Table("Table of Results", outputResults);
        Plot  plot  = new Plot("Output Curve", outputResults, 0, 2, 1);
        Grid  grid  = new Grid("Output Curve", params, config, table, plot);

        plot.showMarkers(false);
        grid.addToolbarButton("Start Output", Main::doOutput);
        grid.addToolbarButton("Stop Experiment", Main::stopExperiment);
        grid.addToolbarButton("Clear", outputResults::clear);

        grid.setNumColumns(2);

        tabs.addTab(grid);


    }

    /**
     * Creates the "Connection Config" tab in the GUI for configuring how to connect to each instrument.
     */
    private static void createConnectionSection() throws Exception {

        // Create a ConfigGrid - a grid of Instrument Connection configuration panels
        ConfigGrid grid = new ConfigGrid("Connection Config", config);
        grid.setNumColumns(1);

        // Add instruments to configure, returns GetSettable objects for the relevant instrument objects
        smu1 = grid.addInstrument("SMU 1", SMU.class);
        smu2 = grid.addInstrument("SMU 2", SMU.class);
        smu3 = grid.addInstrument("SMU 3", SMU.class);
        smu4 = grid.addInstrument("SMU 4", SMU.class);

        // Add this section to the tabs
        tabs.addTab(grid);

        // Attempt to connect with whatever config has been loaded from our config storage
        grid.connectAll();

    }

    /**
     * Creates the "Instrument Config" tab in the GUI for configuring which SMU channel should be used for what.
     */
    private static void createConfigSection() throws Exception {

        // Create panel for Source-Drain configuration
        Fields sourceDrain = new Fields("Source-Drain SMU");

        // Adding a choice box with the options of "SMU1", "SMU2", "SMU3", and "SMU4". Will return an integer when queried
        // representing which option was chosen (0 for SMU1, 3 for SMU4 etc).
        SetGettable<Integer> sdSMU     = sourceDrain.addChoice("SMU", "SMU 1", "SMU 2", "SMU 3", "SMU 4");
        SetGettable<Integer> sdChannel = sourceDrain.addIntegerField("Channel Number");

        // If the relevant config entries exist in our config storage, then load them in
        if (config.has("SDSMU") && config.has("SDCHN")) {
            sdSMU.set(config.getInt("SDSMU"));
            sdChannel.set(config.getInt("SDCHN"));
        } else {
            sdSMU.set(0);
            sdChannel.set(0);
        }

        // Add "Apply" button
        sourceDrain.addButton("Apply", () -> smuSD = applyButton("SD", sdSMU, sdChannel, true));

        // Rinse-and-repeat
        Fields               sourceGate = new Fields("Source-Gate SMU");
        SetGettable<Integer> sgSMU      = sourceGate.addChoice("SMU", "SMU 1", "SMU 2", "SMU 3", "SMU 4");
        SetGettable<Integer> sgChannel  = sourceGate.addIntegerField("Channel Number");

        if (config.has("SGSMU") && config.has("SGCHN")) {
            sgSMU.set(config.getInt("SGSMU"));
            sgChannel.set(config.getInt("SGCHN"));
        } else {
            sgSMU.set(1);
            sgChannel.set(0);
        }

        sourceGate.addButton("Apply", () -> smuG = applyButton("SG", sgSMU, sgChannel, true));

        Fields               fourPoint1 = new Fields("Four-Point-Probe 1");
        SetGettable<Integer> fp1SMU     = fourPoint1.addChoice("SMU", "SMU 1", "SMU 2", "SMU 3", "SMU 4");
        SetGettable<Integer> fp1Channel = fourPoint1.addIntegerField("Channel Number");

        if (config.has("FP1SMU") && config.has("FP1CHN")) {
            fp1SMU.set(config.getInt("FP1SMU"));
            fp1Channel.set(config.getInt("FP1CHN"));
        } else {
            fp1SMU.set(2);
            fp1Channel.set(0);
        }

        fourPoint1.addButton("Apply", () -> smu4P1 = applyButton("FP1", fp1SMU, fp1Channel, true));

        Fields               fourPoint2 = new Fields("Four-Point-Probe 2");
        SetGettable<Integer> fp2SMU     = fourPoint2.addChoice("SMU", "SMU 1", "SMU 2", "SMU 3", "SMU 4");
        SetGettable<Integer> fp2Channel = fourPoint2.addIntegerField("Channel Number");

        if (config.has("FP2SMU") && config.has("FP2CHN")) {
            fp2SMU.set(config.getInt("FP2SMU"));
            fp2Channel.set(config.getInt("FP2CHN"));
        } else {
            fp2SMU.set(3);
            fp2Channel.set(0);
        }

        fourPoint2.addButton("Apply", () -> smu4P2 = applyButton("FP2", fp2SMU, fp2Channel, true));

        Grid grid = new Grid("Instrument Config", sourceDrain, sourceGate, fourPoint1, fourPoint2);
        grid.setNumColumns(1);

        tabs.addTab(grid);

        // Keep this for later when we want to apply all at once
        doConfig = () -> {

            try {
                smuSD = applyButton("SD", sdSMU, sdChannel, false);
                smuG = applyButton("SG", sgSMU, sgChannel, false);
                smu4P1 = applyButton("FP1", fp1SMU, fp1Channel, false);
                smu4P2 = applyButton("FP2", fp2SMU, fp2Channel, false);
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }

        };

    }

    private static SMU applyButton(String tag, SetGettable<Integer> smuI, SetGettable<Integer> channelI, boolean message) throws Exception {

        config.set(tag + "SMU", smuI.get());
        config.set(tag + "CHN", channelI.get());

        // Store SMUs in array for easy access
        InstrumentConfig[] SMUs = new InstrumentConfig[]{smu1, smu2, smu3, smu4};

        // If the index is not between 0 and 3, then something's wrong (we only have four configurable SMUs)
        if (smuI.get() < 0 || smuI.get() > 3) {
            if (message) {
                GUI.errorAlert("Error", "Select SMU", "Please select an SMU.");
            }
            return null;
        }

        // Get the SMU that the user has selected
        SMU smu = (SMU) SMUs[smuI.get()].get();

        // It will be null if it has not been successfully connected yet
        if (smu == null) {
            if (message) {
                GUI.errorAlert("Error", "Not Connected", "That SMU is not connected!");
            }
            return null;
        }

        // Get the channel number the user has specified
        int channel = channelI.get();

        // If it's a multi-channel SMU then we need to use the channel to number to select the correct channel
        if (smu instanceof MCSMU) {

            if (channel >= ((MCSMU) smu).getNumChannels() || channel < 0) {
                if (message) {
                    GUI.errorAlert("Error", "Invalid Channel", "That SMU does not have that channel.");
                }
                return null;
            }

            return ((MCSMU) smu).getChannel(channel);

        } else {
            return smu;
        }
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

    private static void applyChannelConfiguration() {
        doConfig.run();
    }

    /**
     * Performs a transfer curve characterisation, outputting the data to a CSV file.
     *
     * @throws Exception Upon something going wrong
     */
    private static void doTransfer() throws Exception {

        // Make sure we have applied our config before running
        doConfig.run();

        // Do we want to use four-point-probe measurements?
        boolean useFourProbe = fourProbeT.get();

        // Create an array list to put errors in
        LinkedList<String> errors = new LinkedList<>();

        // Check that the source-drain and source-gate SMUs are connected and configured
        if (smuSD == null || smuG == null) {
            errors.add("Source-Drain and Source-Gate SMUs are not configured.");
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
            GUI.errorAlert("Error", "Could Not Start Experiment", String.join("\n\n", errors));
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
        double currentLimit = limitT.get();
        int    averageCount = countT.get();
        int    delayMSec    = (int) (delayT.get() * 1000); // Convert to milli-seconds
        double intTime      = intTimeT.get();

        // Create arrays of the voltages we are going to use
        double[] gateVoltages = Util.makeLinearArray(minVG, maxVG, stepsVG);
        double[] sdVoltages   = Util.makeLinearArray(minSDV, maxSDV, stepsSDV);

        // Configure the Source-Drain SMU channel
        smuSD.turnOff();                                           // Make sure we're not outputting anything yet
        smuSD.setVoltage(minSDV);                                  // Source voltage, initial value
        smuSD.setLimits(1.1 * maxSDV, currentLimit);               // Set the compliance limits
        smuSD.useAutoRanges();                                     // Use auto-ranging for both voltage and current
        smuSD.setAveraging(SMU.AMode.MEAN_REPEAT, averageCount);   // Use repeated-mean averaging with user-inputted n
        smuSD.useFourProbe(false);                                 // We only want 2-wire measurements
        smuSD.setIntegrationTime(intTime);                         // Set the integration time

        // Configure the Gate SMU channel
        smuG.turnOff();                                            // Make sure we're not outputting anything yet
        smuG.setVoltage(minVG);                                    // Source voltage, initial value
        smuG.setLimits(1.1 * maxVG, currentLimit);                 // Set the compliance limits
        smuG.useAutoRanges();                                      // Use auto-ranging for both voltage and current
        smuG.setAveraging(SMU.AMode.MEAN_REPEAT, averageCount);    // Set-up averaging
        smuG.useFourProbe(false);                                  // 2-wire measurements
        smuG.setIntegrationTime(intTime);                          // Set the integration time


        if (useFourProbe) {

            // Configure the 4PP-1 SMU channel to act as a voltmeter
            smu4P1.turnOff();                                         // Make sure we're not outputting yet
            smu4P1.setCurrent(0);                                     // We want to source 0 A of current
            smu4P1.setLimits(maxSDV, 0.0);                            // Limit current to 0
            smu4P1.useAutoRanges();                                   // Auto-ranging
            smu4P1.setAveraging(SMU.AMode.MEAN_REPEAT, averageCount); // Averaging mode and count
            smu4P1.useFourProbe(false);                               // 2-wire measurements
            smu4P1.setIntegrationTime(intTime);                       // Set the integration time

            // Configure the 4PP-2 SMU channel to act as a voltmeter
            smu4P2.turnOff();                                         // Make sure we're not outputting yet
            smu4P2.setCurrent(0);                                     // We want to source 0 A of current
            smu4P2.setLimits(maxSDV, 0.0);                            // Limit current to 0
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

        // Run that config code we stored previously
        doConfig.run();

        LinkedList<String> errors = new LinkedList<>();

        // If the source-drain and/or gate SMU is not connected/configured then we can't continue
        if (smuSD == null || smuG == null) {
            errors.add("Source-Drain and Source-Gate SMUs are not configured.");
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
            GUI.errorAlert("Error", "Could Not Start Experiment", String.join("\n\n", errors));
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
        double currentLimit = limitO.get();
        int    averageCount = countO.get();
        int    delayMSec    = (int) (delayO.get() * 1000); // Convert to milliseconds
        double intTime      = intTimeO.get();

        // Generate arrays of the voltage values we will use
        double[] gateVoltages = Util.makeLinearArray(minVG, maxVG, stepsVG);
        double[] sdVoltages   = Util.makeLinearArray(minSDV, maxSDV, stepsSDV);

        // Configure Source-Drain SMU
        smuSD.turnOff();                                         // Make sure we're not outputting yet
        smuSD.setVoltage(minSDV);                                // Source voltage, set initial value
        smuSD.useAutoRanges();                                   // Use auto-ranging for both quantities
        smuSD.setLimits(1.1 * maxSDV, currentLimit);             // Set the compliance limits
        smuSD.setAveraging(SMU.AMode.MEAN_REPEAT, averageCount); // Set averaging to mean and user-defined count
        smuSD.useFourProbe(false);                               // We want 2-wire measurements
        smuSD.setIntegrationTime(intTime);                       // Set the integration time to what the user entered

        // Configure Source-Gate SMU
        smuG.turnOff();                                          // Make sure we're not outputting yet
        smuG.setVoltage(minVG);                                  // Source voltage, set initial value
        smuG.useAutoRanges();                                    // Use auto-ranging for both quantities
        smuG.setLimits(1.1 * maxVG, currentLimit);               // Set the compliance limits
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
